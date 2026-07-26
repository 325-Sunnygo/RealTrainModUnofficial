package jp.ngt.ngtlib.block;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * 本家 jp.ngt.ngtlib.block.NGTObject (MCTE ミニチュアの中身 = ブロックの3D集合) の
 * 最低限実装。NGTO Builder のスクリプトが xSize/ySize/zSize/getBlockSet を使う。
 * ブロックは (x,y,z) → index = x + z*xSize + y*xSize*zSize で平坦化して保持。
 */
public class NGTObject {
    public long objId;
    public List<BlockSet> blockList = new ArrayList<>();
    public int xSize;
    public int ySize;
    public int zSize;
    public int origX;
    public int origY;
    public int origZ;

    private BlockSet[] grid;

    protected NGTObject() {
    }

    public static NGTObject createNGTO(Object blocks, int w, int h, int d, int x, int y, int z) {
        NGTObject obj = new NGTObject();
        obj.objId = jp.ngt.ngtlib.util.NGTUtil.getUniqueId();
        obj.xSize = Math.max(w, 1);
        obj.ySize = Math.max(h, 1);
        obj.zSize = Math.max(d, 1);
        obj.origX = x;
        obj.origY = y;
        obj.origZ = z;
        obj.grid = new BlockSet[obj.xSize * obj.ySize * obj.zSize];
        for (Object o : coerceList(blocks)) {
            if (o instanceof BlockSet set) {
                obj.blockList.add(set);
                obj.put(set);
            }
        }
        return obj;
    }

    private static List<Object> coerceList(Object blocks) {
        List<Object> out = new ArrayList<>();
        if (blocks instanceof List<?> l) {
            out.addAll(l);
        } else if (blocks instanceof Object[] arr) {
            java.util.Collections.addAll(out, arr);
        } else if (blocks != null) {
            out.add(blocks);
        }
        return out;
    }

    private void put(BlockSet set) {
        int x = set.x;
        int y = Math.max(set.y, 0);
        int z = set.z;
        if (x >= 0 && x < xSize && y < ySize && z >= 0 && z < zSize) {
            grid[x + z * xSize + y * xSize * zSize] = set;
        }
    }

    public BlockSet getBlockSet(int x, int y, int z) {
        if (x >= 0 && y >= 0 && z >= 0 && x < xSize && y < ySize && z < zSize) {
            BlockSet set = grid[x + z * xSize + y * xSize * zSize];
            return set != null ? set : BlockSet.AIR;
        }
        return BlockSet.AIR;
    }

    /** 本家 setBlockSet: 指定位置のブロックを差し替える。変わったら true。 */
    public boolean setBlockSet(int x, int y, int z, net.minecraft.world.level.block.Block block, int meta) {
        if (x < 0 || y < 0 || z < 0 || x >= this.xSize || y >= this.ySize || z >= this.zSize) {
            return false;
        }
        BlockSet old = this.getBlockSet(x, y, z);
        if (old != null && old.block == block && old.metadata == (byte) meta) {
            return false;
        }
        BlockSet set = new BlockSet(x, y, z, block, meta);
        this.grid[x + z * this.xSize + y * this.xSize * this.zSize] = set;
        this.blockList.removeIf(b -> b.x == x && b.y == y && b.z == z);
        this.blockList.add(set);
        this.lightValue = -1;
        return true;
    }

    /** ミニチュア内のエンティティ。RTMU はブロックのみ保持する。 */
    public java.util.List<Object> getEntityList() {
        return java.util.List.of();
    }

    /** 本家 getLightValue: 含まれるブロックの最大発光量。 */
    public int getLightValue() {
        if (this.lightValue < 0) {
            int brightness = 0;
            for (BlockSet set : this.blockList) {
                if (set == null) {
                    continue;
                }
                brightness = Math.max(brightness, set.getState().getLightEmission());
            }
            this.lightValue = brightness;
        }
        return this.lightValue;
    }

    /** 発光量キャッシュ (-1 = 未計算)。 */
    private int lightValue = -1;

    /** 本家 exportToFile: gzip NBT でファイルへ書き出す。 */
    public void exportToFile(java.io.File file) {
        try {
            net.minecraft.nbt.NbtIo.writeCompressed(this.writeToNBT(), file.toPath());
        } catch (java.io.IOException e) {
            jp.ngt.ngtlib.io.NGTLog.debug("[NGTObject] export failed: " + e);
        }
    }

    /** 本家 importFromFile。読めなければ null。 */
    public static NGTObject importFromFile(java.io.File file) {
        try {
            CompoundTag tag = net.minecraft.nbt.NbtIo.readCompressed(
                    file.toPath(), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            return tag == null ? null : readFromNBT(tag);
        } catch (java.io.IOException e) {
            jp.ngt.ngtlib.io.NGTLog.debug("[NGTObject] import failed: " + e);
            return null;
        }
    }

    /** 本家 load: ストリームから読む (パック内のミニチュア用)。 */
    public static NGTObject load(java.io.InputStream stream) {
        try {
            CompoundTag tag = net.minecraft.nbt.NbtIo.readCompressed(
                    stream, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            return tag == null ? null : readFromNBT(tag);
        } catch (java.io.IOException e) {
            jp.ngt.ngtlib.io.NGTLog.debug("[NGTObject] load failed: " + e);
            return null;
        }
    }

    public CompoundTag writeToNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("ObjId", this.objId);
        tag.putInt("SizeX", this.xSize);
        tag.putInt("SizeY", this.ySize);
        tag.putInt("SizeZ", this.zSize);
        tag.putInt("OrigX", this.origX);
        tag.putInt("OrigY", this.origY);
        tag.putInt("OrigZ", this.origZ);
        ListTag list = new ListTag();
        for (BlockSet set : this.blockList) {
            list.add(set.writeToVanillaNBT());
        }
        tag.put("BlocksData", list);
        return tag;
    }

    public static NGTObject readFromNBT(CompoundTag tag) {
        NGTObject obj = new NGTObject();
        obj.objId = tag.getLong("ObjId");
        obj.xSize = Math.max(tag.getInt("SizeX"), 1);
        obj.ySize = Math.max(tag.getInt("SizeY"), 1);
        obj.zSize = Math.max(tag.getInt("SizeZ"), 1);
        obj.origX = tag.getInt("OrigX");
        obj.origY = tag.getInt("OrigY");
        obj.origZ = tag.getInt("OrigZ");
        obj.grid = new BlockSet[obj.xSize * obj.ySize * obj.zSize];
        net.minecraft.core.HolderGetter<net.minecraft.world.level.block.Block> blocks =
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup();
        ListTag list = tag.getList("BlocksData", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            BlockSet set = BlockSet.readFromNBT(list.getCompound(i), blocks);
            obj.blockList.add(set);
            obj.put(set);
        }
        return obj;
    }
}
