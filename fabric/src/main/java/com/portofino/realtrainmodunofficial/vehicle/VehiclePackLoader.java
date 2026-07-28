package com.portofino.realtrainmodunofficial.vehicle;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.portofino.realtrainmodunofficial.BundledPackStore;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.rail.RailPackLoader;
import com.portofino.realtrainmodunofficial.util.PackTextDecoder;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class VehiclePackLoader {
    private static final List<VehicleDefinition> LOADED = new ArrayList<>();
    private static final String GENERATED_SOUND_PACK_DIR = "generated_sound_pack";
    private static boolean loaded = false;

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        LOADED.clear();
        loadFromModJar();
        loadFromExternalDirectories();
        loadFromGameDirectories();
        VehicleRegistry.setDefinitions(LOADED);
    }


    private static void loadFromModJar() {
        // 本家の既定モデルを全て同梱する方針にしたので、列車 (ModelTrain_*) も自動車
        // (ModelVehicle_*) も jar 内の assets/minecraft/models/json から読む。
        // (以前は「デフォルト列車/c-toki 等を出さない」という要望で列車だけ除外していた)
        try {
            var modFileEntry = ModList.get().getModFileById(RealTrainModUnofficial.MODID);
            if (modFileEntry == null) return;
            var modFile = modFileEntry.getFile();
            Path jsonDir = modFile.findResource("assets", "minecraft", "models", "json");
            if (jsonDir == null || !Files.isDirectory(jsonDir)) return;
            int[] trains = {0};
            int[] cars = {0};
            try (var stream = Files.list(jsonDir)) {
                stream.filter(Files::isRegularFile)
                    .forEach(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        if (!n.endsWith(".json")) {
                            return;
                        }
                        boolean isTrain = n.startsWith("modeltrain_");
                        boolean isCar = n.startsWith("modelvehicle_");
                        if (!isTrain && !isCar) {
                            return;
                        }
                        try {
                            parseTrainJson(Files.readAllBytes(p), RealTrainModUnofficial.MODID, p.getFileName().toString());
                            if (isTrain) {
                                trains[0]++;
                            } else {
                                cars[0]++;
                            }
                        } catch (Exception e) {
                            RealTrainModUnofficial.LOGGER.warn("Failed to load bundled vehicle {}", p, e);
                        }
                    });
            }
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("Could not load bundled car definitions", e);
        }
    }

    private static void loadFromExternalDirectories() {
        for (String dirName : new String[]{"vehicle_packs", "packs", ""}) {
            try {
                Path externalDir = FMLPaths.GAMEDIR.get().resolve("config").resolve("realtrainmodunofficial");
                if (!dirName.isEmpty()) externalDir = externalDir.resolve(dirName);
                if (Files.exists(externalDir)) scanPackRoot(externalDir);
            } catch (Exception e) {
                RealTrainModUnofficial.LOGGER.warn("Could not scan external vehicle packs {}", dirName, e);
            }
        }
    }

    private static void loadFromGameDirectories() {
        try {
            Path gameDir = FMLPaths.GAMEDIR.get();
            if (Files.exists(gameDir)) {
                scanPackRoot(gameDir);
                Path modsDir = gameDir.resolve("mods");
                if (Files.exists(modsDir)) {
                    scanPackRoot(modsDir);
                    // 整理用に mods/modelpacks/ へ置いたモデルパックも読み込む。
                    Path modelPacksDir = modsDir.resolve("modelpacks");
                    if (Files.exists(modelPacksDir)) scanPackRoot(modelPacksDir);
                }
            }
            Path contentDir = gameDir.resolve("content");
            if (Files.exists(contentDir)) scanPackRoot(contentDir);
            Path vehiclePacksDir = gameDir.resolve("vehicle_packs");
            if (Files.exists(vehiclePacksDir)) scanPackRoot(vehiclePacksDir);
            Path defaultAssetsDir = com.portofino.realtrainmodunofficial.DefaultAssetsFolder.get();
            if (Files.exists(defaultAssetsDir)) scanPackRoot(defaultAssetsDir);
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("Could not scan game directory for vehicle packs", e);
        }
    }

    private static void scanPackRoot(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return;
        try (var stream = Files.list(dir)) {
            stream.forEach(path -> {
                try {
                    if (isGeneratedSoundPack(path)) {
                        return;
                    }
                    // ユーザー要望により RTM 公式車両アセット (RTM-Official-Assets.zip 等) からは
                    // 車両定義をロードしない。 信号 / レール / 自動車などは別 loader が読む。
                    String lowerName = path.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (lowerName.contains("rtm-official-assets")) {
                        return;
                    }
                    // KaizPatchX-compat.zip は RTM 公式デフォルト車両 / c-toki / df200 /
                    //  /  / chiki7000 / MechaCentipede / RocketSled 等 40 種) の
                    // 定義を内包しており、これが車両選択にデフォルト車両として大量に残る原因。
                    // ユーザー要望「デフォルト車両を全部消す」に従い車両定義をロードしない。
                    if (lowerName.contains("kaizpatchx")) {
                        return;
                    }
                    // 自分自身の mod jar はスキップする。jar 内蔵の RTM デフォルト車両
                    // / c-toki /  等) は loadFromModJar で意図的に読み込まない
                    // 方針だが、mods/ を scanPackRoot が走査すると mod jar 自体がアーカイブとして
                    // 読まれ、バンドルされた models/json/ModelTrain_*.json が復活していた
                    // (ユーザー報告「や大井川 c-toki のデフォルト車両が残る」)。
                    if (lowerName.contains("realtrainmodunofficial")) {
                        return;
                    }
                    // README 同意ゲート: 未同意/拒否のパックはロードしない (タイトル画面で同意を取る)。
                    // zip のみ対象 (ディレクトリ展開パックは README 同梱の概念が無いので従来どおり)。
                    if (!Files.isDirectory(path)
                            && !com.portofino.realtrainmodunofficial.pack.PackConsent.isAllowed(path)) {
                        return;
                    }
                    if (Files.isDirectory(path)) {
                        if (looksLikeVehiclePackDirectory(path)) {
                            loadVehiclePackDirectory(path);
                        }
                    } else if (isSupportedArchive(path)) {
                        // 配布形式に依らず同じ入口で処理できるよう、archive としてまとめて扱う。
                        loadVehicleZip(path);
                    }
                } catch (Exception e) {
                    RealTrainModUnofficial.LOGGER.warn("Failed to scan vehicle pack {}", path.getFileName(), e);
                }
            });
        }
    }

    private static boolean looksLikeVehiclePackDirectory(Path dir) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        if (isGeneratedSoundPack(dir)) {
            return false;
        }
        if (Files.exists(dir.resolve("assets")) || Files.exists(dir.resolve("scripts")) || Files.exists(dir.resolve("textures"))) {
            return true;
        }
        try (var stream = Files.walk(dir, 4)) {
            return stream
                .filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString().toLowerCase(Locale.ROOT))
                .anyMatch(name -> name.endsWith(".json")
                    && (name.startsWith("modeltrain_")
                    || name.startsWith("train_")
                    || name.startsWith("modelvehicle_")
                    || name.startsWith("vehicle_")));
        } catch (IOException e) {
            RealTrainModUnofficial.LOGGER.warn("Could not inspect vehicle pack directory {}", dir.getFileName(), e);
            return false;
        }
    }

    /**
     * エントリ名が Shift-JIS の zip (Windows 製の日本語名パック) は UTF-8 では読めないので、
     * その場合だけ Shift-JIS で開き直す。
     */
    private static void loadVehicleZip(Path zipPath) throws Exception {
        String packName = zipPath.getFileName().toString();
        com.portofino.realtrainmodunofficial.util.PackZip.readWithFallback(
            () -> Files.newInputStream(zipPath), packName,
            (in, charset) -> loadVehiclePack(in, packName, 0, charset));
    }

    private static boolean isSupportedArchive(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(".zip") && !fileName.endsWith(".jar")) {
            return false;
        }
        return !isBundledModArchive(path, fileName);
    }

    private static boolean isBundledModArchive(Path path, String fileName) {
        if (fileName.startsWith(RealTrainModUnofficial.MODID.toLowerCase(Locale.ROOT) + "-") && fileName.endsWith(".jar")) {
            return true;
        }
        try {
            var modFile = ModList.get().getModFileById(RealTrainModUnofficial.MODID);
            if (modFile == null) {
                return false;
            }
            Path ownArchive = modFile.getFile().getFilePath();
            return ownArchive != null && Files.isSameFile(path, ownArchive);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isGeneratedSoundPack(Path path) {
        return path != null
            && path.getFileName() != null
            && GENERATED_SOUND_PACK_DIR.equalsIgnoreCase(path.getFileName().toString());
    }

    private static void loadVehiclePackDirectory(Path packDir) throws IOException {
        String packName = packDir.getFileName().toString();
        try (var stream = Files.walk(packDir)) {
            stream.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().toLowerCase().endsWith(".json"))
                .forEach(path -> {
                    try {
                        parseTrainJson(Files.readAllBytes(path), packName, normalize(packDir.relativize(path).toString()));
                    } catch (Exception e) {
                        RealTrainModUnofficial.LOGGER.warn("Failed to load vehicle pack json {} in {}", path, packName, e);
                    }
                });
        }
    }

    public static synchronized void reload() {
        loaded = false;
        load();
    }

    private static void loadVehiclePack(InputStream zipInput, String packName) throws IOException {
        loadVehiclePack(zipInput, packName, 0, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void loadVehiclePack(InputStream zipInput, String packName, int depth,
                                        java.nio.charset.Charset charset) throws IOException {
        List<byte[]> jsonBytes = new ArrayList<>();
        List<String> jsonPaths = new ArrayList<>();
        List<NestedArchive> nestedArchives = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(zipInput, charset)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    String name = normalize(entry.getName());
                    if (isTrainJson(name)) {
                        jsonBytes.add(zip.readAllBytes());
                        jsonPaths.add(name);
                    } else if (depth < 2 && isArchiveName(name)) {
                        nestedArchives.add(new NestedArchive(name, zip.readAllBytes()));
                    }
                }
                zip.closeEntry();
            }
        }
        for (int i = 0; i < jsonBytes.size(); i++) {
            parseTrainJson(jsonBytes.get(i), packName, jsonPaths.get(i));
        }
        for (NestedArchive nested : nestedArchives) {
            Path materialized = RailPackLoader.materializeNestedPack(nested.name(), nested.bytes());
            try (InputStream input = Files.newInputStream(materialized)) {
                loadVehiclePack(input, nested.name(), depth + 1, charset);
            }
        }
    }

    private static boolean isTrainJson(String path) {
        String normalized = normalize(path).toLowerCase(Locale.ROOT);
        if (!normalized.endsWith(".json")) {
            return false;
        }
        String leaf = normalized.substring(normalized.lastIndexOf('/') + 1);
        boolean rtmStyleName = leaf.startsWith("modeltrain_") || leaf.startsWith("train_");
        boolean rtmVehicleStyleName = leaf.startsWith("modelvehicle_") || leaf.startsWith("vehicle_");
        boolean rtmStyleDir = normalized.contains("/json/") || normalized.contains("/models/json/");
        return rtmStyleName || rtmVehicleStyleName || rtmStyleDir;
    }

    private static boolean isVehicleJsonPath(String path) {
        String normalized = normalize(path).toLowerCase(Locale.ROOT);
        String leaf = normalized.substring(normalized.lastIndexOf('/') + 1);
        return leaf.startsWith("modelvehicle_") || leaf.startsWith("vehicle_");
    }

    private static boolean isArchiveName(String path) {
        String lower = normalize(path).toLowerCase(Locale.ROOT);
        return lower.endsWith(".zip") || lower.endsWith(".jar");
    }

    private static String normalize(String raw) {
        return raw.replace('\\', '/');
    }

    private static void parseTrainJson(byte[] bytes, String packName, String sourcePath) {
        try {
            JsonElement el = JsonParser.parseString(PackTextDecoder.decodeJson(bytes));
            if (!el.isJsonObject()) return;
            JsonObject obj = el.getAsJsonObject();
            String id = firstNonBlank(getString(obj, "trainName"), getString(obj, "name"));
            if (id == null || id.isBlank()) id = fallbackTrainId(sourcePath);
            // 本家に displayName は無い。表示名は name / trainName から取る。
            String displayName = firstNonBlank(getString(obj, "name"), id);
            JsonObject trainModel = getObject(obj, "trainModel2");
            if (trainModel == null) trainModel = getObject(obj, "trainModel");
            // SuperRailBuilder3 などは "model" キーを使う。互換のため fallback。
            if (trainModel == null) trainModel = getObject(obj, "model");
            if (trainModel == null) return;
            String modelFile = getString(trainModel, "modelFile");
            if (modelFile == null || modelFile.isBlank()) return;
            String buttonTexture = firstNonBlank(getString(obj, "buttonTexture"), getString(trainModel, "buttonTexture"));
            Map<String, String> tex = parseTextures(trainModel);
            Vec3 offset = parseVec3(trainModel, "offset", 1.0 / 16.0);
            // 本家 ModelObject.render / RenderVehicleBase は ModelConfig (=JSON のルート) の
            // offset を ブロック単位でそのまま glTranslate する。trainModel2 側にしか
            // offset を見ていなかったので、ルートに書くパックで車体の高さが合わなかった
            // (mo1600 は "offset": [0, -1, 0] で、無視すると台車から 1 ブロック浮く)。
            if (offset.equals(Vec3.ZERO)) {
                offset = parseVec3(obj, "offset", 1.0);
            }
            float scale = parseFloat(trainModel, "scale", 1.0F);
            // .ngto (ボクセル) は本家が ModelConfig の scale をモデル自体に掛ける。
            // その scale は JSON のルートにあることが多い (mo1600 は 0.1)。
            if (scale == 1.0F && com.portofino.realtrainmodunofficial.client.model.NgtoModelGeometry.isNgto(modelFile)) {
                scale = parseFloat(obj, "scale", 1.0F);
            }
            String scriptPath = getString(trainModel, "rendererPath");
            if (scriptPath == null || scriptPath.isBlank()) {
                scriptPath = null;
            }
            if (scriptPath == null || scriptPath.isBlank()) {
                scriptPath = getString(obj, "rendererPath");
            }
            if (scriptPath == null || scriptPath.isBlank()) {
                scriptPath = null;
            }
            if (scriptPath == null || scriptPath.isBlank()) {
                scriptPath = null;
            }
            String soundScriptPath = firstNonBlank(
                getString(trainModel, "soundScriptPath"),
                getString(obj, "soundScriptPath")
            );
            boolean sourceLooksLikeVehicle = isVehicleJsonPath(sourcePath);
            String vehicleType = firstNonBlank(
                getString(trainModel, "vehicleType"),
                getString(obj, "vehicleType"),
                getString(trainModel, "trainType"),
                getString(obj, "trainType"),
                sourceLooksLikeVehicle ? "Car" : "Train"
            );

            // 本家に doorType は無い
            String doorType = null;
            if (doorType == null || doorType.isBlank()) {
                doorType = null;
            }

            List<Vec3> bogiePositions = new ArrayList<>();
            appendRawArray(trainModel, "bogiePos", bogiePositions);
            appendRawArray(obj, "bogiePos", bogiePositions);
            List<VehicleDefinition.BogieDefinition> bogies = new ArrayList<>();
            if (obj.has("bogieModel3") && obj.get("bogieModel3").isJsonArray()) {
                JsonArray arr = obj.getAsJsonArray("bogieModel3");
                for (int i = 0; i < arr.size(); i++) {
                    JsonElement element = arr.get(i);
                    if (!element.isJsonObject()) continue;
                    JsonObject bogieModel = element.getAsJsonObject();
                    String bogieFile = getString(bogieModel, "modelFile");
                    if (bogieFile == null || bogieFile.isBlank()) continue;
                    String bogieScript = getString(bogieModel, "rendererPath");
                    Map<String, String> bogieTex = parseTextures(bogieModel);
                    Vec3 position = i < bogiePositions.size() ? bogiePositions.get(i) : Vec3.ZERO;
                    bogies.add(new VehicleDefinition.BogieDefinition(bogieFile, bogieTex, position, bogieScript));
                }
            } else {
                JsonObject bogieModel = getObject(obj, "bogieModel2");
                if (bogieModel == null) bogieModel = getObject(obj, "bogieModel");
                Map<String, String> bogieTex = bogieModel != null ? parseTextures(bogieModel) : Map.of();
                String bogieFile = bogieModel != null ? getString(bogieModel, "modelFile") : null;
                String bogieScript = bogieModel != null ? getString(bogieModel, "rendererPath") : "";
                if (bogieFile != null && !bogieFile.isBlank()) {
                    if (bogiePositions.isEmpty()) {
                        bogiePositions.add(new Vec3(0.0, 0.0, 0.0));
                    }
                    for (Vec3 p : bogiePositions) {
                        bogies.add(new VehicleDefinition.BogieDefinition(bogieFile, bogieTex, p, bogieScript));
                    }
                }
            }
            List<VehicleDefinition.SeatMarker> seatMarkers = new ArrayList<>();
            appendDriverSeatArray(trainModel, "playerPos", seatMarkers);
            appendDriverSeatArray(obj, "playerPos", seatMarkers);
            appendDriverSeatArray(trainModel, "playerPosF", seatMarkers);
            appendDriverSeatArray(obj, "playerPosF", seatMarkers);
            appendTypedSeatArray(trainModel, "seatPos", seatMarkers, 1.0D / 16.0D);
            appendTypedSeatArray(obj, "seatPos", seatMarkers, 1.0D / 16.0D);
            appendTypedSeatArray(trainModel, "seatPosF", seatMarkers, 1.0D);
            appendTypedSeatArray(obj, "seatPosF", seatMarkers, 1.0D);
            List<VehicleDefinition.SeatMarker> rideableSeatMarkers = new ArrayList<>();
            for (VehicleDefinition.SeatMarker seatMarker : seatMarkers) {
                if (seatMarker.isRideable()) {
                    rideableSeatMarkers.add(seatMarker);
                }
            }

            // seatPos: integer values in 1/16-block units → divide by 16
            List<Vec3> seats = new ArrayList<>();
            appendSeatArray(trainModel, "seatPos", seats);
            appendSeatArray(obj, "seatPos", seats);
            // seatPosF: float values already in block units → no division
            appendRawArray(trainModel, "seatPosF", seats);
            appendRawArray(obj, "seatPosF", seats);

            // playerPos / playerPosF: float values already in block units → no division
            List<Vec3> playerPositions = new ArrayList<>();
            appendRawArray(trainModel, "playerPos", playerPositions);
            appendRawArray(obj, "playerPos", playerPositions);
            appendRawArray(trainModel, "playerPosF", playerPositions);
            appendRawArray(obj, "playerPosF", playerPositions);

            Vec3 seatOffset = !playerPositions.isEmpty() ? playerPositions.get(0) : (!seats.isEmpty() ? seats.get(0) : null);
            float trainDistance = parseFloat(trainModel, "trainDistance", parseFloat(obj, "trainDistance", 4.5F));
            // 本家に driverSeatIndex は無い (運転席は playerPos から決まる)
            int driverSeatIndex = 0;
            int frontDriverSeatIndex = resolveFrontDriverSeatIndex(obj, trainModel, rideableSeatMarkers, driverSeatIndex);
            int rearDriverSeatIndex = resolveRearDriverSeatIndex(obj, trainModel, rideableSeatMarkers, frontDriverSeatIndex);
            List<VehicleDefinition.DoorAnimationDefinition> leftDoors = parseDoorAnimations(obj, trainModel, "door_left");
            List<VehicleDefinition.DoorAnimationDefinition> rightDoors = parseDoorAnimations(obj, trainModel, "door_right");
            // ★速度性能は JSON から読まない。運転席GUI (ドアタブ) で車両ごとに設定する。
            // 空リスト = 既定値 (EntityTrainBase / TrainConfig.init の 0.36〜1.80) を使う。
            List<Float> notchMaxSpeeds = List.of();
            List<String> rollsignNames = parseStringList(obj, trainModel, "rollsignNames");
            List<String> customButtonNames = parseCustomButtonNames(obj, trainModel);
            List<List<String>> customButtonOptions = parseCustomButtonOptions(obj, trainModel);
            String rollsignTexture = firstNonBlank(getString(trainModel, "rollsignTexture"), getString(obj, "rollsignTexture"));
            List<VehicleDefinition.RollsignDefinition> rollsigns = parseRollsigns(obj, trainModel);
            // RTMU 追加: 種別幕 (方向幕と同じ書式・別項目)。typeSignNames / typeSignTexture / typeSigns。
            List<String> typeSignNames = parseStringList(obj, trainModel, "typeSignNames");
            // 本家に typeSignTexture は無い
            String typeSignTexture = null;
            List<VehicleDefinition.RollsignDefinition> typeSigns = parseSignPanels(obj, trainModel, "typeSigns");
            List<VehicleDefinition.LightDefinition> headLights = parseLights(obj, trainModel, "headLights");
            List<VehicleDefinition.LightDefinition> tailLights = parseLights(obj, trainModel, "tailLights");
            List<VehicleDefinition.LightDefinition> interiorLights = parseLights(obj, trainModel, "interiorLights");
            String hornSound = firstNonBlank(getString(trainModel, "sound_Horn"), getString(obj, "sound_Horn"));
            String soundStop = firstNonBlank(getString(trainModel, "sound_Stop"), getString(obj, "sound_Stop"));
            String soundStartAcceleration = firstNonBlank(getString(trainModel, "sound_S_A"), getString(obj, "sound_S_A"));
            String soundAcceleration = firstNonBlank(getString(trainModel, "sound_Acceleration"), getString(obj, "sound_Acceleration"));
            String soundDeceleration = firstNonBlank(getString(trainModel, "sound_Deceleration"), getString(obj, "sound_Deceleration"));
            String soundDecelerationStop = firstNonBlank(getString(trainModel, "sound_D_S"), getString(obj, "sound_D_S"));
            String soundBrakeRelease = firstNonBlank(getString(trainModel, "sound_BrakeRelease"), getString(obj, "sound_BrakeRelease"));
            String soundBrakeRelease2 = firstNonBlank(getString(trainModel, "sound_BrakeRelease2"), getString(obj, "sound_BrakeRelease2"));
            String soundDoorOpen = firstNonBlank(getString(trainModel, "sound_DoorOpen"), getString(obj, "sound_DoorOpen"));
            String soundDoorClose = firstNonBlank(getString(trainModel, "sound_DoorClose"), getString(obj, "sound_DoorClose"));
            List<String> announcementSounds = parseAnnouncementSounds(obj, trainModel);
            // ★加速度も JSON から読まない (上に同じ)。0 = 既定値を使う。
            float acceleration = 0.0F;
            boolean smoothing = parseBoolean(trainModel, "smoothing", parseBoolean(obj, "smoothing", false));
            boolean doCulling = parseBoolean(trainModel, "doCulling", parseBoolean(obj, "doCulling", false));
            boolean hasConfiguredLights = !headLights.isEmpty() || !tailLights.isEmpty() || !interiorLights.isEmpty();
            boolean renderLight = parseBoolean(trainModel, "renderLight", parseBoolean(obj, "renderLight", hasConfiguredLights));
            boolean notDisplayCab = parseBoolean(trainModel, "notDisplayCab", parseBoolean(obj, "notDisplayCab", false));
            boolean singleTrain = parseBoolean(trainModel, "isSingleTrain", parseBoolean(obj, "isSingleTrain", false));

            String serverScriptPath = firstNonBlank(
                getString(trainModel, "serverScriptPath"),
                getString(obj, "serverScriptPath")
            );
            VehicleDefinition definition = new VehicleDefinition(
                id,
                displayName,
                packName,
                modelFile,
                buttonTexture,
                tex,
                offset,
                scale,
                bogies,
                rideableSeatMarkers,
                seats,
                playerPositions,
                seatOffset,
                scriptPath,
                soundScriptPath,
                vehicleType,
                doorType,
                trainDistance,
                driverSeatIndex,
                frontDriverSeatIndex,
                rearDriverSeatIndex,
                leftDoors,
                rightDoors,
                notchMaxSpeeds,
                acceleration,
                smoothing,
                rollsignNames,
                customButtonNames,
                customButtonOptions,
                rollsignTexture,
                rollsigns,
                headLights,
                tailLights,
                interiorLights,
                hornSound,
                announcementSounds,
                doCulling,
                renderLight,
                notDisplayCab,
                singleTrain
            );
            definition.setServerScriptPath(serverScriptPath);
            // 本家 KaizPatchX の customIconTexture。持ち物欄でのアイテムの絵を差し替える。
            // 車両 (自動車) も設置物と同じく「1 つのアイテムで中身を選ぶ」ので同じ扱いにする。
            definition.setCustomIconTexture(firstNonBlank(getString(obj, "customIconTexture"),
                getString(trainModel, "customIconTexture")));
            // 本家 sound_Announcement の表示名 ([[名前, 音]] の名前側)
            definition.setAnnouncementNames(parseAnnouncementNames(obj, trainModel));
            definition.setSlotPositions(parseSlotPositions(trainModel, obj));
            definition.setJsonRunningSounds(
                soundStop,
                soundStartAcceleration,
                soundAcceleration,
                soundDeceleration,
                soundDecelerationStop
            );
            definition.setBrakeReleaseSounds(soundBrakeRelease, soundBrakeRelease2);
            definition.setDoorSounds(soundDoorOpen, soundDoorClose);
            definition.setTypeSign(typeSignNames, typeSignTexture, typeSigns);
            LOADED.add(definition);
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("Failed to parse train json {} in {}: {}", sourcePath, packName, e.getMessage());
        }
    }

    /**
     * 本家 slotPos: [[x, y, z, seatType], ...] (ブロック単位、seatType 省略時は 2=座席)。
     * trainModel2 側と JSON ルートの両方を読む。
     */
    private static java.util.List<float[]> parseSlotPositions(JsonObject trainModel, JsonObject obj) {
        java.util.List<float[]> out = new ArrayList<>();
        for (JsonObject src : new JsonObject[]{trainModel, obj}) {
            if (src == null || !src.has("slotPos")) continue;
            try {
                for (var el : src.getAsJsonArray("slotPos")) {
                    var arr = el.getAsJsonArray();
                    if (arr.size() < 3) continue;
                    float type = arr.size() >= 4 ? arr.get(3).getAsFloat() : 2.0F;
                    out.add(new float[]{
                            arr.get(0).getAsFloat(),
                            arr.get(1).getAsFloat(),
                            arr.get(2).getAsFloat(),
                            type});
                }
            } catch (Exception ignored) {
            }
            if (!out.isEmpty()) break;
        }
        return out;
    }

    private static String fallbackTrainId(String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank()) {
            return "train";
        }
        String normalized = normalize(sourcePath);
        String leaf = normalized.substring(normalized.lastIndexOf('/') + 1);
        int dot = leaf.lastIndexOf('.');
        return dot > 0 ? leaf.substring(0, dot) : leaf;
    }

    private static int resolveFrontDriverSeatIndex(JsonObject root, JsonObject trainModel, List<VehicleDefinition.SeatMarker> seats, int fallback) {
        int configured = Integer.MIN_VALUE;
        if (configured != Integer.MIN_VALUE) {
            return configured;
        }

        int configuredDriverSeat = Integer.MIN_VALUE;
        if (configuredDriverSeat != Integer.MIN_VALUE) {
            return configuredDriverSeat;
        }

        if (seats.isEmpty()) {
            return fallback;
        }

        int driverExtreme = findExtremeDriverSeatIndexByZ(seats, true);
        if (driverExtreme >= 0) {
            return driverExtreme;
        }

        return findExtremeSeatIndexByZ(seats, true);
    }

    private static int resolveRearDriverSeatIndex(JsonObject root, JsonObject trainModel, List<VehicleDefinition.SeatMarker> seats, int fallbackFrontIndex) {
        int configured = Integer.MIN_VALUE;
        if (configured != Integer.MIN_VALUE) {
            return configured;
        }

        if (seats.isEmpty()) {
            return fallbackFrontIndex;
        }

        int driverExtreme = findExtremeDriverSeatIndexByZ(seats, false);
        if (driverExtreme >= 0) {
            return driverExtreme;
        }

        return findExtremeSeatIndexByZ(seats, false);
    }

    private static int findExtremeSeatIndexByZ(List<VehicleDefinition.SeatMarker> seats, boolean front) {
        if (seats.isEmpty()) {
            return 0;
        }

        int bestIndex = 0;
        double bestZ = seats.get(0).position().z;
        for (int i = 1; i < seats.size(); i++) {
            double z = seats.get(i).position().z;
            if (front ? z > bestZ : z < bestZ) {
                bestZ = z;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private static int findExtremeDriverSeatIndexByZ(List<VehicleDefinition.SeatMarker> seats, boolean front) {
        int bestIndex = -1;
        double bestZ = 0.0D;
        for (int i = 0; i < seats.size(); i++) {
            VehicleDefinition.SeatMarker seat = seats.get(i);
            if (!seat.driverCab()) {
                continue;
            }
            double z = seat.position().z;
            if (bestIndex < 0 || (front ? z > bestZ : z < bestZ)) {
                bestIndex = i;
                bestZ = z;
            }
        }
        return bestIndex;
    }

    private static List<VehicleDefinition.DoorAnimationDefinition> parseDoorAnimations(JsonObject root, JsonObject trainModel, String key) {
        List<VehicleDefinition.DoorAnimationDefinition> doors = new ArrayList<>();
        appendDoorAnimations(trainModel, key, doors);
        appendDoorAnimations(root, key, doors);
        return doors;
    }

    private static void appendDoorAnimations(JsonObject obj, String key, List<VehicleDefinition.DoorAnimationDefinition> out) {
        if (obj == null || !obj.has(key) || !obj.get(key).isJsonArray()) return;
        appendVehicleParts(obj.getAsJsonArray(key), out);
    }

    /**
     * 本家 BasicVehiclePartsRenderer.getParts と同じ読み取り。
     * childParts を再帰で辿り、transform は全要素を並び順のまま保持する。
     */
    private static void appendVehicleParts(JsonArray array, List<VehicleDefinition.DoorAnimationDefinition> out) {
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject door = element.getAsJsonObject();
            List<String> objects = new ArrayList<>();
            if (door.has("objects") && door.get("objects").isJsonArray()) {
                for (JsonElement objectElement : door.getAsJsonArray("objects")) {
                    if (objectElement.isJsonPrimitive()) {
                        String name = objectElement.getAsString();
                        if (!name.isBlank()) objects.add(name);
                    }
                }
            }
            List<VehicleDefinition.DoorAnimationDefinition> children = new ArrayList<>();
            if (door.has("childParts") && door.get("childParts").isJsonArray()) {
                appendVehicleParts(door.getAsJsonArray("childParts"), children);
            }
            if (objects.isEmpty() && children.isEmpty()) continue;
            Vec3 pos = parseVec3(door, "pos", 1.0D);
            List<float[]> transforms = parseVehiclePartsTransforms(door);
            out.add(new VehicleDefinition.DoorAnimationDefinition(
                    objects, pos, firstTranslation(transforms), transforms, children));
        }
    }

    /**
     * 本家 VehicleParts.transform: 要素数 3 = 平行移動、4 = 回転。
     * 長さをそのまま保持する — 4 要素を平行移動として読むと
     * {angle, vecX, vecY, vecZ} が translate(angle, vecX, vecY) になり、
     * 回転式のドア (プラグドア・バス扉) が明後日の方向へ飛ぶ。
     */
    private static List<float[]> parseVehiclePartsTransforms(JsonObject door) {
        if (door == null || !door.has("transform") || !door.get("transform").isJsonArray()) {
            return List.of();
        }
        List<float[]> out = new ArrayList<>();
        for (JsonElement element : door.getAsJsonArray("transform")) {
            if (!element.isJsonArray()) continue;
            JsonArray entry = element.getAsJsonArray();
            if (entry.size() != 3 && entry.size() != 4) continue;
            float[] values = new float[entry.size()];
            try {
                for (int i = 0; i < values.length; i++) {
                    values[i] = entry.get(i).getAsFloat();
                }
            } catch (Exception e) {
                continue;
            }
            out.add(values);
        }
        return out;
    }

    /** 最初の平行移動 (ドア位置の推定用)。回転しかない部品は ZERO。 */
    private static Vec3 firstTranslation(List<float[]> transforms) {
        for (float[] t : transforms) {
            if (t.length == 3) {
                return new Vec3(t[0], t[1], t[2]);
            }
        }
        return Vec3.ZERO;
    }


    private static List<Float> parseFloatList(JsonObject root, JsonObject trainModel, String key) {
        JsonArray array = null;
        if (trainModel != null && trainModel.has(key) && trainModel.get(key).isJsonArray()) {
            array = trainModel.getAsJsonArray(key);
        } else if (root != null && root.has(key) && root.get(key).isJsonArray()) {
            array = root.getAsJsonArray(key);
        }
        if (array == null) {
            return List.of();
        }
        List<Float> values = new ArrayList<>();
        for (JsonElement element : array) {
            try {
                values.add(element.getAsFloat());
            } catch (Exception ignored) {
            }
        }
        return values;
    }

    private static List<String> parseStringList(JsonObject root, JsonObject trainModel, String key) {
        JsonArray array = null;
        if (trainModel != null && trainModel.has(key) && trainModel.get(key).isJsonArray()) {
            array = trainModel.getAsJsonArray(key);
        } else if (root != null && root.has(key) && root.get(key).isJsonArray()) {
            array = root.getAsJsonArray(key);
        }
        if (array == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonElement element : array) {
            try {
                // ★空文字コマも保持する。
                // (=生の JSON 配列長。末尾やコマ間の "" も1コマとして数える) でテクスチャを縦分割する。
                if (element != null && element.isJsonPrimitive()) {
                    values.add(element.getAsString());
                }
            } catch (Exception ignored) {
            }
        }
        return values;
    }

    private static List<String> parseCustomButtonNames(JsonObject root, JsonObject trainModel) {
        List<List<String>> options = parseCustomButtonOptions(root, trainModel);
        if (!options.isEmpty()) {
            List<String> labels = new ArrayList<>();
            for (List<String> optionList : options) {
                labels.add(optionList.isEmpty() ? "" : optionList.get(0));
            }
            return labels;
        }
        List<String> values = parseNamedButtonList(trainModel, "customButtonNames");
        if (!values.isEmpty()) {
            return values;
        }
        values = parseNamedButtonList(root, "customButtonNames");
        if (!values.isEmpty()) {
            return values;
        }
        values = parseNamedButtonList(trainModel, "buttonNames");
        if (!values.isEmpty()) {
            return values;
        }
        values = parseNamedButtonList(root, "buttonNames");
        if (!values.isEmpty()) {
            return values;
        }
        values = parseNamedButtonList(trainModel, "customButtons");
        if (!values.isEmpty()) {
            return values;
        }
        values = parseNamedButtonList(root, "customButtons");
        if (!values.isEmpty()) {
            return values;
        }
        values = parseNamedButtonList(trainModel, "buttons");
        if (!values.isEmpty()) {
            return values;
        }
        return parseNamedButtonList(root, "buttons");
    }

    private static List<List<String>> parseCustomButtonOptions(JsonObject root, JsonObject trainModel) {
        List<List<String>> values = parseButtonOptionGrid(trainModel, "customButtons");
        if (!values.isEmpty()) {
            return values;
        }
        return parseButtonOptionGrid(root, "customButtons");
    }

    private static List<List<String>> parseButtonOptionGrid(JsonObject source, String key) {
        if (source == null || !source.has(key) || !source.get(key).isJsonArray()) {
            return List.of();
        }
        List<List<String>> result = new ArrayList<>();
        for (JsonElement element : source.getAsJsonArray(key)) {
            if (!element.isJsonArray()) {
                continue;
            }
            List<String> values = new ArrayList<>();
            for (JsonElement option : element.getAsJsonArray()) {
                try {
                    String text = option.getAsString();
                    if (!text.isBlank()) {
                        values.add(text);
                    }
                } catch (Exception ignored) {
                }
            }
            if (!values.isEmpty()) {
                result.add(values);
            }
        }
        return result;
    }

    private static List<String> parseNamedButtonList(JsonObject source, String key) {
        if (source == null || !source.has(key) || !source.get(key).isJsonArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        collectNamedButtonValues(source.getAsJsonArray(key), values);
        return values;
    }

    private static void collectNamedButtonValues(JsonArray array, List<String> values) {
        for (JsonElement element : array) {
            collectNamedButtonValue(element, values);
        }
    }

    private static void collectNamedButtonValue(JsonElement element, List<String> values) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        try {
            if (element.isJsonPrimitive()) {
                String value = element.getAsString();
                if (value != null && !value.isBlank()) {
                    values.add(value);
                }
                return;
            }
            if (element.isJsonArray()) {
                collectNamedButtonValues(element.getAsJsonArray(), values);
                return;
            }
            if (element.isJsonObject()) {
                JsonObject button = element.getAsJsonObject();
                // 本家のボタン定義は name のみ
                String value = getString(button, "name");
                if (value != null && !value.isBlank()) {
                    values.add(value);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static List<VehicleDefinition.RollsignDefinition> parseRollsigns(JsonObject root, JsonObject trainModel) {
        return parseSignPanels(root, trainModel, "rollsigns");
    }

    /** rollsigns / typeSigns など、uv+pos で定義された幕パネル配列を読む (種別幕でも流用)。 */
    private static List<VehicleDefinition.RollsignDefinition> parseSignPanels(JsonObject root, JsonObject trainModel, String key) {
        JsonArray array = null;
        if (trainModel != null && trainModel.has(key) && trainModel.get(key).isJsonArray()) {
            array = trainModel.getAsJsonArray(key);
        } else if (root != null && root.has(key) && root.get(key).isJsonArray()) {
            array = root.getAsJsonArray(key);
        }
        if (array == null) {
            return List.of();
        }
        List<VehicleDefinition.RollsignDefinition> values = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject rollsign = element.getAsJsonObject();
            float[] uv = parseFloatArray(rollsign.getAsJsonArray("uv"), 4);
            float[][][] pos = parseRollsignPositions(rollsign.getAsJsonArray("pos"));
            if (uv.length < 4 || pos.length == 0) {
                continue;
            }
            values.add(new VehicleDefinition.RollsignDefinition(
                uv,
                pos,
                parseBoolean(rollsign, "doAnimation", false),
                parseBoolean(rollsign, "disableLighting", false)
            ));
        }
        return values;
    }

    private static List<VehicleDefinition.LightDefinition> parseLights(JsonObject root, JsonObject trainModel, String key) {
        JsonArray array = null;
        if (trainModel != null && trainModel.has(key) && trainModel.get(key).isJsonArray()) {
            array = trainModel.getAsJsonArray(key);
        } else if (root != null && root.has(key) && root.get(key).isJsonArray()) {
            array = root.getAsJsonArray(key);
        }
        if (array == null) {
            return List.of();
        }
        List<VehicleDefinition.LightDefinition> values = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject light = element.getAsJsonObject();
            float[] pos = parseFloatArray(light.getAsJsonArray("pos"), 3);
            if (pos.length < 3) {
                continue;
            }
            values.add(new VehicleDefinition.LightDefinition(
                (byte) parseInt(light, "type", 0),
                parseInt(light, "color", 0xFFFFFF),
                new Vec3(pos[0], pos[1], pos[2]),
                parseFloat(light, "r", 0.25F),
                parseBoolean(light, "reverse", false)
            ));
        }
        return values;
    }

    private static float[][][] parseRollsignPositions(JsonArray array) {
        if (array == null) {
            return new float[0][][];
        }
        List<float[][]> quads = new ArrayList<>();
        for (JsonElement quadElement : array) {
            if (!quadElement.isJsonArray()) {
                continue;
            }
            JsonArray quadArray = quadElement.getAsJsonArray();
            if (quadArray.size() < 4) {
                continue;
            }
            float[][] quad = new float[4][3];
            boolean valid = true;
            for (int i = 0; i < 4; i++) {
                if (!quadArray.get(i).isJsonArray()) {
                    valid = false;
                    break;
                }
                float[] point = parseFloatArray(quadArray.get(i).getAsJsonArray(), 3);
                if (point.length < 3) {
                    valid = false;
                    break;
                }
                quad[i] = point;
            }
            if (valid) {
                quads.add(quad);
            }
        }
        return quads.toArray(float[][][]::new);
    }

    private static float[] parseFloatArray(JsonArray array, int minLength) {
        if (array == null) {
            return new float[0];
        }
        float[] values = new float[array.size()];
        for (int i = 0; i < array.size(); i++) {
            try {
                values[i] = array.get(i).getAsFloat();
            } catch (Exception e) {
                values[i] = 0.0F;
            }
        }
        return values.length < minLength ? new float[0] : values;
    }

    /** seatPos: integer values in 1/16-block units, divide by 16 */
    private static void appendSeatArray(JsonObject obj, String key, List<Vec3> out) {
        if (!obj.has(key) || !obj.get(key).isJsonArray()) return;
        for (JsonElement e : obj.getAsJsonArray(key)) {
            if (!e.isJsonArray()) continue;
            JsonArray a = e.getAsJsonArray();
            if (a.size() < 3) continue;
            out.add(new Vec3(a.get(0).getAsDouble() / 16.0, a.get(1).getAsDouble() / 16.0, a.get(2).getAsDouble() / 16.0));
        }
    }

    private static void appendTypedSeatArray(JsonObject obj, String key, List<VehicleDefinition.SeatMarker> out, double scale) {
        if (obj == null || !obj.has(key) || !obj.get(key).isJsonArray()) return;
        for (JsonElement e : obj.getAsJsonArray(key)) {
            if (!e.isJsonArray()) continue;
            JsonArray a = e.getAsJsonArray();
            if (a.size() < 3) continue;
            int type = a.size() >= 4 ? a.get(3).getAsInt() : 1;
            out.add(new VehicleDefinition.SeatMarker(
                new Vec3(a.get(0).getAsDouble() * scale, a.get(1).getAsDouble() * scale, a.get(2).getAsDouble() * scale),
                type,
                false
            ));
        }
    }

    private static void appendDriverSeatArray(JsonObject obj, String key, List<VehicleDefinition.SeatMarker> out) {
        if (obj == null || !obj.has(key) || !obj.get(key).isJsonArray()) return;
        for (JsonElement e : obj.getAsJsonArray(key)) {
            if (!e.isJsonArray()) continue;
            JsonArray a = e.getAsJsonArray();
            if (a.size() < 3) continue;
            out.add(new VehicleDefinition.SeatMarker(
                new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()),
                VehicleDefinition.SEAT_TYPE_DRIVER_CAB,
                true
            ));
        }
    }

    /** playerPos / seatPosF / bogiePos: float values already in block units, no division */
    private static void appendRawArray(JsonObject obj, String key, List<Vec3> out) {
        if (obj == null || !obj.has(key) || !obj.get(key).isJsonArray()) return;
        for (JsonElement e : obj.getAsJsonArray(key)) {
            if (!e.isJsonArray()) continue;
            JsonArray a = e.getAsJsonArray();
            if (a.size() < 3) continue;
            out.add(new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()));
        }
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

    private static List<String> parseAnnouncementSounds(JsonObject obj, JsonObject trainModel) {
        List<String> sounds = new ArrayList<>();
        appendAnnouncementSounds(trainModel, sounds);
        appendAnnouncementSounds(obj, sounds);
        return List.copyOf(sounds);
    }

    /**
     * 本家 sound_Announcement は [[表示名, 音声パス], ...]。表示名 (要素0) を取り出す。
     * 名前が無い書き方 (文字列だけ等) の場合はそのエントリを空文字にして、
     * 表示側で「アナウンス N」にフォールバックさせる。
     */
    private static List<String> parseAnnouncementNames(JsonObject obj, JsonObject trainModel) {
        List<String> names = new ArrayList<>();
        appendAnnouncementNames(trainModel, names);
        appendAnnouncementNames(obj, names);
        return List.copyOf(names);
    }

    private static void appendAnnouncementNames(JsonObject json, List<String> target) {
        if (json == null || !json.has("sound_Announcement")) {
            return;
        }
        JsonElement element = json.get("sound_Announcement");
        if (!element.isJsonArray()) {
            return;
        }
        for (JsonElement entry : element.getAsJsonArray()) {
            // 音声が取れないエントリは parseAnnouncementSounds 側でも捨てられるので、
            // 名前リストとインデックスがずれないよう同じ条件で揃える。
            String sound = extractAnnouncementSound(entry);
            if (sound == null || sound.isBlank()) {
                continue;
            }
            target.add(extractAnnouncementName(entry));
        }
    }

    private static String extractAnnouncementName(JsonElement entry) {
        if (entry != null && entry.isJsonArray()) {
            JsonArray array = entry.getAsJsonArray();
            // [表示名, 音声パス] — 2要素以上のときだけ要素0を名前とみなす
            if (array.size() >= 2 && array.get(0).isJsonPrimitive() && array.get(0).getAsJsonPrimitive().isString()) {
                return array.get(0).getAsString();
            }
        }
        if (entry != null && entry.isJsonObject()) {
            JsonObject object = entry.getAsJsonObject();
            return getString(object, "name");
        }
        return "";
    }

    private static void appendAnnouncementSounds(JsonObject json, List<String> target) {
        if (json == null || !json.has("sound_Announcement")) {
            return;
        }
        JsonElement element = json.get("sound_Announcement");
        if (!element.isJsonArray()) {
            return;
        }
        for (JsonElement entry : element.getAsJsonArray()) {
            String sound = extractAnnouncementSound(entry);
            if (sound != null && !sound.isBlank()) {
                target.add(sound);
            }
        }
    }

    private static String extractAnnouncementSound(JsonElement entry) {
        if (entry == null || entry.isJsonNull()) {
            return null;
        }
        if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
            return entry.getAsString();
        }
        if (entry.isJsonArray()) {
            JsonArray array = entry.getAsJsonArray();
            if (array.size() >= 2 && array.get(1).isJsonPrimitive() && array.get(1).getAsJsonPrimitive().isString()) {
                return array.get(1).getAsString();
            }
            if (!array.isEmpty()) {
                return extractAnnouncementSound(array.get(0));
            }
            return null;
        }
        if (entry.isJsonObject()) {
            JsonObject object = entry.getAsJsonObject();
            return firstNonBlank(
                getString(object, "sound"),
                getString(object, "soundName")
            );
        }
        return null;
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
            if (!mat.isBlank() && !tex.isBlank()) {
                overrides.put(mat, encodeTextureDescriptor(pair));
            }
        }
        return overrides;
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

    private static int parseInt(JsonObject obj, String key, int def) {
        if (obj == null || !obj.has(key)) return def;
        try {
            return obj.get(key).getAsInt();
        } catch (Exception e) {
            return def;
        }
    }

    private static boolean parseBoolean(JsonObject obj, String key, boolean def) {
        if (obj == null || !obj.has(key)) return def;
        try {
            return obj.get(key).getAsBoolean();
        } catch (Exception e) {
            return def;
        }
    }

    public static VehicleDefinition getVehicleDefinitionById(String id) {
        return VehicleRegistry.getById(id);
    }

    public static InputStream openPackStream(VehicleDefinition definition) throws IOException {
        if (definition == null) return null;
        Path p = RailPackLoader.resolvePackPath(definition.getPackName());
        return p == null ? null : Files.newInputStream(p);
    }

    public static String readScriptContent(VehicleDefinition definition) {
        if (definition == null || definition.getScriptPath() == null || definition.getScriptPath().isBlank()) {
            return null;
        }
        Path packPath = RailPackLoader.resolvePackPath(definition.getPackName());
        if (packPath == null) {
            return null;
        }
        String scriptPath = normalize(definition.getScriptPath());
        String scriptFileName = scriptPath.contains("/") ? scriptPath.substring(scriptPath.lastIndexOf('/') + 1).toLowerCase() : scriptPath.toLowerCase();

        try {
            if (Files.isDirectory(packPath)) {
                Path resolved = resolveFilePath(packPath, scriptPath);
                if (resolved != null) {
                    return PackTextDecoder.readText(resolved);
                }
                // fallback by file name only within pack
                try (var stream = Files.walk(packPath)) {
                    for (Path file : (Iterable<Path>) stream::iterator) {
                        if (!Files.isRegularFile(file)) continue;
                        String name = file.getFileName().toString().toLowerCase();
                        if (name.equals(scriptFileName)) {
                            return PackTextDecoder.readText(file);
                        }
                    }
                }
                // 自パックに無い: 前提/ベーススクリプトパックを横断して探す。
                return readScriptFromGlobalIndex(scriptPath);
            }
            try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(Files.newInputStream(packPath))) {
                java.util.zip.ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String name = normalize(entry.getName());
                    if (name.equalsIgnoreCase(scriptPath) || name.toLowerCase().endsWith("/" + scriptFileName) || name.toLowerCase().equals(scriptFileName)) {
                        return PackTextDecoder.readText(zip);
                    }
                    zip.closeEntry();
                }
            }
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("Failed to read vehicle script {} from pack {}", definition.getScriptPath(), definition.getPackName(), e);
        }
        // 自パックに無い / パック解決失敗: 前提・ベーススクリプトパックを横断して探す。
        // (例: JRCT_Keiyo が車両を定義し、実体スクリプト scripts/Render_script_jre233_mi.js は
        // 前提の JR-CommuterTrainPack 側にある、という分割構成。
        return readScriptFromGlobalIndex(scriptPath);
    }

    /**
     * 車両スクリプトを全ロード済みパック横断の資産索引 (NGTFileLoader) から読む。
     * rendererPath が前提/ベーススクリプトパックのファイルを指す分割構成のフォールバック。
     */
    private static String readScriptFromGlobalIndex(String scriptPath) {
        if (scriptPath == null || scriptPath.isBlank()) {
            return null;
        }
        try {
            byte[] bytes = jp.ngt.ngtlib.io.NGTFileLoader.findAsset(scriptPath);
            if (bytes != null) {
                return PackTextDecoder.readText(new java.io.ByteArrayInputStream(bytes));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Path resolveFilePath(Path root, String relative) throws IOException {
        if (relative == null) return null;
        String norm = relative.replace('\\', '/');
        Path candidate = root.resolve(norm);
        if (Files.exists(candidate) && Files.isRegularFile(candidate)) return candidate;
        candidate = root.resolve("assets/minecraft").resolve(norm);
        if (Files.exists(candidate) && Files.isRegularFile(candidate)) return candidate;
        String leaf = norm.contains("/") ? norm.substring(norm.lastIndexOf('/') + 1) : norm;
        try (var stream = Files.walk(root)) {
            for (Path file : (Iterable<Path>) stream::iterator) {
                if (!Files.isRegularFile(file)) continue;
                String name = file.getFileName().toString();
                if (name.equalsIgnoreCase(norm) || name.equalsIgnoreCase(leaf)) return file;
            }
        }
        return null;
    }

    private record NestedArchive(String name, byte[] bytes) {
    }
}
