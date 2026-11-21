package jp.kaiz.atsassistmod.block.tileentity;

import jp.kaiz.atsassistmod.ATSAssistCore;
import jp.kaiz.atsassistmod.block.GroundUnitBlock;
import jp.kaiz.atsassistmod.block.GroundUnitLogic;
import jp.kaiz.atsassistmod.block.GroundUnitType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 本家 TileEntityGroundUnit の BlockEntity 部分 (1.21)。動作は {@link GroundUnitLogic} に委譲。
 * 種類の変更はブロックステート (unit_type) と logic の差し替えで行う。
 */
public class GroundUnitBlockEntity extends BlockEntity {

    private GroundUnitLogic logic = new GroundUnitLogic.None();
    private boolean linkRedStone;
    private int redStoneOutput;

    public GroundUnitBlockEntity(BlockPos pos, BlockState state) {
        super(ATSAssistCore.GROUND_UNIT_BE.get(), pos, state);
        //ブロックステートの種類に合わせて logic を用意
        this.logic = GroundUnitType.getType(state.getValue(GroundUnitBlock.UNIT_TYPE)).newLogic();
    }

    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos,
                                  BlockState state, GroundUnitBlockEntity be) {
        be.logic.tick(level, pos, be);
    }

    public GroundUnitLogic getLogic() {
        return this.logic;
    }

    public GroundUnitType getUnitType() {
        return this.logic.getType();
    }

    public boolean isLinkRedStone() {
        return this.linkRedStone;
    }

    public void setLinkRedStone(boolean linkRedStone) {
        this.linkRedStone = linkRedStone;
    }

    public int getRedStoneOutput() {
        return this.redStoneOutput;
    }

    /** 本家 setRedStoneOutput: 変化したら周囲へ通知。 */
    public void setRedStoneOutput(int power) {
        if (this.redStoneOutput != power) {
            this.redStoneOutput = power;
            if (this.level != null) {
                this.level.updateNeighborsAt(this.worldPosition, this.getBlockState().getBlock());
            }
        }
    }

    /** 種類変更 (GUI から)。ブロックステートも合わせて変えて見た目を切り替える。 */
    public void setUnitType(GroundUnitType type) {
        if (this.logic.getType() == type) {
            return;
        }
        this.logic = type.newLogic();
        if (this.level != null) {
            this.level.setBlock(this.worldPosition,
                    this.getBlockState().setValue(GroundUnitBlock.UNIT_TYPE, type.id), 3);
        }
        this.setChanged();
    }

    /** ATACS_Disable 地上子の自己変換 (本家仕様)。 */
    public void convertTo(GroundUnitType type) {
        this.setUnitType(type);
        this.sync();
    }

    /** GUI からの設定 (unitType + 各パラメータ + linkRedStone) を一括適用。 */
    public void applyConfig(CompoundTag tag) {
        GroundUnitType type = GroundUnitType.getType(tag.getInt("unitType"));
        if (this.logic.getType() != type) {
            this.setUnitType(type);
        }
        this.logic.readNBT(tag);
        this.linkRedStone = tag.getBoolean("linkRedStone");
        this.setChanged();
        this.sync();
    }

    /** 現在の設定を GUI 表示用に集める。 */
    public CompoundTag collectConfig() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("unitType", this.logic.getType().id);
        tag.putBoolean("linkRedStone", this.linkRedStone);
        this.logic.writeNBT(tag);
        return tag;
    }

    private void sync() {
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    //------------------------------------------------------------ NBT

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        GroundUnitType type = GroundUnitType.getType(tag.getInt("unitType"));
        if (this.logic.getType() != type) {
            this.logic = type.newLogic();
        }
        this.linkRedStone = tag.getBoolean("linkRedStone");
        this.redStoneOutput = tag.getInt("redStoneOutput");
        this.logic.readNBT(tag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("unitType", this.logic.getType().id);
        tag.putBoolean("linkRedStone", this.linkRedStone);
        tag.putInt("redStoneOutput", this.redStoneOutput);
        this.logic.writeNBT(tag);
    }

    //クライアント同期 (GUI が現在値を読むため)
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
