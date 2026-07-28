package jp.ngt.rtm.rail;

import jp.ngt.rtm.rail.util.RailMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 本家 jp.ngt.rtm.rail.TileEntityLargeRailNormalCore の忠実移植。
 */
public class TileEntityLargeRailNormalCore extends TileEntityLargeRailCore {

    public TileEntityLargeRailNormalCore(BlockPos pos, BlockState state) {
        super(RTMRailBlockEntities.LARGE_RAIL_NORMAL_CORE.get(), pos, state);
    }

    @Override
    public String getRailShapeName() {
        RailMap map = this.getRailMap(null);
        return "Type:Normal, " +
                "X:" + (map.getEndRP().blockX - map.getStartRP().blockX) + ", " +
                "Y:" + (map.getEndRP().blockY - map.getStartRP().blockY) + ", " +
                "Z:" + (map.getEndRP().blockZ - map.getStartRP().blockZ);
    }
}
