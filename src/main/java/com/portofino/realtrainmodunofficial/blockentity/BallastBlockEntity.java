package com.portofino.realtrainmodunofficial.blockentity;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 道床(バラスト)ブロックのブロックエンティティ。
 * 対応するレールコア(LargeRailCore)の位置を保持し、道床を壊すとレールも撤去できるようにする。
 */
public class BallastBlockEntity extends BlockEntity {
    private BlockPos corePos;

    public BallastBlockEntity(BlockPos pos, BlockState state) {
        super(RealTrainModUnofficialBlockEntities.BALLAST.get(), pos, state);
    }

    public void setCorePos(BlockPos corePos) {
        this.corePos = corePos;
        setChanged();
    }

    /**
     * チャンクを組み立てるときに、道床の形 (4 隅の高さと貼るブロック) を渡す。
     *
     * <p>これがチャンクのメッシュへ焼かれるので、描いたあとは毎フレームの負担が無い。
     * レールを敷き直したときは {@code requestModelDataUpdate} で作り直させる。
     */
    @Override
    public net.neoforged.neoforge.client.model.data.ModelData getModelData() {
        if (this.level == null) {
            return net.neoforged.neoforge.client.model.data.ModelData.EMPTY;
        }
        var shape = com.portofino.realtrainmodunofficial.client.render.BallastGeometry
            .shapeAt(this.level, this.worldPosition);
        if (shape == null) {
            return net.neoforged.neoforge.client.model.data.ModelData.EMPTY;
        }
        return net.neoforged.neoforge.client.model.data.ModelData.builder()
            .with(com.portofino.realtrainmodunofficial.client.render.BallastModel.SHAPE, shape)
            .build();
    }

    public BlockPos getCorePos() {
        return corePos;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (corePos != null) {
            tag.putIntArray("CorePos", new int[]{corePos.getX(), corePos.getY(), corePos.getZ()});
        }
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
