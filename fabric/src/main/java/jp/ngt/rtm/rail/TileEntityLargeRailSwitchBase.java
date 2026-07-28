package jp.ngt.rtm.rail;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 本家 jp.ngt.rtm.rail.TileEntityLargeRailSwitchBase の忠実移植。
 */
public class TileEntityLargeRailSwitchBase extends TileEntityLargeRailBase {

    public TileEntityLargeRailSwitchBase(BlockPos pos, BlockState state) {
        super(RTMRailBlockEntities.LARGE_RAIL_SWITCH_BASE.get(), pos, state);
    }
}
