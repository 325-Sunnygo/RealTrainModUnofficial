package com.portofino.realtrainmodunofficial.client;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.BundledPackStore;
import com.portofino.realtrainmodunofficial.rail.RailPackLoader;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.neoforged.fml.loading.FMLPaths;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * RTM pack の buttonTexture を選択画面で再利用するための小さいキャッシュ。
 */
public final class PackButtonTextureCache {
    public record ButtonTextureInfo(ResourceLocation location, int width, int height,
                                    int sourceX, int sourceY, int sourceWidth, int sourceHeight) {}

    private static final Map<String, ButtonTextureInfo> CACHE = new ConcurrentHashMap<>();

    private PackButtonTextureCache() {
    }

    public static ButtonTextureInfo get(String packName, String texturePath) {
        if (packName == null || packName.isBlank() || texturePath == null || texturePath.isBlank()) {
            return null;
        }
        String key = packName + "|" + texturePath;
        return CACHE.computeIfAbsent(key, ignored -> load(packName, texturePath));
    }

    private static ButtonTextureInfo load(String packName, String texturePath) {
        Path packPath = RailPackLoader.resolvePackPath(packName);
        if (packPath == null) {
            try {
                NativeImage fallbackImage = loadBySearchingAllPacks(texturePath);
                if (fallbackImage == null) {
                    return null;
                }
                return registerDynamicTexture(packName, texturePath, fallbackImage);
            } catch (Exception e) {
                return null;
            }
        }
        try {
            NativeImage image = Files.isDirectory(packPath)
                ? loadFromDirectory(packPath, texturePath)
                : loadFromArchive(packPath, texturePath);
            if (image == null) {
                image = loadBySearchingAllPacks(texturePath);
            }
            if (image == null) {
                return null;
            }
            return registerDynamicTexture(packName, texturePath, image);
        } catch (Exception e) {
            return null;
        }
    }

    private static ButtonTextureInfo registerDynamicTexture(String packName, String texturePath, NativeImage image) {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
            RealTrainModUnofficial.MODID,
            "dynamic/button/" + sanitize(packName) + "/" + sanitize(texturePath)
        );
        int width = image.getWidth();
        int height = image.getHeight();
        // RTM 本家どおり、テクスチャは (0,0) から 160×32 をそのまま使う。
        // 以前は余白検出クロップ+アスペクト比維持で縮小していたため余白だらけになっていた。
        int srcW = Math.min(width, 160);
        int srcH = Math.min(height, 32);
        Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(image));
        return new ButtonTextureInfo(location, width, height, 0, 0, srcW, srcH);
    }

    private static NativeImage loadFromDirectory(Path packPath, String texturePath) throws Exception {
        Path resolved = resolveDirectoryTexture(packPath, texturePath);
        if (resolved == null) {
            return null;
        }
        try (InputStream input = Files.newInputStream(resolved)) {
            return NativeImage.read(input);
        }
    }

    private static NativeImage loadFromArchive(Path packPath, String texturePath) throws Exception {
        try (ZipFile zipFile = new ZipFile(packPath.toFile())) {
            ZipEntry entry = findEntry(zipFile, texturePath);
            if (entry == null) {
                return null;
            }
            try (InputStream input = zipFile.getInputStream(entry)) {
                return NativeImage.read(input);
            }
        }
    }

    private static Path resolveDirectoryTexture(Path root, String texturePath) throws Exception {
        String normalized = normalize(texturePath);
        Path direct = root.resolve(normalized);
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        Path assets = root.resolve("assets").resolve("minecraft").resolve(normalized);
        if (Files.isRegularFile(assets)) {
            return assets;
        }
        Path textures = root.resolve("textures").resolve(normalized);
        if (Files.isRegularFile(textures)) {
            return textures;
        }
        String leaf = normalized.substring(normalized.lastIndexOf('/') + 1);
        try (var stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().equalsIgnoreCase(leaf))
                .findFirst()
                .orElse(null);
        }
    }

    private static NativeImage loadBySearchingAllPacks(String texturePath) throws Exception {
        for (Path candidate : listAllPackCandidates()) {
            NativeImage image = Files.isDirectory(candidate)
                ? loadFromDirectory(candidate, texturePath)
                : loadFromArchive(candidate, texturePath);
            if (image != null) {
                return image;
            }
        }
        return null;
    }

    private static ZipEntry findEntry(ZipFile zipFile, String texturePath) {
        String normalized = normalize(texturePath).toLowerCase(Locale.ROOT);
        String leaf = normalized.substring(normalized.lastIndexOf('/') + 1);
        return zipFile.stream()
            .filter(entry -> !entry.isDirectory())
            .filter(entry -> {
                String name = normalize(entry.getName()).toLowerCase(Locale.ROOT);
                return name.equals(normalized)
                    || name.endsWith("/" + normalized)
                    || name.endsWith("/" + leaf)
                    || name.contains("/textures/" + normalized);
            })
            .findFirst()
            .orElse(null);
    }

    private static String normalize(String raw) {
        return raw.replace('\\', '/').replaceFirst("^/+", "");
    }

    private static String sanitize(String raw) {
        return raw.replace('\\', '/').toLowerCase(Locale.ROOT)
                  .replaceAll("[^a-z0-9/._-]", "_")
                  .replaceFirst("^[/_]+", "");
    }

    private static List<Path> listAllPackCandidates() {
        Set<Path> seen = new LinkedHashSet<>();
        List<Path> result = new ArrayList<>();
        Path gameDir = FMLPaths.GAMEDIR.get();
        addDirectoryChildren(gameDir, seen, result);
        addArchiveChildren(gameDir, seen, result);
        addDirectoryChildren(gameDir.resolve("mods"), seen, result);
        addArchiveChildren(gameDir.resolve("mods"), seen, result);
        addDirectoryChildren(gameDir.resolve("content"), seen, result);
        addArchiveChildren(gameDir.resolve("content"), seen, result);
        addDirectoryChildren(gameDir.resolve("vehicle_packs"), seen, result);
        addArchiveChildren(gameDir.resolve("vehicle_packs"), seen, result);
        addDirectoryChildren(com.portofino.realtrainmodunofficial.DefaultAssetsFolder.get(), seen, result);
        addArchiveChildren(com.portofino.realtrainmodunofficial.DefaultAssetsFolder.get(), seen, result);
        addDirectoryChildren(gameDir.resolve("config").resolve("realtrainmodunofficial"), seen, result);
        addArchiveChildren(gameDir.resolve("config").resolve("realtrainmodunofficial"), seen, result);
        for (String category : new String[]{"vehicle", "rail", "installed_object", "official"}) {
            for (Path path : BundledPackStore.listBundledPacks(category)) {
                if (seen.add(path)) {
                    result.add(path);
                }
            }
        }
        return result;
    }

    private static void addArchiveChildren(Path dir, Set<Path> seen, List<Path> result) {
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                .filter(path -> {
                    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                    return name.endsWith(".zip") || name.endsWith(".jar");
                })
                .forEach(path -> {
                    if (seen.add(path)) {
                        result.add(path);
                    }
                });
        } catch (Exception ignored) {
        }
    }

    private static void addDirectoryChildren(Path dir, Set<Path> seen, List<Path> result) {
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.list(dir)) {
            stream.filter(Files::isDirectory)
                .forEach(path -> {
                    if (seen.add(path)) {
                        result.add(path);
                    }
                });
        } catch (Exception ignored) {
        }
    }

    private static int[] detectContentBounds(NativeImage image, String texturePath) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] legacyAtlasBounds = detectLegacyRtmButtonAtlasBounds(image);
        if (legacyAtlasBounds != null) {
            return legacyAtlasBounds;
        }
        if (width >= 160 && height >= 32) {
            int widthScale = width / 160;
            int heightScale = height / 32;
            if (width % 160 == 0 && height % 32 == 0 && widthScale == heightScale) {
                return new int[]{0, 0, width, height};
            }
        }
        int[] edgeTrimBounds = detectUniformEdgeBounds(image);
        if (edgeTrimBounds != null) {
            return edgeTrimBounds;
        }
        int[] detectedBounds = detectDominantBackgroundBounds(image);
        if (detectedBounds != null) {
            return detectedBounds;
        }
        if (width >= 160 && height >= 32) {
            int widthScale = width / 160;
            int heightScale = height / 32;
            if (width % 160 == 0 && height % 32 == 0 && widthScale == heightScale) {
                return new int[]{0, 0, width, height};
            }
            return new int[]{0, 0, 160, 32};
        }
        return new int[]{0, 0, width, height};
    }

    private static int[] detectLegacyRtmButtonAtlasBounds(NativeImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (width != height || width < 256) {
            return null;
        }
        int background = image.getPixelRGBA(width - 1, height - 1);
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = image.getPixelRGBA(x, y);
                if (((pixel >>> 24) & 0xFF) <= 8 || colorDistanceSq(pixel, background) <= 8 * 8 * 4) {
                    continue;
                }
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        if (maxX < 0 || maxY < 0 || maxX > width / 2 + 16 || maxY > height / 4) {
            return null;
        }
        int sourceWidth = Math.min(width, Math.max(160, roundUp(maxX + 1, 16)));
        int sourceHeight = Math.min(height, Math.max(32, roundUp(maxY + 1, 16)));
        return new int[]{0, 0, sourceWidth, sourceHeight};
    }

    private static int roundUp(int value, int step) {
        return ((Math.max(1, value) + step - 1) / step) * step;
    }

    private static int[] detectDominantBackgroundBounds(NativeImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= 0 || height <= 0) {
            return null;
        }

        java.util.Map<Integer, Integer> counts = new java.util.HashMap<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                counts.merge(image.getPixelRGBA(x, y), 1, Integer::sum);
            }
        }

        int dominantColor = 0;
        int dominantCount = -1;
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > dominantCount) {
                dominantColor = entry.getKey();
                dominantCount = entry.getValue();
            }
        }
        if (dominantCount <= (width * height) / 3) {
            return null;
        }

        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = image.getPixelRGBA(x, y);
                if (((pixel >>> 24) & 0xFF) <= 8 || colorDistanceSq(pixel, dominantColor) <= 8 * 8 * 4) {
                    continue;
                }
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        if (maxX < minX || maxY < minY) {
            return null;
        }
        // RTM buttonTexture is usually used with a very tight crop plus a 1px margin.
        minX = Math.max(0, minX - 1);
        minY = Math.max(0, minY - 1);
        maxX = Math.min(width - 1, maxX + 1);
        maxY = Math.min(height - 1, maxY + 1);
        return new int[]{minX, minY, maxX - minX + 1, maxY - minY + 1};
    }

    private static int[] detectUniformEdgeBounds(NativeImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= 2 || height <= 2) {
            return null;
        }
        int frameColor = image.getPixelRGBA(0, 0);
        int minX = 0;
        int minY = 0;
        int maxX = width - 1;
        int maxY = height - 1;

        while (minY < maxY && rowMatches(image, minY, frameColor)) {
            minY++;
        }
        while (maxY > minY && rowMatches(image, maxY, frameColor)) {
            maxY--;
        }
        while (minX < maxX && columnMatches(image, minX, minY, maxY, frameColor)) {
            minX++;
        }
        while (maxX > minX && columnMatches(image, maxX, minY, maxY, frameColor)) {
            maxX--;
        }

        if (minX == 0 && minY == 0 && maxX == width - 1 && maxY == height - 1) {
            return null;
        }

        minX = Math.max(0, minX - 1);
        minY = Math.max(0, minY - 1);
        maxX = Math.min(width - 1, maxX + 1);
        maxY = Math.min(height - 1, maxY + 1);
        return new int[]{minX, minY, maxX - minX + 1, maxY - minY + 1};
    }

    private static boolean rowMatches(NativeImage image, int y, int referenceColor) {
        for (int x = 0; x < image.getWidth(); x++) {
            int pixel = image.getPixelRGBA(x, y);
            if (((pixel >>> 24) & 0xFF) > 8 && colorDistanceSq(pixel, referenceColor) > 4 * 4 * 4) {
                return false;
            }
        }
        return true;
    }

    private static boolean columnMatches(NativeImage image, int x, int minY, int maxY, int referenceColor) {
        for (int y = minY; y <= maxY; y++) {
            int pixel = image.getPixelRGBA(x, y);
            if (((pixel >>> 24) & 0xFF) > 8 && colorDistanceSq(pixel, referenceColor) > 4 * 4 * 4) {
                return false;
            }
        }
        return true;
    }

    private static int colorDistanceSq(int a, int b) {
        int ar = a & 0xFF;
        int ag = (a >>> 8) & 0xFF;
        int ab = (a >>> 16) & 0xFF;
        int aa = (a >>> 24) & 0xFF;
        int br = b & 0xFF;
        int bg = (b >>> 8) & 0xFF;
        int bb = (b >>> 16) & 0xFF;
        int ba = (b >>> 24) & 0xFF;
        int dr = ar - br;
        int dg = ag - bg;
        int db = ab - bb;
        int da = aa - ba;
        return dr * dr + dg * dg + db * db + da * da;
    }
}
