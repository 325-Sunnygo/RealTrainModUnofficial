package com.portofino.realtrainmodunofficial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 設置オブジェクト (標識/看板/信号/踏切) の焼き込みキャッシュ。本家 RTM のディスプレイリスト方式の移植。
 * 本家の作り
 * 本家 RailPartsRenderer.renderRailStatic (KaizPatchX) はこうなっている:
 * hasList = prepareStaticDisplayList(te);                       //無ければ生成
 * if (hasList && te.shouldRerenderRail && !shouldRefreshDisplayList(te))
 * te.shouldRerenderRail = false;                            //★キーが同じなら焼き直さずフラグだけ下ろす
 * if (!hasList || te.shouldRerenderRail) compileStaticRailParts(te);
 * if (hasList) renderStaticDisplayList(te);                     //push→translate→bindTexture→callList→pop
 */
public final class ObjectMeshCache {

    /** 保持する焼き込みの上限。超えたら古いものから VBO を解放する。 */
    private static final int MAX_ENTRIES = 1024;
    /** 1 つの焼き込みで許す頂点数。異常に大きいものは焼かず CPU 経路に任せる。 */
    private static final int MAX_VERTICES = 1 << 20;
    /**
     * 1 フレームに焼く数の上限。視界に大量の設置物が入った瞬間のスパイクを散らす
     * (レール側 RailMeshCache.MAX_BAKES_PER_FRAME と同じ考え方)。
     */
    private static final int MAX_BAKES_PER_FRAME = 8;


    private static int bakesThisFrame;
    private static int frameCounter;

    /**
     * 1 つの設置物が保持できる「向き違いの焼き込み」の数。
     * 回転灯 (24 ポーズ) / ミラーボール (360 ポーズ) を丸ごと保持できる値にしてある。
     * これを超えて新しいキーが来続けるものは「連続変化」と見なして焼くのをやめる (dynamic)。
     */
    private static final int POSES_PER_ENTRY = 1024;

    /**
     * これより頂点が多いモデルは「向き別キャッシュ」の対象外にする。
     * 1 ポーズの焼き込みが重い巨大モデルを毎フレーム焼くと、そのままクライアントが
     * 固まってしまう (MSE の 4.9MB MQO 等)。回転灯/ミラーボールは数千頂点なので対象内。
     */
    private static final int MAX_POSES_VERTICES = 16384;

    /** 何フレーム連続で内容が変わったら「可動物」と見なすか。 */
    private static final int DYNAMIC_AFTER = 4;
    /** 可動物と判定した後、再び焼けるか試すまでのフレーム数。 */
    private static final int DYNAMIC_RETRY_FRAMES = 200;

    /** 焼いたセクションの総頂点数。 */
    private static int vertexCountOf(List<MeshCapture.Section> sections) {
        int n = 0;
        for (MeshCapture.Section s : sections) {
            n += s.vertexCount();
        }
        return n;
    }

    private static final class Entry {
        /**
         * 内容キー → 焼き込み。本家のディスプレイリストは「形を焼いて回転は行列」なので
         * 同じ形の向き違いは 1 回焼けば使い回せる。RTMU は向き込みで焼くため、
         * 向きごとに焼き込みを保持して再焼きを無くす (回転灯/ミラーボール対策)。
         */
        final Map<Integer, List<MeshCapture.Section>> poses =
            new LinkedHashMap<>(8, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, List<MeshCapture.Section>> eldest) {
                    if (size() > POSES_PER_ENTRY) {
                        for (MeshCapture.Section s : eldest.getValue()) {
                            s.close();
                        }
                        return true;
                    }
                    return false;
                }
            };
        /** 焼いたメッシュが大きすぎる (巨大モデル) なら true。向き別キャッシュにしない。 */
        boolean big;
        /** 連続で内容が変わった回数。 */
        int churn;
        /** 可動物と判定した (焼かない)。 */
        boolean dynamic;
        /** 可動物判定を解除して再挑戦するフレーム。 */
        int retryAtFrame;

        void close() {
            for (List<MeshCapture.Section> sections : poses.values()) {
                for (MeshCapture.Section s : sections) {
                    s.close();
                }
            }
            poses.clear();
        }
    }

    /**
     * 焼き込みの置き場。本家は te.glLists だが、RTMU はブロックエンティティに手を入れずに済むよう
     * ここで持つ。アクセス順で上限を切り、あふれたぶんは VBO を解放する (描画スレッドで起きる)。
     */
    private static final Map<BlockEntity, Entry> CACHE =
        new LinkedHashMap<>(64, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<BlockEntity, Entry> eldest) {
                if (size() > MAX_ENTRIES) {
                    eldest.getValue().close();
                    return true;
                }
                return false;
            }
        };

    private ObjectMeshCache() {
    }

    /**
     * 焼き込みを描く。本家 renderRailStatic と同じ流れ。
     * 単位行列基準 (ブロックエンティティ原点) で描画すること。
     * @param key   焼き直し判定キー (本家 createStaticRenderKey 相当)
     * @param baker キーが変わったときだけ呼ばれる。渡された MultiBufferSource へ
     * @return true = 描画を予約した (呼び出し元は CPU 経路で描かない)
     */
    public static boolean draw(BlockEntity be, PoseStack poseStack, int key,
                               Consumer<MultiBufferSource> baker) {
        if (be == null || poseStack == null || baker == null) {
            return false;
        }

        // ★ シェーダーパック使用中は焼き込み VBO を使わない。
        //   この VBO は RailDrawQueue 経由 (CPU 合成 ModelView + drawWithShader) で描かれるため、
        //   Iris では行列が一致せず設置物が引き伸ばされる。false を返すと呼び出し側
        //   (InstalledObjectScriptCache / MachineScriptRenderers) が毎フレーム即時描画する。
        if (com.portofino.realtrainmodunofficial.client.ShaderCompat.active()) {
            return false;
        }

        Entry entry = CACHE.computeIfAbsent(be, k -> new Entry());

        // 可動物と判定済み: 焼かずに CPU 経路へ返す。ときどき再挑戦する。
        if (entry.dynamic) {
            if (frameCounter - entry.retryAtFrame < 0) {
                return false;
            }
            entry.dynamic = false;
            entry.churn = 0;
        }

        // ★本家の要点そのもの: キーが同じなら焼き直さない。
        // ★回転灯/ミラーボールは向きごとにキーが変わるが、向きの数は有限なので
        //   「向きごとに焼いて保持」する (poses)。これで再焼きが起きなくなる。
        // ★ただし巨大モデル (MSE 等) は 1 ポーズが重すぎるので向き別キャッシュにしない。
        //   向き別に持つと毎フレーム焼き続けてクライアントが固まる (統合サーバーが
        //   "Can't keep up!" を出す)。その場合は 1 ポーズだけ保持し、
        //   内容が変わり続けるなら焼くのをやめる (dynamic)。
        List<MeshCapture.Section> sections = entry.poses.get(key);
        if (sections == null || !isUsable(sections)) {
            if (bakesThisFrame >= MAX_BAKES_PER_FRAME) {
                return false;
            }
            int poseCap = entry.big ? 1 : POSES_PER_ENTRY;
            if (entry.poses.size() >= poseCap) {
                if (++entry.churn >= DYNAMIC_AFTER) {
                    entry.close();
                    entry.dynamic = true;
                    entry.retryAtFrame = frameCounter + DYNAMIC_RETRY_FRAMES;
                    return false;
                }
            } else {
                entry.churn = 0;
            }
            bakesThisFrame++;
            List<MeshCapture.Section> baked = bake(baker);
            if (baked == null) {
                // 焼けなかった (頂点ゼロ/大きすぎ)。CPU 経路に任せる。
                entry.dynamic = true;
                entry.retryAtFrame = frameCounter + DYNAMIC_RETRY_FRAMES;
                return false;
            }
            if (vertexCountOf(baked) > MAX_POSES_VERTICES) {
                // 巨大モデル: これ以降は向き別キャッシュせず 1 ポーズだけ持つ。
                entry.close();
                entry.big = true;
            }
            entry.poses.put(key, baked);
            sections = baked;
        } else {
            entry.churn = 0;
        }
        // 本家 renderStaticDisplayList: push → translate → bindTexture (リストの外) → callList → pop。
        // ここでは RenderType がテクスチャを持ち、pose が translate にあたる。
        boolean drewAny = false;
        for (MeshCapture.Section s : sections) {
            if (RailDrawQueue.enqueue(s.vbo(), s.renderType(), poseStack)) {
                drewAny = true;
            }
        }
        return drewAny;
    }

    /** 1 フレームの焼き込み枠をリセットする (描画開始時に 1 回)。 */
    public static void beginFrame() {
        bakesThisFrame = 0;
        frameCounter++;
        // ワールドを移ったら全部捨てる。ここは BlockEntity を強参照で持つので、
        // 捨てないと前のワールドのブロックエンティティと VBO を掴んだままになる
        // (レール側 RailMeshCache.dropIfLevelChanged と同じ)。
        net.minecraft.world.level.Level level = net.minecraft.client.Minecraft.getInstance().level;
        if (level != lastLevel) {
            clear();
            lastLevel = level;
        }
        // 壊された設置物を落とす。毎フレーム全部見ると重いので少しずつ。
        if ((frameCounter & 63) == 0) {
            CACHE.entrySet().removeIf(e -> {
                if (e.getKey().isRemoved()) {
                    e.getValue().close();
                    return true;
                }
                return false;
            });
        }
    }

    private static net.minecraft.world.level.Level lastLevel;

    /** 焼く。呼び出し元の描画をそのまま捕まえて RenderType ごとの VBO にする。 */
    private static List<MeshCapture.Section> bake(Consumer<MultiBufferSource> baker) {
        MeshCapture.Source source = new MeshCapture.Source();
        // ★レール側 (RailMeshCache.bake) と同じ。
        // 静的 VBO 直描画を止めて、頂点が必ずこの Source へ流れるようにする。
        boolean prevCapture = com.portofino.realtrainmodunofficial.client.model.MqoModelLoader.captureMode;
        com.portofino.realtrainmodunofficial.client.model.MqoModelLoader.captureMode = true;
        try {
            baker.accept(source);
        } catch (Throwable t) {
            com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.warn(
                "Object mesh bake failed", t);
            return null;
        } finally {
            com.portofino.realtrainmodunofficial.client.model.MqoModelLoader.captureMode = prevCapture;
        }
        long vertices = source.totalVertices();
        if (vertices <= 0L || vertices > MAX_VERTICES) {
            return null;
        }
        List<MeshCapture.Section> sections = new ArrayList<>(source.upload());
        return sections.isEmpty() ? null : sections;
    }

    private static boolean isUsable(List<MeshCapture.Section> sections) {
        if (sections.isEmpty()) {
            return false;
        }
        for (MeshCapture.Section s : sections) {
            if (s.vbo() == null || s.vbo().isInvalid()) {
                return false;
            }
        }
        return true;
    }

    /** 明示的に捨てる (設置物が壊された/モデルが差し替わった等)。 */
    public static void invalidate(BlockEntity be) {
        Entry entry = CACHE.remove(be);
        if (entry != null) {
            entry.close();
        }
    }

    /** リソースリロード時などに全部捨てる。 */
    public static void clear() {
        for (Entry e : CACHE.values()) {
            e.close();
        }
        CACHE.clear();
    }
}
