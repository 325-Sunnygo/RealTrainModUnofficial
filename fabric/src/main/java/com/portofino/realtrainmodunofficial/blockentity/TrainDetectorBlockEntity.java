package com.portofino.realtrainmodunofficial.blockentity;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlockEntities;
import com.portofino.realtrainmodunofficial.block.TrainDetectorBlock;
import com.portofino.realtrainmodunofficial.entity.TrainEntity;
import com.portofino.realtrainmodunofficial.signal.SignalAspect;
import com.portofino.realtrainmodunofficial.signal.SignalNetworkSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class TrainDetectorBlockEntity extends BlockEntity {
    private static final int DEFAULT_RANGE = 3;
    private static final int MAX_RANGE = 64;
    private static final int RELEASE_DELAY_TICKS = 40;

    private int linkedChannel = -1;
    private int detectionRange = DEFAULT_RANGE;
    private boolean occupied;
    private int releaseCooldown;

    public TrainDetectorBlockEntity(BlockPos pos, BlockState blockState) {
        super(RealTrainModUnofficialBlockEntities.TRAIN_DETECTOR.get(), pos, blockState);
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, TrainDetectorBlockEntity blockEntity) {
        if ((level.getGameTime() + pos.asLong()) % 5L != 0L) {
            return;
        }
        double range = Math.max(1, blockEntity.detectionRange);
        AABB box = new AABB(pos).inflate(range, 3.0D, range);
        boolean occupiedNow = !level.getEntitiesOfClass(TrainEntity.class, box, train -> train.isAlive()).isEmpty();
        if (occupiedNow) {
            blockEntity.releaseCooldown = RELEASE_DELAY_TICKS;
        } else if (blockEntity.releaseCooldown > 0) {
            blockEntity.releaseCooldown--;
        }
        blockEntity.setOccupied(level, pos, state, occupiedNow || blockEntity.releaseCooldown > 0);
    }

    private void setOccupied(ServerLevel level, BlockPos pos, BlockState state, boolean occupiedNow) {
        if (this.occupied == occupiedNow && state.getValue(TrainDetectorBlock.POWERED) == occupiedNow) {
            return;
        }
        this.occupied = occupiedNow;
        if (state.hasProperty(TrainDetectorBlock.POWERED) && state.getValue(TrainDetectorBlock.POWERED) != occupiedNow) {
            level.setBlock(pos, state.setValue(TrainDetectorBlock.POWERED, occupiedNow), 3);
            level.updateNeighborsAt(pos, state.getBlock());
        }
        if (linkedChannel > 0) {
            SignalNetworkSavedData.get(level).setAspect(level.getServer(), linkedChannel, occupiedNow ? SignalAspect.STOP : SignalAspect.PROCEED);
        }
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("LinkedChannel", linkedChannel);
        tag.putInt("DetectionRange", detectionRange);
        tag.putBoolean("Occupied", occupied);
        tag.putInt("ReleaseCooldown", releaseCooldown);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        linkedChannel = tag.getInt("LinkedChannel");
        detectionRange = clampRange(tag.getInt("DetectionRange"));
        occupied = tag.getBoolean("Occupied");
        releaseCooldown = Math.max(0, tag.getInt("ReleaseCooldown"));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public int getLinkedChannel() {
        return linkedChannel;
    }

    public int getDetectionRange() {
        return detectionRange;
    }

    public boolean isOccupied() {
        return occupied;
    }

    public void configure(int linkedChannel, int detectionRange) {
        this.linkedChannel = linkedChannel;
        this.detectionRange = clampRange(detectionRange);
        setChanged();
    }

    private static int clampRange(int value) {
        return Math.max(1, Math.min(MAX_RANGE, value <= 0 ? DEFAULT_RANGE : value));
    }
}
