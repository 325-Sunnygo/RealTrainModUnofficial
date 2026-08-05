package com.portofino.realtrainmodunofficial.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import jp.ngt.rtm.entity.train.parts.EntityTie;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * 貨物用枕木の描画。本家 {@code RenderTie} の移植。
 *
 * <p>本家は ModelBase の箱 (40x2x40 / テクスチャ 256x64) を
 * {@code textures/train/tie.png} で描く。バニラの箱 UV 配置をそのまま再現する。
 */
public class TieRenderer extends EntityRenderer<EntityTie> {

    private static final ResourceLocation TEXTURE =
        //★assets/rtm/textures/train/** はビルドで除外されるので自前名前空間に置く
        ResourceLocation.fromNamespaceAndPath("realtrainmodunofficial", "textures/train/tie.png");

    //本家 ModelTie: addBox(-20,-2,-20, 40,2,40)。単位は 1/16 ブロック
    private static final float HALF = 40.0F / 16.0F / 2.0F;   //2.5 / 2
    private static final float HEIGHT = 2.0F / 16.0F;         //0.125
    private static final float TEX_W = 256.0F;
    private static final float TEX_H = 64.0F;

    public TieRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(EntityTie entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.rotLerp(partialTicks, entity.yRotO, entity.getYRot())));
        poseStack.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partialTicks, entity.xRotO, entity.getXRot())));

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        Matrix4f mat = poseStack.last().pose();
        //バニラの箱 UV 配置 (w=40, h=2, d=40, offset 0,0)
        float w = 40.0F, h = 2.0F, d = 40.0F;
        //上面
        quad(consumer, mat, packedLight,
            -HALF, HEIGHT, -HALF, HALF, HEIGHT, HALF, d, 0, d + w, d, 0, 1, 0);
        //底面
        quad(consumer, mat, packedLight,
            -HALF, 0, -HALF, HALF, 0, HALF, d + w, 0, d + 2 * w, d, 0, -1, 0);
        //北 (z-)
        quadZ(consumer, mat, packedLight,
            -HALF, 0, -HALF, HALF, HEIGHT, d, d, d + w, d + h, 0, 0, -1);
        //南 (z+)
        quadZ(consumer, mat, packedLight,
            -HALF, 0, HALF, HALF, HEIGHT, d + w + d, d, d + w + d + w, d + h, 0, 0, 1);
        //西 (x-)
        quadX(consumer, mat, packedLight,
            -HALF, 0, -HALF, HALF, HEIGHT, 0, d, d, d + h, -1, 0, 0);
        //東 (x+)
        quadX(consumer, mat, packedLight,
            HALF, 0, -HALF, HALF, HEIGHT, d + w, d, d + w + d, d + h, 1, 0, 0);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    /** 水平の四角形 (上面/底面)。 */
    private static void quad(VertexConsumer c, Matrix4f m, int light,
                             float x0, float y, float z0, float x1, float y2, float z1,
                             float u0, float v0, float u1, float v1, float nx, float ny, float nz) {
        vert(c, m, light, x0, y, z0, u0 / TEX_W, v0 / TEX_H, nx, ny, nz);
        vert(c, m, light, x0, y, z1, u0 / TEX_W, v1 / TEX_H, nx, ny, nz);
        vert(c, m, light, x1, y, z1, u1 / TEX_W, v1 / TEX_H, nx, ny, nz);
        vert(c, m, light, x1, y, z0, u1 / TEX_W, v0 / TEX_H, nx, ny, nz);
    }

    /** Z 面 (奥/手前)。 */
    private static void quadZ(VertexConsumer c, Matrix4f m, int light,
                              float x0, float y0, float z, float x1, float y1,
                              float u0, float v0, float u1, float v1, float nx, float ny, float nz) {
        vert(c, m, light, x0, y1, z, u0 / TEX_W, v0 / TEX_H, nx, ny, nz);
        vert(c, m, light, x0, y0, z, u0 / TEX_W, v1 / TEX_H, nx, ny, nz);
        vert(c, m, light, x1, y0, z, u1 / TEX_W, v1 / TEX_H, nx, ny, nz);
        vert(c, m, light, x1, y1, z, u1 / TEX_W, v0 / TEX_H, nx, ny, nz);
    }

    /** X 面 (左右)。 */
    private static void quadX(VertexConsumer c, Matrix4f m, int light,
                              float x, float y0, float z0, float z1, float y1,
                              float u0, float v0, float u1, float v1, float nx, float ny, float nz) {
        vert(c, m, light, x, y1, z0, u0 / TEX_W, v0 / TEX_H, nx, ny, nz);
        vert(c, m, light, x, y0, z0, u0 / TEX_W, v1 / TEX_H, nx, ny, nz);
        vert(c, m, light, x, y0, z1, u1 / TEX_W, v1 / TEX_H, nx, ny, nz);
        vert(c, m, light, x, y1, z1, u1 / TEX_W, v0 / TEX_H, nx, ny, nz);
    }

    private static void vert(VertexConsumer c, Matrix4f m, int light,
                             float x, float y, float z, float u, float v,
                             float nx, float ny, float nz) {
        c.addVertex(m, x, y, z).setColor(255, 255, 255, 255).setUv(u, v)
            .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
            .setLight(light).setNormal(nx, ny, nz);
    }

    @Override
    public ResourceLocation getTextureLocation(EntityTie entity) {
        return TEXTURE;
    }
}
