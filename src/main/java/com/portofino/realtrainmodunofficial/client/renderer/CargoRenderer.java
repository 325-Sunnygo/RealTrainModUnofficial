package com.portofino.realtrainmodunofficial.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.portofino.realtrainmodunofficial.cargo.CargoDefinition;
import com.portofino.realtrainmodunofficial.client.model.MqoModelLoader;
import jp.ngt.rtm.entity.train.parts.EntityArtillery;
import jp.ngt.rtm.entity.train.parts.EntityCargoWithModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * コンテナ / 火砲の描画。本家 {@code RenderContainer} / {@code RenderArtillery} 相当。
 *
 * <p>パックのモデルをそのまま描く。火砲は砲塔 (Y 回転) と砲身 (X 回転) を持つが、
 * ★<b>パーツ分けの描画は未移植</b>で、いまは車体ごと砲塔の向きへ回している。
 */
public class CargoRenderer<T extends EntityCargoWithModel> extends EntityRenderer<T> {

    public CargoRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(T entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        CargoDefinition def = entity.getDefinition();
        if (def == null || def.getModelFile() == null || def.getModelFile().isBlank()) {
            return;
        }
        MqoModelLoader.MqoModel model = MqoModelLoader.loadModelFromPack(
            def.getPackName(), def.getModelFile(), def.getTextureOverrides(), null, true);
        if (model == null) {
            return;
        }

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(
            Mth.rotLerp(partialTicks, entity.yRotO, entity.getYRot())));
        if (entity instanceof EntityArtillery artillery) {
            poseStack.mulPose(Axis.YP.rotationDegrees(artillery.getBarrelYaw()));
        }
        MqoModelLoader.renderModel(model, poseStack, buffer, packedLight);
        poseStack.popPose();

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return ResourceLocation.withDefaultNamespace("missingno");
    }
}
