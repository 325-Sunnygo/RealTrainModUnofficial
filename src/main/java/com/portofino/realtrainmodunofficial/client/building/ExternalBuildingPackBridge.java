package com.portofino.realtrainmodunofficial.client.building;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.building.ExternalBuildingBlocks;
import com.portofino.realtrainmodunofficial.building.ExternalBuildingBlocks.Entry;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * {@link ExternalBuildingBlocks} が発見した 1.7.10 建材ブロックぶんの
 * blockstate / block model / item model / texture / lang を、ランタイム生成の
 * クライアントリソースパックとして注入する。
 *
 * <p>仕組みは {@code ExternalSoundPackBridge} と同一: {@code config/realtrainmodunofficial/
 * generated_building_pack} 以下にファイルを書き出し、{@link AddPackFindersEvent} で
 * {@link PathPackResources} として最優先で追加する。{@code AddPackFindersEvent} は
 * ブロックアトラス構築前に発火するため、コピーした PNG はアトラスに正しく焼かれる。
 */
public final class ExternalBuildingPackBridge {
    private static final String PACK_ID = "realtrainmodunofficial:external_building_bridge";
    private static final Component PACK_TITLE = Component.literal("RTM External Building Blocks");
    private static final Path PACK_ROOT = FMLPaths.GAMEDIR.get()
        .resolve("config")
        .resolve("realtrainmodunofficial")
        .resolve("generated_building_pack");

    private ExternalBuildingPackBridge() {
    }

    public static void register(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        Path packRoot;
        try {
            packRoot = rebuildGeneratedPack();
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("Could not build external building block pack", e);
            return;
        }
        if (packRoot == null) {
            return;
        }
        Pack pack = Pack.readMetaAndCreate(
            new PackLocationInfo(PACK_ID, PACK_TITLE, PackSource.BUILT_IN, Optional.empty()),
            new PathPackResources.PathResourcesSupplier(packRoot),
            PackType.CLIENT_RESOURCES,
            new PackSelectionConfig(true, Pack.Position.TOP, false)
        );
        if (pack == null) {
            RealTrainModUnofficial.LOGGER.warn("Generated external building pack could not be registered");
            return;
        }
        event.addRepositorySource(consumer -> consumer.accept(pack));
    }

    private static Path rebuildGeneratedPack() throws IOException {
        deleteDirectoryIfExists(PACK_ROOT);
        List<Entry> entries = ExternalBuildingBlocks.ENTRIES;
        if (entries.isEmpty()) {
            return null;
        }
        Files.createDirectories(PACK_ROOT);
        String ns = ExternalBuildingBlocks.NAMESPACE;
        //同じ jar は 1 回だけ開く。
        Map<Path, List<Entry>> byJar = new LinkedHashMap<>();
        for (Entry e : entries) {
            byJar.computeIfAbsent(e.sourceJar(), k -> new ArrayList<>()).add(e);
        }
        TreeMap<String, String> lang = new TreeMap<>();
        int written = 0;
        for (Map.Entry<Path, List<Entry>> jarEntries : byJar.entrySet()) {
            try (ZipFile zip = new ZipFile(jarEntries.getKey().toFile())) {
                for (Entry e : jarEntries.getValue()) {
                    try {
                        if (writeBlockResources(ns, e, zip)) {
                            lang.put("block." + ns + "." + e.blockId(), e.displayName());
                            written++;
                        }
                    } catch (Exception ex) {
                    }
                }
            } catch (Exception ex) {
            }
        }
        if (written == 0) {
            deleteDirectoryIfExists(PACK_ROOT);
            return null;
        }
        writeLang(ns, lang);
        writePackMeta();
        return PACK_ROOT;
    }

    private static boolean writeBlockResources(String ns, Entry e, ZipFile zip) throws IOException {
        ZipEntry texEntry = zip.getEntry(e.textureEntry());
        if (texEntry == null) {
            return false;
        }
        String id = e.blockId();
        Path assets = PACK_ROOT.resolve("assets").resolve(ns);

        //テクスチャ (1.7.10 の blocks/ を 1.21 の block/ へ移す)
        Path tex = assets.resolve("textures").resolve("block").resolve(id + ".png");
        Files.createDirectories(tex.getParent());
        try (InputStream in = zip.getInputStream(texEntry)) {
            Files.write(tex, in.readAllBytes(),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        }
        //アニメーション .mcmeta があればそのままコピー (縦フレームのアニメ建材が動く)。
        if (e.mcmetaEntry() != null) {
            ZipEntry mm = zip.getEntry(e.mcmetaEntry());
            if (mm != null) {
                try (InputStream in = zip.getInputStream(mm)) {
                    Files.write(tex.resolveSibling(id + ".png.mcmeta"), in.readAllBytes(),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                }
            }
        }

        //blockstate
        writeString(assets.resolve("blockstates").resolve(id + ".json"),
            "{\"variants\":{\"\":{\"model\":\"" + ns + ":block/" + id + "\"}}}");
        //block model (cube_all)。アルファ持ちは cutout、不透明は solid で描く。
        String renderType = e.transparent() ? "minecraft:cutout" : "minecraft:solid";
        writeString(assets.resolve("models").resolve("block").resolve(id + ".json"),
            "{\"parent\":\"minecraft:block/cube_all\",\"render_type\":\"" + renderType
                + "\",\"textures\":{\"all\":\"" + ns + ":block/" + id + "\"}}");
        //item model
        writeString(assets.resolve("models").resolve("item").resolve(id + ".json"),
            "{\"parent\":\"" + ns + ":block/" + id + "\"}");
        return true;
    }

    private static void writeLang(String ns, Map<String, String> lang) throws IOException {
        StringBuilder sb = new StringBuilder("{\n");
        int i = 0;
        for (Map.Entry<String, String> e : lang.entrySet()) {
            if (i++ > 0) {
                sb.append(",\n");
            }
            sb.append("  ").append(jsonString(e.getKey())).append(": ").append(jsonString(e.getValue()));
        }
        sb.append("\n}\n");
        Path langFile = PACK_ROOT.resolve("assets").resolve(ns).resolve("lang").resolve("en_us.json");
        Files.createDirectories(langFile.getParent());
        writeString(langFile, sb.toString());
    }

    private static void writePackMeta() throws IOException {
        int packFormat = SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES);
        String packMeta = """
            {
              "pack": {
                "pack_format": %d,
                "description": "RTM external 1.7.10 building blocks"
              }
            }
            """.formatted(packFormat);
        writeString(PACK_ROOT.resolve("pack.mcmeta"), packMeta);
    }

    private static void writeString(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private static String jsonString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    private static void deleteDirectoryIfExists(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
