package com.portofino.realtrainmodunofficial.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import jp.ngt.rtm.block.decoration.DecorationModel;
import jp.ngt.rtm.block.decoration.DecorationStore;
import jp.ngt.rtm.item.ItemDecoration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 装飾ブロックアイテムの描画。本家はモデル json {@code builtin/entity} +
 * RenderDecoration.renderItem でアイテムに実モデルを出す。その移植。
 */
public class DecorationItemRenderer extends BlockEntityWithoutLevelRenderer {

    public DecorationItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
              Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                             MultiBufferSource buffer, int packedLight, int packedOverlay) {
        DecorationModel model = DecorationStore.INSTANCE.getModel(ItemDecoration.getModelName(stack));
        poseStack.pushPose();
        DecorationRenderer.renderModel(model, poseStack, buffer, packedLight);
        poseStack.popPose();
    }
}
