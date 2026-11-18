package com.portofino.realtrainmodunofficial.installedobject;

import com.portofino.realtrainmodunofficial.util.OrderedPackTasks;

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
        com.portofino.realtrainmodunofficial.cargo.CargoPackLoader.begin();
        com.portofino.realtrainmodunofficial.npc.NpcRegistry.begin();
        try(var tasks=OrderedPackTasks.begin()) {
            loadFromModJar();
            var active=com.portofino.realtrainmodunofficial.modelpack.transfer.ServerPackStorage.activeDirectory();
            if(active!=null && Files.isDirectory(active))loadArchiveDirectory(active);
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
        com.portofino.realtrainmodunofficial.cargo.CargoPackLoader.end();
        com.portofino.realtrainmodunofficial.npc.NpcRegistry.end();
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
            // RTM-Official-Assets (コネクタ Input01/Output01 等の本家デフ…
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
                            OrderedPackTasks.read(path,bytes->parse(normalize(path.getFileName().toString()),bytes,packName));
                        } catch (Exception e) {
                            RealTrainModUnofficial.LOGGER.warn("Failed to load built-in installed object definition {}", path.getFileName(), e);
                        }
                    });
            }
            // ★旗の既定 (Flag_*.json) は models/json ではなく textures/f…
            Path flagDir = modFile.findResource("assets", "minecraft", "textures", "flag");
            if (flagDir != null && Files.isDirectory(flagDir)) {
                try (var stream = Files.list(flagDir)) {
                    stream.filter(Files::isRegularFile)
                        .filter(p -> isSupportedJson(normalize(p.getFileName().toString())))
                        .forEach(path -> {
                            try {
                                OrderedPackTasks.read(path,bytes->parse(normalize(path.getFileName().toString()),bytes,packName));
                            } catch (Exception e) {
                                RealTrainModUnofficial.LOGGER.warn(
                                    "Failed to load built-in flag definition {}", path.getFileName(), e);
                            }
                        });
                }
            }

            // ★看板の既定 (SignBoard_*.json) は models/json ではなく text…
            Path signboardDir = modFile.findResource("assets", "minecraft", "textures", "signboard");
            if (signboardDir != null && Files.isDirectory(signboardDir)) {
                try (var stream = Files.list(signboardDir)) {
                    stream.filter(Files::isRegularFile)
                        .filter(p -> isSupportedJson(normalize(p.getFileName().toString())))
                        .forEach(path -> {
                            try {
                                OrderedPackTasks.read(path,bytes->parse(normalize(path.getFileName().toString()),bytes,packName));
                            } catch (Exception e) {
                                RealTrainModUnofficial.LOGGER.warn(
                                    "Failed to load built-in signboard definition {}", path.getFileName(), e);
                            }
                        });
                }
            }

            // 標識 (RRS) は JSON を持たず textures/rrs/*.png 自体が選択肢になる。
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
        // mods/ に置かれた自分自身の jar をパックとして読み直さない。
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

    /** エントリ名が Shift-JIS の zip (Windows 製の日本語名パック) は UTF-… */
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
                            OrderedPackTasks.read(path,bytes->parse(relative,bytes,packName));
                        } else if (isRrsTexture(relative)) {
                            registerRailroadSign(relative, packName);
                        }
                    } catch (Exception e) {
                        RealTrainModUnofficial.LOGGER.warn("Failed to parse installed object json {} in {}", path, packName, e);
                    }
                });
        }
    }

    // ---- 標識 (本家 RRS) ---- 本家の標識だけは他の設置物と違って JSON を持たな…

    private static boolean isRrsTexture(String path) {
        String lower = normalize(path).toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") && lower.contains("textures/rrs/");
    }

    
    private static void registerRailroadSign(String path, String packName) {
        String normalized = normalize(path);
        int index = normalized.toLowerCase(Locale.ROOT).indexOf("textures/rrs/");
        if (index < 0) {
            return;
        }
        // パックテクスチャの解決は "textures/..." からの相対パスで行う (MqoModelL…
        String texture = normalized.substring(index);
        String name = leaf(texture).replace(".png", "");
        String id = InstalledObjectCategory.RAILROAD_SIGN.name().toLowerCase(Locale.ROOT) + ":" + packName + ":" + name;
        OrderedPackTasks.publish(new InstalledObjectDefinition(
            id, name, packName, InstalledObjectCategory.RAILROAD_SIGN,
            // モデルは無い。板とポールは BER が直接描く (本家 RenderRailroadSign と同…
            "", "", texture, Map.of(),
            Vec3.ZERO, 1.0F, false,
            1.0F, 1.0F, 0.125F, texture, "", "",
            Map.of(), List.of(), Vec3.ZERO, 1, 0), LOADED::add);
    }

    /** コネクタ定義がパックから読めなかった場合の保険: 本家デフォルト (Input01/Output0… */
    private static void ensureDefaultConnectors() {
        boolean hasInput = LOADED.stream().anyMatch(d -> d.getCategory() == InstalledObjectCategory.CONNECTOR_INPUT);
        boolean hasOutput = LOADED.stream().anyMatch(d -> d.getCategory() == InstalledObjectCategory.CONNECTOR_OUTPUT);
        if (!hasInput) {
            OrderedPackTasks.publish(builtinConnector(InstalledObjectCategory.CONNECTOR_INPUT, "Input01", "textures/connector/input.png",
                    "textures/connector/button_Input01.png"), LOADED::add);
        }
        if (!hasOutput) {
            OrderedPackTasks.publish(builtinConnector(InstalledObjectCategory.CONNECTOR_OUTPUT, "Output01", "textures/connector/output.png",
                    "textures/connector/button_Output01.png"), LOADED::add);
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

    /** 本家 FlagConfig の移植。既定値は FlagConfig.init と同じ (resol… */
    private static void parseFlag(JsonObject obj, String packName, String file) {
        try {
            String texture = obj.get("texture").getAsString();
            float width = obj.has("width") ? obj.get("width").getAsFloat() : 1.0F;
            float height = obj.has("height") ? obj.get("height").getAsFloat() : 1.0F;
            int resolutionU = obj.has("resolutionU") ? obj.get("resolutionU").getAsInt() : 0;
            int resolutionV = obj.has("resolutionV") ? obj.get("resolutionV").getAsInt() : 0;
            float poleLength = obj.has("poleLength") ? obj.get("poleLength").getAsFloat() : 0.0F;
            if (resolutionU <= 0) {
                resolutionU = 24;
            }
            if (resolutionV <= 0) {
                resolutionV = 16;
            }
            if (poleLength <= 0.0F) {
                poleLength = 1.0F;
            }
            String name = file.endsWith(".json") ? file.substring(0, file.length() - 5) : file;
            String id = "flag:" + packName + ":" + name;
            java.util.Map<String, String> textures = new HashMap<>();
            textures.put("default", texture);
            InstalledObjectDefinition def = new InstalledObjectDefinition(
                    id, name, packName, InstalledObjectCategory.FLAG,
                    "", "", texture, textures,
                    Vec3.ZERO, 1.0F, false,
                    width, height, 0.0F, "", "", "",
                    Map.of(), List.of(), Vec3.ZERO, 1, 1);
            def.setFlagParams(new InstalledObjectDefinition.FlagParams(
                    texture, width, height, resolutionU, resolutionV, poleLength));
            OrderedPackTasks.publish(def, LOADED::add);
        } catch (Exception e) {
            com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.warn(
                    "[RTMU] 旗記述子を読めません: {} ({})", file, e.toString());
        }
    }

    private static boolean isSupportedJson(String path) {
        String file = leaf(path).toLowerCase(Locale.ROOT);
        return file.endsWith(".json") && (
            file.startsWith("modelmachine_")
            || file.startsWith("modelsignal_")
            || file.startsWith("modelconnector_")
            || file.startsWith("modelwire_")
            || file.startsWith("modelcrossing_")
            // 本家 ModelOrnament_ (蛍光灯/架線柱)。
            || file.startsWith("modelornament_")
            || file.startsWith("signboard_")
            // 貨物 (コンテナ / 火砲)。設置物ではないが、走査はこのローダーに相乗りしている。
            || file.startsWith("modelcontainer_")
            || file.startsWith("modelfirearm_")
            // 本家 ModelFlag_ / ModelMechanism_
            || file.startsWith("modelflag_")
            || file.startsWith("modelmechanism_")
            || file.startsWith("modelnpc_")
            // 本家の既定旗 (textures/flag/Flag_*.json)。モデルでなくテクスチャ記述子。
            || file.startsWith("flag_")
        );
    }

    private static boolean isArchiveName(String path) {
        String lower = normalize(path).toLowerCase(Locale.ROOT);
        return lower.endsWith(".zip") || lower.endsWith(".jar");
    }

    private static void parse(String path, byte[] bytes, String packName) {
        OrderedPackTasks.submit(()->parseNow(path,bytes,packName));
    }
    private static void parseNow(String path,byte[] bytes,String packName) {
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
            // 本家の旗記述子 (textures/flag/Flag_*.json)。
            if (lower.startsWith("flag_") && obj.has("texture") && !obj.has("model")) {
                parseFlag(obj, packName, file);
                return;
            }
            // 貨物 (コンテナ / 火砲) は設置物ではないので専用の受け皿へ渡す。
            if (com.portofino.realtrainmodunofficial.cargo.CargoPackLoader.tryParse(obj, lower, packName)) {
                return;
            }
            if (com.portofino.realtrainmodunofficial.npc.NpcRegistry.tryParse(obj, lower, packName)) {
                return;
            }

            InstalledObjectCategory category = categoryFor(obj, lower);
            if (category == null) {
                // 未対応の飾り(足場/階段/パイプ/植物)。登録しない。
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
            // 本家 ModelConfig.smoothing の既定値は false (JSON で明示された…
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
                // 点灯用テクスチャ (本家 SignalConfig.lightTexture / TextureS…
                (category == InstalledObjectCategory.SIGNAL || category == InstalledObjectCategory.LIGHT)
                    ? firstNonBlank(getString(obj, "lightTexture"), getString(obj, "emissiveTexture"))
                    : "",
                runningSound,
                // 照明(LIGHT)も信号と同じ "lights": ["S(1) P(部品名)"] 形式で発光パー…
                (category == InstalledObjectCategory.SIGNAL || category == InstalledObjectCategory.LIGHT)
                    ? parseSignalLights(obj) : Map.of(),
                parseRenderObjects(model, modelPartsBody),
                parseVec3(modelPartsBody, "pos", 1.0),
                1,
                1
            );
            if (category == InstalledObjectCategory.SIGNAL) {
                // 本家 SignalConfig.modelPartsFixture / rotateBody。
                JsonObject modelPartsFixture = getObject(obj, "modelPartsFixture");
                def.setSignalParts(
                    parseRenderObjects(modelPartsFixture),
                    parseVec3(modelPartsFixture, "pos", 1.0),
                    getBoolean(obj, "rotateBody", false));
            }
            if (category == InstalledObjectCategory.WIRE) {
                // ワイヤーは sectionLength(モデル1個分の長さ)と deflectionCoeffic…
                float sectionLength = parseFloat(obj, "sectionLength", 0.5F);
                float deflection = parseFloat(obj, "deflectionCoefficient", 0.0F);
                float lengthCoef = parseFloat(obj, "lengthCoefficient", 0.0F);
                def.setWireParams(sectionLength, deflection, lengthCoef);
            }
            // 本家 KaizPatchX の customIconTexture。
            def.setCustomIconTexture(firstNonBlank(getString(obj, "customIconTexture"),
                model == null ? null : getString(model, "customIconTexture")));
            def.setWireAttachPos(parseVec3(obj, "wirePos", 1.0));
            // 本家 connectorType ("Relay"/"Input"/"Output")。
            def.setSubType(firstNonBlank(getString(obj, "connectorType"), getString(obj, "ConnectorType")));
            // 本家 MachineConfig.rotateByMetadata。
            boolean rotByMeta = getBoolean(obj, "rotateByMetadata", false)
                || (model != null && getBoolean(model, "rotateByMetadata", false));
            def.setRotateByMetadata(rotByMeta);
            // 本家 MachineConfig.followRailAngle (ATC/列車検知器/車止め)。
            def.setFollowRailAngle(getBoolean(obj, "followRailAngle", false)
                || (model != null && getBoolean(model, "followRailAngle", false)));
            // 本家 ModelConfig.serverScriptPath。
            def.setServerScriptPath(getString(obj, "serverScriptPath"));
            // 本家 ModelConfig.defaultValues: DataMap の既定値 (スクリプト…
            if (obj.has("defaultValues") && obj.get("defaultValues").isJsonArray()) {
                for (JsonElement dve : obj.getAsJsonArray("defaultValues")) {
                    if (!dve.isJsonObject()) {
                        continue;
                    }
                    JsonObject dv = dve.getAsJsonObject();
                    String dvKey = getString(dv, "key");
                    if (dvKey.isBlank()) {
                        continue;
                    }
                    java.util.List<String> dvValues = new java.util.ArrayList<>();
                    if (dv.has("values") && dv.get("values").isJsonArray()) {
                        for (JsonElement v : dv.getAsJsonArray("values")) {
                            dvValues.add(v.getAsString());
                        }
                    }
                    def.getDefaultValues().add(new InstalledObjectDefinition.DefaultValue(
                        getString(dv, "type"), getString(dv, "elementType"), dvKey,
                        getString(dv, "value"), dvValues));
                }
            }
            // 本家 ModelConfig.customForm: 機械右クリックの DataMap 編集フォー…
            if (obj.has("customForm") && obj.get("customForm").isJsonObject()) {
                JsonObject form = obj.getAsJsonObject("customForm");
                java.util.List<InstalledObjectDefinition.FormField> fields = new java.util.ArrayList<>();
                if (form.has("fields") && form.get("fields").isJsonArray()) {
                    for (JsonElement fe : form.getAsJsonArray("fields")) {
                        if (!fe.isJsonObject()) {
                            continue;
                        }
                        JsonObject f = fe.getAsJsonObject();
                        String key = getString(f, "key");
                        if (key == null || key.isBlank()) {
                            continue;
                        }
                        fields.add(new InstalledObjectDefinition.FormField(
                            key, getString(f, "label"),
                            f.has("row") ? f.get("row").getAsInt() : 0,
                            f.has("column") ? f.get("column").getAsInt() : 0));
                    }
                }
                def.setCustomForm(new InstalledObjectDefinition.CustomForm(getString(form, "title"), fields));
            }
            // 本家 ModelConfig.doCulling (既定 false = 両面描画)。
            def.setDoCulling(getBoolean(obj, "doCulling", false));
            // 本家 OrnamentConfig.conveyorSpeed (足場/エスカレーターが乗った物を…
            def.setConveyorSpeed(getFloat(obj, "conveyorSpeed", 0.0F));
            OrderedPackTasks.publish(def, LOADED::add);
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
        // 本家 SignboardConfig.backTexture は初期化子が無く init でも補正…
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
        // 本家 SignboardConfig: アニメ周期 / 板の側面色 / 発光量。
        def.setSignboardParams(
            (int) getDouble(obj, "animationCycle", 1.0),
            (int) getDouble(obj, "color", 0.0),
            (int) getDouble(obj, "lightValue", 0.0)
        );
        OrderedPackTasks.publish(def, LOADED::add);
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

    /** JSON から設置物カテゴリを決める。 */
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
        // モデル/レンダラのパスも分類材料に含める。
        JsonObject modelObjForCat = getObject(obj, "model");
        // getString はキーが無いと null を返すので null 安全に(以前は null.to…
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
        // 分類対象文字列(ファイル名+name+machineType+modelFile+ornament…
        String hay = lowerFile + " " + name + " " + machineType + " " + modelFile + " " + ornamentType;

        // 明示プレフィックスを最優先。 本家 ModelConnector_*.json の connect…
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
        // 本家 ModelOrnament_*.json は ornamentType (Lamp/Pole…
        if (lowerFile.startsWith("modelornament_")) {
            // ★判定は lowerFile ではなく hay (modelFile/ornamentType を…
            if (ornamentType.equals("lamp") || containsAny(hay, "fluorescent", "蛍光灯")) {
                return InstalledObjectCategory.FLUORESCENT;
            }
            if (ornamentType.equals("pole") || containsAny(hay, "pole", "架線柱")) {
                return InstalledObjectCategory.OVERHEAD_LINE_POLE;
            }
            // 本家 ModelOrnament_Pipe01 / Pipe01_Connectable (orn…
            if (ornamentType.equals("pipe") || containsAny(hay, "pipe", "パイプ")) {
                return InstalledObjectCategory.PIPE;
            }
            // 本家 ornamentType の残り。以前は null で捨てていたので、同梱パックの Plan…
            if (ornamentType.equals("plant") || containsAny(hay, "plant", "植物")) {
                return InstalledObjectCategory.PLANT;
            }
            if (ornamentType.equals("stair") || containsAny(hay, "stair", "階段")) {
                return InstalledObjectCategory.STAIR;
            }
            if (ornamentType.equals("scaffold") || containsAny(hay, "scaffold", "足場")) {
                return InstalledObjectCategory.SCAFFOLD;
            }
            return null;
        }

        if (lowerFile.startsWith("modelflag_")) {
            return InstalledObjectCategory.FLAG;
        }
        if (lowerFile.startsWith("modelmechanism_")) {
            return InstalledObjectCategory.MECHANISM;
        }
        // 踏切は改札より先に判定する(CrossingGate を "gate" で改札に誤分類しないため)。
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
        // 本家 ModelMachine_BumpingPost_Type2.json: machineTy…
        if (machineType.equals("bumpingpost") || containsAny(hay, "bumpingpost", "bumping_post", "車止め")) {
            return InstalledObjectCategory.BUMPING_POST;
        }
        // 本家 ModelMachine_Point01M/A.json: machineType="Poi…
        if (machineType.equals("point") || lowerFile.startsWith("modelmachine_point") || hay.contains("転轍")) {
            return InstalledObjectCategory.POINT;
        }
        // 本家 ModelMachine_Vendor01/02.json: machineType="Ve…
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
        // 本家 ATC 地上子: ModelMachine_ATC_01/02.json の machine…
        if (machineType.equals("antenna_send") || lowerFile.startsWith("modelmachine_atc")
                || containsAny(hay, "antenna_send", "地上子")) {
            return InstalledObjectCategory.ATC;
        }
        // 本家 列車検知器: ModelMachine_TrainDetector_01.json の ma…
        if (containsAny(hay, "antenna_receive", "traindetector", "train_detector", "列車検知", "電車検知")) {
            return InstalledObjectCategory.TRAIN_DETECTOR;
        }
        // 照明系(明確なキーワードを持つものだけ)。これで照明カテゴリが何でも箱にならない。
        if (containsAny(hay, "light", "lamp", "lantern", "照明", "ライト", "beacon")) {
            return InstalledObjectCategory.LIGHT;
        }
        // ★安全網: 別名 JSON (ModelOrnament_ で始まらない) でも、中身 (mode…
        if (containsAny(hay, "scaffold", "足場")) {
            return InstalledObjectCategory.SCAFFOLD;
        }
        if (containsAny(hay, "stair", "階段")) {
            return InstalledObjectCategory.STAIR;
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

    private static float getFloat(JsonObject object, String key, float fallback) {
        try {
            return object.has(key) && object.get(key).isJsonPrimitive()
                ? object.get(key).getAsFloat() : fallback;
        } catch (Exception e) {
            return fallback;
        }
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
        return com.portofino.realtrainmodunofficial.modelpack.LegacyTextureDescriptor.encode(pair);
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
