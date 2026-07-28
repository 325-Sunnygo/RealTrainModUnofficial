package com.portofino.realtrainmodunofficial.convert;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 変換したワールドに置く復元データ (rtmu_restore.dat)。
 *
 * <p>旧ワールドから抜いた RTM オブジェクトの<b>生 NBT</b> をそのまま持つ。
 * 実際の変換 (モデル名 → RTMU の定義、レールの敷設) は、ワールドを最初に開いたときに
 * ゲーム内で行う ({@link LegacyRestorer})。モデルパックのレジストリはゲームが動いていないと
 * 引けないため。
 */
public final class RestoreData {

    /** ワールドフォルダ直下に置くファイル名。復元が終わると .done にリネームされる。 */
    public static final String FILE_NAME = "rtmu_restore.dat";
    public static final String DONE_NAME = "rtmu_restore.done";

    public static final int VERSION = 1;

    public final List<ObjectRecord> objects = new ArrayList<>();
    public final List<EntityRecord> entities = new ArrayList<>();
    /**
     * 旧 MOD のブロックを<b>バニラブロックに読み替えて置き直す</b>ためのリスト。
     * Minecraft の変換 (DataFixer) は MOD のブロックを空気にしてしまうので、
     * 位置と種類をここに退避しておき、ワールドを開いたときに置く。
     */
    public final List<BlockRecord> blocks = new ArrayList<>();

    public static final class BlockRecord {
        public String dimension = "minecraft:overworld";
        public int x;
        public int y;
        public int z;
        /** 旧 MOD の登録名 (名前空間なし。例: "cobbled_deepslate")。 */
        public String name = "";
        /** 旧ブロックのメタデータ (向き・色)。 */
        public int meta;
    }

    public static final class ObjectRecord {
        /** 正規化した旧タイルエンティティ id ("terailcore" 等)。 */
        public String type = "";
        public String dimension = "minecraft:overworld";
        public int x;
        public int y;
        public int z;
        /** 旧ブロックのメタデータ (取付面など)。 */
        public int meta;
        /** 旧タイルエンティティの生 NBT。 */
        public CompoundTag nbt = new CompoundTag();
    }

    public static final class EntityRecord {
        public String type = "";
        public String dimension = "minecraft:overworld";
        public CompoundTag nbt = new CompoundTag();
    }

    public boolean isEmpty() {
        return objects.isEmpty() && entities.isEmpty() && blocks.isEmpty();
    }

    // ---- 保存 / 読み込み ----

    public void write(Path worldDir) throws IOException {
        CompoundTag root = new CompoundTag();
        root.putInt("Version", VERSION);

        ListTag objectList = new ListTag();
        for (ObjectRecord rec : objects) {
            CompoundTag tag = new CompoundTag();
            tag.putString("Type", rec.type);
            tag.putString("Dim", rec.dimension);
            tag.putInt("X", rec.x);
            tag.putInt("Y", rec.y);
            tag.putInt("Z", rec.z);
            tag.putInt("Meta", rec.meta);
            tag.put("Nbt", rec.nbt);
            objectList.add(tag);
        }
        root.put("Objects", objectList);

        ListTag entityList = new ListTag();
        for (EntityRecord rec : entities) {
            CompoundTag tag = new CompoundTag();
            tag.putString("Type", rec.type);
            tag.putString("Dim", rec.dimension);
            tag.put("Nbt", rec.nbt);
            entityList.add(tag);
        }
        root.put("Entities", entityList);

        //ブロックは数が多い (数十万になりうる) のでコンパクトに:
        //  名前のパレット + [x, y, z, パレット番号, メタ] の int 配列
        if (!blocks.isEmpty()) {
            List<String> palette = new ArrayList<>();
            java.util.Map<String, Integer> index = new java.util.HashMap<>();
            int[] data = new int[blocks.size() * 6];
            List<String> dims = new ArrayList<>();
            java.util.Map<String, Integer> dimIndex = new java.util.HashMap<>();
            int i = 0;
            for (BlockRecord rec : blocks) {
                int nameId = index.computeIfAbsent(rec.name, n -> {
                    palette.add(n);
                    return palette.size() - 1;
                });
                int dimId = dimIndex.computeIfAbsent(rec.dimension, d -> {
                    dims.add(d);
                    return dims.size() - 1;
                });
                data[i++] = rec.x;
                data[i++] = rec.y;
                data[i++] = rec.z;
                data[i++] = nameId;
                data[i++] = rec.meta;
                data[i++] = dimId;
            }
            ListTag paletteTag = new ListTag();
            palette.forEach(n -> paletteTag.add(net.minecraft.nbt.StringTag.valueOf(n)));
            ListTag dimTag = new ListTag();
            dims.forEach(d -> dimTag.add(net.minecraft.nbt.StringTag.valueOf(d)));
            root.put("BlockPalette", paletteTag);
            root.put("BlockDims", dimTag);
            root.putIntArray("BlockData", data);
        }

        NbtIo.writeCompressed(root, worldDir.resolve(FILE_NAME));
    }

    public static RestoreData read(Path file) throws IOException {
        CompoundTag root = NbtIo.readCompressed(file, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        RestoreData data = new RestoreData();

        ListTag objectList = root.getList("Objects", Tag.TAG_COMPOUND);
        for (int i = 0; i < objectList.size(); i++) {
            CompoundTag tag = objectList.getCompound(i);
            ObjectRecord rec = new ObjectRecord();
            rec.type = tag.getString("Type");
            rec.dimension = tag.getString("Dim");
            rec.x = tag.getInt("X");
            rec.y = tag.getInt("Y");
            rec.z = tag.getInt("Z");
            rec.meta = tag.getInt("Meta");
            rec.nbt = tag.getCompound("Nbt");
            data.objects.add(rec);
        }

        ListTag entityList = root.getList("Entities", Tag.TAG_COMPOUND);
        for (int i = 0; i < entityList.size(); i++) {
            CompoundTag tag = entityList.getCompound(i);
            EntityRecord rec = new EntityRecord();
            rec.type = tag.getString("Type");
            rec.dimension = tag.getString("Dim");
            rec.nbt = tag.getCompound("Nbt");
            data.entities.add(rec);
        }

        if (root.contains("BlockData")) {
            ListTag paletteTag = root.getList("BlockPalette", Tag.TAG_STRING);
            ListTag dimTag = root.getList("BlockDims", Tag.TAG_STRING);
            int[] arr = root.getIntArray("BlockData");
            for (int i = 0; i + 5 < arr.length; i += 6) {
                BlockRecord rec = new BlockRecord();
                rec.x = arr[i];
                rec.y = arr[i + 1];
                rec.z = arr[i + 2];
                int nameId = arr[i + 3];
                rec.name = nameId >= 0 && nameId < paletteTag.size() ? paletteTag.getString(nameId) : "";
                rec.meta = arr[i + 4];
                int dimId = arr[i + 5];
                rec.dimension = dimId >= 0 && dimId < dimTag.size() ? dimTag.getString(dimId) : "minecraft:overworld";
                data.blocks.add(rec);
            }
        }
        return data;
    }

    public static boolean exists(Path worldDir) {
        return Files.isRegularFile(worldDir.resolve(FILE_NAME));
    }
}
