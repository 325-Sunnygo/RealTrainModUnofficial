package jp.ngt.rtm.rail;

import jp.ngt.rtm.rail.util.RailMap;
import jp.ngt.rtm.rail.util.RailMapSlope;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 本家 jp.ngt.rtm.rail.TileEntityLargeRailSlopeCore の忠実移植。
 */
public class TileEntityLargeRailSlopeCore extends TileEntityLargeRailCore {
    private byte slopeType;

    public TileEntityLargeRailSlopeCore(BlockPos pos, BlockState state) {
        super(RTMRailBlockEntities.LARGE_RAIL_SLOPE_CORE.get(), pos, state);
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);

        this.slopeType = nbt.getByte("slopeType");
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.saveAdditional(nbt, registries);

        nbt.putByte("slopeType", this.slopeType);
    }

    @Override
    public void createRailMap() {
        if (this.railPositions != null) {
            this.railmap = new RailMapSlope(this.railPositions[0], this.railPositions[1], this.slopeType);
        }
    }

    public byte getSlopeType() {
        return this.slopeType;
    }

    public void setSlopeType(byte par1) {
        this.slopeType = par1;
    }

    @Override
    public int[] getRailSize() {
        int startX = this.railPositions[0].blockX;
        int endX = this.railPositions[1].blockX;
        int startZ = this.railPositions[0].blockZ;
        int endZ = this.railPositions[1].blockZ;

        int minX = Math.min(startX, endX);
        int maxX = Math.max(startX, endX);
        int minY = this.worldPosition.getY();
        int maxY = this.worldPosition.getY();
        int minZ = Math.min(startZ, endZ);
        int maxZ = Math.max(startZ, endZ);
        return new int[]{minX, minY, minZ, maxX, maxY, maxZ};
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
