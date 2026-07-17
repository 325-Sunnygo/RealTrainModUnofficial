package com.myname.legacyloader.bridge.client.renderer.tileentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.myname.legacyloader.bridge.client.registry.LegacyClientRegistry;
import com.myname.legacyloader.bridge.client.renderer.LegacyTesrContext;
import com.myname.legacyloader.bridge.tileentity.LegacyTileEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;

/**
 * 共有 {@link LegacyTileEntity#LEGACY_TYPE} に登録される唯一の BlockEntityRenderer。
 * 描画対象の TileEntity クラスに紐づく 1.7.10 TESR
 * ({@link LegacyClientRegistry#bindTileEntitySpecialRenderer} で登録) を毎フレーム引き当て、
 * {@link LegacyTesrContext} を張って {@code func_147500_a} (renderTileEntityAt) を呼ぶ。
 */
public class LegacyBlockEntityDispatcher implements BlockEntityRenderer<LegacyTileEntity> {

    public LegacyBlockEntityDispatcher() {
    }

    /** 描画例外の警告は最初の数回だけ (毎フレーム呼ばれるため)。 */
    private static final java.util.concurrent.atomic.AtomicInteger WARNED = new java.util.concurrent.atomic.AtomicInteger();

    @Override
    public void render(LegacyTileEntity te, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Object tesr = LegacyClientRegistry.getTileEntitySpecialRenderer(te.getClass());
        if (!(tesr instanceof LegacyTileEntitySpecialRenderer legacyTesr)) {
            return;
        }
        LegacyTesrContext.begin(poseStack, bufferSource, packedLight);
        try {
            legacyTesr.func_147500_a(te, 0, 0, 0, partialTick);
        } catch (Throwable t) {
            if (WARNED.getAndIncrement() < 10) {
                com.myname.legacyloader.LegacyLoaderMod.LOGGER.warn(
                        "LegacyLoader: TESR render threw for {}", te.getClass().getName(), t);
            }
        } finally {
            LegacyTesrContext.end();
        }
    }

    /** 常に描画対象にする (TESR は範囲外でも描くことがあるため)。 */
    @Override
    public boolean shouldRenderOffScreen(LegacyTileEntity te) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
