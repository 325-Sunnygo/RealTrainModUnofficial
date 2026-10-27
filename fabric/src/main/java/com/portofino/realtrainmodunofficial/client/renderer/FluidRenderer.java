package com.portofino.realtrainmodunofficial.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import jp.ngt.rtm.entity.fluid.EntityFluid;
import jp.ngt.rtm.entity.fluid.FluidType;
import jp.ngt.rtm.entity.fluid.FluidVertexHolder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * 溶けた金属 / コークスの粒の描画。本家 {@code jp.ngt.rtm.entity.fluid.RenderFluid} の移植。
 *
 * <p>テクスチャ無しの球を、近くの粒へ向かって膨らませて描く (メタボール)。
 * 色は上下グラデーション × 温度。
 *
 * <p>★本家 pass 1 の「熱いコークスから出る炎」は未移植。
 */
public class FluidRenderer extends EntityRenderer<EntityFluid> {

    public FluidRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(EntityFluid entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        if (entity.fluidVtx == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.0D, EntityFluid.R, 0.0D);
        float scale = entity.getNormalizedLife();
        poseStack.scale(scale, scale, scale);

        if (entity.getFluidType().type == FluidType.Type.SOLID) {
            //本家: 固体は個体ごとに少しだけ傾ける
            switch (entity.getId() % 4) {
                case 0 -> poseStack.mulPose(Axis.XP.rotationDegrees(5.0F));
                case 1 -> poseStack.mulPose(Axis.ZP.rotationDegrees(5.0F));
                case 2 -> poseStack.mulPose(Axis.XP.rotationDegrees(-5.0F));
                default -> poseStack.mulPose(Axis.ZP.rotationDegrees(-5.0F));
            }
        }

        this.renderMetaball(entity, poseStack, buffer);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    /** 本家 renderMetaball。GL_TRIANGLE_STRIP を 1.21 の QUADS に組み直している。 */
    private void renderMetaball(EntityFluid entity, PoseStack poseStack, MultiBufferSource buffer) {
        float normTemp = entity.getNormalizedTemperture();
        boolean isSolid = (entity.getFluidType().type == FluidType.Type.SOLID);
        int splitW = isSolid ? 4 : 8;
        VertexConsumer consumer = buffer.getBuffer(RenderType.debugQuads());
        Matrix4f mat = poseStack.last().pose();

        for (int i = 0; i < FluidVertexHolder.SPLIT_H; ++i) {      //縦
            for (int j = 0; j < splitW; ++j) {                     //横
                int j0 = j % splitW;
                int j1 = (j + 1) % splitW;
                int upper0 = (i + 1) * FluidVertexHolder.SPLIT_W + j0;
                int lower0 = i * FluidVertexHolder.SPLIT_W + j0;
                int upper1 = (i + 1) * FluidVertexHolder.SPLIT_W + j1;
                int lower1 = i * FluidVertexHolder.SPLIT_W + j1;
                this.addVertex(consumer, mat, entity, lower0, normTemp);
                this.addVertex(consumer, mat, entity, lower1, normTemp);
                this.addVertex(consumer, mat, entity, upper1, normTemp);
                this.addVertex(consumer, mat, entity, upper0, normTemp);
            }
        }
    }

    private void addVertex(VertexConsumer consumer, Matrix4f mat, EntityFluid fluid, int index, float temp) {
        float[] fa = fluid.fluidVtx.buffer[index];
        int color = fluid.getFluidType().getColor(fa[3], temp);
        consumer.addVertex(mat, fa[0], fa[1], fa[2])
            .setColor((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, 255);
    }

    @Override
    public ResourceLocation getTextureLocation(EntityFluid entity) {
        return ResourceLocation.withDefaultNamespace("missingno");
    }
}
