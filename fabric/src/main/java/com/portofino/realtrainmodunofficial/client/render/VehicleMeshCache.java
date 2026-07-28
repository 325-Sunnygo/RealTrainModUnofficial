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
 * 車両の不透明パス (pass0) と発光パスの焼き込みキャッシュ。
 * 本家 RTM のディスプレイリスト方式の移植。
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
     * 焼いたメッシュ 1 つ。形が同じ車両どうしで共有する。
     * 以前は「車両 1 両につき 1 つ」だったので、10 両編成では中身が完全に同じ VBO を
     * 10 本焼いて 10 両ぶんの VRAM を使っていた。
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
     * 「毎フレーム絵が変わる車両か」の判定は車両ごとに持つ。
     * メッシュ側を状態ごとに分けたので、走行中は毎フレーム別のキーになる。
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
     * @param key   焼き直し判定キー (本家 createStaticRenderKey 相当)
     * @param baker キーが変わったときだけ呼ばれる。単位行列基準で描くこと
     * @return true = 描画した (呼び出し元は replay しない)
     */
    public static boolean draw(Object entity, int slot, PoseStack poseStack, int key,
                               Consumer<MultiBufferSource> baker) {
        return draw(entity, entity, slot, poseStack, key, baker);
    }

    /**
     * 焼き込みで描く。
     * entity を渡すと従来どおり 1 両ごとに焼く
     * @param shape 同じ絵になる車両どうしで共有する単位 (車両定義 ID 等)。
     * @param key   焼き直し判定キー (本家 createStaticRenderKey 相当)
     * @param baker キーが変わったときだけ呼ばれる。単位行列基準で描くこと
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
            // ★キーが変わったのに焼き直せないときは古い絵を描かない。
            // 以前はここで前フレームのメッシュを描いて次へ回していたが、枠が空かない限り
            // 古い絵のまま固まる。踏切のランプが「音は鳴るのに点滅しない」のはこれ。
            if (mesh != null) {
                mesh.close();
                MESHES.remove(meshKey);
            }
            if (bakesThisFrame >= MAX_BAKES_PER_FRAME) {
                return false;
            }
            // この車両にとって「前回と違うキー」なら、絵が動いている可能性を数える。
            // 別の車が既に焼いたメッシュに当たっただけなら数えない (共有の恩恵を潰さないため)。
            if (churn.hasKey && churn.lastKey != key && ++churn.count >= DYNAMIC_AFTER) {
                // 走行中・ドア開閉中など、毎フレーム絵が変わる車両。焼くほうが高くつく。
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
            // 同じ絵が続いている = 止まっている車両
            churn.count = 0;
        }
        churn.lastKey = key;
        churn.hasKey = true;
        // 台数の集計は通常パスだけで取る (発光パスまで数えると命中率が読めなくなる)。
        if (slot == 0) {
            com.portofino.realtrainmodunofficial.perf.RtmuProfiler.addVehicle(valid);
        }

        return drawSections(mesh.sections, poseStack);
    }

    /**
     * その場で描く。
     * (push → translate → bindTexture はリストの外 → callList → pop)。
     */
    private static boolean drawSections(List<MeshCapture.Section> sections, PoseStack poseStack) {
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix())
            .mul(poseStack.last().pose());
        Matrix4f projection = RenderSystem.getProjectionMatrix();
        // ★焼き込みは単位行列で録るので、頂点法線は車両ローカルのまま。
        // 平行光の向きを車両ローカルへ回してから描く (CPU 経路と同じ陰影になる)。
        // これが無いと、焼き込みで描く車体と CPU で描く可動部 (ドア) で
        // 明るさが食い違う (向きによってドアだけ陰影が乗らない)。
        org.joml.Vector3f origL0 = null;
        org.joml.Vector3f origL1 = null;
        java.lang.reflect.Field field = lightDirsField();
        if (field != null) {
            try {
                if (field.get(null) instanceof org.joml.Vector3f[] dirs
                        && dirs.length >= 2 && dirs[0] != null && dirs[1] != null) {
                    origL0 = new org.joml.Vector3f(dirs[0]);
                    origL1 = new org.joml.Vector3f(dirs[1]);
                    org.joml.Matrix3f invRot =
                        new org.joml.Matrix3f(poseStack.last().normal()).transpose();
                    RenderSystem.setShaderLights(
                        invRot.transform(new org.joml.Vector3f(origL0)),
                        invRot.transform(new org.joml.Vector3f(origL1)));
                }
            } catch (Throwable ignored) {
                origL0 = null;
                origL1 = null;
            }
        }
        boolean drew = false;
        try {
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
        } finally {
            if (origL0 != null) {
                RenderSystem.setShaderLights(origL0, origL1);
            }
        }
        return drew;
    }

    /** RenderSystem.shaderLightDirections。取れなければ null (補正なしで描く)。 */
    private static java.lang.reflect.Field shaderLightDirsField;
    private static boolean shaderLightDirsFailed;

    private static java.lang.reflect.Field lightDirsField() {
        if (shaderLightDirsField != null || shaderLightDirsFailed) {
            return shaderLightDirsField;
        }
        try {
            java.lang.reflect.Field f = RenderSystem.class.getDeclaredField("shaderLightDirections");
            f.setAccessible(true);
            shaderLightDirsField = f;
        } catch (Throwable t) {
            shaderLightDirsFailed = true;
        }
        return shaderLightDirsField;
    }

    private static List<MeshCapture.Section> bake(Consumer<MultiBufferSource> baker) {
        MeshCapture.Source source = new MeshCapture.Source();
        // レール/設置物と同じ作法。captureMode 中は静的 VBO 直描画を止めて、
        // 頂点が必ずこの Source へ流れるようにする。
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
        // 焼き直しの判定も白紙に戻す (捨てた直後に「動いている車両」と誤判定しないため)
        CHURN.clear();
    }
}
