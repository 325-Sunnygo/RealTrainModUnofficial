package com.myname.legacyloader.bridge.client.renderer.tileentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.myname.legacyloader.bridge.client.renderer.LegacyTesrContext;
import com.myname.legacyloader.bridge.tileentity.LegacyTileEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 1.7.10 の {@code net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer} の代役。
 * mod の TESR (例 AsphaltMod の RenderColorCone) はこのクラスを継承し、
 * {@code func_147500_a} (renderTileEntityAt) 内で GL11 即時モードで OBJ を描く。
 * 実際の描画は {@link LegacyTesrContext} が現行バッファへ橋渡しする
 * (ディスパッチは {@link LegacyBlockEntityDispatcher})。
 */
public abstract class LegacyTileEntitySpecialRenderer implements BlockEntityRenderer<BlockEntity> {

    public LegacyTileEntityRendererDispatcher field_147501_a = LegacyTileEntityRendererDispatcher.field_147556_a;

    // 1.7.10: renderTileEntityAt(TileEntity, double x, double y, double z, float partialTicks)
    // net.minecraft.tileentity.TileEntity は LegacyTileEntity にマッピングされるため、mod の
    // オーバーライドメソッドは remap 後に LegacyTileEntity 引数になる。ここも LegacyTileEntity で
    // 宣言しないと記述子が一致せずオーバーライドが成立しない (親の no-op が呼ばれ描画されない)。
    public void renderTileEntityAt(LegacyTileEntity te, double x, double y, double z, float partialTicks) {
    }

    // SRG名 (func_147500_a) — 既定は renderTileEntityAt へ委譲。mod はどちらかをオーバーライドする。
    public void func_147500_a(LegacyTileEntity te, double x, double y, double z, float partialTicks) {
        renderTileEntityAt(te, x, y, z, partialTicks);
    }

    // bindTexture (func_147499_a) — TESR 描画中のテクスチャを設定。OBJ 描画がこれを使う。
    public void func_147499_a(ResourceLocation location) {
        LegacyTesrContext ctx = LegacyTesrContext.current();
        if (ctx != null) {
            ctx.setTexture(location);
        }
    }

    public void bindTexture(ResourceLocation location) {
        func_147499_a(location);
    }

    public void setRendererDispatcher(LegacyTileEntityRendererDispatcher dispatcher) {
        this.field_147501_a = dispatcher != null ? dispatcher : LegacyTileEntityRendererDispatcher.field_147556_a;
    }

    public void func_147497_a(LegacyTileEntityRendererDispatcher dispatcher) {
        setRendererDispatcher(dispatcher);
    }

    // 1.21 の描画メソッド。通常はディスパッチャ経由で func_147500_a が呼ばれるためここは使われないが、
    // 万一 mod TESR が直接 BlockEntityRenderer として登録された場合の保険。
    @Override
    public void render(BlockEntity te, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (!(te instanceof LegacyTileEntity legacyTe)) {
            return;
        }
        LegacyTesrContext.begin(poseStack, bufferSource, packedLight);
        try {
            func_147500_a(legacyTe, 0, 0, 0, partialTick);
        } catch (Throwable ignored) {
        } finally {
            LegacyTesrContext.end();
        }
    }
}
