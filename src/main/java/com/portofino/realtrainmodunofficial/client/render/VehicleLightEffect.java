package com.portofino.realtrainmodunofficial.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

/** 前照灯・尾灯の光の表現。 本家 jp.ngt.rtm.entity.vehicle.RenderV… */
public final class VehicleLightEffect extends RenderStateShard {

    private VehicleLightEffect(String name, Runnable setup, Runnable clear) {
        super(name, setup, clear);
    }

    /** 本家 RenderVehicleBase.DAYLIGHT_LIMIT。 */
    private static final int DAYLIGHT_LIMIT = 7;
    /** 本家 PartsRenderer.DIV_NUM。 */
    private static final int DIV_NUM = 32;
    private static final float ANGLE = 360.0F / DIV_NUM;
    /** 本家 PartsRenderer.BRIGHTNESS_RATE。 */
    private static final double BRIGHTNESS_RATE = 1.0D / 256.0D;
    /** 本家 RenderVehicleBase が renderLightEffectS へ渡す根元半径。 */
    private static final float ROOT_RADIUS = 0.0625F;
    /** 本家 RenderVehicleBase が renderLightEffectS へ渡す光錐の長… */
    private static final float CONE_LENGTH = 16.0F;

    /** 本家の glBlendFunc(GL_SRC_ALPHA, GL_ONE) + glDepthMa… */
    private static final TransparencyStateShard ADDITIVE = new TransparencyStateShard(
        "rtmu_light_effect_additive",
        () -> {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        },
        () -> {
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
        });

    private static final RenderType LIGHT_EFFECT = RenderType.create(
        "rtmu_light_effect",
        DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.QUADS, 1536, false, true,
        RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
            .setTransparencyState(ADDITIVE)
            .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
            .setCullState(RenderStateShard.NO_CULL)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .createCompositeState(false));

    /** 本家 RenderVehicleBase.renderLightEffect(vehicle, m… */
    public static void render(VehicleDefinition def, int mode, int dir, boolean frontFree, boolean rearFree,
                              double worldX, double worldY, double worldZ, float yaw, float pitch,
                              int ambientLight, PoseStack poseStack, MultiBufferSource buffer) {
        if (def == null) {
            return;
        }
        List<VehicleDefinition.LightDefinition> headLights = def.getHeadLights();
        List<VehicleDefinition.LightDefinition> tailLights = def.getTailLights();
        // 本家: headLights か tailLights のどちらかが未定義なら何も描かない
        if (headLights.isEmpty() || tailLights.isEmpty() || mode <= 0) {
            return;
        }

        int renderModeHead;
        int renderModeTail;
        boolean singleTrain = def.isSingleTrain() && frontFree && rearFree;
        if (singleTrain) {
            renderModeHead = (mode == 1) ? dir : (mode == 2 ? 2 : -1);
            renderModeTail = (mode == 1) ? 1 - dir : (mode == 2 ? 2 : -1);
        } else {
            renderModeHead = ((mode == 1 && frontFree) || mode == 2) ? 0 : -1;
            renderModeTail = ((mode == 1 && !frontFree && rearFree) || mode == 2) ? 0 : -1;
        }

        // 光軸 (ワールド空間)。本家は (0,0,1) を rotateAroundY(yaw) → ro…
        Vector3f normal = rotate(0.0F, 0.0F, 1.0F, yaw, pitch);

        VertexConsumer consumer = buffer.getBuffer(LIGHT_EFFECT);
        if (renderModeHead >= 0) {
            for (VehicleDefinition.LightDefinition light : headLights) {
                if (light.type() == 1 && ambientLight > DAYLIGHT_LIMIT) {
                    continue;
                }
                renderLight(consumer, poseStack, normal, light, renderModeHead,
                    worldX, worldY, worldZ, yaw, pitch);
            }
        }
        // ★AppleExtended はここも headLights を回す (尾灯ループの写し間違い)。
        if (renderModeTail >= 0) {
            for (VehicleDefinition.LightDefinition light : headLights) {
                if (light.type() == 1 && ambientLight > DAYLIGHT_LIMIT) {
                    continue;
                }
                renderLight(consumer, poseStack, normal, light, renderModeTail,
                    worldX, worldY, worldZ, yaw, pitch);
            }
        }
    }

    /** 本家 RenderVehicleBase.renderLightEffect(vehicle, n… */
    private static void renderLight(VertexConsumer consumer, PoseStack poseStack, Vector3f normal,
                                    VehicleDefinition.LightDefinition light, int mode,
                                    double worldX, double worldY, double worldZ, float yaw, float pitch) {
        if (mode == 2) {
            // 両側。手前向きと奥向きの 2 回
            renderLight(consumer, poseStack, normal, light, 0, worldX, worldY, worldZ, yaw, pitch);
            renderLight(consumer, poseStack, normal, light, 1, worldX, worldY, worldZ, yaw, pitch);
            return;
        }
        Vec3 pos = light.position();
        if (pos == null) {
            return;
        }
        // 視線ベクトル用の灯具のワールド座標
        Vector3f offset = rotate((float) pos.x, (float) pos.y, (float) pos.z, yaw, pitch);
        double lx = worldX + offset.x;
        double ly = worldY + offset.y;
        double lz = worldZ + offset.z;

        poseStack.pushPose();
        poseStack.translate(pos.x, pos.y, mode == 0 ? pos.z : -pos.z);
        if (mode == 1) {
            // 反対側の灯具は Z を反転して奥向きにする
            poseStack.scale(1.0F, 1.0F, -1.0F);
        }
        renderLightEffectS(consumer, poseStack.last().pose(), normal, lx, ly, lz,
            light.radius(), ROOT_RADIUS, CONE_LENGTH, light.color(), light.type(), mode == 1);
        poseStack.popPose();
    }

    /** 本家 PartsRenderer.renderLightEffectS。 */
    private static void renderLightEffectS(VertexConsumer consumer, Matrix4f mat, Vector3f normal,
                                           double x, double y, double z, float rL, float rS, float length,
                                           int color, int type, boolean reverse) {
        Vector3f viewerVec = viewerVec(x, y, z);
        float viewerAngle = (float) Math.toDegrees(angleBetween(normal, viewerVec));
        if (reverse) {
            viewerAngle = Mth.wrapDegrees(viewerAngle + 180.0F);
        }
        if (viewerAngle > 90.0F) {
            viewerAngle = 180.0F - viewerAngle;
        }

        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;

        if (type == 0) {
            // 視線が光軸から 45 度を超えると縮み、90 度で消える
            float lightStrength = 1.0F;
            if (viewerAngle > 45.0F) {
                lightStrength = (90.0F - viewerAngle) / 45.0F;
            }
            float r = rL * lightStrength;
            // 本家は GL_TRIANGLE_FAN。QUADS なので中心を 2 回積んで三角形 1 枚ぶんに…
            for (int i = 0; i < DIV_NUM; ++i) {
                float rad0 = (float) Math.toRadians(i * ANGLE);
                float rad1 = (float) Math.toRadians((i + 1) * ANGLE);
                vertex(consumer, mat, 0.0F, 0.0F, 0.0F, red, green, blue, 0xFF);
                vertex(consumer, mat, Mth.cos(rad0) * r, Mth.sin(rad0) * r, 0.0F, 0, 0, 0, 0);
                vertex(consumer, mat, Mth.cos(rad1) * r, Mth.sin(rad1) * r, 0.0F, 0, 0, 0, 0);
                vertex(consumer, mat, 0.0F, 0.0F, 0.0F, red, green, blue, 0xFF);
            }
        } else if (type == 1) {
            float angle = (float) Math.toDegrees(Math.atan2(rL, length));
            float distance = viewerVec.lengthSquared();

            float brightness;
            if (viewerAngle < angle) {
                brightness = 1.0F - (viewerAngle / angle);
            } else {
                float b0 = (viewerAngle - angle) / (90.0F - angle);
                float b1 = (float) ((double) distance * BRIGHTNESS_RATE);
                if (b1 > 1.0F) {
                    b1 = 1.0F;
                }
                brightness = b0 * b1;
            }

            if (brightness <= 0.0F) {
                return;
            }
            int alpha = (int) (255.0F * brightness);

            // 円錐 (根元が灯色、先端の縁が黒 α0)
            for (int i = 0; i < DIV_NUM; ++i) {
                float rad0 = (float) Math.toRadians(-i * ANGLE);
                float rad1 = (float) Math.toRadians(-(i + 1) * ANGLE);
                vertex(consumer, mat, 0.0F, 0.0F, 0.0F, red, green, blue, alpha);
                vertex(consumer, mat, Mth.cos(rad0) * rL, Mth.sin(rad0) * rL, length, 0, 0, 0, 0);
                vertex(consumer, mat, Mth.cos(rad1) * rL, Mth.sin(rad1) * rL, length, 0, 0, 0, 0);
                vertex(consumer, mat, 0.0F, 0.0F, 0.0F, red, green, blue, alpha);
            }

            // 放射状の筋。本家は GL_TRIANGLES で 1 本ずつ独立した三角形を積む
            float b3 = (float) ((double) distance * BRIGHTNESS_RATE);
            if (b3 > 1.0F) {
                b3 = 1.0F;
            }
            float f3 = rS * b3;
            for (int i = 0; i <= DIV_NUM; ++i) {
                float rad = (float) Math.toRadians(i * ANGLE);
                float sin = Mth.sin(rad);
                float cos = Mth.cos(rad);
                vertex(consumer, mat, cos * rL, sin * rL, length, 0, 0, 0, 0);
                vertex(consumer, mat, 0.0F, 0.0F, 0.0F, red, green, blue, alpha >> 1);
                vertex(consumer, mat, cos * f3, sin * f3, 0.0F, 0, 0, 0, 0);
                vertex(consumer, mat, cos * f3, sin * f3, 0.0F, 0, 0, 0, 0);
            }
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f mat, float x, float y, float z,
                               int red, int green, int blue, int alpha) {
        consumer.addVertex(mat, x, y, z).setColor(red, green, blue, alpha);
    }

    /** 本家 PartsRenderer.getViewerVec: 視点から灯具へのベクトル。 */
    private static Vector3f viewerVec(double x, double y, double z) {
        Vec3 eye = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        return new Vector3f((float) (eye.x - x), (float) (eye.y - y), (float) (eye.z - z));
    }

    /** 本家 Vector3f.angle: 2 ベクトルのなす角 (ラジアン)。 */
    private static float angleBetween(Vector3f a, Vector3f b) {
        float d = a.x * b.x + a.y * b.y + a.z * b.z;
        float len = (float) (Math.sqrt(a.lengthSquared()) * Math.sqrt(b.lengthSquared()));
        if (len == 0.0F) {
            return 0.0F;
        }
        return (float) Math.acos(Mth.clamp(d / len, -1.0F, 1.0F));
    }

    /** 本家 Vec3.rotateAroundY(yaw) → rotateAroundZ(pitch)… */
    private static Vector3f rotate(float x, float y, float z, float yaw, float pitch) {
        float ry = (float) Math.toRadians(yaw);
        float cy = Mth.cos(ry);
        float sy = Mth.sin(ry);
        float x1 = x * cy + z * sy;
        float z1 = z * cy - x * sy;
        float rz = (float) Math.toRadians(pitch);
        float cz = Mth.cos(rz);
        float sz = Mth.sin(rz);
        float x2 = x1 * cz + y * sz;
        float y2 = y * cz - x1 * sz;
        return new Vector3f(x2, y2, z1);
    }
}
