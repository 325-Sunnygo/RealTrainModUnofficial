package jp.ngt.rtm.rail;

import jp.ngt.rtm.rail.util.RailMaker;
import jp.ngt.rtm.rail.util.RailMap;
import jp.ngt.rtm.rail.util.RailMapSwitch;
import jp.ngt.rtm.rail.util.RailPosition;
import jp.ngt.rtm.rail.util.SwitchType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.stream.IntStream;

/**
 * 本家 jp.ngt.rtm.rail.TileEntityLargeRailSwitchCore (KaizPatchX) の忠実移植。
 * NBT: Size / RP0..RPn / fixRTMRailMapVersion (本家キーそのまま。1.7.10.19 互換パスは省略)
 */
public class TileEntityLargeRailSwitchCore extends TileEntityLargeRailCore {
    private SwitchType switchObj;

    public TileEntityLargeRailSwitchCore(BlockPos pos, BlockState state) {
        super(RTMRailBlockEntities.LARGE_RAIL_SWITCH_CORE.get(), pos, state);
    }

    @Override
    protected void readRailData(CompoundTag nbt) {
        if (nbt.contains("Size")) {
            byte size = nbt.getByte("Size");
            this.railPositions = new RailPosition[size];

            IntStream.range(0, size).forEach(i -> this.railPositions[i] = RailPosition.readFromNBT(nbt.getCompound("RP" + i)));
            this.fixRTMRailMapVersion = nbt.getInt("fixRTMRailMapVersion");
        }
        this.switchObj = null;
    }

    @Override
    protected void writeRailData(CompoundTag nbt) {
        if (this.railPositions == null) {
            return;
        }
        nbt.putByte("Size", (byte) this.railPositions.length);

        IntStream.range(0, this.railPositions.length).forEach(i -> nbt.put("RP" + i, this.railPositions[i].writeToNBT()));
        nbt.putInt("fixRTMRailMapVersion", this.fixRTMRailMapVersion);
    }

    @Override
    public void setRailPositions(RailPosition[] par1) {
        super.setRailPositions(par1);
        this.switchObj = null;
        this.onBlockChanged();
    }

    @Override
    public void createRailMap() {
        if (this.isLoaded() && this.switchObj == null) {
            this.switchObj = (new RailMaker(this.level, this.railPositions, this.fixRTMRailMapVersion)).getSwitch();
        }
    }

    public SwitchType getSwitch() {
        if (this.switchObj == null) {
            this.createRailMap();
        }
        return this.switchObj;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.getSwitch() != null) {
            this.getSwitch().onUpdate(this.level);
        }
    }

    /**
     * ブロック更新時。
     * 注意: ここでフル NBT 再同期 (markBlockForUpdate) を行うとクライアントの
     * SwitchType/Point が再生成され moveCount=0 にリセットされて転てつアニメが
     * 破綻する (OFF 時に即戻り)。クライアントは自前の tick + RS 参照でアニメする
     * ため、サーバー側の開通状態更新のみ行う。
     */
    public void onBlockChanged() {
        if (this.getSwitch() != null && this.level != null) {
            this.getSwitch().onBlockChanged(this.level);
            if (!this.level.isClientSide) {
                this.setChanged();
            }
        }
    }

    @Override
    public RailMap getRailMap(Entity entity) {
        SwitchType st = this.getSwitch();
        if (st == null) {
            return null;
        }

        if (entity == null) {
            RailMap[] maps = this.getAllRailMaps();
            return maps != null && maps.length > 0 ? maps[0] : null;
        }

        return st.getRailMap(entity);
    }

    @Override
    public RailMapSwitch[] getAllRailMaps() {
        if (this.getSwitch() != null) {
            return this.getSwitch().getAllRailMap();
        }
        return null;
    }

    @Override
    public int[] getRailSize() {
        int minX = this.startPoint[0];
        int maxX = this.startPoint[0];
        int minY = this.worldPosition.getY();
        int maxY = this.worldPosition.getY();
        int minZ = this.startPoint[2];
        int maxZ = this.startPoint[2];
        for (RailPosition rp : this.railPositions) {
            minX = Math.min(minX, rp.blockX);
            maxX = Math.max(maxX, rp.blockX);
            minZ = Math.min(minZ, rp.blockZ);
            maxZ = Math.max(maxZ, rp.blockZ);
        }
        return new int[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    /**
     * @deprecated Remaster 暫定互換 (旧レンダラ用)。
     */
    @Deprecated
    @Override
    public jp.ngt.rtm.rail.util.Point[] getSwitchPoints() {
        SwitchType st = this.getSwitch();
        return st != null ? st.getPoints() : null;
    }

    /**
     * @deprecated Remaster 暫定互換 (旧レンダラ用)。
     */
    @Deprecated
    @Override
    public int getActiveSegmentIndex() {
        SwitchType st = this.getSwitch();
        return st != null ? st.firstOpenRailIndex() : 0;
    }

    /**
     * @deprecated Remaster 暫定互換 (旧レンダラ用)。
     */
    @Deprecated
    @Override
    public int getPreviousSegmentIndex() {
        return this.getActiveSegmentIndex();
    }

    @Override
    public String getRailShapeName() {
        SwitchType st = this.getSwitch();
        int[] box = this.getRailSize();
        return "Type:Switch " + (st != null ? st.getName() : "?") + ", " +
                "X:" + (box[3] - box[0]) + ", " +
                "Y:" + (box[4] - box[1]) + ", " +
                "Z:" + (box[5] - box[2]);
    }
}
