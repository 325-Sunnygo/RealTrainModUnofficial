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
    /** 表示解像度へニアレスト焼き直し済みテクスチャ (key = pack|path|WxH → location)。 */
    private static final Map<String, ResourceLocation> SCALED = new ConcurrentHashMap<>();

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
                    return missingButtonInfo();
                }
                return registerDynamicTexture(packName, texturePath, fallbackImage);
            } catch (Exception e) {
                return missingButtonInfo();
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
                return missingButtonInfo();
            }
            return registerDynamicTexture(packName, texturePath, image);
        } catch (Exception e) {
            return missingButtonInfo();
        }
    }

    private static volatile ButtonTextureInfo missingInfo;

    /**
     * テクスチャ欠落ボタン用の紫黒チェッカー(本家missingno風)。
     * null返しはcomputeIfAbsentにキャッシュされず、毎フレーム全パック走査が走ってFPSが激減するため、
     * 必ずこれを返してキャッシュに乗せる。
     */
    private static ButtonTextureInfo missingButtonInfo() {
        ButtonTextureInfo cached = missingInfo;
        if (cached != null) {
            return cached;
        }
        synchronized (PackButtonTextureCache.class) {
            if (missingInfo != null) {
                return missingInfo;
            }
            NativeImage img = new NativeImage(NativeImage.Format.RGBA, 16, 16, false);
            int magenta = 0xFFF800F8; //ABGR
            int black = 0xFF000000;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    img.setPixelRGBA(x, y, ((x / 8 + y / 8) % 2 == 0) ? magenta : black);
                }
            }
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(
                RealTrainModUnofficial.MODID, "dynamic/button/_missing");
            DynamicTexture tex = new DynamicTexture(img);
            Minecraft.getInstance().getTextureManager().register(loc, tex);
            tex.setFilter(false, false);
            missingInfo = new ButtonTextureInfo(loc, 16, 16, 0, 0, 16, 16);
            return missingInfo;
        }
    }

    /**
     * buttonTexture のソース矩形を<b>ニアレストで target サイズへ焼き直した</b>テクスチャの location。
     * これを 1:1 (target と同じ UV サイズ) で blit すれば、GUI の描画経路が線形補間を強制していても
     * スケーリングが起きない (テクセル=ピクセル) ので<b>必ず鮮明</b>になる。にじみ対策の本命。
     * target は「ボタンの GUI サイズ × guiScale」を渡すこと (画面ピクセルと 1:1 になる)。失敗時は素の location。
     */
    public static ResourceLocation getCrisp(String packName, String texturePath, int targetW, int targetH) {
        ButtonTextureInfo base = get(packName, texturePath);
        if (base == null) {
            return null;
        }
        if (targetW < 1 || targetH < 1) {
            return base.location();
        }
        String key = packName + "|" + texturePath + "|" + targetW + "x" + targetH;
        ResourceLocation cached = SCALED.get(key);
        if (cached != null) {
            return cached;
        }
        ResourceLocation result = base.location();
        try {
            NativeImage src = readImage(packName, texturePath);
            if (src != null) {
                int[] region = computeSourceRegion(src.getWidth(), src.getHeight());
                NativeImage out = nearestScale(src, region[0], region[1], targetW, targetH);
                src.close();
                ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(
                    RealTrainModUnofficial.MODID,
                    "dynamic/button_crisp/" + sanitize(packName) + "/" + sanitize(texturePath)
                        + "/" + targetW + "x" + targetH);
                DynamicTexture tex = new DynamicTexture(out);
                Minecraft.getInstance().getTextureManager().register(loc, tex);
                result = loc;
            }
        } catch (Exception ignored) {
            // 読み込み/生成不可なら素の location にフォールバック。
        }
        SCALED.put(key, result);
        return result;
    }

    /**
     * {@link #getCrisp} の<b>全画像</b>版。ボタン矩形(左上62.5%×12.5%)ではなく画像全体を
     * ニアレストで target へ焼き直す。標識/看板のように buttonTexture が 160×32 ボタン形式でなく
     * <b>実寸のフル画像</b>のものはこちらを使う (region 切り出しだと絵が見切れる)。
     * 呼び出し側で元画像のアスペクト比に合わせた target を渡すこと (歪み防止)。
     */
    public static ResourceLocation getCrispFull(String packName, String texturePath, int targetW, int targetH) {
        ButtonTextureInfo base = get(packName, texturePath);
        if (base == null) {
            return null;
        }
        if (targetW < 1 || targetH < 1) {
            return base.location();
        }
        String key = packName + "|" + texturePath + "|full|" + targetW + "x" + targetH;
        ResourceLocation cached = SCALED.get(key);
        if (cached != null) {
            return cached;
        }
        ResourceLocation result = base.location();
        try {
            NativeImage src = readImage(packName, texturePath);
            if (src != null) {
                NativeImage out = nearestScale(src, src.getWidth(), src.getHeight(), targetW, targetH);
                src.close();
                ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(
                    RealTrainModUnofficial.MODID,
                    "dynamic/button_full/" + sanitize(packName) + "/" + sanitize(texturePath)
                        + "/" + targetW + "x" + targetH);
                DynamicTexture tex = new DynamicTexture(out);
                Minecraft.getInstance().getTextureManager().register(loc, tex);
                result = loc;
            }
        } catch (Exception ignored) {
        }
        SCALED.put(key, result);
        return result;
    }

    /** ソース矩形 (0,0)-(srcW,srcH) をニアレストで dstW×dstH に焼き直した新規画像。 */
    private static NativeImage nearestScale(NativeImage src, int srcW, int srcH, int dstW, int dstH) {
        NativeImage out = new NativeImage(NativeImage.Format.RGBA, dstW, dstH, false);
        int sw = Math.max(1, srcW);
        int sh = Math.max(1, srcH);
        for (int y = 0; y < dstH; y++) {
            int sy = Math.min(sh - 1, (int) ((long) y * sh / dstH));
            for (int x = 0; x < dstW; x++) {
                int sx = Math.min(sw - 1, (int) ((long) x * sw / dstW));
                out.setPixelRGBA(x, y, src.getPixelRGBA(sx, sy));
            }
        }
        return out;
    }

    /** RTM 256-UV のボタンソース矩形 (正方キャンバスの左上 62.5%×12.5%)。 */
    private static int[] computeSourceRegion(int width, int height) {
        if (width >= 160 && height >= 128) {
            return new int[]{
                Math.min(width, Math.round(width * 160.0F / 256.0F)),
                Math.min(height, Math.round(height * 32.0F / 256.0F))
            };
        }
        return new int[]{Math.min(width, 160), Math.min(height, 32)};
    }

    /** パックから buttonTexture の NativeImage を読み込む (登録しない)。呼び出し側で close する。 */
    private static NativeImage readImage(String packName, String texturePath) throws Exception {
        Path packPath = RailPackLoader.resolvePackPath(packName);
        NativeImage image = null;
        if (packPath != null) {
            image = Files.isDirectory(packPath)
                ? loadFromDirectory(packPath, texturePath)
                : loadFromArchive(packPath, texturePath);
        }
        if (image == null) {
            image = loadBySearchingAllPacks(texturePath);
        }
        return image;
    }

    private static ButtonTextureInfo registerDynamicTexture(String packName, String texturePath, NativeImage image) {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
            RealTrainModUnofficial.MODID,
            "dynamic/button/" + sanitize(packName) + "/" + sanitize(texturePath)
        );
        int width = image.getWidth();
        int height = image.getHeight();
        // RTM 本家のボタン UV は 256px 空間で (0,0)-(160,32) を <b>固定倍率 f=1/256</b> で描く。
        // = テクスチャ左上の <b>62.5% × 12.5%</b> をサンプルする (解像度に依らずこの割合)。
        // 実際のボタン画像は正方キャンバス (256/512/652…) の左上にこの比率で描かれているので、
        // ソース矩形も解像度に比例させないと、512/652px 等で左上のごく一部だけ拾って
        // 2〜4倍に拡大 = 文字化け/巨大化する (旧 min(w,160)/min(h,32) の不具合)。
        int srcW;
        int srcH;
        if (width >= 160 && height >= 128) {
            // 256 以上の(ほぼ)正方ボタンキャンバス: 左上 160/256 × 32/256。
            srcW = Math.min(width, Math.round(width * 160.0F / 256.0F));
            srcH = Math.min(height, Math.round(height * 32.0F / 256.0F));
        } else {
            // 想定外 (小さい/横長) のファイルは従来どおり左上 160×32 (無ければ全体)。
            srcW = Math.min(width, 160);
            srcH = Math.min(height, 32);
        }
        DynamicTexture dynamicTexture = new DynamicTexture(image);
        // GUI ボタンはピクセル等倍で鮮明に見せたい。DynamicTexture は既定で線形補間が効いて
        // 拡大縮小時ににじむ (ユーザー報告「ボタンがぼやける」)。最近傍・ミップマップ無しに固定する。
        // setFilter だけだと 1.21.1 では bind されず効かない場合があるので、テクスチャを明示バインドして
        // 低レベル (glTexParameter) でも GL_NEAREST を強制する。RTM 本家 (1.7.10) も GUI は最近傍。
        // 登録 (= GPU アップロード) してから最近傍を設定する。アップロード前に setFilter しても
        // 上書きされる実装があるため、順序が重要。
        Minecraft.getInstance().getTextureManager().register(location, dynamicTexture);
        dynamicTexture.setFilter(false, false);
        forceNearestFilter(dynamicTexture);
        return new ButtonTextureInfo(location, width, height, 0, 0, srcW, srcH);
    }

    /** バインドして MIN/MAG フィルタを GL_NEAREST に固定 (にじみ防止)。 */
    private static void forceNearestFilter(DynamicTexture texture) {
        try {
            com.mojang.blaze3d.systems.RenderSystem.bindTexture(texture.getId());
            com.mojang.blaze3d.platform.GlStateManager._texParameter(
                org.lwjgl.opengl.GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER,
                org.lwjgl.opengl.GL11.GL_NEAREST);
            com.mojang.blaze3d.platform.GlStateManager._texParameter(
                org.lwjgl.opengl.GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER,
                org.lwjgl.opengl.GL11.GL_NEAREST);
        } catch (Exception ignored) {
            // 描画スレッド外など不可の場合は setFilter に委ねる。
        }
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
        //1) フルパス一致を最優先。button_*.png が normal/ と new/ のように<b>別フォルダに同名で</b>
        //   同居していると、リーフ名一致だけでは zip 内の先着を拾って取り違える (E259 と E259N で
        //   同じボタンが出る不具合)。まず要求パスそのものに一致するエントリを探す。
        ZipEntry byFullPath = zipFile.stream()
            .filter(entry -> !entry.isDirectory())
            .filter(entry -> {
                String name = normalize(entry.getName()).toLowerCase(Locale.ROOT);
                return name.equals(normalized)
                    || name.endsWith("/" + normalized)
                    || name.contains("/textures/" + normalized);
            })
            .findFirst()
            .orElse(null);
        if (byFullPath != null) {
            return byFullPath;
        }
        //2) フルパスで見つからないときだけ、最後の手段としてリーフ名一致にフォールバック。
        return zipFile.stream()
            .filter(entry -> !entry.isDirectory())
            .filter(entry -> {
                String name = normalize(entry.getName()).toLowerCase(Locale.ROOT);
                return name.equals(leaf) || name.endsWith("/" + leaf);
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
        addDirectoryChildren(gameDir.resolve("mods").resolve("modelpacks"), seen, result);
        addArchiveChildren(gameDir.resolve("mods").resolve("modelpacks"), seen, result);
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
