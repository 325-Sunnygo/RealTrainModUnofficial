package com.portofino.realtrainmodunofficial.building;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * mods フォルダに入れた 1.7.10 の建材 mod からブロックテクスチャ
 * (assets/<ns>/textures/blocks/*.png) をかき集め、フルキューブブロックとして登録する。
 * 1.7.10 の Block クラス (Java バイトコード) は 1.21 では実行不可能なので、形状・機能・面別
 * テクスチャは再現できない。
 */
public final class ExternalBuildingBlocks {
    /** 生成ブロック/リソースの名前空間 (RTMU 自身)。 */
    public static final String NAMESPACE = RealTrainModUnofficial.MODID;
    /** 登録数の安全上限 (巨大 mod で起動が固まらないように)。 */
    private static final int MAX_BLOCKS = 4096;

    /** 1 ブロック分の発見情報。 */
    public record Entry(String blockId, Path sourceJar, String textureEntry,
                        String mcmetaEntry, String displayName, boolean transparent) {
    }

    /** 発見した建材ブロック (スキャン順)。パック生成側とタブが参照する。 */
    public static final List<Entry> ENTRIES = new ArrayList<>();
    /** クリエイティブタブに並べる BlockItem (ENTRIES と同順)。 */
    public static final List<DeferredItem<BlockItem>> TAB_ITEMS = new ArrayList<>();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NAMESPACE);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NAMESPACE);

    private static boolean initialized;

    private ExternalBuildingBlocks() {
    }

    /**
     * @Mod コンストラクタから呼ぶ。スキャン → ブロック/アイテム登録 → バス接続。
     * ここで登録したものは、コンストラクタ後に発火する RegisterEvent (凍結前) で確定する。
     */
    public static void init(IEventBus modBus) {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            scan();
        } catch (Throwable t) {
            RealTrainModUnofficial.LOGGER.warn("External building block scan failed", t);
        }
        for (Entry e : ENTRIES) {
            DeferredBlock<Block> block = BLOCKS.register(e.blockId(), () -> {
                BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
                    .strength(1.5F).sound(SoundType.STONE);
                // アルファ持ちテクスチャ (ガラス等) は隣面カリングの見え方バグを避けるため非遮蔽に。
                if (e.transparent()) {
                    props = props.noOcclusion();
                }
                return new Block(props);
            });
            DeferredItem<BlockItem> item = ITEMS.register(e.blockId(),
                () -> new BlockItem(block.get(), new Item.Properties()));
            TAB_ITEMS.add(item);
        }
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }

    private static void scan() {
        Path modsDir = FMLPaths.GAMEDIR.get().resolve("mods");
        if (!Files.isDirectory(modsDir)) {
            return;
        }
        List<Path> jars = new ArrayList<>();
        try (var stream = Files.list(modsDir)) {
            stream.forEach(p -> {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                if (Files.isRegularFile(p) && (n.endsWith(".jar") || n.endsWith(".zip"))
                        && !n.contains("realtrainmodunofficial")) {
                    jars.add(p);
                }
            });
        } catch (Exception e) {
            return;
        }
        // 決定的な順序 (レジストリ/リソースパックで ID が安定するように)。
        jars.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)));
        Set<String> seen = new HashSet<>();
        for (Path jar : jars) {
            if (ENTRIES.size() >= MAX_BLOCKS) {
                RealTrainModUnofficial.LOGGER.warn(
                    "External building block cap ({}) reached; remaining textures skipped", MAX_BLOCKS);
                break;
            }
            scanJar(jar, seen);
        }
    }

    private static void scanJar(Path jar, Set<String> seen) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                if (ENTRIES.size() >= MAX_BLOCKS) {
                    return;
                }
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName().replace('\\', '/');
                String lower = name.toLowerCase(Locale.ROOT);
                if (!lower.endsWith(".png")) {
                    continue;
                }
                // assets/<ns>/textures/blocks/<...>.png  (1.7.10 は複数形 blocks)
                String[] parts = name.split("/");
                if (parts.length < 5
                        || !parts[0].equalsIgnoreCase("assets")
                        || !parts[2].equalsIgnoreCase("textures")
                        || !parts[3].equalsIgnoreCase("blocks")) {
                    continue;
                }
                String ns = sanitize(parts[1]);
                String rel = String.join("_", Arrays.copyOfRange(parts, 4, parts.length));
                String base = rel.substring(0, rel.length() - ".png".length());
                String blockId = sanitize(ns + "_" + base);
                if (blockId.isBlank() || !seen.add(blockId)) {
                    continue;
                }
                String mcmeta = null;
                ZipEntry mm = zip.getEntry(name + ".mcmeta");
                if (mm != null) {
                    mcmeta = mm.getName();
                }
                // PNG ヘッダ (IHDR) を読んで 正方形か / アルファ有無 を判定 (フルデコードしない)。
                int[] header = readPngHeader(zip, entry);
                if (header == null) {
                    continue;
                }
                int colorType = header[0];
                int width = header[1];
                int height = header[2];
                boolean square = width == height && width > 0;
                // 非正方形テクスチャは .mcmeta (アニメ宣言) が無いとアトラス貼り付けで壊れるのでスキップ。
                if (!square && mcmeta == null) {
                    continue;
                }
                boolean transparent = colorType == 4 || colorType == 6;
                String display = niceName(base) + " (" + parts[1].toLowerCase(Locale.ROOT) + ")";
                ENTRIES.add(new Entry(blockId, jar, name, mcmeta, display, transparent));
            }
        } catch (Exception e) {
        }
    }

    /**
     * PNG の IHDR チャンクから {colorType, width, height} を返す。フルデコードしない。
     * PNG レイアウト: signature(8) len(4) "IHDR"(4) width(4) height(4) bitDepth(1) colorType(1)。
     */
    private static int[] readPngHeader(ZipFile zip, ZipEntry entry) {
        try (InputStream in = zip.getInputStream(entry)) {
            byte[] h = in.readNBytes(26);
            if (h.length < 26) {
                return null;
            }
            int width = ((h[16] & 0xFF) << 24) | ((h[17] & 0xFF) << 16) | ((h[18] & 0xFF) << 8) | (h[19] & 0xFF);
            int height = ((h[20] & 0xFF) << 24) | ((h[21] & 0xFF) << 16) | ((h[22] & 0xFF) << 8) | (h[23] & 0xFF);
            int colorType = h[25] & 0xFF;
            return new int[]{colorType, width, height};
        } catch (Exception e) {
            return null;
        }
    }

    /** ResourceLocation パスに使える [a-z0-9_] へ正規化する。 */
    static String sanitize(String s) {
        if (s == null) {
            return "";
        }
        String lower = s.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.toString().replaceAll("_+", "_").replaceAll("^_|_$", "");
    }

    /** テクスチャ名から表示名 (単語頭大文字) を作る。 */
    static String niceName(String base) {
        String s = base.replace('/', ' ').replace('_', ' ').trim();
        if (s.isEmpty()) {
            return "Block";
        }
        StringBuilder sb = new StringBuilder();
        for (String word : s.split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }
}
