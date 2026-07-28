package com.portofino.realtrainmodunofficial.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import jp.ngt.mcte.item.ItemMiniature;
import jp.ngt.ngtlib.block.BlockSet;
import jp.ngt.ngtlib.block.NGTObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * ミニチュアアイテムの描画 (neo mcte)。本家 MCTE {@code RenderItemMiniature} 相当。
 *
 * <p>インベントリや手元で<b>中身が見える</b>ようにする。ミニチュアを何個も持つと
 * 見分けがつかないのが本家からの不満点だったので、neo mcte では既定で中身を描く。
 *
 * <p>アイテム表示では設定の {@code Scale} は使わず、<b>スロットに収まる大きさへ自動で正規化</b>する。
 * 設定の縮尺は「ワールドに置いたときの大きさ」であって、アイコンの大きさとは別物のため。
 */
public class MiniatureItemRenderer extends BlockEntityWithoutLevelRenderer {

    /** アイコン用の描画ブロック数上限。インベントリに何十個も並ぶので厳しめ。 */
    private static final int MAX_BLOCKS_ICON = 512;

    public MiniatureItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
              Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                             MultiBufferSource buffer, int packedLight, int packedOverlay) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = data != null ? data.copyTag() : null;
        NGTObject obj = tag == null ? null : ItemMiniature.getNGTObject(tag);
        if (obj == null || obj.blockList.isEmpty()) {
            return;
        }

        int size = Math.max(1, Math.max(obj.xSize, Math.max(obj.ySize, obj.zSize)));
        float fit = 0.85F / size;

        var dispatcher = Minecraft.getInstance().getBlockRenderer();

        poseStack.pushPose();
        //アイテムの原点はモデル空間の (0,0,0)。中央へ寄せてから縮める。
        poseStack.translate(0.5D, 0.5D, 0.5D);
        //少し傾けて立体的に見せる (アイコンで正面から見ると板に見えるため)
        if (context == ItemDisplayContext.GUI) {
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(30.0F));
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-45.0F));
        }
        poseStack.scale(fit, fit, fit);
        poseStack.translate(-obj.xSize * 0.5D, -obj.ySize * 0.5D, -obj.zSize * 0.5D);

        int drawn = 0;
        for (BlockSet set : obj.blockList) {
            if (set == null || set.state == null || set.state.isAir()) {
                continue;
            }
            if (++drawn > MAX_BLOCKS_ICON) {
                break;
            }
            poseStack.pushPose();
            poseStack.translate(set.x, set.y, set.z);
            try {
                dispatcher.renderSingleBlock(set.state, poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);
            } catch (Throwable ignored) {
                //描けないブロックは飛ばす。1 個の失敗でアイコンごと消さない。
            }
            poseStack.popPose();
        }
        poseStack.popPose();
    }
}
