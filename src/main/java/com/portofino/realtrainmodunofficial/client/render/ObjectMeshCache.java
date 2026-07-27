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
 * 設置オブジェクト (標識/看板/信号/踏切) の焼き込みキャッシュ。<b>本家 RTM のディスプレイリスト方式の移植</b>。
 *
 * <h2>本家の作り</h2>
 * <p>本家 {@code RailPartsRenderer.renderRailStatic} (KaizPatchX) はこうなっている:
 * <pre>
 *   hasList = prepareStaticDisplayList(te);                       //無ければ生成
 *   if (hasList &amp;&amp; te.shouldRerenderRail &amp;&amp; !shouldRefreshDisplayList(te))
 *       te.shouldRerenderRail = false;                            //★キーが同じなら焼き直さずフラグだけ下ろす
 *   if (!hasList || te.shouldRerenderRail) compileStaticRailParts(te);
 *   if (hasList) renderStaticDisplayList(te);                     //push→translate→bindTexture→callList→pop
 *
 *   shouldRefreshDisplayList = createStaticRenderKey(形状, 明るさ) != te.getStaticRenderKey(i)
 *   createStaticRenderKey    = 31*(31*deepHashCode(形状) + hashCode(明るさ)) + モデルキー
 * </pre>
 * 要点は 3 つ:
 * <ol>
 *   <li>焼き先は<b>ブロックエンティティ側</b>に持つ (te.glLists)</li>
 *   <li>無効化は<b>内容キーの比較</b>。「変わったかもしれない」フラグだけでは焼き直さない</li>
 *   <li><b>テクスチャの bind はリストの外</b> (本家コメント「ディスプレイリストに入れると生成重い」)</li>
 * </ol>
 *
 * <h2>1.21 での対応物</h2>
 * <p>ディスプレイリスト → {@link com.mojang.blaze3d.vertex.VertexBuffer} (VBO)。
 * {@code glTranslate + glCallList} → {@code vbo.drawWithShader(ModelView × pose, ...)}。
 * どちらも<b>頂点の CPU 処理がゼロ</b>で、GPU に置いた頂点を行列 1 本で描き直す点が同じ。
 * テクスチャは {@link net.minecraft.client.renderer.RenderType} が持つので、本家と同じく焼き込みの外側にある。
 *
 * <p>キーは本家の「形状 + 明るさ + モデル」ではなく {@link jp.ngt.ngtlib.renderer.GLRecorder#contentKey()}
 * (スクリプトが吐いた描画コマンドそのもの) + 明るさを使う。設置物のスクリプトは点滅・スクロール・可動部を
 * 自前で進めるので入力を数え上げられない。<b>出力を見れば取りこぼしが起きない</b>ため、
 * 「アニメが止まる」という以前の失敗をしない。スクリプト自体は毎フレーム走らせたままで、
 * 削るのは頂点提出のほうである (スクリプト実行は実測で列車コストの 1%)。
 *
 * <p>描画は {@link RailDrawQueue} へ渡す。焼き込み VBO を RenderType ごとに 1 回だけ
 * ステート設定してまとめて描く仕組みが既にあるので、レールと同じ土俵に載せたほうが速い。
 */
public final class ObjectMeshCache {

    /** 保持する焼き込みの上限。超えたら古いものから VBO を解放する。 */
    private static final int MAX_ENTRIES = 1024;
    /** 1 つの焼き込みで許す頂点数。異常に大きいものは焼かず CPU 経路に任せる。 */
    private static final int MAX_VERTICES = 1 << 20;
    /**
     * 1 フレームに焼く数の上限。視界に大量の設置物が入った瞬間のスパイクを散らす
     * (レール側 {@code RailMeshCache.MAX_BAKES_PER_FRAME} と同じ考え方)。
     */
    private static final int MAX_BAKES_PER_FRAME = 8;
    /**
     * 何フレーム連続で内容が変わったら「可動物」と見なすか。
     *
     * <p>本家は {@code renderRailStatic} と {@code renderRailDynamic} を<b>作る側が</b>分けていて、
     * 動く部分はそもそも焼き込みに入れない。RTMU はスクリプトの中身を見て静的/可動を判別できないので、
     * <b>実際に毎フレーム変わり続けたら可動物と判定して焼くのをやめる</b>。
     * 焼き直しは捕捉 + アップロードを伴うので、毎フレーム変わる物を焼くと CPU 提出より高くつく。
     */
    private static final int DYNAMIC_AFTER = 4;
    /** 可動物と判定した後、再び焼けるか試すまでのフレーム数 (点滅が止まった信号を拾い直す)。 */
    private static final int DYNAMIC_RETRY_FRAMES = 200;

    private static int bakesThisFrame;
    private static int frameCounter;

    private static final class Entry {
        int key;
        boolean hasKey;
        List<MeshCapture.Section> sections = List.of();
        /** 連続で内容が変わった回数。 */
        int churn;
        /** 可動物と判定した (焼かない)。 */
        boolean dynamic;
        /** 可動物判定を解除して再挑戦するフレーム。 */
        int retryAtFrame;

        void close() {
            for (MeshCapture.Section s : sections) {
                s.close();
            }
            sections = List.of();
            hasKey = false;
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
     * 焼き込みを描く。本家 {@code renderRailStatic} と同じ流れ。
     *
     * @param key   焼き直し判定キー (本家 createStaticRenderKey 相当)
     * @param baker キーが変わったときだけ呼ばれる。渡された {@link MultiBufferSource} へ
     *              <b>単位行列基準 (ブロックエンティティ原点) で</b>描画すること。
     *              カメラ相対の pose を掛けて焼くと、カメラが動いた瞬間にずれる
     * @return true = 描画を予約した (呼び出し元は CPU 経路で描かない)
     */
    public static boolean draw(BlockEntity be, PoseStack poseStack, int key,
                               Consumer<MultiBufferSource> baker) {
        if (be == null || poseStack == null || baker == null) {
            return false;
        }
        //シェーダーパック使用中に焼き込みを使うかは設定次第 (既定 OFF)。
        //ここで諦めておかないと、描けもしないのに焼くだけ焼いて捨てることになる。
        if (!RailDrawQueue.vboAllowed()) {
            return false;
        }

        Entry entry = CACHE.computeIfAbsent(be, k -> new Entry());

        //可動物と判定済み: 焼かずに CPU 経路へ返す。ときどき再挑戦する。
        if (entry.dynamic) {
            if (frameCounter - entry.retryAtFrame < 0) {
                return false;
            }
            entry.dynamic = false;
            entry.churn = 0;
        }

        //★本家の要点そのもの: キーが同じなら焼き直さない。
        boolean valid = entry.hasKey && entry.key == key && isUsable(entry.sections);
        if (!valid) {
            //★視界に大量に入った瞬間のスパイクを散らす。ここを先に見るのが要点で、
            //  枠待ちを「内容が変わった」と数えると、設置物が多い場面ほど可動物と誤判定されて
            //  いちばん効かせたい所でキャッシュが外れる。
            if (bakesThisFrame >= MAX_BAKES_PER_FRAME) {
                //焼き済みがあるなら 1 フレーム古いものを描いて次へ回す (消さない)。
                //無いならこのフレームだけ CPU 経路に任せる。
                if (!isUsable(entry.sections)) {
                    return false;
                }
            } else if (entry.hasKey && ++entry.churn >= DYNAMIC_AFTER) {
                //焼き直しが続く = 中身が動き続けている。焼くほうが高くつくので手を引く。
                entry.close();
                entry.dynamic = true;
                entry.retryAtFrame = frameCounter + DYNAMIC_RETRY_FRAMES;
                return false;
            } else {
                bakesThisFrame++;
                entry.close();
                List<MeshCapture.Section> baked = bake(baker);
                if (baked == null) {
                    //焼けなかった (頂点ゼロ/大きすぎ)。以降は CPU 経路に任せる。
                    entry.dynamic = true;
                    entry.retryAtFrame = frameCounter + DYNAMIC_RETRY_FRAMES;
                    return false;
                }
                entry.sections = baked;
                entry.key = key;
                entry.hasKey = true;
            }
        } else {
            entry.churn = 0;
        }
        com.portofino.realtrainmodunofficial.perf.RtmuProfiler.addObject(valid);

        //本家 renderStaticDisplayList: push → translate → bindTexture (リストの外) → callList → pop。
        //ここでは RenderType がテクスチャを持ち、pose が translate にあたる。
        boolean drewAny = false;
        for (MeshCapture.Section s : entry.sections) {
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
        //ワールドを移ったら全部捨てる。ここは BlockEntity を強参照で持つので、
        //捨てないと前のワールドのブロックエンティティと VBO を掴んだままになる
        //(レール側 RailMeshCache.dropIfLevelChanged と同じ)。
        net.minecraft.world.level.Level level = net.minecraft.client.Minecraft.getInstance().level;
        if (level != lastLevel) {
            clear();
            lastLevel = level;
        }
        //壊された設置物を落とす。毎フレーム全部見ると重いので少しずつ。
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
        //★レール側 (RailMeshCache.bake) と同じ。captureMode 中は MqoModelLoader の
        //静的 VBO 直描画を止めて、頂点が必ずこの Source へ流れるようにする。
        //これが無いと一部のバッチが単位行列のまま即座に描かれてしまう。
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
