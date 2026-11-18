package com.portofino.realtrainmodunofficial.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import jp.ngt.rtm.entity.train.parts.EntityFloor;
import jp.ngt.rtm.entity.train.parts.ModelCrossSeat;
import jp.ngt.rtm.entity.vehicle.EntityVehicleBase;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 本家 RenderSeat + ModelCrossSeat (1.7.10) の忠実移植。
 *
 * <p>本家と同じ変換パイプライン:
 * translate(entity pos) → yaw → pitch → roll → scale(1,-1,-1) →
 * ModelRenderer (rotation point + rotateAngleX + 1/16 scale + バニラボックスUV)。</p>
 */
public class RtmFloorRenderer extends EntityRenderer<EntityFloor> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("realtrainmodunofficial", "textures/train/seat.png");
    private static final float PIXEL = 1.0F / 16.0F;

    public RtmFloorRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(EntityFloor entity) {
        return TEXTURE;
    }

    @Override
    public void render(EntityFloor entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        byte seatType = entity.getSeatType();
        EntityVehicleBase vehicle = entity.getVehicle();
        if (vehicle == null || (seatType != 1 && seatType != 3)) {
            return;
        }
        poseStack.pushPose();
        // 本家 RenderSeat: 車両の yaw/pitch/roll に追従
        float yaw = Mth.rotLerp(partialTicks, vehicle.yRotO, vehicle.getYRot());
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        float pitch = Mth.lerp(partialTicks, vehicle.xRotO, vehicle.getXRot());
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        float roll = Mth.lerp(partialTicks, vehicle.prevRotationRoll, vehicle.rotationRoll);
        poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
        // 本家 GL11.glScalef(1, -1, -1)
        poseStack.scale(1.0F, -1.0F, -1.0F);

        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        Matrix4f mat = poseStack.last().pose();
        Matrix3f norm = poseStack.last().normal();

        // seatRotation (-45..45) → 背もたれ角度 -17..+17 度 (本家と同じ式)
        float backAngle = Mth.clamp(vehicle.seatRotation * 17.0F / 45.0F, -17.0F, 17.0F);

        // Shape1 (座面): rotation point (0,0,-1)
        drawBox(vc, mat, norm, ModelCrossSeat.SEAT_BOX, ModelCrossSeat.SEAT_POINT,
                ModelCrossSeat.SEAT_U, ModelCrossSeat.SEAT_V, 0.0F, packedLight);
        // Shape2 (クッション): rotation point (0,-5,-2)
        drawBox(vc, mat, norm, ModelCrossSeat.CUSHION_BOX, ModelCrossSeat.CUSHION_POINT,
                ModelCrossSeat.CUSHION_U, ModelCrossSeat.CUSHION_V, 0.0F, packedLight);
        // Shape3 (背もたれ): rotation point (0,6,4) + 基本角 -0.296706 + seatRotation 角度
        drawBox(vc, mat, norm, ModelCrossSeat.BACK_BOX, ModelCrossSeat.BACK_POINT,
                ModelCrossSeat.BACK_U, ModelCrossSeat.BACK_V,
                (float) Math.toRadians(backAngle) + ModelCrossSeat.BACK_BASE_ANGLE_X, packedLight);

        poseStack.popPose();
    }

    /**
     * 1.7.10 ModelRenderer の描画を再現する:
     * rotation point 分の平行移動 → rotateAngleX(X軸回転、rotation point ピボット) →
     * addBox のローカル座標でボックスを描く。スケールは 1/16。
     */
    private static void drawBox(VertexConsumer vc, Matrix4f mat, Matrix3f norm,
                                float[] box, float[] point, int texU, int texV,
                                float rotateAngleX, int light) {
        poseStackForBox.push(mat, norm, point, rotateAngleX);
        try {
            Matrix4f m = poseStackForBox.mat;
            Matrix3f n = poseStackForBox.norm;
            // バニラ ModelRenderer.addBox と同じ UV 割り当て
            float w = box[3], h = box[4], d = box[5];
            float x0 = box[0] * PIXEL, y0 = box[1] * PIXEL, z0 = box[2] * PIXEL;
            float x1 = (box[0] + w) * PIXEL, y1 = (box[1] + h) * PIXEL, z1 = (box[2] + d) * PIXEL;
            float u = texU, v = texV;
            // top: (U+D, V, U+D+W, V+D)
            float uT0 = (u + d), vT0 = v, uT1 = (u + d + w), vT1 = (v + d);
            // bottom: (U+D+W, V, U+D+2W, V+D)
            float uB0 = (u + d + w), vB0 = v, uB1 = (u + d + 2 * w), vB1 = (v + d);
            // -Z (front): (U+D, V+D, U+D+W, V+D+H)
            float uN0 = (u + d), vN0 = (v + d), uN1 = (u + d + w), vN1 = (v + d + h);
            // +Z: (U+D+W+D, V+D, U+D+2W+D, V+D+H)
            float uZ0 = (u + d + w + d), vZ0 = (v + d), uZ1 = (u + d + 2 * w + d), vZ1 = (v + d + h);
            // +X: (U, V+D, U+D, V+D+H)
            float uE0 = u, vE0 = (v + d), uE1 = (u + d), vE1 = (v + d + h);
            // -X: (U+D+W, V+D, U+D+W+D, V+D+H)
            float uW0 = (u + d + w), vW0 = (v + d), uW1 = (u + d + 2 * w), vW1 = (v + d + h);

            Vector3f[] p = new Vector3f[8];
            int i = 0;
            for (int sy = 0; sy <= 1; sy++) {
                for (int sz = 0; sz <= 1; sz++) {
                    for (int sx = 0; sx <= 1; sx++) {
                        p[i++] = new Vector3f(sx == 0 ? x0 : x1, sy == 0 ? y0 : y1, sz == 0 ? z0 : z1);
                    }
                }
            }
            // p index: bit0=x, bit1=z, bit2=y → 0:(x0,y0,z0) 1:(x1,y0,z0) 2:(x0,y0,z1) 3:(x1,y0,z1)
            //          4:(x0,y1,z0) 5:(x1,y1,z0) 6:(x0,y1,z1) 7:(x1,y1,z1)
            // top (y1), bottom (y0), -Z (z0), +Z (z1), +X (x1), -X (x0)
            quad(vc, m, n, p[4], p[5], p[7], p[6], uT0, vT0, uT1, vT1, light);
            quad(vc, m, n, p[0], p[1], p[3], p[2], uB0, vB0, uB1, vB1, light);
            quad(vc, m, n, p[0], p[1], p[5], p[4], uN0, vN0, uN1, vN1, light);
            quad(vc, m, n, p[2], p[3], p[7], p[6], uZ0, vZ0, uZ1, vZ1, light);
            quad(vc, m, n, p[1], p[3], p[7], p[5], uE0, vE0, uE1, vE1, light);
            quad(vc, m, n, p[0], p[2], p[6], p[4], uW0, vW0, uW1, vW1, light);
        } finally {
            poseStackForBox.pop();
        }
    }

    /** drawBox 内で使う一時スタック (アロケーション削減)。 */
    private static final BoxPose poseStackForBox = new BoxPose();

    private static final class BoxPose {
        Matrix4f mat = new Matrix4f();
        Matrix3f norm = new Matrix3f();
        boolean active;

        void push(Matrix4f parent, Matrix3f parentNorm, float[] point, float rotX) {
            mat.set(parent);
            mat.translate(point[0] * PIXEL, point[1] * PIXEL, point[2] * PIXEL);
            if (rotX != 0.0F) {
                mat.rotate((float) Math.toRadians(-rotX), 1.0F, 0.0F, 0.0F);
            }
            norm.set(parentNorm);
            if (rotX != 0.0F) {
                norm.rotate((float) Math.toRadians(-rotX), 1.0F, 0.0F, 0.0F);
            }
            active = true;
        }

        void pop() {
            active = false;
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f mat, Matrix3f norm, Vector3f a, Vector3f b,
                             Vector3f c, Vector3f d, float u0, float v0, float u1, float v1, int light) {
        Vector3f e1 = new Vector3f(b).sub(a);
        Vector3f e2 = new Vector3f(c).sub(a);
        Vector3f n = e1.cross(e2);
        if (n.lengthSquared() < 1.0E-8F) {
            return;
        }
        n.normalize();
        float nx = norm.m00() * n.x + norm.m10() * n.y + norm.m20() * n.z;
        float ny = norm.m01() * n.x + norm.m11() * n.y + norm.m21() * n.z;
        float nz = norm.m02() * n.x + norm.m12() * n.y + norm.m22() * n.z;
        vertex(vc, mat, a, u1, v1, light, nx, ny, nz);
        vertex(vc, mat, b, u0, v1, light, nx, ny, nz);
        vertex(vc, mat, c, u0, v0, light, nx, ny, nz);
        vertex(vc, mat, d, u1, v0, light, nx, ny, nz);
    }

    private static void vertex(VertexConsumer vc, Matrix4f mat, Vector3f pos, float u, float v, int light,
                               float nx, float ny, float nz) {
        vc.addVertex(mat, pos.x, pos.y, pos.z)
                .setColor(255, 255, 255, 255)
                .setUv(u / (float) ModelCrossSeat.TEXTURE_WIDTH, v / (float) ModelCrossSeat.TEXTURE_HEIGHT)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(nx, ny, nz);
    }
}
