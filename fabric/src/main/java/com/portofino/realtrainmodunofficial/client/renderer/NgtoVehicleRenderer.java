package com.portofino.realtrainmodunofficial.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import jp.ngt.ngtlib.block.BlockSet;
import jp.ngt.rtm.entity.vehicle.EntityVehicle;
import jp.ngt.rtm.entity.vehicle.VehicleNGTO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * ブロック製の乗り物の描画。本家 {@code RenderVehicleBase.renderVehicleNGTO} 相当。
 * NGTO のブロック一覧を 1 個ずつバニラのブロック描画で置く (模型と同じやり方)。
 */
public class NgtoVehicleRenderer extends EntityRenderer<EntityVehicle> {

    public NgtoVehicleRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(EntityVehicle entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        VehicleNGTO obj = entity.getNGTO();
        if (obj == null || obj.ngto == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partialTicks, entity.yRotO, entity.getYRot())));
        poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partialTicks, entity.xRotO, entity.getXRot())));
        poseStack.mulPose(Axis.ZP.rotationDegrees(
            Mth.lerp(partialTicks, entity.prevRotationRoll, entity.rotationRoll)));
        float scale = obj.scale <= 0.0F ? 1.0F : obj.scale;
        poseStack.scale(scale, scale, scale);
        //中央を原点にする (NGTO はコーナー原点)
        poseStack.translate(-obj.ngto.xSize / 2.0D + obj.offsetX,
            obj.offsetY, -obj.ngto.zSize / 2.0D + obj.offsetZ);

        var dispatcher = Minecraft.getInstance().getBlockRenderer();
        for (BlockSet set : obj.ngto.blockList) {
            if (set == null || set.state == null || set.state.isAir()) {
                continue;
            }
            poseStack.pushPose();
            poseStack.translate(set.x, set.y, set.z);
            dispatcher.renderSingleBlock(set.state, poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(EntityVehicle entity) {
        return ResourceLocation.withDefaultNamespace("missingno");
    }
}
