package com.portofino.realtrainmodunofficial.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import jp.ngt.rtm.entity.train.parts.EntityFloor;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * EntityFloor (slotPos 座席) — 見た目は車両モデル側にあるため描画なし。
 * (本家は seatType==1 のみ簡易クロスシートを描画 — TODO)
 */
public class RtmFloorRenderer extends EntityRenderer<EntityFloor> {

    public RtmFloorRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(EntityFloor entity) {
        return ResourceLocation.withDefaultNamespace("missingno");
    }

    @Override
    public void render(EntityFloor entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
    }
}
