package jp.ngt.ngtlib.io;

import net.neoforged.fml.loading.FMLPaths;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 本家 jp.ngt.ngtlib.io.NGTFileLoader のスクリプト互換ファサード。
 * パックスクリプトは getInputStream(ResourceLocation) でパック内アセット
 * (scripts/xxx.gif 等) を読む。全パック zip/フォルダを走査して解決する。
 */
public final class NGTFileLoader {
    /** 本家 NO_ZIP: 「zip の中ではなく素のファイル」を表す ScanResult のキー。 */
    public static final String NO_ZIP = "no_zip";

    /** 正規化パス (assets/minecraft/ 以降, 小文字) → コンテナ */
    private static Map<String, AssetRef> index;
    private static final Map<String, byte[]> CONTENT_CACHE = new ConcurrentHashMap<>();
    /** 索引に無いと確定したキー。全走査の再実行を防ぐ。 */
    private static final java.util.Set<String> MISSING_CACHE = ConcurrentHashMap.newKeySet();
    private static final Map<String, net.minecraft.resources.ResourceLocation> TEXTURE_CACHE = new ConcurrentHashMap<>();

    private record AssetRef(Path container, String entryName) {
    }

    private NGTFileLoader() {
    }

    /** スクリプト用: ResourceLocation (mccompat/実物) からパックアセットのストリームを開く。 */
    public static InputStream getInputStream(Object resource) {
        String path = pathOf(resource);
        if (path == null) {
            return null;
        }
        byte[] bytes = findAsset(path);
        return bytes != null ? new ByteArrayInputStream(bytes) : null;
    }

    public static byte[] findAsset(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String key = normalize(path);
        byte[] cached = CONTENT_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        if (MISSING_CACHE.contains(key)) {
            return null;
        }
        Map<String, AssetRef> idx = getIndex();
        AssetRef ref = idx.get(key);
        if (ref == null) {
            // サフィックス一致 (パス表記ゆれ対策)。
            // (見つからない要求を毎回走査すると、スクリプトが欠落アセットを繰り返し要求したとき
            // 索引サイズ×要求回数の走査になり描画が止まる)。
            // ★ファイル名だけの一致は取らない (本家は完全パスでしか引かない)。
            String suffix = "/" + key;
            for (Map.Entry<String, AssetRef> e : idx.entrySet()) {
                if (e.getKey().endsWith(suffix)) {
                    ref = e.getValue();
                    break;
                }
            }
        }
        if (ref == null) {
            // mod 自身の同梱アセット (assets/minecraft/scripts/... 等)。
            // 索引はワールドの mods/ や config/ の外部パックしか見ていないため、
            // 同梱のレール描画スクリプトなどはここまで来ないと見つからない
            // (開発環境では jar が mods/ に無いので索引にすら載らない)。
            byte[] bundled = findBundledAsset(key);
            if (bundled != null) {
                if (bundled.length < 4 * 1024 * 1024) {
                    CONTENT_CACHE.put(key, bundled);
                }
                return bundled;
            }
            // ★TypeScript のパックへの配慮。定義 JSON が "scripts/Foo.js" のままでも、
            // 同梱されているのが Foo.ts なら拾う。逆は起きない (.ts を書いた人は .ts を指す)。
            String ts = com.portofino.realtrainmodunofficial.script.TypeScriptTranspiler
                .toTypeScriptPath(key);
            if (ts != null) {
                byte[] alt = findAsset(ts);
                if (alt != null) {
                    return alt;
                }
            }
            MISSING_CACHE.add(key);
            return null;
        }
        try {
            byte[] bytes;
            if (Files.isDirectory(ref.container)) {
                bytes = Files.readAllBytes(ref.container.resolve(ref.entryName));
            } else {
                try (ZipFile zip = openZip(ref.container)) {
                    ZipEntry entry = zip.getEntry(ref.entryName);
                    if (entry == null) {
                        MISSING_CACHE.add(key);
                        return null;
                    }
                    bytes = zip.getInputStream(entry).readAllBytes();
                }
            }
            if (bytes.length < 4 * 1024 * 1024) {
                CONTENT_CACHE.put(key, bytes);
            }
            return bytes;
        } catch (IOException e) {
            NGTLog.debug("[NGTFileLoader] failed to read " + path + ": " + e);
            return null;
        }
    }

    /** mod jar (= クラスパス) に同梱しているアセットを探す。 */
    private static byte[] findBundledAsset(String key) {
        String[] candidates = {
            "/assets/minecraft/" + key,
            "/assets/realtrainmodunofficial/" + key,
            "/" + key,
        };
        for (String candidate : candidates) {
            try (java.io.InputStream in = NGTFileLoader.class.getResourceAsStream(candidate)) {
                if (in != null) {
                    return in.readAllBytes();
                }
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    /**
     * パックアセットをテキスト行として読む安定 API (単一引数・オーバーロード無し)。
     * スクリプトの自前 include (eval(append(NGTText.readText(getResource(path)))) ) は
     * 最終的にここへ来る。見つからなければ空リスト。
     */
    public static List<String> readAssetLines(String path) {
        List<String> lines = new ArrayList<>();
        byte[] bytes = findAsset(path);
        if (bytes == null) {
            return lines;
        }
        String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        for (String line : text.split("\n", -1)) {
            lines.add(line);
        }
        return lines;
    }

    /** パック内画像を動的テクスチャとして登録し RL を返す (NGTUtilClient.bindTexture 用)。 */
    public static net.minecraft.resources.ResourceLocation resolvePackTexture(String path) {
        if (path == null) {
            return null;
        }
        String key = normalize(path);
        net.minecraft.resources.ResourceLocation cached = TEXTURE_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        byte[] bytes = findAsset(key);
        if (bytes == null) {
            return null;
        }
        try {
            var img = com.mojang.blaze3d.platform.NativeImage.read(new ByteArrayInputStream(bytes));
            var rl = net.minecraft.client.Minecraft.getInstance().getTextureManager()
                    .register("rtmu_pack_tex", new net.minecraft.client.renderer.texture.DynamicTexture(img));
            TEXTURE_CACHE.put(key, rl);
            // 発光テクスチャ (***_light*.png): 黒地=非発光として加算合成で描くための印
            if (key.toLowerCase(java.util.Locale.ROOT).contains("_light")) {
                LIGHT_OVERLAY_TEXTURES.add(rl);
            }
            return rl;
        } catch (IOException e) {
            return null;
        }
    }

    private static final java.util.Set<net.minecraft.resources.ResourceLocation> LIGHT_OVERLAY_TEXTURES =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * resolvePackTexture で登録した発光系テクスチャ (パスに _light を含む) か。
     * 描画側はこれを加算合成 (黒=寄与なし) + フルブライトで描く。
     */
    public static boolean isLightOverlayTexture(net.minecraft.resources.ResourceLocation rl) {
        return rl != null && LIGHT_OVERLAY_TEXTURES.contains(rl);
    }

    private static String pathOf(Object resource) {
        if (resource instanceof jp.ngt.mccompat.ResourceLocation compat) {
            return compat.func_110623_a();
        }
        if (resource instanceof net.minecraft.resources.ResourceLocation rl) {
            return rl.getPath();
        }
        return resource != null ? resource.toString() : null;
    }

    private static String normalize(String path) {
        String p = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        if (p.startsWith("assets/minecraft/")) {
            p = p.substring("assets/minecraft/".length());
        }
        return p;
    }

    /**
     * zip を開く。UTF-8 で開けないエントリ名 (Windows 製の Shift_JIS パック) は SJIS で開き直す。
     * これをしないと SJIS パックが 1 つあるだけで "invalid CEN header" を出して索引から丸ごと漏れる。
     */
    private static ZipFile openZip(Path container) throws IOException {
        try {
            return new ZipFile(container.toFile());
        } catch (IOException first) {
            try {
                return new ZipFile(container.toFile(), java.nio.charset.Charset.forName("Shift_JIS"));
            } catch (Exception ignored) {
                throw first;
            }
        }
    }

    private static synchronized Map<String, AssetRef> getIndex() {
        if (index == null) {
            Map<String, AssetRef> map = new ConcurrentHashMap<>();
            for (Path container : collectContainers()) {
                try {
                    if (Files.isDirectory(container)) {
                        try (var stream = Files.walk(container)) {
                            for (Path file : (Iterable<Path>) stream::iterator) {
                                if (Files.isRegularFile(file)) {
                                    String rel = container.relativize(file).toString();
                                    map.putIfAbsent(normalize(rel), new AssetRef(container, rel));
                                }
                            }
                        }
                    } else {
                        try (ZipFile zip = openZip(container)) {
                            var entries = zip.entries();
                            while (entries.hasMoreElements()) {
                                ZipEntry e = entries.nextElement();
                                if (!e.isDirectory()) {
                                    map.putIfAbsent(normalize(e.getName()), new AssetRef(container, e.getName()));
                                }
                            }
                        }
                    }
                } catch (IOException ignored) {
                }
            }
            index = map;
            NGTLog.debug("[NGTFileLoader] indexed " + map.size() + " pack assets");
        }
        return index;
    }

    /**
     * 本家 getModsDir: パックを探すディレクトリ一覧。
     * RTMU は mods/ に加えて config/realtrainmodunofficial/ 以下も見る。
     */
    public static List<java.io.File> getModsDir() {
        List<java.io.File> out = new ArrayList<>();
        Path gameDir = FMLPaths.GAMEDIR.get();
        out.add(gameDir.resolve("mods").toFile());
        Path cfg = gameDir.resolve("config").resolve("realtrainmodunofficial");
        for (String sub : new String[]{"vehicle_packs", "rail_packs", "packs", "nested_pack_cache"}) {
            out.add(cfg.resolve(sub).toFile());
        }
        return out;
    }

    /** 本家 getArchiveSuffix: 絶対パスに含まれるアーカイブ拡張子 (".zip"/".jar")。無ければ空。 */
    public static String getArchiveSuffix(String absPath) {
        if (absPath == null) {
            return "";
        }
        if (absPath.contains(".zip")) {
            return ".zip";
        }
        if (absPath.contains(".jar")) {
            return ".jar";
        }
        return "";
    }

    /** 本家 getArchivePath: "…/pack.zip/scripts/a.js" から "…/pack.zip" を切り出す。 */
    public static String getArchivePath(String absPath, String suffix) {
        int index = absPath.indexOf(suffix);
        return index < 0 ? absPath : absPath.substring(0, index + suffix.length());
    }

    /** 本家 getArchive: zip/jar を開く。encoding が空なら UTF-8。 */
    public static ZipFile getArchive(java.io.File file, String encoding) throws IOException {
        if (file == null || !file.isFile()) {
            return null;
        }
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".zip") && !name.endsWith(".jar")) {
            return null;
        }
        java.nio.charset.Charset cs = encoding == null || encoding.isEmpty()
                ? java.nio.charset.StandardCharsets.UTF_8
                : java.nio.charset.Charset.forName(encoding);
        return new ZipFile(file, cs);
    }

    /** 本家 getStreamFromArchive: アーカイブ内の同名エントリを開く。 */
    public static InputStream getStreamFromArchive(java.io.File file, String suffix) throws IOException {
        String zipPath = getArchivePath(file.getAbsolutePath(), suffix);
        ZipFile zip = getArchive(new java.io.File(zipPath), "");
        if (zip == null) {
            throw new java.io.FileNotFoundException("On get stream : " + file.getName());
        }
        var entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry ze = entries.nextElement();
            if (ze.isDirectory()) {
                continue;
            }
            String entryName = ze.getName();
            int slash = entryName.lastIndexOf('/');
            String base = slash < 0 ? entryName : entryName.substring(slash + 1);
            if (base.equals(file.getName())) {
                // ZipFile は close するとストリームも閉じるので、内容を読み切って返す
                byte[] bytes = zip.getInputStream(ze).readAllBytes();
                zip.close();
                return new ByteArrayInputStream(bytes);
            }
        }
        zip.close();
        throw new java.io.FileNotFoundException("On get stream : " + file.getName());
    }

    /** 本家 getInputStreamFromFile: 素のファイルでもアーカイブ内でも開ける。 */
    public static InputStream getInputStreamFromFile(java.io.File file) throws IOException {
        String suffix = getArchiveSuffix(file.getAbsolutePath());
        if (!suffix.isEmpty() && !file.isFile()) {
            return getStreamFromArchive(file, suffix);
        }
        return Files.newInputStream(file.toPath());
    }

    /** 本家 readBytes。 */
    public static byte[] readBytes(java.io.File file) throws IOException {
        return Files.readAllBytes(file.toPath());
    }

    /**
     * 本家 findFile: 探索ディレクトリ以下から条件に合うファイルを集める。
     * matcher は match(File) を持つオブジェクト (スクリプトの関数でも可)。
     */
    public static List<java.io.File> findFile(Object matcher) {
        List<java.io.File> out = new ArrayList<>();
        for (java.io.File dir : getModsDir()) {
            findFileInDirectory(out, dir, matcher);
        }
        return out;
    }

    /** 本家 findFileInDirectory: 1 ディレクトリ以下を再帰的に探す。 */
    public static List<java.io.File> findFileInDirectory(java.io.File dir, Object matcher) {
        List<java.io.File> out = new ArrayList<>();
        findFileInDirectory(out, dir, matcher);
        return out;
    }

    private static void findFileInDirectory(List<java.io.File> out, java.io.File dir, Object matcher) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        java.io.File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (java.io.File entry : files) {
            if (entry.isDirectory()) {
                findFileInDirectory(out, entry, matcher);
            } else if (matches(matcher, entry)) {
                out.add(entry);
            }
        }
    }

    /** matcher.match(file) を呼ぶ。呼べない matcher は「全て一致」とみなす。 */
    private static boolean matches(Object matcher, java.io.File file) {
        if (matcher == null) {
            return true;
        }
        try {
            Object r = matcher.getClass().getMethod("match", java.io.File.class).invoke(matcher, file);
            return !(r instanceof Boolean b) || b;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return true;
        }
    }

    private static List<Path> collectContainers() {
        List<Path> out = new ArrayList<>();
        Path gameDir = FMLPaths.GAMEDIR.get();
        addZips(out, gameDir.resolve("mods"));
        addZips(out, com.portofino.realtrainmodunofficial.DefaultAssetsFolder.get());
        Path cfg = gameDir.resolve("config").resolve("realtrainmodunofficial");
        for (String sub : new String[]{"vehicle_packs", "rail_packs", "packs", "nested_pack_cache"}) {
            addZips(out, cfg.resolve(sub));
        }
        addOwnModInDevelopment(out);
        return out;
    }

    /**
     * 開発環境でだけ、mod 自身の資源を索引に入れる。
     * 製品版では mod は mods/ の中の jar なので、上の mods 走査で
     * 自分の jar も索引に入る。
     */
    private static void addOwnModInDevelopment(List<Path> out) {
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return;
        }
        net.fabricmc.loader.api.FabricLoader.getInstance()
            .getModContainer(com.portofino.realtrainmodunofficial.RealTrainModUnofficial.MODID)
            .ifPresent(container -> out.addAll(container.getRootPaths()));
    }

    private static void addZips(List<Path> out, Path dir) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.list(dir)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                if (Files.isRegularFile(p) && (name.endsWith(".zip") || name.endsWith(".jar"))) {
                    out.add(p);
                } else if (Files.isDirectory(p)) {
                    out.add(p);
                }
            }
        } catch (IOException ignored) {
        }
    }
}
