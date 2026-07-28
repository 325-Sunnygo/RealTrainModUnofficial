package com.portofino.realtrainmodunofficial.installedobject;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.portofino.realtrainmodunofficial.BundledPackStore;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.rail.RailPackLoader;
import com.portofino.realtrainmodunofficial.util.PackTextDecoder;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class InstalledObjectPackLoader {
    private static final Pattern LIGHT_STATE_PATTERN = Pattern.compile("S\\((\\d+)\\)");
    private static final Pattern LIGHT_PARTS_PATTERN = Pattern.compile("P\\(([^)]+)\\)");
    private static final List<InstalledObjectDefinition> LOADED = new ArrayList<>();
    private static boolean loaded;

    private InstalledObjectPackLoader() {
    }

    public static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        LOADED.clear();
        try {
            loadFromModJar();
            loadDirectoryPacks(FMLPaths.GAMEDIR.get());
            loadArchiveDirectory(FMLPaths.GAMEDIR.get());
            Path modsDir = FMLPaths.GAMEDIR.get().resolve("mods");
            if (Files.isDirectory(modsDir)) {
                loadDirectoryPacks(modsDir);
                loadArchiveDirectory(modsDir);
            }
            Path modelPacksDir = modsDir.resolve("modelpacks");
            if (Files.isDirectory(modelPacksDir)) {
                loadDirectoryPacks(modelPacksDir);
                loadArchiveDirectory(modelPacksDir);
            }
            Path contentDir = FMLPaths.GAMEDIR.get().resolve("content");
            if (Files.isDirectory(contentDir)) {
                loadDirectoryPacks(contentDir);
                loadArchiveDirectory(contentDir);
            }
            Path defaultAssetsDir = com.portofino.realtrainmodunofficial.DefaultAssetsFolder.get();
            if (Files.isDirectory(defaultAssetsDir)) {
                loadDirectoryPacks(defaultAssetsDir);
                loadArchiveDirectory(defaultAssetsDir);
            }
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("Could not scan installed object packs", e);
        }
        ensureDefaultConnectors();
        InstalledObjectRegistry.setDefinitions(LOADED);
        long connectors = LOADED.stream().filter(d -> d.getCategory() == InstalledObjectCategory.CONNECTOR_INPUT
                || d.getCategory() == InstalledObjectCategory.CONNECTOR_OUTPUT).count();
        //カテゴリ別の内訳。「看板の選択画面が空」等の切り分けがログだけでできるようにしておく。
        java.util.Map<InstalledObjectCategory, Long> byCategory = LOADED.stream()
                .collect(java.util.stream.Collectors.groupingBy(InstalledObjectDefinition::getCategory,
                        java.util.stream.Collectors.counting()));
    }

    private static void loadFromModJar() {
        loadFromModJarDirect();
        try {
            for (Path path : BundledPackStore.listBundledPacks("rail")) {
                try (InputStream input = Files.newInputStream(path)) {
                    loadPack(input, path.getFileName().toString());
                }
            }
            for (Path path : BundledPackStore.listBundledPacks("installed_object")) {
                try (InputStream input = Files.newInputStream(path)) {
                    loadPack(input, path.getFileName().toString());
                }
            }
            //RTM-Official-Assets (コネクタ Input01/Output01 等の本家デフォルトモデルを含む)
            for (Path path : BundledPackStore.listBundledPacks("official")) {
                try (InputStream input = Files.newInputStream(path)) {
                    loadPack(input, path.getFileName().toString());
                }
            }
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("Could not scan bundled installed object packs", e);
        }
    }

    private static void loadFromModJarDirect() {
        try {
            var modFileEntry = ModList.get().getModFileById(RealTrainModUnofficial.MODID);
            if (modFileEntry == null) return;
            var modFile = modFileEntry.getFile();
            Path jsonDir = modFile.findResource("assets", "minecraft", "models", "json");
            if (jsonDir == null || !Files.isDirectory(jsonDir)) return;
            Path modRoot = modFile.getFilePath();
            if (modRoot == null) return;
            String packName = RealTrainModUnofficial.MODID;
            try (var stream = Files.list(jsonDir)) {
                stream.filter(Files::isRegularFile)
                    .filter(p -> isSupportedJson(normalize(p.getFileName().toString())))
                    .forEach(path -> {
                        try {
                            parse(normalize(path.getFileName().toString()), Files.readAllBytes(path), packName);
                        } catch (Exception e) {
                            RealTrainModUnofficial.LOGGER.warn("Failed to load built-in installed object definition {}", path.getFileName(), e);
                        }
                    });
            }
            //標識 (RRS) は JSON を持たず textures/rrs/*.png 自体が選択肢になる。
            Path rrsDir = modFile.findResource("assets", "minecraft", "textures", "rrs");
            if (rrsDir != null && Files.isDirectory(rrsDir)) {
                try (var stream = Files.list(rrsDir)) {
                    stream.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                        .forEach(path -> registerRailroadSign("textures/rrs/" + path.getFileName(), packName));
                }
            }
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("Could not load built-in installed object definitions from mod JAR", e);
        }
    }

    public static synchronized void reload() {
        loaded = false;
        load();
    }

    private static void loadDirectoryPacks(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.list(dir)) {
            stream.filter(Files::isDirectory)
                .filter(InstalledObjectPackLoader::looksLikeInstalledObjectPackDirectory)
                .forEach(path -> {
                    try {
                        loadPackDirectory(path, path.getFileName().toString());
                    } catch (Exception e) {
                        RealTrainModUnofficial.LOGGER.warn("Failed to load installed object directory pack {}", path.getFileName(), e);
                    }
                });
        }
    }

    private static void loadArchiveDirectory(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.list(dir)) {
            stream.filter(InstalledObjectPackLoader::isSupportedArchive)
                .forEach(path -> {
                    try {
                        loadPackFile(path, path.getFileName().toString());
                    } catch (Exception e) {
                        RealTrainModUnofficial.LOGGER.warn("Failed to load installed object pack {}", path.getFileName(), e);
                    }
                });
        }
    }

    private static boolean isSupportedArchive(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(".zip") && !fileName.endsWith(".jar")) {
            return false;
        }
        //mods/ に置かれた自分自身の jar をパックとして読み直さない。
        //読み直すと jar 同梱の本家定義が二重登録される (BundledPackStore.isOwnModJar 参照)。
        return !BundledPackStore.isOwnModJar(path);
    }

    private static boolean looksLikeInstalledObjectPackDirectory(Path dir) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        if (Files.exists(dir.resolve("assets")) || Files.exists(dir.resolve("models")) || Files.exists(dir.resolve("scripts"))) {
            return true;
        }
        try (var stream = Files.walk(dir, 4)) {
            return stream
                .filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString().toLowerCase(Locale.ROOT))
                .anyMatch(name -> name.endsWith(".json") && (
                    name.startsWith("modelmachine_")
                        || name.startsWith("modelsignal_")
                        || name.startsWith("modelconnector_")
                        || name.startsWith("modelwire_")
                        || name.startsWith("modelcrossing_")
                        || name.startsWith("modelornament_")
                        || name.startsWith("signboard_")
                ));
        } catch (IOException e) {
            return false;
        }
    }

    private static void loadPack(InputStream zipInput, String packName) throws IOException {
        loadPack(zipInput, packName, 0, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * エントリ名が Shift-JIS の zip (Windows 製の日本語名パック) は UTF-8 では読めないので、
     * その場合だけ Shift-JIS で開き直す。
     */
    private static void loadPackFile(Path path, String packName) throws Exception {
        com.portofino.realtrainmodunofficial.util.PackZip.readWithFallback(
            () -> Files.newInputStream(path), packName,
            (in, charset) -> loadPack(in, packName, 0, charset));
    }

    private static void loadPack(InputStream zipInput, String packName, int depth,
                                 java.nio.charset.Charset charset) throws IOException {
        List<EntryData> entries = new ArrayList<>();
        List<NestedArchive> nestedArchives = new ArrayList<>();
        List<String> rrsTextures = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(zipInput, charset)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    String normalized = normalize(entry.getName());
                    if (isSupportedJson(normalized)) {
                        entries.add(new EntryData(normalized, zip.readAllBytes()));
                    } else if (isRrsTexture(normalized)) {
                        rrsTextures.add(normalized);
                    } else if (depth < 2 && isArchiveName(normalized)) {
                        nestedArchives.add(new NestedArchive(normalized, zip.readAllBytes()));
                    }
                }
                zip.closeEntry();
            }
        }
        for (EntryData entry : entries) {
            parse(entry.path(), entry.bytes(), packName);
        }
        for (String rrs : rrsTextures) {
            registerRailroadSign(rrs, packName);
        }
        for (NestedArchive nested : nestedArchives) {
            Path materialized = RailPackLoader.materializeNestedPack(nested.name(), nested.bytes());
            try (InputStream input = Files.newInputStream(materialized)) {
                loadPack(input, nested.name(), depth + 1, charset);
            }
        }
    }

    private static void loadPackDirectory(Path packDir, String packName) throws IOException {
        try (var stream = Files.walk(packDir)) {
            stream.filter(Files::isRegularFile)
                .forEach(path -> {
                    String relative = normalize(packDir.relativize(path).toString());
                    try {
                        if (isSupportedJson(relative)) {
                            parse(relative, Files.readAllBytes(path), packName);
                        } else if (isRrsTexture(relative)) {
                            registerRailroadSign(relative, packName);
                        }
                    } catch (Exception e) {
                        RealTrainModUnofficial.LOGGER.warn("Failed to parse installed object json {} in {}", path, packName, e);
                    }
                });
        }
    }

    // ---- 標識 (本家 RRS) ----
    //
    //本家の標識だけは他の設置物と違って JSON を持たない。ResourceType RRS は
    //setCustomLoading(true) で textures/rrs/ 以下の png を総なめして「テクスチャそのもの」を
    //選択肢にする。ここでも同じく、パック/JAR に入っている textures/rrs/*.png を1枚1定義として登録する。

    private static boolean isRrsTexture(String path) {
        String lower = normalize(path).toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") && lower.contains("textures/rrs/");
    }

    /**
     * @param path 例: "assets/minecraft/textures/rrs/rrs_01.png"
     */
    private static void registerRailroadSign(String path, String packName) {
        String normalized = normalize(path);
        int index = normalized.toLowerCase(Locale.ROOT).indexOf("textures/rrs/");
        if (index < 0) {
            return;
        }
        //パックテクスチャの解決は "textures/..." からの相対パスで行う (MqoModelLoader.resolvePackTexture)。
        String texture = normalized.substring(index);
        String name = leaf(texture).replace(".png", "");
        String id = InstalledObjectCategory.RAILROAD_SIGN.name().toLowerCase(Locale.ROOT) + ":" + packName + ":" + name;
        LOADED.add(new InstalledObjectDefinition(
            id, name, packName, InstalledObjectCategory.RAILROAD_SIGN,
            //モデルは無い。板とポールは BER が直接描く (本家 RenderRailroadSign と同じ)。
            "", "", texture, Map.of(),
            Vec3.ZERO, 1.0F, false,
            1.0F, 1.0F, 0.125F, texture, "", "",
            Map.of(), List.of(), Vec3.ZERO, 1, 0));
    }

    /**
     * コネクタ定義がパックから読めなかった場合の保険:
     * 本家デフォルト (Input01/Output01, RTM-Official-Assets の Connector.mqo) をコードで登録する。
     */
    private static void ensureDefaultConnectors() {
        boolean hasInput = LOADED.stream().anyMatch(d -> d.getCategory() == InstalledObjectCategory.CONNECTOR_INPUT);
        boolean hasOutput = LOADED.stream().anyMatch(d -> d.getCategory() == InstalledObjectCategory.CONNECTOR_OUTPUT);
        if (!hasInput) {
            LOADED.add(builtinConnector(InstalledObjectCategory.CONNECTOR_INPUT, "Input01", "textures/connector/input.png",
                    "textures/connector/button_Input01.png"));
        }
        if (!hasOutput) {
            LOADED.add(builtinConnector(InstalledObjectCategory.CONNECTOR_OUTPUT, "Output01", "textures/connector/output.png",
                    "textures/connector/button_Output01.png"));
        }
    }

    private static InstalledObjectDefinition builtinConnector(InstalledObjectCategory category, String name,
                                                              String texture, String buttonTexture) {
        String packName = "RTM-Official-Assets.zip";
        String id = category.name().toLowerCase(Locale.ROOT) + ":" + packName + ":" + name;
        Map<String, String> textures = new HashMap<>();
        textures.put("default", texture);
        InstalledObjectDefinition def = new InstalledObjectDefinition(
                id, name, packName, category,
                "Connector.mqo", "", buttonTexture, textures,
                Vec3.ZERO, 1.0F, false,
                1.0F, 1.0F, 0.125F, "", "", "",
                Map.of(), List.of(), Vec3.ZERO, 1, 1);
        def.setWireAttachPos(new Vec3(0.0D, -0.25D, 0.0D));
        return def;
    }

    private static boolean isSupportedJson(String path) {
        String file = leaf(path).toLowerCase(Locale.ROOT);
        return file.endsWith(".json") && (
            file.startsWith("modelmachine_")
            || file.startsWith("modelsignal_")
            || file.startsWith("modelconnector_")
            || file.startsWith("modelwire_")
            || file.startsWith("modelcrossing_")
            //本家 ModelOrnament_ (蛍光灯/架線柱)。ornamentType が Lamp/Pole 以外のもの
            //(足場/階段/パイプ/植物) は categoryFor が null を返して捨てる。
            || file.startsWith("modelornament_")
            || file.startsWith("signboard_")
        );
    }

    private static boolean isArchiveName(String path) {
        String lower = normalize(path).toLowerCase(Locale.ROOT);
        return lower.endsWith(".zip") || lower.endsWith(".jar");
    }

    private static void parse(String path, byte[] bytes, String packName) {
        try {
            JsonElement element = JsonParser.parseString(PackTextDecoder.decodeJson(bytes));
            if (!element.isJsonObject()) {
                return;
            }
            JsonObject obj = element.getAsJsonObject();
            String file = leaf(path);
            String lower = file.toLowerCase(Locale.ROOT);
            if (lower.startsWith("signboard_")) {
                parseSignboard(obj, packName, file);
                return;
            }

            InstalledObjectCategory category = categoryFor(obj, lower);
            if (category == null) {
                //未対応の飾り(足場/階段/パイプ/植物)。登録しない。
                return;
            }
            JsonObject model = getObject(obj, "model");
            JsonObject modelPartsBody = getObject(obj, "modelPartsBody");
            String modelFile = firstNonBlank(model == null ? null : getString(model, "modelFile"), getString(obj, "signalModel"));
            if (modelFile == null || modelFile.isBlank()) {
                return;
            }
            String name = firstNonBlank(getString(obj, "name"), getString(obj, "signalName"), file.replace(".json", ""));
            String id = category.name().toLowerCase(Locale.ROOT) + ":" + packName + ":" + name;
            String scriptPath = firstNonBlank(model == null ? null : getString(model, "rendererPath"), getString(obj, "rendererPath"));
            String runningSound = firstNonBlank(
                model == null ? null : getString(model, "sound_Running"),
                model == null ? null : getString(model, "soundRunning"),
                getString(obj, "sound_Running"),
                getString(obj, "soundRunning")
            );
            Vec3 offset = parseVec3(model, "offset", 1.0 / 16.0);
            float scale = parseFloat(model, "scale", 1.0F);
            //本家 ModelConfig.smoothing の既定値は false (JSON で明示された時だけスムージング)。
            //実パックはほぼ全モデルで明示指定しており、未指定モデルを true にすると本家より
            //滑らかになってしまうため本家に合わせる。
            boolean smoothing = getBoolean(obj, "smoothing", false);
            Map<String, String> textures = new HashMap<>(parseTextures(model));
            if (category == InstalledObjectCategory.SIGNAL) {
                String signalTexture = getString(obj, "signalTexture");
                if (signalTexture != null && !signalTexture.isBlank()) {
                    textures.putIfAbsent("default", signalTexture);
                }
            }
            InstalledObjectDefinition def = new InstalledObjectDefinition(
                id,
                name,
                packName,
                category,
                modelFile,
                scriptPath,
                firstNonBlank(getString(obj, "buttonTexture"), model == null ? null : getString(model, "buttonTexture")),
                textures,
                offset,
                scale,
                smoothing,
                1.0F,
                1.0F,
                0.125F,
                "",
                // 点灯用テクスチャ (本家 SignalConfig.lightTexture / TextureSet "Light")。
                // buttonTexture は GUI のボタン画像なので現示灯には使えない (フォールバック禁止)。
                (category == InstalledObjectCategory.SIGNAL || category == InstalledObjectCategory.LIGHT)
                    ? firstNonBlank(getString(obj, "lightTexture"), getString(obj, "emissiveTexture"))
                    : "",
                runningSound,
                // 照明(LIGHT)も信号と同じ "lights": ["S(1) P(部品名)"] 形式で発光パーツを定義できる。
                (category == InstalledObjectCategory.SIGNAL || category == InstalledObjectCategory.LIGHT)
                    ? parseSignalLights(obj) : Map.of(),
                parseRenderObjects(model, modelPartsBody),
                parseVec3(modelPartsBody, "pos", 1.0),
                1,
                1
            );
            if (category == InstalledObjectCategory.WIRE) {
                // ワイヤーは sectionLength(モデル1個分の長さ)と deflectionCoefficient(たるみ)を持つ。
                float sectionLength = parseFloat(obj, "sectionLength", 0.5F);
                float deflection = parseFloat(obj, "deflectionCoefficient", 0.0F);
                def.setWireParams(sectionLength, deflection);
            }
            //本家 KaizPatchX の customIconTexture。持ち物欄でのアイテムの絵を差し替える。
            //架線柱のように「1 つのアイテムで中身を選ぶ」物を見分けるための機能で、
            //本家では ModelOrnament_LinePole01〜/SignalPole01 が使っている。
            def.setCustomIconTexture(firstNonBlank(getString(obj, "customIconTexture"),
                model == null ? null : getString(model, "customIconTexture")));
            def.setWireAttachPos(parseVec3(obj, "wirePos", 1.0));
            //本家 connectorType ("Relay"/"Input"/"Output")。NGTO Builder がリレー碍子判定に使う。
            def.setSubType(firstNonBlank(getString(obj, "connectorType"), getString(obj, "ConnectorType")));
            //本家 MachineConfig.rotateByMetadata。true の照明はクリック面 (meta) で回して置く。
            def.setRotateByMetadata(getBoolean(obj, "rotateByMetadata", false));
            //本家 ModelConfig.serverScriptPath。サーバー側で毎 tick onUpdate が回るスクリプト
            //(列車検知器は全ての処理をここに書く)。
            def.setServerScriptPath(getString(obj, "serverScriptPath"));
            //本家 ModelConfig.doCulling (既定 false = 両面描画)。本家 ModelObject.render は
            //車両も設置オブジェクトも同じ経路で !doCulling のとき GL_CULL_FACE を切る。
            //未移植だったため設置オブジェクトが常に片面になり、片面モデリングの架線・踏切が
            //裏側から消えていた。
            def.setDoCulling(getBoolean(obj, "doCulling", false));
            LOADED.add(def);
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("Failed to parse installed object json {} in {}: {}", path, packName, e.getMessage());
        }
    }

    private static void parseSignboard(JsonObject obj, String packName, String file) {
        String texture = normalizeSignboardTexture(getString(obj, "texture"));
        if (texture == null || texture.isBlank()) {
            return;
        }
        String name = file.replace(".json", "");
        String id = InstalledObjectCategory.SIGNBOARD.name().toLowerCase(Locale.ROOT) + ":" + packName + ":" + name;
        int frame = (int) getDouble(obj, "frame", 1.0);
        //本家 SignboardConfig.backTexture は初期化子が無く init() でも補正されないので、
        //キーが無いときの既定は 0 (= 表と同じテクスチャを裏にも貼る)。1 にすると
        //backTexture を書いていない設定 (ngt_b01/b02/b03/c01/test01) でテクスチャが
        //左半分だけ引き伸ばされてしまう。
        int backTexture = (int) getDouble(obj, "backTexture", 0.0);
        InstalledObjectDefinition def = new InstalledObjectDefinition(
            id,
            name,
            packName,
            InstalledObjectCategory.SIGNBOARD,
            "",
            "",
            texture,
            Map.of(),
            Vec3.ZERO,
            1.0F,
            false,
            (float) getDouble(obj, "width", 1.0),
            (float) getDouble(obj, "height", 1.0),
            (float) getDouble(obj, "depth", 0.125),
            texture,
            "",
            "",
            Map.of(),
            Vec3.ZERO,
            frame,
            backTexture
        );
        //本家 SignboardConfig: アニメ周期 / 板の側面色 / 発光量。
        def.setSignboardParams(
            (int) getDouble(obj, "animationCycle", 1.0),
            (int) getDouble(obj, "color", 0.0),
            (int) getDouble(obj, "lightValue", 0.0)
        );
        LOADED.add(def);
    }

    private static String normalizeSignboardTexture(String texture) {
        if (texture == null || texture.isBlank()) {
            return "";
        }
        String normalized = normalize(texture);
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return normalized.contains("/") ? normalized : "textures/signboard/" + normalized;
        }
        if (normalized.contains("/")) {
            return normalized + ".png";
        }
        return "textures/signboard/" + normalized + ".png";
    }

    /**
     * JSON から設置物カテゴリを決める。
     *
     * @return null = 対応していない種類 (足場/階段/パイプ/植物など)。呼び出し側は登録せず捨てる。
     */
    private static InstalledObjectCategory categoryFor(JsonObject obj, String lowerFile) {
        String machineType = firstNonBlank(getString(obj, "machineType"), getString(obj, "MachineType")).toLowerCase(Locale.ROOT);
        String ornamentType = firstNonBlank(getString(obj, "ornamentType"), getString(obj, "OrnamentType")).toLowerCase(Locale.ROOT);
        String name = firstNonBlank(getString(obj, "name"), getString(obj, "signalName")).toLowerCase(Locale.ROOT);
        String runningSound = firstNonBlank(
            getString(obj, "sound_Running"),
            getString(obj, "soundRunning"),
            getObject(obj, "model") == null ? null : getString(getObject(obj, "model"), "sound_Running"),
            getObject(obj, "model") == null ? null : getString(getObject(obj, "model"), "soundRunning")
        ).toLowerCase(Locale.ROOT);
        // モデル/レンダラのパスも分類材料に含める。masa 踏切パックの遮断機は
        // name/file が "MasaGate*"(=crossing を含まない)だが rendererPath が "MasaCrossingGate*.js"
        // なので、これを見ないと踏切でなく照明カテゴリに落ちて選択に出なくなる。
        JsonObject modelObjForCat = getObject(obj, "model");
        // getString はキーが無いと null を返すので null 安全に(以前は null.toLowerCase で NPE→分類失敗→
        // rendererPath/modelFile を持たない踏切がレッドストーンに反応しなくなっていた)。
        String rendererPathRaw = modelObjForCat == null ? null : getString(modelObjForCat, "rendererPath");
        String modelFileRaw = modelObjForCat == null ? null : getString(modelObjForCat, "modelFile");
        String rendererPath = rendererPathRaw == null ? "" : rendererPathRaw.toLowerCase(Locale.ROOT);
        String modelFile = modelFileRaw == null ? "" : modelFileRaw.toLowerCase(Locale.ROOT);
        boolean looksLikeCrossing = lowerFile.contains("crossing")
            || lowerFile.contains("fumikiri")
            || name.contains("crossing")
            || name.contains("fumikiri")
            || runningSound.contains("crossing")
            || runningSound.contains("fumikiri")
            || runningSound.contains("toryanse")
            || rendererPath.contains("crossing")
            || rendererPath.contains("fumikiri")
            || modelFile.contains("crossing")
            || modelFile.contains("fumikiri");
        boolean looksLikeSpeaker = lowerFile.contains("speaker")
            || name.contains("speaker")
            || machineType.contains("speaker");
        // 分類対象文字列(ファイル名+name+machineType)。RTM は設置物の大半が ModelMachine_ なので
        // プレフィックスでなくキーワードで種類を判定する。
        String hay = lowerFile + " " + name + " " + machineType;

        // 明示プレフィックスを最優先。
        //本家 ModelConnector_*.json の connectorType: "Input"/"Output" (入出力コネクタ)
        String connectorType = firstNonBlank(getString(obj, "connectorType"), getString(obj, "ConnectorType"));
        if (connectorType != null && !connectorType.isBlank()) {
            if (connectorType.equalsIgnoreCase("Input")) {
                return InstalledObjectCategory.CONNECTOR_INPUT;
            }
            if (connectorType.equalsIgnoreCase("Output")) {
                return InstalledObjectCategory.CONNECTOR_OUTPUT;
            }
        }
        if (lowerFile.startsWith("modelsignal_")) {
            return InstalledObjectCategory.SIGNAL;
        }
        if (lowerFile.startsWith("modelwire_")) {
            return InstalledObjectCategory.WIRE;
        }
        //本家 ModelOrnament_*.json は ornamentType (Lamp/Pole/Stair/Scaffold/Pipe/Plant) で種類が決まる。
        //移植済みは Lamp(蛍光灯) と Pole(架線柱) だけなので、それ以外は null で捨てる。
        //捨てないと汎用フォールバックの INSULATOR に落ちて、碍子の選択欄が植物や足場で埋まる。
        if (lowerFile.startsWith("modelornament_")) {
            if (ornamentType.equals("lamp") || containsAny(lowerFile, "fluorescent", "蛍光灯")) {
                return InstalledObjectCategory.FLUORESCENT;
            }
            if (ornamentType.equals("pole") || containsAny(lowerFile, "pole", "架線柱")) {
                return InstalledObjectCategory.OVERHEAD_LINE_POLE;
            }
            //本家 ModelOrnament_Pipe01 / Pipe01_Connectable (ornamentType="Pipe")
            if (ornamentType.equals("pipe") || containsAny(lowerFile, "pipe", "パイプ")) {
                return InstalledObjectCategory.PIPE;
            }
            return null;
        }
        // 踏切は改札より先に判定する(CrossingGate を "gate" で改札に誤分類しないため)。
        // 本家 RTM は machineType="Gate" (RTMResource.MACHINE_GATE = MACHINE.getSubType("Gate"),
        // 既定 "CrossingGate01L") をすべて踏切カテゴリに入れる。名前やファイル名に "crossing" を
        // 含まない踏切系設置物 (hi03 Train Melodies の ModelMachine_hi03ECDM-* など、machineType が
        // 素の "Gate" でメロディ音を鳴らす音響ブロック) が、キーワード判定を素通りして modelmachine_
        // フォールバックの LIGHT に落ち、踏切の選択画面に出てこなくなっていた。machineType の完全一致で
        // 拾えば本家と同じく踏切に並ぶ。改札(Turnstile)や券売機は machineType が別なので誤取り込みしない。
        if (lowerFile.startsWith("modelcrossing_") || looksLikeCrossing
                || machineType.equals("gate")
                || containsAny(hay, "crossing", "fumikiri", "踏切", "toryanse")) {
            return InstalledObjectCategory.CROSSING;
        }
        // 改札(Turnstile / TicketGate / 改札)。"gate" 単独では拾わない。
        if (containsAny(hay, "turnstile", "ticketgate", "ticket_gate", "ticketmachine",
                "kaisatsu", "改札", "automaticgate", "iccard")) {
            return InstalledObjectCategory.TICKET_GATE;
        }
        if (looksLikeSpeaker || containsAny(hay, "speaker", "スピーカ")) {
            return InstalledObjectCategory.SPEAKER;
        }
        //本家 ModelMachine_BumpingPost_Type2.json: machineType="BumpingPost" (車止め)
        if (machineType.equals("bumpingpost") || containsAny(hay, "bumpingpost", "bumping_post", "車止め")) {
            return InstalledObjectCategory.BUMPING_POST;
        }
        //本家 ModelMachine_Point01M/A.json: machineType="Point" (転轍機)。
        //"point" は一般語すぎるので machineType の完全一致かファイル名プレフィックスでしか拾わない。
        if (machineType.equals("point") || lowerFile.startsWith("modelmachine_point") || hay.contains("転轍")) {
            return InstalledObjectCategory.POINT;
        }
        //本家 ModelMachine_Vendor01/02.json: machineType="Vendor" (券売機)
        if (machineType.equals("vendor") || containsAny(hay, "ticketvendor", "ticket_vendor", "券売")) {
            return InstalledObjectCategory.TICKET_VENDOR;
        }
        if (containsAny(hay, "linepole", "line_pole", "catenarypole", "catenary_pole",
                "poleglay", "架線柱", "架線")) {
            return InstalledObjectCategory.OVERHEAD_LINE_POLE;
        }
        if (containsAny(hay, "signboard", "sign_board", "billboard", "看板")) {
            return InstalledObjectCategory.SIGNBOARD;
        }
        //本家 ATC 地上子: ModelMachine_ATC_01/02.json の machineType は "Antenna_Send" (検知器の送信側)。
        //レールに signal を書き込む。検知器 (Antenna_Receive) と紛れないよう先に判定する。
        if (machineType.equals("antenna_send") || lowerFile.startsWith("modelmachine_atc")
                || containsAny(hay, "antenna_send", "地上子")) {
            return InstalledObjectCategory.ATC;
        }
        //本家 列車検知器: ModelMachine_TrainDetector_01.json の machineType は "Antenna_Receive"。
        //照明の受け皿より先に判定しないと、検知器が照明カテゴリに落ちて選択に出てこない。
        if (containsAny(hay, "antenna_receive", "traindetector", "train_detector", "列車検知", "電車検知")) {
            return InstalledObjectCategory.TRAIN_DETECTOR;
        }
        // 照明系(明確なキーワードを持つものだけ)。これで照明カテゴリが何でも箱にならない。
        if (containsAny(hay, "light", "lamp", "lantern", "照明", "ライト", "beacon")) {
            return InstalledObjectCategory.LIGHT;
        }
        // それ以外の汎用 ModelMachine_(鳥居/モニタ/自販機等)は照明に置く(従来の落とし先)。
        if (lowerFile.startsWith("modelmachine_")) {
            return InstalledObjectCategory.LIGHT;
        }
        return InstalledObjectCategory.INSULATOR;
    }

    private static boolean containsAny(String hay, String... needles) {
        if (hay == null) return false;
        for (String n : needles) {
            if (hay.contains(n)) return true;
        }
        return false;
    }

    private static String normalize(String value) {
        return value.replace('\\', '/');
    }

    private static String leaf(String value) {
        int idx = value.lastIndexOf('/');
        return idx >= 0 ? value.substring(idx + 1) : value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static JsonObject getObject(JsonObject object, String key) {
        if (object == null || key == null || key.isBlank()) {
            return null;
        }
        return object.has(key) && object.get(key).isJsonObject() ? object.getAsJsonObject(key) : null;
    }

    private static String getString(JsonObject object, String key) {
        if (object == null || key == null || key.isBlank()) {
            return null;
        }
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : null;
    }

    private static boolean getBoolean(JsonObject object, String key, boolean fallback) {
        if (object == null || key == null || key.isBlank()) {
            return fallback;
        }
        if (!object.has(key)) {
            return fallback;
        }
        try {
            return object.get(key).getAsBoolean();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static double getDouble(JsonObject object, String key, double fallback) {
        if (object == null || key == null || key.isBlank()) {
            return fallback;
        }
        if (!object.has(key)) {
            return fallback;
        }
        try {
            return object.get(key).getAsDouble();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static float parseFloat(JsonObject object, String key, float fallback) {
        return (float) getDouble(object, key, fallback);
    }

    private static Vec3 parseVec3(JsonObject object, String key, double scale) {
        if (object == null || !object.has(key) || !object.get(key).isJsonArray()) {
            return Vec3.ZERO;
        }
        JsonArray array = object.getAsJsonArray(key);
        if (array.size() < 3) {
            return Vec3.ZERO;
        }
        try {
            return new Vec3(array.get(0).getAsDouble() * scale, array.get(1).getAsDouble() * scale, array.get(2).getAsDouble() * scale);
        } catch (Exception e) {
            return Vec3.ZERO;
        }
    }

    private static Map<String, String> parseTextures(JsonObject modelObj) {
        if (modelObj == null || !modelObj.has("textures") || !modelObj.get("textures").isJsonArray()) {
            return Map.of();
        }
        Map<String, String> textures = new HashMap<>();
        JsonArray array = modelObj.getAsJsonArray("textures");
        for (JsonElement element : array) {
            if (!element.isJsonArray()) {
                continue;
            }
            JsonArray pair = element.getAsJsonArray();
            if (pair.size() < 2) {
                continue;
            }
            String material = pair.get(0).getAsString();
            String texture = pair.get(1).getAsString();
            if (!material.isBlank() && !texture.isBlank()) {
                textures.put(material, encodeTextureDescriptor(pair));
            }
        }
        return textures;
    }

    private static List<String> parseRenderObjects(JsonObject... objects) {
        List<String> result = new ArrayList<>();
        for (JsonObject object : objects) {
            if (object == null || !object.has("objects") || !object.get("objects").isJsonArray()) {
                continue;
            }
            JsonArray array = object.getAsJsonArray("objects");
            for (JsonElement element : array) {
                if (!element.isJsonPrimitive()) {
                    continue;
                }
                String value = element.getAsString();
                if (value != null && !value.isBlank() && result.stream().noneMatch(value::equalsIgnoreCase)) {
                    result.add(value.trim());
                }
            }
        }
        return List.copyOf(result);
    }

    private static String encodeTextureDescriptor(JsonArray pair) {
        String texture = pair.get(1).getAsString();
        if (pair.size() < 3) {
            return texture;
        }
        List<String> flags = new ArrayList<>();
        for (int i = 2; i < pair.size(); i++) {
            JsonElement option = pair.get(i);
            if (option == null || !option.isJsonPrimitive()) {
                continue;
            }
            String value = option.getAsString();
            if (!value.isBlank()) {
                flags.add(value.trim());
            }
        }
        if (flags.isEmpty()) {
            return texture;
        }
        return texture + "|ptmeta=" + String.join(",", flags);
    }

    private static Map<Integer, List<String>> parseSignalLights(JsonObject obj) {
        if (!obj.has("lights") || !obj.get("lights").isJsonArray()) {
            return Map.of();
        }
        Map<Integer, List<String>> lights = new HashMap<>();
        JsonArray array = obj.getAsJsonArray("lights");
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive()) {
                continue;
            }
            String line = element.getAsString();
            Matcher stateMatcher = LIGHT_STATE_PATTERN.matcher(line);
            Matcher partsMatcher = LIGHT_PARTS_PATTERN.matcher(line);
            if (!stateMatcher.find() || !partsMatcher.find()) {
                continue;
            }
            int state = Integer.parseInt(stateMatcher.group(1));
            String[] parts = partsMatcher.group(1).trim().split("\\s+");
            List<String> groups = new ArrayList<>();
            for (String part : parts) {
                if (!part.isBlank()) {
                    groups.add(part);
                }
            }
            if (!groups.isEmpty()) {
                lights.put(state, List.copyOf(groups));
            }
        }
        return lights;
    }

    public static Path resolvePackPath(String packName) {
        return RailPackLoader.resolvePackPath(packName);
    }

    private record EntryData(String path, byte[] bytes) {
    }

    private record NestedArchive(String name, byte[] bytes) {
    }
}
