package com.portofino.realtrainmodunofficial;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves pack archives bundled inside the mod jar and materializes them into a private cache
 * directory when file-based model loaders need a real path.
 */
public final class BundledPackStore {
    private static final String ROOT = "bundled_packs";

    private BundledPackStore() {
    }

    /**
     * このパスが <b>この MOD 自身の jar</b> か。
     * <p>
     * 各パックローダは mods/ や gameDir 直下の .zip/.jar を「モデルパック」として総なめする。
     * この MOD の jar も mods/ にあるので、除外しないと <b>jar に同梱した本家定義が
     * もう一度パックとして読み込まれ、全定義が二重登録される</b> (選択欄に同じモデルが
     * 2 つ並ぶ)。同梱モデルを持たなかった頃は実害が無かったが、本家の 209 定義を
     * 全て同梱した今は必ず弾く必要がある。
     */
    public static boolean isOwnModJar(Path path) {
        if (path == null) {
            return false;
        }
        String fileName = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        //開発環境や配布名の揺れに備えて、ファイル名でも実体でも判定する。
        if (fileName.startsWith(RealTrainModUnofficial.MODID.toLowerCase(java.util.Locale.ROOT) + "-")
                && fileName.endsWith(".jar")) {
            return true;
        }
        try {
            var modFile = ModList.get().getModFileById(RealTrainModUnofficial.MODID);
            if (modFile == null) {
                return false;
            }
            Path ownArchive = modFile.getFile().getFilePath();
            return ownArchive != null && Files.exists(path) && Files.isSameFile(path, ownArchive);
        } catch (Exception e) {
            return false;
        }
    }

    public static List<Path> listBundledPacks(String category) {
        List<Path> result = new ArrayList<>();
        addBundledPacks(category, result);
        if (!"official".equals(category)) {
            addBundledPacks("official", result);
        }
        return result;
    }

    private static void addBundledPacks(String category, List<Path> result) {
        try {
            Path dir = ModList.get().getModFileById(RealTrainModUnofficial.MODID).getFile()
                .findResource("assets", RealTrainModUnofficial.MODID, ROOT, category);
            if (dir == null || !Files.isDirectory(dir)) {
                return;
            }
            try (var stream = Files.list(dir)) {
                stream.filter(Files::isRegularFile)
                    .filter(BundledPackStore::isArchive)
                    .forEach(result::add);
            }
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("Could not list bundled {} packs", category, e);
        }
    }

    /**
     * 同梱パックに付属する zip 以外のファイル (パック作者の Readme 等)。
     * 展開先フォルダ (rtm_default_assets) に zip と並べて置くために使う。
     */
    public static List<Path> listBundledExtras(String category) {
        List<Path> result = new ArrayList<>();
        try {
            Path dir = ModList.get().getModFileById(RealTrainModUnofficial.MODID).getFile()
                .findResource("assets", RealTrainModUnofficial.MODID, ROOT, category);
            if (dir == null || !Files.isDirectory(dir)) {
                return result;
            }
            try (var stream = Files.list(dir)) {
                stream.filter(Files::isRegularFile)
                    .filter(path -> !isArchive(path))
                    .forEach(result::add);
            }
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("Could not list bundled {} extras", category, e);
        }
        return result;
    }

    public static Path resolveBundledPack(String packName) {
        if (packName == null || packName.isBlank()) {
            return null;
        }
        for (String category : new String[]{"rail", "vehicle", "installed_object", "official"}) {
            for (Path path : listBundledPacks(category)) {
                if (path.getFileName().toString().equalsIgnoreCase(packName)) {
                    return path;
                }
            }
        }
        return null;
    }

    public static Path materializeBundledPack(String packName) {
        Path source = resolveBundledPack(packName);
        if (source == null) {
            return null;
        }
        try {
            Path cacheDir = FMLPaths.GAMEDIR.get().resolve("config").resolve("realtrainmodunofficial").resolve("bundled_pack_cache");
            Files.createDirectories(cacheDir);
            Path target = cacheDir.resolve(source.getFileName().toString());
            long sourceSize = -1;
            try { sourceSize = Files.size(source); } catch (Exception ignored) {}
            boolean needsCopy = !Files.exists(target);
            if (!needsCopy && sourceSize >= 0) {
                try { needsCopy = Files.size(target) != sourceSize; } catch (Exception ignored) { needsCopy = true; }
            }
            if (needsCopy) {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("Could not materialize bundled pack {}", packName, e);
            return null;
        }
    }

    public static boolean isBundledPackName(String packName) {
        if (packName == null || packName.isBlank()) return false;
        return resolveBundledPack(packName) != null;
    }

    public static Path getModJarPath() {
        try {
            var modFileEntry = ModList.get().getModFileById(RealTrainModUnofficial.MODID);
            if (modFileEntry == null) return null;
            Path p = modFileEntry.getFile().getFilePath();
            if (p != null && Files.exists(p)) return p.toAbsolutePath().normalize();
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("Could not get mod JAR path", e);
        }
        return null;
    }

    public static InputStream openBundledPack(String packName) throws IOException {
        Path source = resolveBundledPack(packName);
        return source == null ? null : Files.newInputStream(source);
    }

    private static boolean isArchive(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".zip") || name.endsWith(".jar");
    }
}
