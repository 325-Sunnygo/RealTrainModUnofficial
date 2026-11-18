package jp.ngt.rtm.block.tileentity;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** 駅コアの中身。本家 jp.ngt.rtm.block.tileentity.TileEntityS… */
public class TileEntityStation extends BlockEntity {
    private String stationName;
    public int maxHeight;

    public TileEntityStation(BlockPos pos, BlockState state) {
        super(RealTrainModUnofficialBlockEntities.STATION.get(), pos, state);
        // 本家と同じで、置いた瞬間は重複しない仮の名前
        this.stationName = String.format("default_%d", System.currentTimeMillis());
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);
        String name = nbt.getString("station_name");
        if (!name.isEmpty()) {
            this.stationName = name;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.saveAdditional(nbt, registries);
        nbt.putString("station_name", this.stationName);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putString("station_name", this.stationName);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public String getStationName() {
        return this.stationName;
    }

    public void setStationName(String par1) {
        this.stationName = par1 == null ? "" : par1;
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
        }
    }

    /** 本家 checkHeight: この駅の上に何ブロック空きがあるか。 */
    public void checkHeight() {
        if (this.level == null) {
            return;
        }
        BlockPos pos = this.getBlockPos();
        this.maxHeight = this.level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()) - pos.getY();
    }

}
