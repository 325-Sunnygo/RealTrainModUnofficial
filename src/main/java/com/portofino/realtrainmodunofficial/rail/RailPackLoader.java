package com.portofino.realtrainmodunofficial.rail;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.portofino.realtrainmodunofficial.BundledPackStore;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.util.PackTextDecoder;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class RailPackLoader {
    private static final List<RailDefinition> LOADED = new ArrayList<>();
    private static final Map<String, Path> VIRTUAL_PACKS = new ConcurrentHashMap<>();
    private static boolean loaded = false;

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        LOADED.clear();
        // jar 同梱の本家レール定義。以前は loadFromModJar がどこからも呼ばれておらず
        // (デッドコード)、既定レールが 1 つも登録されていなかった。
        loadFromModJar();
        loadFromExternalDirectories();
        loadFromGameDirectories();
        RailRegistry.setDefinitions(LOADED);
    }

    private static void loadFromModJar() {
        loadBuiltinRailsFromModJar();
        try {
            for (Path path : BundledPackStore.listBundledPacks("rail")) {
                loadRailPackFile(path, path.getFileName().toString());
            }
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("Could not load bundled rail packs from mod jar", e);
        }
    }

    /**
     * MOD の jar に同梱した本家レール定義 (assets/minecraft/models/json/ModelRail_*.json) を読む。
     * ここが無かったため、レール定義はパック zip からしか読めなかった。
     */
    private static void loadBuiltinRailsFromModJar() {
        try {
            var modFileEntry = net.neoforged.fml.ModList.get().getModFileById(RealTrainModUnofficial.MODID);
            if (modFileEntry == null) {
                return;
            }
            Path jsonDir = modFileEntry.getFile().findResource("assets", "minecraft", "models", "json");
            if (jsonDir == null || !Files.isDirectory(jsonDir)) {
                return;
            }
            int[] count = {0};
            try (var stream = Files.list(jsonDir)) {
                stream.filter(Files::isRegularFile)
                    .filter(p -> isRailJson(p.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .forEach(p -> {
                        try {
                            parseRailJson(Files.readAllBytes(p), RealTrainModUnofficial.MODID);
                            count[0]++;
                        } catch (Exception e) {
                            RealTrainModUnofficial.LOGGER.warn("Failed to load bundled rail {}", p.getFileName(), e);
                        }
                    });
            }
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("Could not load bundled rail definitions", e);
        }
    }

    private static void loadFromExternalDirectories() {
        for (String dirName : new String[]{"rail_packs", "packs", ""}) {
            try {
                Path externalDir = FMLPaths.GAMEDIR.get().resolve("config").resolve("realtrainmodunofficial");
                if (!dirName.isEmpty()) externalDir = externalDir.resolve(dirName);
                if (Files.isDirectory(externalDir)) loadArchiveDirectory(externalDir);
            } catch (Exception e) {
                RealTrainModUnofficial.LOGGER.warn("Could not scan external rail packs {}", dirName, e);
            }
        }
    }

    private static void loadFromGameDirectories() {
        try {
            Path gameDir = FMLPaths.GAMEDIR.get();
            if (Files.isDirectory(gameDir)) {
                loadArchiveDirectory(gameDir);
                Path modsDir = gameDir.resolve("mods");
                if (Files.isDirectory(modsDir)) {
                    loadArchiveDirectory(modsDir);
                    Path modelPacksDir = modsDir.resolve("modelpacks");
                    if (Files.isDirectory(modelPacksDir)) loadArchiveDirectory(modelPacksDir);
                }
            }
            Path contentDir = gameDir.resolve("content");
            if (Files.isDirectory(contentDir)) loadArchiveDirectory(contentDir);
            Path vp = gameDir.resolve("vehicle_packs");
            if (Files.isDirectory(vp)) loadArchiveDirectory(vp);
            Path defaultAssets = com.portofino.realtrainmodunofficial.DefaultAssetsFolder.get();
            if (Files.isDirectory(defaultAssets)) loadArchiveDirectory(defaultAssets);
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("Could not scan game directory for rail packs", e);
        }
    }

    private static void loadArchiveDirectory(Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            // RTM 系 pack は zip / jar の両方で配られるので、入口は archive に寄せる。
            stream.filter(RailPackLoader::isSupportedArchive)
                .forEach(zipPath -> {
                    // README 同意ゲート: 未同意/拒否のパックはロードしない。
                    if (!com.portofino.realtrainmodunofficial.pack.PackConsent.isAllowed(zipPath)) {
                        return;
                    }
                    try {
                        loadRailPackFile(zipPath, zipPath.getFileName().toString());
                    } catch (Exception e) {
                        RealTrainModUnofficial.LOGGER.warn("Failed to load rail pack {}", zipPath.getFileName(), e);
                    }
                });
        }
    }

    private static boolean isSupportedArchive(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(".zip") && !fileName.endsWith(".jar")) {
            return false;
        }
        // 自分自身の jar を「パック」として読み直すと、jar 同梱の本家レール定義が
        // 二重登録される。ファイル名だけだとリネームされた jar をすり抜けるので実体でも見る。
        return !fileName.contains("realtrainmodunofficial")
            && !fileName.contains("rtm-official-assets")
            && !fileName.contains("kaizpatchx")
            && !BundledPackStore.isOwnModJar(path);
    }

    public static synchronized void reload() {
        loaded = false;
        load();
    }

    private static void loadRailPack(InputStream zipInput, String packName) throws IOException {
        loadRailPack(zipInput, packName, 0, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * パスから読む版。エントリ名が Shift-JIS の zip (Windows 製の日本語名パック) は
     * UTF-8 では読めないので、その場合だけ Shift-JIS で開き直す。
     */
    private static void loadRailPackFile(java.nio.file.Path path, String packName) throws Exception {
        com.portofino.realtrainmodunofficial.util.PackZip.readWithFallback(
            () -> Files.newInputStream(path), packName,
            (in, charset) -> loadRailPack(in, packName, 0, charset));
    }

    private static void loadRailPack(InputStream zipInput, String packName, int depth,
                                     java.nio.charset.Charset charset) throws IOException {
        List<byte[]> jsonBytes = new ArrayList<>();
        List<NestedArchive> nestedArchives = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(zipInput, charset)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    String name = normalize(entry.getName());
                    if (isRailJson(name)) {
                        jsonBytes.add(zip.readAllBytes());
                    } else if (depth < 2 && isArchiveName(name)) {
                        nestedArchives.add(new NestedArchive(name, zip.readAllBytes()));
                    }
                }
                zip.closeEntry();
            }
        }
        for (byte[] bytes : jsonBytes) {
            parseRailJson(bytes, packName);
        }
        for (NestedArchive nested : nestedArchives) {
            Path materialized = materializeNestedPack(nested.name(), nested.bytes());
            try (InputStream input = Files.newInputStream(materialized)) {
                loadRailPack(input, nested.name(), depth + 1, charset);
            }
        }
    }

    public static Path materializeNestedPack(String nestedName, byte[] bytes) throws IOException {
        String leaf = nestedName == null || nestedName.isBlank() ? "nested_pack.zip"
            : normalize(nestedName).substring(normalize(nestedName).lastIndexOf('/') + 1);
        String safeName = leaf.replaceAll("[^A-Za-z0-9._-]", "_");
        String hash = Integer.toHexString(Arrays.hashCode(bytes));
        Path cacheDir = FMLPaths.GAMEDIR.get()
            .resolve("config")
            .resolve("realtrainmodunofficial")
            .resolve("nested_pack_cache");
        Files.createDirectories(cacheDir);
        Path cached = cacheDir.resolve(hash + "_" + safeName);
        if (!Files.exists(cached) || Files.size(cached) != bytes.length) {
            Files.write(cached, bytes);
        }
        VIRTUAL_PACKS.put(leaf, cached);
        VIRTUAL_PACKS.put(nestedName, cached);
        VIRTUAL_PACKS.put(cached.getFileName().toString(), cached);
        return cached;
    }

    private static void parseRailJson(byte[] bytes, String packName) {
        try {
            JsonElement el = JsonParser.parseString(PackTextDecoder.decodeJson(bytes));
            if (!el.isJsonObject()) return;
            JsonObject obj = el.getAsJsonObject();
            String railName = getString(obj, "railName");
            String id = railName != null ? railName : "rail";
            String displayName = railName != null ? railName : id;
            JsonObject model = getObject(obj, "model");
            if (model == null) model = getObject(obj, "railModel2");

            String modelFile;
            Map<String, String> tex;
            if (model != null) {
                modelFile = getString(model, "modelFile");
                tex = parseTextures(model);
            } else {
                // ★ 旧形式 (本家 RailConfig.init): "model" オブジェクトが無いパックは
                // "railModel" (モデルのパス) と "railTexture" (テクスチャのパス) を
                // 文字列で直接持っている。
                // this.model.modelFile = this.railModel;
                // this.model.textures  = {{"default", this.railTexture}};
                modelFile = getString(obj, "railModel");
                if (modelFile == null || modelFile.isBlank()) {
                    return;
                }
                Map<String, String> legacyTex = new HashMap<>();
                String railTexture = getString(obj, "railTexture");
                if (railTexture != null && !railTexture.isBlank()) {
                    legacyTex.put("default", railTexture);
                }
                tex = legacyTex;
                model = new JsonObject();
            }
            if (modelFile == null || modelFile.isBlank()) return;
            String scriptPath = getString(model, "rendererPath");
            if (scriptPath == null || scriptPath.isBlank()) scriptPath = getString(obj, "rendererPath");
            if (scriptPath == null || scriptPath.isBlank()) scriptPath = getString(obj, "scriptPath");
            String buttonTexture = firstNonBlank(getString(obj, "buttonTexture"), getString(model, "buttonTexture"));
            Vec3 offset = parseVec3(model, "offset", 1.0 / 16.0);
            float scale = parseFloat(model, "scale", 1.0F);
            // ballastWidth / defaultBallast (RTM 公式パック互換) / model.ballastWidth のいずれか。
            // RTM 公式の defaultBallast は配列 [{blockName, blockMetadata, height}, ...]。
            int ballast = 0;
            String ballastBlockId = "";
            if (obj.has("ballastWidth")) {
                ballast = readIntSafe(obj.get("ballastWidth"), 0);
            } else if (model.has("ballastWidth")) {
                ballast = readIntSafe(model.get("ballastWidth"), 0);
            }
            // defaultBallast から敷設ブロック (gravel=砂利 等) を拾う。幅未指定ならここで既定 3。
            JsonElement ballastEl = obj.has("defaultBallast") ? obj.get("defaultBallast")
                : (model.has("defaultBallast") ? model.get("defaultBallast") : null);
            if (ballastEl != null) {
                String firstBlock = firstBallastBlockName(ballastEl);
                if (firstBlock != null && !firstBlock.isBlank()) {
                    ballastBlockId = normalizeBlockId(firstBlock);
                    if (ballast <= 0) ballast = 3;
                } else if (ballastEl.isJsonPrimitive() && ballastEl.getAsJsonPrimitive().isNumber()) {
                    // 旧式: defaultBallast が数値 (= 幅) のパック互換。
                    ballast = readIntSafe(ballastEl, ballast);
                }
            }
            if (ballast <= 0 && ballastBlockId.isEmpty()) {
                String idLower = id == null ? "" : id.toLowerCase(java.util.Locale.ROOT);
                String fileLower = modelFile.toLowerCase(java.util.Locale.ROOT);
                if (idLower.contains("1067mm") || idLower.contains("1435mm") || idLower.contains("1524mm")
                    || fileLower.contains("1067mm") || fileLower.contains("1435mm") || fileLower.contains("1524mm")
                    || idLower.contains("762mm") || fileLower.contains("762mm")) {
                    ballast = 3;
                }
            }
            LOADED.add(new RailDefinition(id, displayName, packName, packName, modelFile, scriptPath, buttonTexture, tex, offset, scale, ballast, ballastBlockId));
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("Failed to parse rail json in {}: {}", packName, e.getMessage());
        }
    }

    private static int readIntSafe(JsonElement el, int def) {
        try {
            if (el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) {
                return el.getAsInt();
            }
        } catch (Exception ignored) {
        }
        return def;
    }

    /** defaultBallast (配列/オブジェクト/文字列) から最初の blockName を取り出す。 */
    private static String firstBallastBlockName(JsonElement el) {
        try {
            if (el.isJsonArray()) {
                for (JsonElement e : el.getAsJsonArray()) {
                    if (e.isJsonObject()) {
                        String b = getString(e.getAsJsonObject(), "blockName");
                        if (b != null && !b.isBlank()) return b;
                    } else if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
                        return e.getAsString();
                    }
                }
            } else if (el.isJsonObject()) {
                return getString(el.getAsJsonObject(), "blockName");
            } else if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                return el.getAsString();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** "gravel" → "minecraft:gravel"。名前空間付きならそのまま。 */
    private static String normalizeBlockId(String name) {
        String n = name.trim().toLowerCase(java.util.Locale.ROOT);
        if (n.isEmpty() || n.equals("air") || n.equals("minecraft:air")) return "";
        return n.contains(":") ? n : "minecraft:" + n;
    }

    private static boolean isRailJson(String path) {
        if (!path.endsWith(".json")) return false;
        String n = path.substring(path.lastIndexOf('/') + 1).toLowerCase();
        return n.startsWith("modelrail_");
    }

    private static boolean isArchiveName(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".zip") || lower.endsWith(".jar");
    }

    private static String normalize(String raw) {
        return raw.replace('\\', '/');
    }

    private static JsonObject getObject(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonObject() ? o.getAsJsonObject(k) : null;
    }

    private static String getString(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static Map<String, String> parseTextures(JsonObject modelObj) {
        if (modelObj == null || !modelObj.has("textures") || !modelObj.get("textures").isJsonArray()) {
            return Map.of();
        }
        Map<String, String> overrides = new HashMap<>();
        JsonArray array = modelObj.getAsJsonArray("textures");
        for (JsonElement entry : array) {
            if (!entry.isJsonArray()) continue;
            JsonArray pair = entry.getAsJsonArray();
            if (pair.size() < 2) continue;
            String mat = pair.get(0).getAsString();
            String tex = pair.get(1).getAsString();
            if (!mat.isBlank() && !tex.isBlank()) overrides.put(mat, tex);
        }
        return overrides;
    }

    private static Vec3 parseVec3(JsonObject obj, String key, double scale) {
        if (obj == null || !obj.has(key) || !obj.get(key).isJsonArray()) return Vec3.ZERO;
        JsonArray arr = obj.getAsJsonArray(key);
        if (arr.size() < 3) return Vec3.ZERO;
        try {
            return new Vec3(arr.get(0).getAsDouble() * scale, arr.get(1).getAsDouble() * scale, arr.get(2).getAsDouble() * scale);
        } catch (Exception e) {
            return Vec3.ZERO;
        }
    }

    private static float parseFloat(JsonObject obj, String key, float def) {
        if (obj == null || !obj.has(key)) return def;
        try {
            return obj.get(key).getAsFloat();
        } catch (Exception e) {
            return def;
        }
    }

    public static Path resolvePackPath(String packName) {
        if (packName == null || packName.isBlank()) return null;
        Path virtual = VIRTUAL_PACKS.get(packName);
        if (virtual != null && Files.exists(virtual)) return virtual;
        String normalizedPackName = normalize(packName);
        String leafPackName = normalizedPackName.contains("/")
            ? normalizedPackName.substring(normalizedPackName.lastIndexOf('/') + 1)
            : normalizedPackName;
        virtual = VIRTUAL_PACKS.get(leafPackName);
        if (virtual != null && Files.exists(virtual)) return virtual;
        try {
            Path direct = Path.of(packName);
            if (direct.isAbsolute() && Files.exists(direct)) return direct;
        } catch (Exception ignored) {}
        for (String dir : new String[]{"rail_packs", "packs", "vehicle_packs", ""}) {
            try {
                Path ext = FMLPaths.GAMEDIR.get().resolve("config").resolve("realtrainmodunofficial");
                if (!dir.isEmpty()) ext = ext.resolve(dir);
                ext = ext.resolve(packName);
                if (Files.exists(ext)) return ext;
            } catch (Exception ignored) {
            }
        }
        Path gameDir = FMLPaths.GAMEDIR.get();
        Path candidate = gameDir.resolve(packName);
        if (Files.exists(candidate)) return candidate;
        Path modsDir = gameDir.resolve("mods").resolve(packName);
        if (Files.exists(modsDir)) return modsDir;
        Path modelPacksDir = gameDir.resolve("mods").resolve("modelpacks").resolve(packName);
        if (Files.exists(modelPacksDir)) return modelPacksDir;
        Path defaultAssetsPack = com.portofino.realtrainmodunofficial.DefaultAssetsFolder.get().resolve(packName);
        if (Files.exists(defaultAssetsPack)) return defaultAssetsPack;
        Path contentDir = gameDir.resolve("content").resolve(packName);
        if (Files.exists(contentDir)) return contentDir;
        Path nestedCacheDir = gameDir.resolve("config").resolve("realtrainmodunofficial").resolve("nested_pack_cache");
        if (Files.isDirectory(nestedCacheDir)) {
            try (var stream = java.nio.file.Files.list(nestedCacheDir)) {
                java.util.Optional<Path> hit = stream
                    .filter(java.nio.file.Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith("_" + leafPackName))
                    .findFirst();
                if (hit.isPresent()) {
                    VIRTUAL_PACKS.put(packName, hit.get());
                    VIRTUAL_PACKS.put(leafPackName, hit.get());
                    return hit.get();
                }
            } catch (Exception ignored) {}
        }
        Path materialized = BundledPackStore.materializeBundledPack(packName);
        if (materialized != null) return materialized;
        if (BundledPackStore.isBundledPackName(packName)) {
            Path modJar = BundledPackStore.getModJarPath();
            if (modJar != null) return modJar;
        }
        // packName が mod ID 自身 ("realtrainmodunofficial") のとき = MOD の jar に同梱した
        // 本家定義 (assets/minecraft/models/json/*.json)。
        // assets/minecraft/models/ に入っているので、jar そのものをパックとして返す。
        // ★ここが null を返すと、定義は読めるのにモデルの実体が見つからず「当たり判定は
        // あるのにモデルが透明」になる。
        if (com.portofino.realtrainmodunofficial.RealTrainModUnofficial.MODID.equalsIgnoreCase(packName)) {
            Path modJar = BundledPackStore.getModJarPath();
            if (modJar != null) {
                return modJar;
            }
            // 開発環境 (jar でなく展開済みクラスパス) では jar パスが取れないことがある。
            // その場合は従来どおり mods フォルダの RTM-Official-Assets*.zip を探す。
            Path officialModsDir = gameDir.resolve("mods");
            try (var stream = java.nio.file.Files.list(officialModsDir)) {
                java.util.Optional<Path> hit = stream
                    .filter(java.nio.file.Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                        return name.endsWith(".zip") && name.contains("rtm-official-assets");
                    })
                    .findFirst();
                if (hit.isPresent()) return hit.get();
            } catch (Exception ignored) {}
        }
        return null;
    }

    public static InputStream openPackStream(RailDefinition definition) throws IOException {
        if (definition == null) return null;
        Path p = resolvePackPath(definition.getPackName());
        return p == null ? null : Files.newInputStream(p);
    }

    public static InputStream openPackStreamByName(String packName) throws IOException {
        Path p = resolvePackPath(packName);
        return p == null ? null : Files.newInputStream(p);
    }

    /** スクリプトファイルの内容をパックZIPから読み込む。見つからない場合はnullを返す。 */
    public static String readScriptContent(RailDefinition definition) {
        if (definition == null || definition.getScriptPath() == null || definition.getScriptPath().isBlank()) return null;
        // ★同梱パック (mod jar) は resolvePackPath が null を返す。
        // 同梱レールのスクリプトが永久に読めず、黙ってスクリプト無し描画に落ちていた。
        Path packPath = resolvePackPath(definition.getPackName());
        String scriptPath = normalize(definition.getScriptPath());
        String scriptFileName = scriptPath.contains("/")
            ? scriptPath.substring(scriptPath.lastIndexOf('/') + 1).toLowerCase()
            : scriptPath.toLowerCase();
        if (packPath != null) {
        try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(Files.newInputStream(packPath))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = normalize(entry.getName());
                if (name.equalsIgnoreCase(scriptPath) || name.toLowerCase().endsWith("/" + scriptFileName)
                        || name.toLowerCase().equals(scriptFileName)) {
                    return PackTextDecoder.readText(zip);
                }
                zip.closeEntry();
            }
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("Failed to read script {} from pack {}", definition.getScriptPath(), definition.getPackName(), e);
        }
        }
        // 自パックに無い: 前提/ベーススクリプトパックを横断する資産索引 (NGTFileLoader) から探す。
        // 車両スクリプトと同じく、レールの実体スクリプトが別の前提パックにある分割構成に対応する。
        try {
            byte[] bytes = jp.ngt.ngtlib.io.NGTFileLoader.findAsset(scriptPath);
            if (bytes != null) {
                return PackTextDecoder.readText(new java.io.ByteArrayInputStream(bytes));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private record NestedArchive(String name, byte[] bytes) {
    }
}
