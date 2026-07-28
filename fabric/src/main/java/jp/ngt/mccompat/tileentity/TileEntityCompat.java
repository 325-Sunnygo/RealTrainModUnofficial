package jp.ngt.mccompat.tileentity;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 1.7.10 の {@code TileEntity} の NBT 読み書きを 1.21 へ橋渡しする<b>静的ヘルパー</b>。
 *
 * <p>{@code WorldCompat.func_147438_o} は素の {@link BlockEntity} を返す (スクリプトが
 * 実クラスのメソッドを直接呼ぶため)。したがってラッパーでは包めないので、
 * {@code PackScriptSource.remapVanillaOnlyMethods} が
 * {@code tile.func_145839_a(nbt)} をここへの静的呼び出しへ書き換える。
 *
 * <p>1.21 の NBT 入出力は {@link HolderLookup.Provider} を要求するので、レベルから引いて補う。
 */
public final class TileEntityCompat {

    /**
     * field_145850_b = TileEntity.worldObj。
     * レシーバがバニラ {@code BlockEntity} でフィールドを足せないため、
     * {@code PackScriptSource.remapFieldAccess} がここへ回してくる。
     * <pre>NGTO Builder.zip!.../Wire/server_Wire.js:209  tileEntity.field_145850_b</pre>
     */
    public static jp.ngt.mccompat.WorldCompat field_145850_b(Object tile) {
        if (tile instanceof net.minecraft.world.level.block.entity.BlockEntity be && be.getLevel() != null) {
            return new jp.ngt.mccompat.WorldCompat(be.getLevel());
        }
        return null;
    }

    private TileEntityCompat() {
    }

    private static HolderLookup.Provider registries(BlockEntity tile) {
        return tile != null && tile.getLevel() != null ? tile.getLevel().registryAccess() : null;
    }

    /** func_145839_a = readFromNBT */
    public static void func_145839_a(Object tileObj, Object nbt) {
        if (!(tileObj instanceof BlockEntity tile)) {
            return;
        }
        CompoundTag tag = jp.ngt.mccompat.nbt.NBTTagCompound.unwrap(nbt);
        HolderLookup.Provider registries = registries(tile);
        if (tag == null || registries == null) {
            return;
        }
        tile.loadWithComponents(tag, registries);
        tile.setChanged();
    }

    /** func_145841_b / func_189515_b = writeToNBT。渡された tag に書き込んで返す。 */
    public static Object func_145841_b(Object tileObj, Object nbt) {
        if (!(tileObj instanceof BlockEntity tile)) {
            return nbt;
        }
        CompoundTag tag = jp.ngt.mccompat.nbt.NBTTagCompound.unwrap(nbt);
        HolderLookup.Provider registries = registries(tile);
        if (tag == null || registries == null) {
            return nbt;
        }
        tag.merge(tile.saveWithFullMetadata(registries));
        return nbt;
    }
}
