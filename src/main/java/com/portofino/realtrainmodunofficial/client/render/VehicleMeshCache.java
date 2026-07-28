package com.portofino.realtrainmodunofficial.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 車両の<b>不透明パス (pass0) と発光パス</b>の焼き込みキャッシュ。
 * 本家 RTM のディスプレイリスト方式の移植。
 *
 * <h2>本家の方式</h2>
 * <p>{@link ObjectMeshCache} と同じ。本家 {@code RailPartsRenderer.renderRailStatic} は
 * 「内容キーが変わっていなければ焼き直さず、GPU に置いた頂点をそのまま描く」。
 * ディスプレイリスト → {@link VertexBuffer}、{@code glTranslate + glCallList} →
 * {@code drawWithShader(ModelView × pose, ...)} と対応する。どちらも<b>頂点の CPU 処理がゼロ</b>。
 *
 * <h2>なぜ pass0 だけなのか</h2>
 * <p>本家 {@code RenderVehicleBase.doRender} は pass0 (不透明) → 発光 → pass1 (半透明) の順に描く。
 * このうち<b>pass0 は不透明しか出さない</b> ({@code renderNamedGroups} は translucent=false で呼ばれる)。
 * 不透明は深度テストで前後が決まるので、<b>描く順番が変わっても結果が変わらない</b>。
 * だからバッファを介さずその場で直接描いてよい。
 *
 * <p>発光パスも焼く。焼いた VBO は<b>その場で即座に描かれる</b>ので、
 * 「pass0 → 発光 → 半透明」という本家の順序はむしろ正確に再現される
 * (バッファ経由だと RenderType 単位でまとめられるため、コード側が flushBatch で
 * 順序を作り直していた)。
 *
 * <p>発光パスを焼くもう一つの理由は<b>変換経路を揃える</b>ためである。焼いた頂点は
 * {@code (ModelView × pose) × v} を GPU で計算するが、CPU 提出は {@code ModelView × (pose × v)}
 * になる。float では数 ULP ずれるので、pass0 だけ焼いて発光を CPU に残すと、同じ面なのに
 * 深度が一致せず点灯時にちらつく。これは {@code MqoModelLoader.PIN_CPU_TRANSFORM} が
 * 立っている理由そのものなので、車両については両方 GPU 側へ揃える。
 *
 * <p>半透明 (窓) は順番が結果を変えるうえ後回しキューに乗るので焼かない。本家が
 * {@code renderRailStatic} / {@code renderRailDynamic} を分けているのと同じ考え方で、
 * <b>順序に依存しない部分だけ</b>を焼き込みに入れる。
 *
 * <h2>キャッシュが外れる条件</h2>
 * <p>キーは {@link jp.ngt.ngtlib.renderer.GLRecorder#contentKey()} (スクリプトが吐いた描画コマンド)
 * + 明るさ + 除外パーツ + 発光被覆グループ。以前ここにあった
 * 「完全静止した車両だけキャッシュする」判定 ({@code isFullyStatic}) は、スクリプトが自前で
 * 進めるアニメ (SR1 のパンタ等) を検出できず「0.5 秒に 1 コマ」のカクつきを出したため撤去された。
 * <b>出力そのものをキーにすれば、何が動いていようと取りこぼさない</b>ので同じ失敗をしない。
 * スクリプトは毎フレーム実行したままで、削るのは頂点提出のほうである。
 */
public final class VehicleMeshCache {

    /** 保持する焼き込みの上限 (車両数)。 */
    private static final int MAX_ENTRIES = 256;
    /** 1 つの焼き込みで許す頂点数。 */
    private static final int MAX_VERTICES = 1 << 20;
    /** 1 フレームに焼く数の上限。編成が視界に入った瞬間のスパイクを散らす。 */
    private static final int MAX_BAKES_PER_FRAME = 8;
    /** 1 両あたりの焼き込み枠。0 = 通常パス、1〜3 = 発光パス (前照灯/尾灯/室内灯)。 */
    private static final int SLOTS = 4;
    /** 何フレーム連続で内容が変わったら「動いている車両」と見なして焼くのをやめるか。 */
    private static final int DYNAMIC_AFTER = 3;
    /** 動いていると判定した後、再挑戦するまでのフレーム数。 */
    private static final int DYNAMIC_RETRY_FRAMES = 40;

    private static int bakesThisFrame;
    private static int frameCounter;

    /**
     * 焼いたメッシュ 1 つ。<b>形が同じ車両どうしで共有する。</b>
     *
     * <p>以前は「車両 1 両につき 1 つ」だったので、10 両編成では<b>中身が完全に同じ VBO を
     * 10 本</b>焼いて 10 両ぶんの VRAM を使っていた。同じ車両定義・同じ状態なら絵は同一なので、
     * 1 本を全車で使い回す。編成が長いほど効く。
     *
     * <p>明るさは頂点に焼き込まれるため、先頭がトンネル・後ろが屋外のように
     * <b>明るさが違う車は別のメッシュ</b>になる (それでも同じ明るさの車どうしは共有される)。
     */
    private static final class Mesh {
        List<MeshCapture.Section> sections = List.of();

        void close() {
            for (MeshCapture.Section s : sections) {
                s.close();
            }
            sections = List.of();
        }
    }

    /** メッシュを引くキー。「どの形を」「どの枠で」「どの状態で」焼いたか。 */
    private record MeshKey(Object shape, int slot, int key) {
    }

    /**
     * 「毎フレーム絵が変わる車両か」の判定は<b>車両ごとに</b>持つ。
     *
     * <p>メッシュ側を状態ごとに分けたので、走行中は毎フレーム別のキーになる。
     * ここを共有してしまうと「キーが変わった」を検知できず、<b>毎フレーム新しい VBO を
     * 焼き続ける</b>ことになる (一番やってはいけない壊れ方)。だから判定だけは車両ごと。
     */
    private static final class Churn {
        int lastKey;
        boolean hasKey;
        int count;
        boolean dynamic;
        int retryAtFrame;
    }

    private static final Map<MeshKey, Mesh> MESHES =
        new LinkedHashMap<>(64, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<MeshKey, Mesh> eldest) {
                if (size() > MAX_ENTRIES) {
                    eldest.getValue().close();
                    return true;
                }
                return false;
            }
        };

    /** 車両 (弱参照) → 枠ごとの churn 判定。車両が消えれば一緒に消える。 */
    private static final Map<Object, Churn[]> CHURN = new java.util.WeakHashMap<>();

    private VehicleMeshCache() {
    }

    /** 1 フレームの焼き込み枠をリセットする。 */
    public static void beginFrame() {
        bakesThisFrame = 0;
        frameCounter++;
    }

    /**
     * pass0 を焼き込みで描く。
     *
     * @param key   焼き直し判定キー (本家 createStaticRenderKey 相当)
     * @param baker キーが変わったときだけ呼ばれる。<b>単位行列基準</b>で描くこと
     * @return true = 描画した (呼び出し元は replay しない)
     */
    public static boolean draw(Object entity, int slot, PoseStack poseStack, int key,
                               Consumer<MultiBufferSource> baker) {
        return draw(entity, entity, slot, poseStack, key, baker);
    }

    /**
     * 焼き込みで描く。
     *
     * @param shape 同じ絵になる車両どうしで<b>共有する単位</b> (車両定義 ID 等)。
     *              {@code entity} を渡すと従来どおり 1 両ごとに焼く
     * @param key   焼き直し判定キー (本家 createStaticRenderKey 相当)
     * @param baker キーが変わったときだけ呼ばれる。<b>単位行列基準</b>で描くこと
     * @return true = 描画した (呼び出し元は CPU 経路へ落とさない)
     */
    public static boolean draw(Object shape, Object entity, int slot, PoseStack poseStack, int key,
                               Consumer<MultiBufferSource> baker) {
        if (shape == null || entity == null || poseStack == null || baker == null
                || slot < 0 || slot >= SLOTS) {
            return false;
        }

        Churn[] churnSlots = CHURN.computeIfAbsent(entity, k -> new Churn[SLOTS]);
        Churn churn = churnSlots[slot];
        if (churn == null) {
            churn = new Churn();
            churnSlots[slot] = churn;
        }

        if (churn.dynamic) {
            if (frameCounter - churn.retryAtFrame < 0) {
                return false;
            }
            churn.dynamic = false;
            churn.count = 0;
        }

        MeshKey meshKey = new MeshKey(shape, slot, key);
        Mesh mesh = MESHES.get(meshKey);
        boolean valid = mesh != null && isUsable(mesh.sections);
        if (!valid) {
            //★キーが変わったのに焼き直せないときは<b>古い絵を描かない</b>。
            //  以前はここで前フレームのメッシュを描いて次へ回していたが、枠が空かない限り
            //  古い絵のまま固まる。踏切のランプが「音は鳴るのに点滅しない」のはこれ。
            //  焼けないフレームは CPU 経路へ落とす。1 フレームぶん重いだけで絵は必ず合う。
            if (mesh != null) {
                mesh.close();
                MESHES.remove(meshKey);
            }
            if (bakesThisFrame >= MAX_BAKES_PER_FRAME) {
                return false;
            }
            //この車両にとって「前回と違うキー」なら、絵が動いている可能性を数える。
            //別の車が既に焼いたメッシュに当たっただけなら数えない (共有の恩恵を潰さないため)。
            if (churn.hasKey && churn.lastKey != key && ++churn.count >= DYNAMIC_AFTER) {
                //走行中・ドア開閉中など、毎フレーム絵が変わる車両。焼くほうが高くつく。
                churn.dynamic = true;
                churn.retryAtFrame = frameCounter + DYNAMIC_RETRY_FRAMES;
                return false;
            }
            bakesThisFrame++;
            List<MeshCapture.Section> baked = bake(baker);
            if (baked == null) {
                churn.dynamic = true;
                churn.retryAtFrame = frameCounter + DYNAMIC_RETRY_FRAMES;
                return false;
            }
            mesh = new Mesh();
            mesh.sections = baked;
            MESHES.put(meshKey, mesh);
        } else if (churn.hasKey && churn.lastKey == key) {
            //同じ絵が続いている = 止まっている車両
            churn.count = 0;
        }
        churn.lastKey = key;
        churn.hasKey = true;
        //台数の集計は通常パスだけで取る (発光パスまで数えると命中率が読めなくなる)。
        if (slot == 0) {
            com.portofino.realtrainmodunofficial.perf.RtmuProfiler.addVehicle(valid);
        }

        return drawSections(mesh.sections, poseStack);
    }

    /**
     * その場で描く。本家 {@code renderStaticDisplayList} と同じ
     * (push → translate → bindTexture はリストの外 → callList → pop)。
     *
     * <p>不透明なので描画順は結果に影響しない。バッファへ積まず直接描く。
     */
    private static boolean drawSections(List<MeshCapture.Section> sections, PoseStack poseStack) {
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix())
            .mul(poseStack.last().pose());
        Matrix4f projection = RenderSystem.getProjectionMatrix();
        boolean drew = false;
        for (MeshCapture.Section s : sections) {
            VertexBuffer vbo = s.vbo();
            if (vbo == null || vbo.isInvalid()) {
                continue;
            }
            RenderType type = s.renderType();
            type.setupRenderState();
            try {
                ShaderInstance shader = RenderSystem.getShader();
                if (shader == null) {
                    continue;
                }
                vbo.bind();
                vbo.drawWithShader(modelView, projection, shader);
                VertexBuffer.unbind();
                drew = true;
            } catch (Throwable t) {
                com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.warn(
                    "Vehicle mesh draw failed", t);
            } finally {
                type.clearRenderState();
            }
        }
        return drew;
    }

    private static List<MeshCapture.Section> bake(Consumer<MultiBufferSource> baker) {
        MeshCapture.Source source = new MeshCapture.Source();
        //レール/設置物と同じ作法。captureMode 中は静的 VBO 直描画を止めて、
        //頂点が必ずこの Source へ流れるようにする。
        boolean prevCapture = com.portofino.realtrainmodunofficial.client.model.MqoModelLoader.captureMode;
        com.portofino.realtrainmodunofficial.client.model.MqoModelLoader.captureMode = true;
        try {
            baker.accept(source);
        } catch (Throwable t) {
            com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.warn(
                "Vehicle mesh bake failed", t);
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

    /** リソースリロード・シェーダー切替時などに全部捨てる。 */
    public static void clear() {
        for (Mesh m : MESHES.values()) {
            m.close();
        }
        MESHES.clear();
        //焼き直しの判定も白紙に戻す (捨てた直後に「動いている車両」と誤判定しないため)
        CHURN.clear();
    }
}
