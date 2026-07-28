package jp.ngt.rtm.rail;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 本家 jp.ngt.rtm.rail.TileEntityLargeRailSlopeBase の忠実移植。
 */
public class TileEntityLargeRailSlopeBase extends TileEntityLargeRailBase {

    public TileEntityLargeRailSlopeBase(BlockPos pos, BlockState state) {
        super(RTMRailBlockEntities.LARGE_RAIL_SLOPE_BASE.get(), pos, state);
    }
}
