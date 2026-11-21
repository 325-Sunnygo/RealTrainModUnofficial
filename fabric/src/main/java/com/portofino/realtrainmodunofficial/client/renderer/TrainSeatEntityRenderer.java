package com.portofino.realtrainmodunofficial.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.entity.TrainSeatEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public final class TrainSeatEntityRenderer extends EntityRenderer<TrainSeatEntity> {
    private static final ResourceLocation EMPTY_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "textures/misc/empty.png");

    public TrainSeatEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(TrainSeatEntity entity) {
        return EMPTY_TEXTURE;
    }

    @Override
    public boolean shouldRender(TrainSeatEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return false;
    }

    @Override
    public void render(TrainSeatEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
    }
}
