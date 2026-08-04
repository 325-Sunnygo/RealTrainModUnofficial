package com.portofino.rtmuautodrive;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** 駅列車ブロック。駅名を持つだけ。停止位置はこのブロックの座標。 */
public class StationStopBlockEntity extends BlockEntity {

    private String stationName = "";

    public StationStopBlockEntity(BlockPos pos, BlockState state) {
        super(AutoDriveRegistry.stationBlockEntityType(), pos, state);
    }

    public String getStationName() {
        return this.stationName;
    }

    public void setStationName(String name) {
        this.stationName = name == null ? "" : name;
        this.setChanged();
        if (this.level instanceof ServerLevel server) {
            StationStopRegistry.get(server).setName(this.worldPosition, this.stationName);
            server.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    /** 一覧に出す名前。未設定なら座標で代用する。 */
    public String displayName() {
        return this.stationName.isBlank()
                ? this.worldPosition.getX() + ", " + this.worldPosition.getY() + ", " + this.worldPosition.getZ()
                : this.stationName;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        this.stationName = tag.getString("Name");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putString("Name", this.stationName);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        this.saveAdditional(tag, provider);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
