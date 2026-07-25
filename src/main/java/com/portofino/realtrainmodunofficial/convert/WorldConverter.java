package com.portofino.realtrainmodunofficial.convert;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 1.7.10 / 1.12.2 の RTM ワールドを 1.21.1 の RTMU ワールドへ変換する。
 *
 * <p><b>考え方</b><br>
 * バニラの地形変換 (ブロックのフラット化など) は Minecraft 自身の DataFixerUpper が
 * 完璧にやってくれる。古いワールドをそのまま開けば地形は正しく上がる。ただし
 * <b>MOD のブロックは数値 ID でしか保存されておらず、DFU は空気に潰してしまう</b>。
 *
 * <p>そこで:
 * <ol>
 *   <li>ワールドをコピーする (元は触らない)</li>
 *   <li>コピーから RTM のタイルエンティティ/エンティティを<b>先に抜き出して</b>別ファイルに保存する</li>
 *   <li>level.dat から Forge/FML のレジストリ情報を落とし、現行フォーマットに上げる</li>
 *   <li>チャンクはそのまま残す → Minecraft が開くときに DFU が地形を変換する</li>
 *   <li>最初に開いたときに {@link LegacyRestorer} が RTMU のオブジェクトとして置き直す</li>
 * </ol>
 *
 * <p>この形にすると、変換ロジックを直したときにワールドを再変換しなくてよい
 * (復元データには生の NBT が入っているため)。
 */
public final class WorldConverter {

    /** ここに旧ワールドのフォルダを入れると、起動時に変換される。 */
    public static final String INPUT_DIR = "rtmu_convert";
    /** 変換済みの入力を退避する先。 */
    public static final String DONE_DIR = "done";

    private WorldConverter() {
    }

    public record Report(String name, Path output, int objects, int entities, int chunks,
                         Map<String, Integer> unknown) {
    }

    /**
     * 入力フォルダを走査し、見つかった旧ワールドをすべて変換する。
     *
     * @param gameDir  .minecraft
     * @param savesDir 変換先 (通常は .minecraft/saves)
     */
    public static List<Report> convertAll(Path gameDir, Path savesDir) {
        List<Report> reports = new ArrayList<>();
        Path input = gameDir.resolve(INPUT_DIR);
        if (!Files.isDirectory(input)) {
            try {
                Files.createDirectories(input);
                Files.writeString(input.resolve("ここに旧ワールドのフォルダを入れてください.txt"),
                        """
                        1.7.10 / 1.12.2 の RTM ワールドのフォルダ (level.dat が入っているフォルダ) を
                        このフォルダに入れてゲームを起動すると、1.21.1 の RTMU 用に変換されて
                        saves/ に「<名前> (RTMU)」として出てきます。

                        元のワールドは変更しません (コピーしてから変換します)。
                        変換が終わった入力は done/ に移動します。
                        """);
            } catch (IOException ignored) {
            }
            return reports;
        }

        List<Path> worlds;
        try (Stream<Path> list = Files.list(input)) {
            worlds = list.filter(WorldConverter::isWorldDir).sorted().toList();
        } catch (IOException e) {
            RealTrainModUnofficial.LOGGER.warn("[convert] input dir failed", e);
            return reports;
        }

        for (Path world : worlds) {
            try {
                reports.add(convert(world, savesDir));
                //変換済みの入力は done/ に退避する (毎回変換し直さないように)
                Path done = input.resolve(DONE_DIR);
                Files.createDirectories(done);
                Files.move(world, uniquePath(done.resolve(world.getFileName().toString())));
            } catch (Exception e) {
                RealTrainModUnofficial.LOGGER.error("[convert] failed: {}", world.getFileName(), e);
            }
        }
        return reports;
    }

    private static boolean isWorldDir(Path p) {
        return Files.isDirectory(p)
                && !p.getFileName().toString().equals(DONE_DIR)
                && Files.isRegularFile(p.resolve("level.dat"));
    }

    /**
     * 旧ワールド 1 つを変換する。
     */
    public static Report convert(Path source, Path savesDir) throws IOException {
        String name = source.getFileName().toString();
        Path output = uniquePath(savesDir.resolve(name + " (RTMU)"));

        copyTree(source, output);

        //RTM のオブジェクトを抜き出す (地形は触らない)
        //ブロックの数値 ID → 登録名 の表を先に読む (MOD ブロックをバニラに読み替えるのに要る)
        Map<Integer, String> blockIds = LegacyBlockIds.read(output);

        LegacyScanner.Result scan = new LegacyScanner.Result();
        scan.blockIds = blockIds;
        scanWorld(output, scan);

        scan.unknownBlocks.forEach((n, c) ->
                RealTrainModUnofficial.LOGGER.warn("[convert] {}: 対応表に無い MOD ブロック {} ({} 個)", name, n, c));

        //★ 旧レールのブロックをチャンクから消す。
        //
        //旧 RTM のレール土台/コア/マーカーは、変換後も RTMU の<b>同名ブロック</b>として残る。
        //しかし中身のブロックエンティティは旧 MOD のもので読み込めず、空のまま作り直される。
        //その土台は「自分のコアが無い」と判断して自壊し、隣へ連鎖して、こちらが復元した
        //レールまで巻き添えで壊してしまう。ゲーム内で消そうとしてもこの連鎖には勝てないので、
        //<b>Minecraft がチャンクを読む前に</b>消しておく。
        Set<Integer> railIds = LegacyBlockIds.railBlockIds(blockIds);
        //レールブロックは Minecraft がチャンクを読む前に消す (副作用が目的。戻り値は使わない)
        if (!railIds.isEmpty()) {
            stripBlocks(output, railIds);
        }

        RestoreData data = new RestoreData();
        data.objects.addAll(scan.objects);
        data.entities.addAll(scan.entities);
        data.blocks.addAll(scan.blocks);
        if (!data.isEmpty()) {
            data.write(output);
        }

        fixLevelDat(output);

        scan.unknown.forEach((id, count) ->
                RealTrainModUnofficial.LOGGER.warn("[convert] {}: 未対応の id {} ({} 個)", name, id, count));

        return new Report(name, output, scan.objects.size(), scan.entities.size(), scan.chunks, scan.unknown);
    }

    /**
     * 指定した数値 ID のブロックをチャンクから消す (空気にする)。
     *
     * <p>1.7.10 / 1.12.2 のチャンクはブロックを数値 ID の配列で持っている:
     * {@code Sections[].Blocks} (下位 8bit) + {@code Add} (上位 4bit のニブル配列)。
     * 該当するものを 0 (空気) にして、メタデータも 0 に落とす。
     *
     * @return 消したブロック数
     */
    private static int stripBlocks(Path worldDir, Set<Integer> ids) throws IOException {
        int total = 0;
        for (Path regionDir : regionDirs(worldDir)) {
            try (Stream<Path> files = Files.list(regionDir)) {
                for (Path mca : files.filter(p -> p.getFileName().toString().endsWith(".mca")).toList()) {
                    Map<Integer, CompoundTag> chunks = LegacyRegion.readChunksIndexed(mca);
                    if (chunks.isEmpty()) {
                        continue;
                    }
                    int removed = 0;
                    for (CompoundTag chunk : chunks.values()) {
                        removed += stripChunk(chunk, ids);
                    }
                    if (removed > 0) {
                        LegacyRegion.writeChunks(mca, chunks);
                        total += removed;
                    }
                }
            }
        }
        return total;
    }

    private static int stripChunk(CompoundTag chunk, Set<Integer> ids) {
        CompoundTag level = chunk.contains("Level") ? chunk.getCompound("Level") : chunk;
        net.minecraft.nbt.ListTag sections = level.getList("Sections", net.minecraft.nbt.Tag.TAG_COMPOUND);
        int removed = 0;
        for (int s = 0; s < sections.size(); s++) {
            CompoundTag section = sections.getCompound(s);
            byte[] blocks = section.getByteArray("Blocks");
            if (blocks.length != 4096) {
                continue;
            }
            byte[] add = section.contains("Add") ? section.getByteArray("Add") : null;
            byte[] data = section.contains("Data") ? section.getByteArray("Data") : null;

            for (int i = 0; i < 4096; i++) {
                int id = blocks[i] & 0xFF;
                if (add != null && add.length == 2048) {
                    id |= nibble(add, i) << 8;
                }
                if (!ids.contains(id)) {
                    continue;
                }
                blocks[i] = 0;
                if (add != null && add.length == 2048) {
                    setNibble(add, i, 0);
                }
                if (data != null && data.length == 2048) {
                    setNibble(data, i, 0);
                }
                removed++;
            }
            if (removed > 0) {
                section.putByteArray("Blocks", blocks);
                if (add != null) {
                    section.putByteArray("Add", add);
                }
                if (data != null) {
                    section.putByteArray("Data", data);
                }
            }
        }
        //ブロックが無くなったので、旧レールのタイルエンティティも消す
        //(残しておくと Minecraft が読み込みに失敗して大量の警告を出す)
        if (removed > 0) {
            net.minecraft.nbt.ListTag tiles = level.getList("TileEntities", net.minecraft.nbt.Tag.TAG_COMPOUND);
            net.minecraft.nbt.ListTag kept = new net.minecraft.nbt.ListTag();
            for (int i = 0; i < tiles.size(); i++) {
                CompoundTag te = tiles.getCompound(i);
                String id = LegacyIds.normalize(te.getString("id"));
                if (!id.startsWith("terail") && !id.equals("temarker") && !id.equals("teturntablecore")) {
                    kept.add(te);
                }
            }
            level.put("TileEntities", kept);
        }
        return removed;
    }

    private static int nibble(byte[] arr, int index) {
        int b = arr[index >> 1] & 0xFF;
        return (index & 1) == 0 ? (b & 0x0F) : (b >> 4);
    }

    private static void setNibble(byte[] arr, int index, int value) {
        int half = index >> 1;
        int b = arr[half] & 0xFF;
        arr[half] = (byte) ((index & 1) == 0
                ? (b & 0xF0) | (value & 0x0F)
                : (b & 0x0F) | ((value & 0x0F) << 4));
    }

    private static List<Path> regionDirs(Path worldDir) throws IOException {
        List<Path> dirs = new ArrayList<>();
        Path main = worldDir.resolve("region");
        if (Files.isDirectory(main)) {
            dirs.add(main);
        }
        try (Stream<Path> list = Files.list(worldDir)) {
            for (Path dim : list.filter(Files::isDirectory).toList()) {
                if (!dim.getFileName().toString().toUpperCase(java.util.Locale.ROOT).startsWith("DIM")) {
                    continue;
                }
                Path r = dim.resolve("region");
                if (Files.isDirectory(r)) {
                    dirs.add(r);
                }
            }
        }
        return dirs;
    }

    /** オーバーワールド + ネザー + エンド (旧 DIM-1 / DIM1) を走査する。 */
    private static void scanWorld(Path worldDir, LegacyScanner.Result result) throws IOException {
        LegacyScanner.scanDimension(worldDir.resolve("region"), "minecraft:overworld", result);

        try (Stream<Path> list = Files.list(worldDir)) {
            for (Path dim : list.filter(Files::isDirectory).toList()) {
                String folder = dim.getFileName().toString();
                if (!folder.toUpperCase(java.util.Locale.ROOT).startsWith("DIM")) {
                    continue;
                }
                LegacyScanner.scanDimension(dim.resolve("region"),
                        LegacyScanner.dimensionOf(folder), result);
            }
        }
    }

    /**
     * level.dat を現行フォーマットに上げ、Forge/FML のレジストリ情報を落とす。
     * <p>
     * 旧 Forge のレジストリ ID 表が残っていると NeoForge が「不明なレジストリ」として
     * 警告を出すことがある。中身 (MOD ブロックの数値 ID) はもう使わないので消してよい。
     */
    private static void fixLevelDat(Path worldDir) {
        Path levelDat = worldDir.resolve("level.dat");
        try {
            CompoundTag root = NbtIo.readCompressed(levelDat, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            if (!root.contains("Data")) {
                return;
            }
            CompoundTag data = root.getCompound("Data");

            //旧 Forge / FML の情報は捨てる
            root.remove("FML");
            root.remove("Forge");
            root.remove("fml");
            root.remove("forge");
            data.remove("FML");
            data.remove("Forge");

            int version = data.contains("DataVersion") ? data.getInt("DataVersion") : 0;
            int current = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
            if (version < current) {
                //バニラの DataFixer でワールド設定を現行フォーマットへ
                CompoundTag fixed = net.minecraft.util.datafix.DataFixTypes.LEVEL.updateToCurrentVersion(
                        net.minecraft.util.datafix.DataFixers.getDataFixer(), data, version);
                root.put("Data", fixed);
            }
            NbtIo.writeCompressed(root, levelDat);
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("[convert] level.dat の変換に失敗 (そのまま進めます)", e);
        }
    }

    // ---- ファイル操作 ----

    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path src : walk.toList()) {
                Path dst = to.resolve(from.relativize(src).toString());
                if (Files.isDirectory(src)) {
                    Files.createDirectories(dst);
                } else {
                    Files.createDirectories(dst.getParent());
                    Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static Path uniquePath(Path path) {
        if (!Files.exists(path)) {
            return path;
        }
        for (int i = 2; i < 1000; i++) {
            Path candidate = path.resolveSibling(path.getFileName() + " (" + i + ")");
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        return path;
    }

    /** 空のディレクトリツリーを消す (失敗しても無視)。 */
    public static void deleteQuietly(Path path) {
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
