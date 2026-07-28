package com.portofino.realtrainmodunofficial.blockentity;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlockEntities;
import com.portofino.realtrainmodunofficial.block.SignalStateBlock;
import com.portofino.realtrainmodunofficial.signal.SignalAspect;
import com.portofino.realtrainmodunofficial.signal.SignalNetworkSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class SignalStateBlockEntity extends BlockEntity {
    private int linkedChannel = -1;
    private int aspectId = SignalAspect.STOP.getId();

    public SignalStateBlockEntity(BlockPos pos, BlockState blockState) {
        super(RealTrainModUnofficialBlockEntities.SIGNAL_STATE.get(), pos, blockState);
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, SignalStateBlockEntity blockEntity) {
        if ((level.getGameTime() + pos.asLong()) % 5L != 0L) {
            return;
        }
        int channel = blockEntity.linkedChannel > 0 ? blockEntity.linkedChannel : blockEntity.findAdjacentSourceSignalChannel();
        SignalAspect aspect = channel > 0 ? SignalNetworkSavedData.get(level).getAspect(channel) : SignalAspect.STOP;
        blockEntity.updateState(level, pos, state, channel, aspect);
    }

    private void updateState(ServerLevel level, BlockPos pos, BlockState state, int channel, SignalAspect aspect) {
        int nextAspectId = aspect == null ? SignalAspect.STOP.getId() : aspect.getId();
        boolean changed = aspectId != nextAspectId;
        aspectId = nextAspectId;
        applyAspectToAttachedSignals(aspect);
        if (!changed) {
            return;
        }
        if (state.hasProperty(SignalStateBlock.ASPECT) && state.getValue(SignalStateBlock.ASPECT) != nextAspectId) {
            level.setBlock(pos, state.setValue(SignalStateBlock.ASPECT, nextAspectId), 3);
        }
        setChanged();
        level.sendBlockUpdated(pos, getBlockState(), getBlockState(), 3);
    }

    private int findAdjacentSourceSignalChannel() {
        if (level == null) {
            return -1;
        }
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(worldPosition.relative(direction)) instanceof InstalledObjectBlockEntity installed
                && installed.isSignal()
                && installed.getSignalChannel() > 0) {
                return installed.getSignalChannel();
            }
        }
        return -1;
    }

    private void applyAspectToAttachedSignals(SignalAspect aspect) {
        if (level == null) {
            return;
        }
        for (Direction direction : Direction.values()) {
            BlockEntity blockEntity = level.getBlockEntity(worldPosition.relative(direction));
            if (blockEntity instanceof InstalledObjectBlockEntity installed && installed.isSignal()) {
                installed.setSignalAspect(aspect, true);
            }
        }
    }

    public int getLinkedChannel() {
        return linkedChannel;
    }

    public void setLinkedChannel(int linkedChannel) {
        this.linkedChannel = linkedChannel;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("LinkedChannel", linkedChannel);
        tag.putInt("AspectId", aspectId);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        linkedChannel = tag.getInt("LinkedChannel");
        aspectId = tag.contains("AspectId") ? tag.getInt("AspectId") : SignalAspect.STOP.getId();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
