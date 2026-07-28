package com.portofino.realtrainmodunofficial.blockentity;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlockEntities;
import jp.ngt.mcte.item.ItemMiniature;
import jp.ngt.ngtlib.block.NGTObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * 設置済みミニチュア (neo mcte)。本家 MCTE TileEntityMiniature 相当。
 * 中身はこのブロックエンティティが丸ごと持つ。外部テーブルに ID で預けない。
 */
public class MiniatureBlockEntity extends BlockEntity {

    /** アイテムの NBT をそのまま保持する (ItemMiniature の契約がそのまま効く)。 */
    private CompoundTag miniature = new CompoundTag();
    /** 設置時のプレイヤー向き (度)。 */
    private float rotation;
    /** クリックした面 (Direction.get3DDataValue)。本家 attachSide。 */
    private byte attachSide;

    /** 描画用にほどいた NGTObject のキャッシュ (NBT から毎フレーム作らない)。 */
    private NGTObject cachedObject;
    private boolean cacheBuilt;

    public MiniatureBlockEntity(BlockPos pos, BlockState state) {
        super(RealTrainModUnofficialBlockEntities.MINIATURE.get(), pos, state);
    }

    /** アイテムの NBT を丸ごと受け取る。 */
    public void setMiniatureTag(CompoundTag tag) {
        this.miniature = tag == null ? new CompoundTag() : tag.copy();
        this.cacheBuilt = false;
        this.cachedObject = null;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    /** 壊したときにアイテムへ戻すための NBT。 */
    public CompoundTag getMiniatureTag() {
        return miniature.copy();
    }

    public void setPlacement(float rotation, byte attachSide) {
        this.rotation = rotation;
        this.attachSide = attachSide;
        setChanged();
    }

    public float getRotation() {
        return rotation;
    }

    public byte getAttachSide() {
        return attachSide;
    }

    public float getScale() {
        return ItemMiniature.getScale(miniature);
    }

    public float[] getOffset() {
        return ItemMiniature.getOffset(miniature);
    }

    public ItemMiniature.MiniatureMode getMode() {
        return ItemMiniature.getMode(miniature);
    }

    public int getLightValue() {
        return ItemMiniature.getLightValue(miniature);
    }

    /** 中身。無ければ null。 */
    public NGTObject getNGTObject() {
        if (!cacheBuilt) {
            cacheBuilt = true;
            cachedObject = ItemMiniature.getNGTObject(miniature);
        }
        return cachedObject;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Miniature", miniature.copy());
        tag.putFloat("Rotation", rotation);
        tag.putByte("AttachSide", attachSide);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        miniature = tag.contains("Miniature") ? tag.getCompound("Miniature").copy() : new CompoundTag();
        rotation = tag.getFloat("Rotation");
        attachSide = tag.getByte("AttachSide");
        cacheBuilt = false;
        cachedObject = null;
    }

    // ---- クライアントへの同期 (中身が無いと描けないので初期同期に載せる) ----

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * 縮尺次第で 1 ブロックからはみ出すので、描画範囲を中身の大きさから決める。
     * これが無いと大きなミニチュアがブロック境界で切れる。
     */
    public AABB getRenderBoundingBox() {
        NGTObject obj = getNGTObject();
        BlockPos p = getBlockPos();
        if (obj == null) {
            return new AABB(p);
        }
        float s = Math.max(0.001F, getScale());
        double w = Math.max(obj.xSize, Math.max(obj.ySize, obj.zSize)) * s + 1.0D;
        return new AABB(p).inflate(w);
    }
}
