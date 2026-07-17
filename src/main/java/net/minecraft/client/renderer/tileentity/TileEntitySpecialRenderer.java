package net.minecraft.client.renderer.tileentity;

import com.myname.legacyloader.bridge.client.renderer.tileentity.LegacyTileEntitySpecialRenderer;
import com.myname.legacyloader.bridge.tileentity.LegacyTileEntity;

/** 1.7.10 compatibility name for TileEntitySpecialRenderer. */
public abstract class TileEntitySpecialRenderer extends LegacyTileEntitySpecialRenderer {
    @Override
    public abstract void renderTileEntityAt(LegacyTileEntity te, double x, double y, double z, float partialTicks);
}
