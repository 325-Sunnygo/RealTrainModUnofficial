package com.portofino.realtrainmodunofficial.blockentity;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlockEntities;
import com.portofino.realtrainmodunofficial.block.MarkerBlock;
import jp.ngt.rtm.rail.util.RailPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * マーカーのブロックエンティティ。RTM の TileEntityMarker 相当。
 * 通常は facing からデフォルトの RailPosition を生成するが、レンチ設定GUIで
 * アンカー(曲線制御)・カント(傾き)を上書き設定できる。設定値は NBT に保存・同期する。
 */
public class MarkerBlockEntity extends BlockEntity {
    private boolean configured;
    private float anchorYaw;
    private float anchorPitch;
    private float anchorLengthHorizontal = -1.0F;
    private float anchorLengthVertical;
    private float cantCenter;
    private float cantEdge;
    private float cantRandom;

    public MarkerBlockEntity(BlockPos pos, BlockState state) {
        super(RealTrainModUnofficialBlockEntities.MARKER.get(), pos, state);
    }

    public RailPosition getMarkerRP() {
        if (level == null) return null;
        BlockState st = getBlockState();
        if (!(st.getBlock() instanceof MarkerBlock)) return null;
        int facing = st.getValue(MarkerBlock.FACING);
        int dir = MarkerBlock.getMarkerDir(facing);
        boolean sw = ((MarkerBlock) st.getBlock()).isSwitch;
        RailPosition rp = new RailPosition(getBlockPos().getX(), getBlockPos().getY(), getBlockPos().getZ(), dir, sw ? 1 : 0);
        if (configured) {
            rp.anchorYaw = anchorYaw;
            rp.anchorPitch = anchorPitch;
            rp.anchorLengthHorizontal = anchorLengthHorizontal;
            rp.anchorLengthVertical = anchorLengthVertical;
            rp.cantCenter = cantCenter;
            rp.cantEdge = cantEdge;
            rp.cantRandom = cantRandom;
            rp.init();
        }
        return rp;
    }

    /** レンチ設定GUIからの値を適用する。 */
    public void configure(float anchorYaw, float anchorPitch, float anchorLengthHorizontal, float anchorLengthVertical,
                          float cantCenter, float cantEdge, float cantRandom) {
        this.configured = true;
        this.anchorYaw = anchorYaw;
        this.anchorPitch = anchorPitch;
        this.anchorLengthHorizontal = anchorLengthHorizontal;
        this.anchorLengthVertical = anchorLengthVertical;
        this.cantCenter = cantCenter;
        this.cantEdge = cantEdge;
        this.cantRandom = cantRandom;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    public boolean isConfigured() { return configured; }
    public float getAnchorYaw() { return configured ? anchorYaw : defaultAnchorYaw(); }
    public float getAnchorPitch() { return anchorPitch; }
    public float getAnchorLengthHorizontal() { return anchorLengthHorizontal; }
    public float getAnchorLengthVertical() { return anchorLengthVertical; }
    public float getCantCenter() { return cantCenter; }
    public float getCantEdge() { return cantEdge; }
    public float getCantRandom() { return cantRandom; }

    private float defaultAnchorYaw() {
        BlockState st = getBlockState();
        if (st.getBlock() instanceof MarkerBlock) {
            return MarkerBlock.getMarkerDir(st.getValue(MarkerBlock.FACING)) * 45.0F;
        }
        return 0.0F;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Configured", configured);
        tag.putFloat("A_Direction", anchorYaw);
        tag.putFloat("A_Pitch", anchorPitch);
        tag.putFloat("A_Length", anchorLengthHorizontal);
        tag.putFloat("A_LenV", anchorLengthVertical);
        tag.putFloat("C_Center", cantCenter);
        tag.putFloat("C_Edge", cantEdge);
        tag.putFloat("C_Random", cantRandom);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        configured = tag.getBoolean("Configured");
        anchorYaw = tag.getFloat("A_Direction");
        anchorPitch = tag.getFloat("A_Pitch");
        anchorLengthHorizontal = tag.contains("A_Length") ? tag.getFloat("A_Length") : -1.0F;
        anchorLengthVertical = tag.getFloat("A_LenV");
        cantCenter = tag.getFloat("C_Center");
        cantEdge = tag.getFloat("C_Edge");
        cantRandom = tag.getFloat("C_Random");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public static void tick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, MarkerBlockEntity be) {
    }
}
