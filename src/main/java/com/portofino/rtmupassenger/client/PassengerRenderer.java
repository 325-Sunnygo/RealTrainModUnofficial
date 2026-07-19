package com.portofino.rtmupassenger.client;

import com.portofino.rtmupassenger.entity.PassengerEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * 乗客のレンダラ。バニラのプレイヤーモデル (スティーブ/アレックスのスキン) を使い回すことで
 * 専用モデル/テクスチャ無しで人型の乗客を出す。個体 ID でスキンを出し分ける。
 */
public class PassengerRenderer extends HumanoidMobRenderer<PassengerEntity, PlayerModel<PassengerEntity>> {

    private static final ResourceLocation[] SKINS = {
            ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png"),
            ResourceLocation.withDefaultNamespace("textures/entity/player/wide/alex.png"),
            ResourceLocation.withDefaultNamespace("textures/entity/player/wide/ari.png"),
            ResourceLocation.withDefaultNamespace("textures/entity/player/wide/kai.png"),
            ResourceLocation.withDefaultNamespace("textures/entity/player/wide/makena.png"),
            ResourceLocation.withDefaultNamespace("textures/entity/player/wide/noor.png"),
            ResourceLocation.withDefaultNamespace("textures/entity/player/wide/sunny.png"),
            ResourceLocation.withDefaultNamespace("textures/entity/player/wide/zuri.png"),
    };

    public PassengerRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.4F);
    }

    @Override
    public ResourceLocation getTextureLocation(PassengerEntity entity) {
        return SKINS[Math.floorMod(entity.getUUID().hashCode(), SKINS.length)];
    }

    @Override
    public void render(PassengerEntity entity, float entityYaw, float partialTick,
                       com.mojang.blaze3d.vertex.PoseStack poseStack,
                       net.minecraft.client.renderer.MultiBufferSource buffer, int packedLight) {
        //列車に乗っている間は座り姿勢 (HumanoidModel.riding=脚を曲げて着席)。
        this.getModel().riding = entity.isPassenger();
        this.getModel().young = false;
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }
}
