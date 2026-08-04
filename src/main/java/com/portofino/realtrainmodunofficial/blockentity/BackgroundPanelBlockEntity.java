package com.portofino.realtrainmodunofficial.blockentity;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlockEntities;
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
 * 背景パネル。模型やジオラマの背景に写真を立てるためのもの。
 *
 * <p><b>画像そのものをこのブロックが持つ</b> (PNG のバイト列)。
 * ファイルの置き場所を決めさせない (専用フォルダを作らない) ため、
 * また<b>マルチプレイでも相手に見える</b>ようにするため。
 * 選んだ画像は画面側で縮小して入れてくるので、ここは受け取って持つだけ。
 */
public class BackgroundPanelBlockEntity extends BlockEntity {

    /** 既定の幅 (ブロック)。置いた直後に何も見えないと分からないので、それなりの大きさにする。 */
    public static final float DEFAULT_SCALE = 8.0F;
    public static final float MIN_SCALE = 0.5F;
    public static final float MAX_SCALE = 128.0F;

    /**
     * 埋め込める上限。
     * ★ブロックエンティティの同期は NBT 2 MiB、カスタムパケットは 1 MiB が上限。
     * さらにピックブロックでアイテムに載るとインベントリ同期にも乗るので、
     * 余裕を持って小さく抑える。画面側もこの値まで縮小してから送ってくる。
     */
    public static final int MAX_IMAGE_BYTES = 700 * 1024;

    /** 画像 (PNG のバイト列)。空なら未設定 (紫と黒の格子で描く)。 */
    private byte[] image = new byte[0];
    /** 元のファイル名 (画面に出すだけ)。 */
    private String imageName = "";
    /** 幅 (ブロック)。高さは画像の縦横比から決める。 */
    private float scale = DEFAULT_SCALE;
    /** 上下の位置ずらし (ブロック)。地面に埋まる/浮かせるの微調整。 */
    private float offsetY;

    public BackgroundPanelBlockEntity(BlockPos pos, BlockState state) {
        super(RealTrainModUnofficialBlockEntities.BACKGROUND_PANEL.get(), pos, state);
    }

    public byte[] getImage() {
        return image;
    }

    public boolean hasImage() {
        return image.length > 0;
    }

    public String getImageName() {
        return imageName;
    }

    public float getScale() {
        return scale;
    }

    public float getOffsetY() {
        return offsetY;
    }

    /** 画面から設定を受ける。 */
    public void apply(byte[] newImage, String newName, float newScale, float newOffsetY) {
        byte[] data = newImage == null ? new byte[0] : newImage;
        //大きすぎる物は入れない (同期が通らなくなる)
        this.image = data.length > MAX_IMAGE_BYTES ? this.image : data;
        this.imageName = newName == null ? "" : (newName.length() > 128
            ? newName.substring(newName.length() - 128) : newName);
        this.scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, newScale));
        this.offsetY = Math.max(-64.0F, Math.min(64.0F, newOffsetY));
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (image.length > 0) {
            tag.putByteArray("ImageData", image);
        }
        tag.putString("ImageName", imageName);
        tag.putFloat("Scale", scale);
        tag.putFloat("OffsetY", offsetY);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        image = tag.contains("ImageData") ? tag.getByteArray("ImageData") : new byte[0];
        imageName = tag.getString("ImageName");
        scale = tag.contains("Scale") ? tag.getFloat("Scale") : DEFAULT_SCALE;
        offsetY = tag.getFloat("OffsetY");
    }

    // ---- クライアントへ同期 ----

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    /**
     * ★描画範囲。パネルはブロックより遥かに大きいので、
     * これを返さないと<b>ブロックが画面外に出た瞬間に背景ごと消える</b>。
     */
    public AABB getRenderBoundingBox() {
        double r = scale;
        BlockPos p = getBlockPos();
        return new AABB(p.getX() - r, p.getY() - r + offsetY, p.getZ() - r,
            p.getX() + r + 1, p.getY() + r + offsetY + 1, p.getZ() + r + 1);
    }
}
