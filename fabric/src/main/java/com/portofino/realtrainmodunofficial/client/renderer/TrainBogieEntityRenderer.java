package com.portofino.realtrainmodunofficial.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.portofino.realtrainmodunofficial.entity.TrainBogieEntity;
import com.portofino.realtrainmodunofficial.entity.TrainEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.culling.Frustum;

public final class TrainBogieEntityRenderer extends EntityRenderer<TrainBogieEntity> {
    public TrainBogieEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(TrainBogieEntity entity) {
        return ResourceLocation.withDefaultNamespace("missingno");
    }

    @Override
    public boolean shouldRender(TrainBogieEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        if (entity == null || entity.isRemoved()) return false;
        // 台車のフラスタムカリングは AABB が小さすぎて誤って消えやすい。
        // 親列車の AABB を含めた広めの範囲で判定し、見えるはずの台車が
        // 一瞬でも消失するのを防ぐ。
        TrainEntity train = entity.getTrain();
        if (frustum.isVisible(entity.getBoundingBoxForCulling().inflate(2.0D))) {
            return true;
        }
        if (train != null && frustum.isVisible(train.getBoundingBoxForCulling())) {
            return true;
        }
        return entity.noCulling;
    }

    @Override
    public void render(TrainBogieEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        // Hitbox-only entity. Visual bogies are rendered from TrainEntityRenderer
        // so they inherit the same carbody transform and only correct their rail height.
    }
}
