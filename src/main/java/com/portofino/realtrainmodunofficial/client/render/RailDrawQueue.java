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
 * レールの統合メッシュ (VBO) を <b>RenderType ごとにまとめて</b> 描く。
 *
 * <p>統合メッシュ化 ({@link RailMeshCache}) 自体は効いていて、レール 1 本は数回の VBO 描画で
 * 済むようになっていた。それでも重かったのは、その数回のたびに
 * <pre>
 *   renderType.setupRenderState();   // テクスチャ bind / ブレンド / シェーダ / 深度 …
 *   vbo.bind(); vbo.drawWithShader(...); VertexBuffer.unbind();
 *   renderType.clearRenderState();   // 後始末
 * </pre>
 * を丸ごと 1 セット走らせていたため。GL のステート変更は 1 回が重く、描画呼び出しの回数より
 * <b>ステートを切り替えた回数</b>が効く。視界に 24 本 × セクション数ぶん繰り返せば、それだけで
 * フレーム時間の半分が消える (実測 0.21ms/本 → 24 本で 5ms/frame)。
 *
 * <p>そこで描画をその場では行わず、ここに積む。ブロックエンティティの描画が終わった時点で
 * RenderType ごとに束ね、<b>ステート設定は種類ごとに 1 回だけ</b>にして VBO を連続で描く。
 * 頂点も行列も既存コードが作ったものをそのまま使うので、見た目は変わらない。
 */
@EventBusSubscriber(modid = RealTrainModUnofficial.MODID, value = Dist.CLIENT)
public final class RailDrawQueue {

    /**
     * 1 本ぶんの描画予約。
     *
     * @param modelView カメラ行列 × レールの pose (積んだ時点で確定させる。PoseStack は
     *                  使い回されるので参照を持つとフレーム内で書き換わる)
     * @param normal    法線行列。null = 単位行列 (光源方向の補正が不要)
     */
    private record Draw(VertexBuffer vbo, Matrix4f modelView, Matrix3f normal) {
    }

    private static final Map<RenderType, List<Draw>> QUEUE = new LinkedHashMap<>();

    private RailDrawQueue() {
    }

    /**
     * レール 1 セクションの描画を予約する。
     *
     * @return true = 予約した (呼び出し元は即時描画しない)
     */
    public static boolean enqueue(VertexBuffer vbo, RenderType renderType, PoseStack poseStack) {
        if (vbo == null || vbo.isInvalid() || renderType == null) {
            return false;
        }
        //シェーダーパック使用中はこの経路を使わない (RailMeshCache 側で弾いているが二重に守る)。
        //Iris がコアシェーダーを差し替えているため、VBO を直接描くと行列が噛み合わず
        //レールが画面に貼り付いてしまう。
        if (com.portofino.realtrainmodunofficial.client.ShaderCompat.isShaderPackInUse()) {
            return false;
        }
        PoseStack.Pose pose = poseStack.last();
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.pose());
        //レールの pose は「カメラ相対の平行移動」だけで回転が無いことがほとんど。
        //その場合は光源方向の補正が要らないので、行列も uniform 転送も丸ごと省く。
        Matrix3f normalMatrix = pose.normal();
        Matrix3f normal = isIdentity(normalMatrix) ? null : new Matrix3f(normalMatrix);
        QUEUE.computeIfAbsent(renderType, t -> new ArrayList<>()).add(new Draw(vbo, modelView, normal));
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
     * <p>
     * 元はブロックエンティティの描画中に即時描画していた。この段階は不透明ブロックと
     * エンティティの後・半透明ブロックの前なので、描画順は実質変わらない。
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            return;
        }
        //レール焼き込みの「1フレーム上限」枠を毎フレームここでリセットする
        //(この段階はブロックエンティティ描画=drawBaked 呼び出しの直後・毎フレーム1回)。
        RailMeshCache.beginFrame();
        flush();
    }

    private static void flush() {
        if (QUEUE.isEmpty()) {
            return;
        }
        Matrix4f projection = RenderSystem.getProjectionMatrix();
        Vector3f[] savedLights = null;
        //焼き込み VBO は drawWithShader で直接描く。この段階 (AFTER_BLOCK_ENTITIES) では
        //ライトマップ層 (Sampler2) が OFF のことがあり、頂点に焼いたライト座標が効かず
        //レールがフルブライト (夜でも明るい) になる。明示的に ON にしてから描く。
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
                //★ ここが要点: ステート設定は RenderType ごとに 1 回だけ
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
                            //直前のレールで光源を回していたら戻す
                            RenderSystem.setShaderLights(savedLights[0], savedLights[1]);
                            savedLights = null;
                        }
                        draw.vbo().bind();
                        draw.vbo().drawWithShader(draw.modelView(), projection, shader);
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
