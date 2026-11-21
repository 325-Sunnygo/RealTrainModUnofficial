package com.portofino.realtrainmodunofficial.blockentity;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class RailCollisionBlockEntity extends BlockEntity {
    private BlockPos corePos;
    // ブロック内のレール面の高さ(0.0=底, 1.0=上面)。坂では端数になり、ここに薄い当たり判定を出す。
    private float surfaceY = 0.0f;

    public RailCollisionBlockEntity(BlockPos pos, BlockState state) {
        super(RealTrainModUnofficialBlockEntities.RAIL_COLLISION.get(), pos, state);
    }

    public void setCorePos(BlockPos corePos) {
        this.corePos = corePos;
        setChanged();
    }

    public BlockPos getCorePos() {
        return corePos;
    }

    /** ブロック内のレール面高さ(0..1)。坂で薄い当たり判定をレール面に合わせるために使う。 */
    public float getSurfaceY() {
        return surfaceY;
    }

    public void setSurfaceY(float surfaceY) {
        this.surfaceY = Math.max(0.0f, Math.min(1.0f, surfaceY));
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (corePos != null) {
            tag.putIntArray("CorePos", new int[]{corePos.getX(), corePos.getY(), corePos.getZ()});
        }
        tag.putFloat("SurfaceY", surfaceY);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("CorePos")) {
            int[] a = tag.getIntArray("CorePos");
            if (a.length >= 3) {
                corePos = new BlockPos(a[0], a[1], a[2]);
            }
        }
        if (tag.contains("SurfaceY")) {
            surfaceY = tag.getFloat("SurfaceY");
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}

