package com.myname.legacyloader.bridge.client.renderer.tileentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.myname.legacyloader.bridge.block.LegacyITileEntityProvider;
import com.myname.legacyloader.bridge.client.registry.LegacyClientRegistry;
import com.myname.legacyloader.bridge.client.renderer.LegacyTesrContext;
import com.myname.legacyloader.bridge.tileentity.LegacyTileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TESR 専用ブロック (getRenderType()==-1) のアイテムを描く BEWLR。
 * ブロックからダミー TileEntity を 1 個生成してキャッシュし、対応する 1.7.10 TESR を
 * {@link LegacyTesrContext} 経由で呼ぶ (ワールド内の {@link LegacyBlockEntityDispatcher} と同じ経路)。
 * TESR は (0,0,0) 起点で +0.5 中心に描くので、ItemRenderer 側の表示変換と
 * translate(-0.5) がそのまま正しく効く。
 */
public class LegacyTesrItemRenderer extends BlockEntityWithoutLevelRenderer {

    /** ブロック→ダミーTE。生成失敗は empty をキャッシュして再試行しない。 */
    private static final Map<Block, Optional<LegacyTileEntity>> TE_CACHE = new ConcurrentHashMap<>();

    public LegacyTesrItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                             MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return;
        }
        LegacyTileEntity te = TE_CACHE.computeIfAbsent(blockItem.getBlock(), LegacyTesrItemRenderer::createDummy)
                .orElse(null);
        if (te == null) {
            return;
        }
        Object tesr = LegacyClientRegistry.getTileEntitySpecialRenderer(te.getClass());
        if (!(tesr instanceof LegacyTileEntitySpecialRenderer legacyTesr)) {
            return;
        }
        LegacyTesrContext.begin(poseStack, bufferSource, packedLight);
        try {
            legacyTesr.func_147500_a(te, 0, 0, 0, 0.0F);
        } catch (Throwable ignored) {
            // アイテム描画でクラッシュさせない
        } finally {
            LegacyTesrContext.end();
        }
    }

    private static Optional<LegacyTileEntity> createDummy(Block block) {
        try {
            if (block instanceof LegacyITileEntityProvider provider) {
                return Optional.ofNullable(provider.createNewTileEntity(null, 0));
            }
        } catch (Throwable ignored) {
        }
        return Optional.empty();
    }
}
