package jp.ngt.rtm.block.tileentity;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlockEntities;
import jp.ngt.rtm.block.decoration.DecorationModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 装飾ブロックの BE。本家 {@code TileEntityDecoration} の移植。モデル名だけ持つ。
 */
public class TileEntityDecoration extends BlockEntity {

    private String modelName = DecorationModel.DEFAULT_MODEL.name;

    public TileEntityDecoration(BlockPos pos, BlockState state) {
        super(RealTrainModUnofficialBlockEntities.DECORATION.get(), pos, state);
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);
        this.modelName = nbt.getString("ModelName");
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.saveAdditional(nbt, registries);
        nbt.putString("ModelName", this.modelName);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public void setModelName(String par1) {
        this.modelName = par1 == null ? DecorationModel.DEFAULT_MODEL.name : par1;
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public String getModelName() {
        return this.modelName;
    }
}
