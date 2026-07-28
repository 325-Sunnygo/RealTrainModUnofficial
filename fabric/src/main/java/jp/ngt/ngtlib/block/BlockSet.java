package jp.ngt.ngtlib.block;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 本家 jp.ngt.ngtlib.block.BlockSet の 1.21 版。
 * 本家はブロック+メタだが、1.21 はメタが無いため BlockState を保持する
 * (metadata フィールドはスクリプト互換のため 0 固定で残す)。
 */
public class BlockSet {
    public static final BlockSet AIR = new BlockSet(Blocks.AIR, 0);

    public final int x;
    public final int y;
    public final int z;
    public final Block block;
    public final byte metadata;
    public final CompoundTag nbt;
    /** Remaster 拡張: 実際に設置する状態 */
    public final BlockState state;

    public BlockSet(Block block, int meta) {
        this(0, -1, 0, block, meta, null, block == null ? null : block.defaultBlockState());
    }

    public BlockSet(Block block, int meta, Object nbt) {
        this(0, -1, 0, block, meta, jp.ngt.mccompat.nbt.NBTTagCompound.unwrap(nbt),
                block == null ? null : block.defaultBlockState());
    }

    public BlockSet(int x, int y, int z, Block block, int meta) {
        this(x, y, z, block, meta, null, block == null ? null : block.defaultBlockState());
    }

    public BlockSet(int x, int y, int z, BlockState state, CompoundTag nbt) {
        this(x, y, z, state == null ? Blocks.AIR : state.getBlock(), 0, nbt, state);
    }

    public BlockSet(int x, int y, int z, Block block, int meta, CompoundTag nbt, BlockState state) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.block = block == null ? Blocks.AIR : block;
        this.metadata = (byte) meta;
        this.nbt = nbt;
        this.state = state == null ? this.block.defaultBlockState() : state;
    }

    public BlockState getState() {
        return this.state;
    }

    /** 本家 asKey: 座標を落とした比較用インスタンス (y=-1 が「座標なし」の印)。 */
    public BlockSet asKey() {
        return new BlockSet(0, -1, 0, this.block, this.metadata, this.nbt, this.state);
    }

    public boolean hasNBT() {
        return this.nbt != null;
    }

    /** 本家 setNBT: NBT を差し替えた新しい BlockSet を返す (自身は変えない)。 */
    public BlockSet setNBT(CompoundTag nbt) {
        return new BlockSet(this.x, this.y, this.z, this.block, this.metadata, nbt, this.state);
    }

    /**
     * 本家 BlockSet.writeToNBT。
     * 戻り値はシムの jp.ngt.mccompat.nbt.NBTTagCompound。
     */
    public jp.ngt.mccompat.nbt.NBTTagCompound writeToNBT() {
        return new jp.ngt.mccompat.nbt.NBTTagCompound(writeToVanillaNBT());
    }

    /** Java 側で実タグが要るとき用。 */
    public CompoundTag writeToVanillaNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("X", this.x);
        tag.putInt("Y", this.y);
        tag.putInt("Z", this.z);
        tag.putString("Block", BuiltInRegistries.BLOCK.getKey(this.block).toString());
        tag.put("State", net.minecraft.nbt.NbtUtils.writeBlockState(this.state));
        if (this.nbt != null) {
            tag.put("NBT", this.nbt);
        }
        return tag;
    }

    public static BlockSet readFromNBT(CompoundTag tag, net.minecraft.core.HolderGetter<Block> blocks) {
        BlockState state = net.minecraft.nbt.NbtUtils.readBlockState(blocks, tag.getCompound("State"));
        CompoundTag nbt = tag.contains("NBT") ? tag.getCompound("NBT") : null;
        return new BlockSet(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"), state, nbt);
    }

    @Override
    public int hashCode() {
        return this.state.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof BlockSet other)) {
            return false;
        }
        return this.state == other.state;
    }

    @Override
    public String toString() {
        return "BlockSet[" + BuiltInRegistries.BLOCK.getKey(this.block) + "]";
    }
}
