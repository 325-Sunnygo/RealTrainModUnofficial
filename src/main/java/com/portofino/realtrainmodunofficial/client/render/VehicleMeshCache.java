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
 * 車両の<b>不透明パス (pass0)</b> の焼き込みキャッシュ。本家 RTM のディスプレイリスト方式の移植。
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
 * <p>半透明 (窓) と発光は順番が結果を変えるので焼かない。本家が
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
    private static final int MAX_BAKES_PER_FRAME = 4;
    /** 何フレーム連続で内容が変わったら「動いている車両」と見なして焼くのをやめるか。 */
    private static final int DYNAMIC_AFTER = 3;
    /** 動いていると判定した後、再挑戦するまでのフレーム数。 */
    private static final int DYNAMIC_RETRY_FRAMES = 40;

    private static int bakesThisFrame;
    private static int frameCounter;

    private static final class Entry {
        int key;
        boolean hasKey;
        List<MeshCapture.Section> sections = List.of();
        int churn;
        boolean dynamic;
        int retryAtFrame;

        void close() {
            for (MeshCapture.Section s : sections) {
                s.close();
            }
            sections = List.of();
            hasKey = false;
        }
    }

    private static final Map<Object, Entry> CACHE =
        new LinkedHashMap<>(32, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Object, Entry> eldest) {
                if (size() > MAX_ENTRIES) {
                    eldest.getValue().close();
                    return true;
                }
                return false;
            }
        };

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
    public static boolean draw(Object entity, PoseStack poseStack, int key,
                               Consumer<MultiBufferSource> baker) {
        if (entity == null || poseStack == null || baker == null) {
            return false;
        }
        //シェーダーパック使用中は直接描画を使わない (レール/設置物と同じ判断)。
        if (!RailDrawQueue.vboAllowed()) {
            return false;
        }

        Entry entry = CACHE.computeIfAbsent(entity, k -> new Entry());

        if (entry.dynamic) {
            if (frameCounter - entry.retryAtFrame < 0) {
                return false;
            }
            entry.dynamic = false;
            entry.churn = 0;
        }

        boolean valid = entry.hasKey && entry.key == key && isUsable(entry.sections);
        if (!valid) {
            if (bakesThisFrame >= MAX_BAKES_PER_FRAME) {
                //枠待ち。焼き済みがあれば 1 フレーム古いものを描いて次へ回す。
                //★枠待ちを churn に数えないこと (編成が長いほど誤判定して効かなくなる)。
                if (!isUsable(entry.sections)) {
                    return false;
                }
            } else if (entry.hasKey && ++entry.churn >= DYNAMIC_AFTER) {
                //走行中・ドア開閉中など、毎フレーム絵が変わる車両。焼くほうが高くつく。
                entry.close();
                entry.dynamic = true;
                entry.retryAtFrame = frameCounter + DYNAMIC_RETRY_FRAMES;
                return false;
            } else {
                bakesThisFrame++;
                entry.close();
                List<MeshCapture.Section> baked = bake(baker);
                if (baked == null) {
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
        com.portofino.realtrainmodunofficial.perf.RtmuProfiler.addVehicle(valid);

        return drawSections(entry.sections, poseStack);
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
        for (Entry e : CACHE.values()) {
            e.close();
        }
        CACHE.clear();
    }
}
