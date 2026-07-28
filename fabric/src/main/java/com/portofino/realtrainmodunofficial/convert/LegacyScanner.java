package com.portofino.realtrainmodunofficial.convert;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * 旧ワールド (1.7.10 / 1.12.2) のチャンクから RTM のオブジェクトを抜き出す。
 *
 * <p>抜き出すのは <b>タイルエンティティとエンティティの生 NBT</b> だけ。変換 (名前→定義の解決など) は
 * ゲーム内の復元時に行う。理由は 2 つ:
 * <ul>
 *   <li>モデルパックのレジストリはゲームが起動していないと引けない</li>
 *   <li>変換ロジックを後から直しても、ワールドを再変換しなくてよい</li>
 * </ul>
 *
 * <p>ブロック ID は数値でしか保存されておらず、その対応表 (FML レジストリ) は level.dat にある。
 * ただし RTM の設置物・レールは<b>すべてタイルエンティティを持つ</b>ので、ID 表が無くても
 * 位置と中身は取り出せる。ブロックのメタデータ (取付面) だけはチャンクのセクションから読む。
 */
public final class LegacyScanner {

    /** 1 チャンク = 16x16、セクション = 16 段。 */
    private static final int SECTION_SIZE = 16;

    private LegacyScanner() {
    }

    public static class Result {
        public final List<RestoreData.ObjectRecord> objects = new ArrayList<>();
        public final List<RestoreData.EntityRecord> entities = new ArrayList<>();
        /** 見つかったが対応表に無かった id → 件数 (ログ用)。 */
        public final Map<String, Integer> unknown = new TreeMap<>();
        public int chunks;
        /** 旧 MOD ブロック → バニラに読み替えたリスト。 */
        public final List<RestoreData.BlockRecord> blocks = new ArrayList<>();
        /** ブロックの数値 ID → 登録名 (level.dat の Forge レジストリ)。 */
        public Map<Integer, String> blockIds = Map.of();
        /** 変換できなかった MOD ブロック名 → 件数 (ログ用)。 */
        public final Map<String, Integer> unknownBlocks = new TreeMap<>();
    }

    /**
     * ワールドの 1 ディメンションぶん (region/*.mca) を走査する。
     *
     * @param dimension 復元先のディメンション ID ("minecraft:overworld" 等)
     */
    public static void scanDimension(Path regionDir, String dimension, Result result) {
        if (!Files.isDirectory(regionDir)) {
            return;
        }
        try (Stream<Path> files = Files.list(regionDir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".mca")).sorted().forEach(mca -> {
                for (CompoundTag chunk : LegacyRegion.readChunks(mca)) {
                    scanChunk(chunk, dimension, result);
                    result.chunks++;
                }
            });
        } catch (IOException e) {
            RealTrainModUnofficial.LOGGER.warn("[convert] region dir failed: {}", regionDir, e);
        }
    }

    /**
     * チャンクの中から、バニラに読み替えられる MOD ブロックを拾う。
     *
     * <p>1.7.10 / 1.12.2 のチャンクはブロックを数値 ID で持つ:
     * {@code Sections[].Blocks} (下位 8bit) + {@code Add} (上位 4bit) + {@code Data} (メタ)。
     */
    private static void scanModBlocks(CompoundTag level, String dimension, Result result) {
        if (result.blockIds.isEmpty()) {
            return;
        }
        int chunkX = level.getInt("xPos");
        int chunkZ = level.getInt("zPos");
        net.minecraft.nbt.ListTag sections = level.getList("Sections", Tag.TAG_COMPOUND);
        for (int s = 0; s < sections.size(); s++) {
            CompoundTag section = sections.getCompound(s);
            byte[] blocks = section.getByteArray("Blocks");
            if (blocks.length != 4096) {
                continue;
            }
            int baseY = section.getByte("Y") * 16;
            byte[] add = section.contains("Add") ? section.getByteArray("Add") : null;
            byte[] data = section.contains("Data") ? section.getByteArray("Data") : null;

            for (int i = 0; i < 4096; i++) {
                int id = blocks[i] & 0xFF;
                if (add != null && add.length == 2048) {
                    id |= nibble(add, i) << 8;
                }
                if (id == 0) {
                    continue;
                }
                String name = result.blockIds.get(id);
                if (name == null || !name.toLowerCase(java.util.Locale.ROOT)
                        .startsWith(LegacyVanillaBlocks.MOD_ID + ":")) {
                    continue;
                }
                int meta = data != null && data.length == 2048 ? nibble(data, i) : 0;
                String simple = name.substring(name.indexOf(':') + 1);
                if (!LegacyVanillaBlocks.canMap(simple)) {
                    result.unknownBlocks.merge(simple, 1, Integer::sum);
                    continue;
                }
                RestoreData.BlockRecord rec = new RestoreData.BlockRecord();
                rec.dimension = dimension;
                rec.x = chunkX * 16 + (i & 15);
                rec.y = baseY + (i >> 8);
                rec.z = chunkZ * 16 + ((i >> 4) & 15);
                rec.name = simple;
                rec.meta = meta;
                result.blocks.add(rec);
            }
        }
    }

    private static int nibble(byte[] arr, int index) {
        int b = arr[index >> 1] & 0xFF;
        return (index & 1) == 0 ? (b & 0x0F) : (b >> 4);
    }

    private static void scanChunk(CompoundTag chunk, String dimension, Result result) {
        //1.7.10 / 1.12.2 は Level 直下。念のため Level が無い場合はルートを見る。
        CompoundTag level = chunk.contains("Level") ? chunk.getCompound("Level") : chunk;

        //旧 MOD のブロックをバニラに読み替えるため位置を控える
        scanModBlocks(level, dimension, result);

        Sections sections = Sections.of(level);

        ListTag tiles = level.getList("TileEntities", Tag.TAG_COMPOUND);
        for (int i = 0; i < tiles.size(); i++) {
            CompoundTag te = tiles.getCompound(i);
            String id = LegacyIds.normalize(te.getString("id"));
            if (id.isEmpty()) {
                continue;
            }
            LegacyIds.Kind kind = LegacyIds.tileKind(id);
            if (kind == null) {
                if (LegacyIds.looksLikeRtm(id)) {
                    result.unknown.merge("te:" + id, 1, Integer::sum);
                }
                continue;
            }
            int x = te.getInt("x");
            int y = te.getInt("y");
            int z = te.getInt("z");
            RestoreData.ObjectRecord rec = new RestoreData.ObjectRecord();
            rec.type = id;
            rec.dimension = dimension;
            rec.x = x;
            rec.y = y;
            rec.z = z;
            rec.meta = sections.meta(x, y, z);
            rec.nbt = te.copy();
            result.objects.add(rec);
        }

        ListTag ents = level.getList("Entities", Tag.TAG_COMPOUND);
        for (int i = 0; i < ents.size(); i++) {
            CompoundTag ent = ents.getCompound(i);
            String id = LegacyIds.normalize(ent.getString("id"));
            if (id.isEmpty()) {
                continue;
            }
            LegacyIds.Kind kind = LegacyIds.entityKind(id);
            if (kind == null) {
                if (LegacyIds.looksLikeRtm(id)) {
                    result.unknown.merge("entity:" + id, 1, Integer::sum);
                }
                continue;
            }
            RestoreData.EntityRecord rec = new RestoreData.EntityRecord();
            rec.type = id;
            rec.dimension = dimension;
            rec.nbt = ent.copy();
            result.entities.add(rec);
        }
    }

    /**
     * チャンクのセクション (数値ブロック ID + メタデータ)。取付面の復元にメタデータが要る。
     */
    private static final class Sections {
        private final Map<Integer, byte[]> data = new HashMap<>();

        static Sections of(CompoundTag level) {
            Sections s = new Sections();
            ListTag list = level.getList("Sections", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag sec = list.getCompound(i);
                if (!sec.contains("Data")) {
                    continue;
                }
                s.data.put((int) sec.getByte("Y"), sec.getByteArray("Data"));
            }
            return s;
        }

        /** ニブル配列からメタデータを読む (見つからなければ 0)。 */
        int meta(int x, int y, int z) {
            byte[] nibbles = data.get(Math.floorDiv(y, SECTION_SIZE));
            if (nibbles == null) {
                return 0;
            }
            int index = (Math.floorMod(y, SECTION_SIZE) * SECTION_SIZE + Math.floorMod(z, SECTION_SIZE))
                    * SECTION_SIZE + Math.floorMod(x, SECTION_SIZE);
            int half = index >> 1;
            if (half < 0 || half >= nibbles.length) {
                return 0;
            }
            return (index & 1) == 0 ? (nibbles[half] & 0x0F) : ((nibbles[half] >> 4) & 0x0F);
        }
    }

    /** ディメンションフォルダ名 → 1.21 のディメンション ID。 */
    public static String dimensionOf(String folderName) {
        String n = folderName.toLowerCase(Locale.ROOT);
        return switch (n) {
            case "dim-1" -> "minecraft:the_nether";
            case "dim1" -> "minecraft:the_end";
            default -> "minecraft:overworld";
        };
    }
}
