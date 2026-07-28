package com.portofino.realtrainmodunofficial.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.client.ClientRenderProfiler;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * レールの統合メッシュ (VBO) を RenderType ごとにまとめて 描く。
 * 統合メッシュ化 (RailMeshCache) 自体は効いていて、レール 1 本は数回の VBO 描画で
 * 済むようになっていた。
 */
@EventBusSubscriber(modid = RealTrainModUnofficial.MODID, value = Dist.CLIENT)
public final class RailDrawQueue {

    /**
     * 1 本ぶんの描画予約。
     * 使い回されるので参照を持つとフレーム内で書き換わる)
     * @param modelView カメラ行列 × レールの pose (積んだ時点で確定させる。PoseStack は
     * @param normal    法線行列。null = 単位行列 (光源方向の補正が不要)
     */
    private record Draw(VertexBuffer vbo, Matrix4f modelView, Matrix4f pose, Matrix3f normal) {
    }

    // ★焼き込みは影 mod の有無に関係なく常に使う。
    // 以前はシェーダー使用中だけ焼き込みを止めていた。

    private static final Map<RenderType, List<Draw>> QUEUE = new LinkedHashMap<>();

    private RailDrawQueue() {
    }

    /**
     * レール 1 セクションの描画を予約する。
     * @return true = 予約した (呼び出し元は即時描画しない)
     */
    public static boolean enqueue(VertexBuffer vbo, RenderType renderType, PoseStack poseStack) {
        if (vbo == null || vbo.isInvalid() || renderType == null) {
            return false;
        }
        PoseStack.Pose pose = poseStack.last();
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.pose());
        // Iris 経路では「グローバルの ModelView に積む」ため、pose 単体も持っておく。
        Matrix4f localPose = new Matrix4f(pose.pose());
        // レールの pose は「カメラ相対の平行移動」だけで回転が無いことがほとんど。
        // その場合は光源方向の補正が要らないので、行列も uniform 転送も丸ごと省く。
        Matrix3f normalMatrix = pose.normal();
        Matrix3f normal = isIdentity(normalMatrix) ? null : new Matrix3f(normalMatrix);
        QUEUE.computeIfAbsent(renderType, t -> new ArrayList<>()).add(
            new Draw(vbo, modelView, localPose, normal));
        return true;
    }

    private static boolean isIdentity(Matrix3f m) {
        return m.m00() == 1.0F && m.m11() == 1.0F && m.m22() == 1.0F
            && m.m01() == 0.0F && m.m02() == 0.0F
            && m.m10() == 0.0F && m.m12() == 0.0F
            && m.m20() == 0.0F && m.m21() == 0.0F;
    }

    /**
     * ブロックエンティティの描画が終わった直後に、溜めたレールをまとめて描く。
     * 元はブロックエンティティの描画中に即時描画していた。
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // エンティティ描画より前の段階で「いまワールドを描いている」印を付ける。
        // モデル選択画面のプレビューはこの外で描かれるので、そちらは遅延させない。
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
            // シェーダーパックの ON/OFF を 1 フレームに 1 回だけ引き直す (判定はリフレクション)。
            // 切り替わると頂点フォーマットが変わりうるので、焼いてあるものは全部捨てて焼き直す。
            int prevEpoch = com.portofino.realtrainmodunofficial.client.ShaderCompat.epoch();
            com.portofino.realtrainmodunofficial.client.ShaderCompat.refresh();
            if (com.portofino.realtrainmodunofficial.client.ShaderCompat.epoch() != prevEpoch) {
                RailMeshCache.clear();
                ObjectMeshCache.clear();
                VehicleMeshCache.clear();
            }
            VehicleScriptRenderers.beginLevelRender();
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            return;
        }
        // レール焼き込みの「1フレーム上限」枠を毎フレームここでリセットする
        // (この段階はブロックエンティティ描画=drawBaked 呼び出しの直後・毎フレーム1回)。
        RailMeshCache.beginFrame();
        // 設置物の焼き込みも同じ枠でスパイクを散らす (ObjectMeshCache)
        ObjectMeshCache.beginFrame();
        VehicleMeshCache.beginFrame();
        RailScriptRenderers.beginFrame();
        flush();
        // ★レールを描き終えた「あと」に車両の半透明を描く。
        // 本家は Forge 描画パス 1 (地形半透明・パス0の TileEntity のあと) で
        // renderBodyTransparent を呼ぶので、レールはガラスより先に描き終わっている。
        VehicleScriptRenderers.flushDeferredTranslucent();
    }

    private static void flush() {
        if (QUEUE.isEmpty()) {
            return;
        }
        Matrix4f projection = RenderSystem.getProjectionMatrix();
        boolean shaderPack = com.portofino.realtrainmodunofficial.client.ShaderCompat.active();
        Vector3f[] savedLights = null;
        // 焼き込み VBO は drawWithShader で直接描く。この段階 (AFTER_BLOCK_ENTITIES) では
        // ライトマップ層 (Sampler2) が OFF のことがあり、頂点に焼いたライト座標が効かず
        // レールがフルブライト (夜でも明るい) になる。明示的に ON にしてから描く。
        net.minecraft.client.renderer.LightTexture lightTex =
            net.minecraft.client.Minecraft.getInstance().gameRenderer.lightTexture();
        lightTex.turnOnLightLayer();
        try {
            for (Map.Entry<RenderType, List<Draw>> entry : QUEUE.entrySet()) {
                RenderType renderType = entry.getKey();
                List<Draw> draws = entry.getValue();
                if (draws.isEmpty()) {
                    continue;
                }
                // ★ ここが要点: ステート設定は RenderType ごとに 1 回だけ
                renderType.setupRenderState();
                ClientRenderProfiler.countRailStateSetup();
                try {
                    ShaderInstance shader = RenderSystem.getShader();
                    if (shader == null) {
                        continue;
                    }
                    for (Draw draw : draws) {
                        if (draw.vbo().isInvalid()) {
                            continue;
                        }
                        if (draw.normal() != null) {
                            if (savedLights == null) {
                                savedLights = currentShaderLights();
                            }
                            applyRotatedLights(savedLights, draw.normal());
                        } else if (savedLights != null) {
                            // 直前のレールで光源を回していたら戻す
                            RenderSystem.setShaderLights(savedLights[0], savedLights[1]);
                            savedLights = null;
                        }
                        draw.vbo().bind();
                        if (shaderPack) {
                            // ★Iris 経路。
                            // drawWithShader(modelView, ...) は shader.MODEL_VIEW_MATRIX に値を入れて
                            // apply するが、Iris の ExtendedShader は apply で自前が捕まえた
                            // グローバルの行列を上書きしてしまう。
                            org.joml.Matrix4fStack mvStack = RenderSystem.getModelViewStack();
                            mvStack.pushMatrix();
                            mvStack.mul(draw.pose());
                            RenderSystem.applyModelViewMatrix();
                            try {
                                draw.vbo().drawWithShader(
                                    RenderSystem.getModelViewMatrix(), projection, shader);
                            } finally {
                                mvStack.popMatrix();
                                RenderSystem.applyModelViewMatrix();
                            }
                        } else {
                            draw.vbo().drawWithShader(draw.modelView(), projection, shader);
                        }
                        ClientRenderProfiler.countRailVboDraw();
                    }
                    VertexBuffer.unbind();
                } finally {
                    renderType.clearRenderState();
                }
            }
        } catch (Throwable t) {
            RealTrainModUnofficial.LOGGER.warn("Rail merged draw failed", t);
        } finally {
            if (savedLights != null) {
                RenderSystem.setShaderLights(savedLights[0], savedLights[1]);
            }
            lightTex.turnOffLightLayer();
            QUEUE.clear();
        }
    }

    // ---- 光源方向 (回転のあるレールだけで使う) ----

    private static java.lang.reflect.Field shaderLightDirsField;
    private static boolean shaderLightDirsFailed;

    private static Vector3f[] currentShaderLights() {
        if (shaderLightDirsFailed) {
            return null;
        }
        try {
            if (shaderLightDirsField == null) {
                shaderLightDirsField = RenderSystem.class.getDeclaredField("shaderLightDirections");
                shaderLightDirsField.setAccessible(true);
            }
            Object dirs = shaderLightDirsField.get(null);
            if (dirs instanceof Vector3f[] array && array.length >= 2
                && array[0] != null && array[1] != null) {
                return new Vector3f[]{new Vector3f(array[0]), new Vector3f(array[1])};
            }
        } catch (Throwable t) {
            shaderLightDirsFailed = true;
        }
        return null;
    }

    private static void applyRotatedLights(Vector3f[] saved, Matrix3f normal) {
        if (saved == null) {
            return;
        }
        Matrix3f invRot = new Matrix3f(normal).transpose();
        RenderSystem.setShaderLights(
            invRot.transform(new Vector3f(saved[0])),
            invRot.transform(new Vector3f(saved[1])));
    }
}
