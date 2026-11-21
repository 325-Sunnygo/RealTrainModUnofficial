package com.portofino.realtrainmodunofficial.vehicle;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.rail.RailPackLoader;
import com.portofino.realtrainmodunofficial.util.PackTextDecoder;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class VehiclePackLoader {
    private static final org.apache.logging.log4j.Logger LOGGER =
        org.apache.logging.log4j.LogManager.getLogger(VehiclePackLoader.class);
    private static final List<VehicleDefinition> LOADED = new ArrayList<>();
    private static final String GENERATED_SOUND_PACK_DIR = "generated_sound_pack";
    private static boolean loaded = false;

    /**
     * ★本家 KaizPatchX ModelPackLoadThread.loadModelFromConfig と同じ読み込み経路。
     *
     * <ol>
     *   <li>NGTFileLoader の資産索引 (パック zip / jar / ディレクトリ全て) から
     *       {@code Model*.json} (RTMU は ModelTrain_ / ModelVehicle_ / train_ / vehicle_ /
     *       json ディレクトリ) を集める — 本家 findFile 相当</li>
     *   <li>RTMConfig.loadSpeed に応じたワーカープールで<b>並列に</b> json を読み、
     *       ModelPackManager.registerModelset (Gson リフレクション → cfg.init() → ModelSet 登録)
     *       へ投入 — 本家と同一の登録処理</li>
     *   <li>全タスク終了を待って ModelSet 一覧から VehicleDefinition を確定する</li>
     * </ol>
     *
     * 旧来の「ディレクトリを1つずつ走査して zip を順に開く」「OrderedPackTasks で
     * 登録順を整列して呼び出し側に返す」処理は削除済み (重いパックで本家より遅くなる原因)。
     */
    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        LOADED.clear();

        // 本家の既定モデルを全て同梱する方針にしたので、列車 (ModelTrain_*) も自動車
        // (ModelVehicle_*) も jar 内の assets/minecraft/models/json から読む。
        loadFromModJar();

        // 本家 findFile("Model.*\\.json") 相当: 資産索引から車両定義 json を集める
        java.util.List<jp.ngt.ngtlib.io.NGTFileLoader.PackEntry> entries =
            jp.ngt.ngtlib.io.NGTFileLoader.findPackEntries(VehiclePackLoader::isTrainJson);

        // 本家 loadModelFromConfig と同じ: RTMConfig.loadSpeed でワーカー数を決める
        java.util.concurrent.ExecutorService executor;
        switch (jp.ngt.rtm.RTMConfig.loadSpeed) {
            case 1:
                executor = java.util.concurrent.Executors.newSingleThreadExecutor();
                break;
            case 3:
                executor = java.util.concurrent.Executors.newWorkStealingPool();
                break;
            default:
                executor = java.util.concurrent.Executors.newFixedThreadPool(
                    Math.max(Runtime.getRuntime().availableProcessors() / 3, 1));
                break;
        }
        try {
            for (jp.ngt.ngtlib.io.NGTFileLoader.PackEntry entry : entries) {
                if (!isAllowedContainer(entry.container())) {
                    continue;
                }
                String packName = entry.container().getFileName().toString();
                executor.submit(() -> {
                    try {
                        byte[] bytes = jp.ngt.ngtlib.io.NGTFileLoader.readPackEntry(entry);
                        if (bytes != null) {
                            parseTrainJsonNow(bytes, packName, entry.entryName());
                        }
                    } catch (Throwable t) {
                        LOGGER.warn("Failed to load model json {} in {}: {}",
                            entry.entryName(), packName, t.toString());
                    }
                });
            }
        } finally {
            executor.shutdown();
        }
        try {
            executor.awaitTermination(Long.MAX_VALUE, java.util.concurrent.TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        VehicleRegistry.setDefinitions(LOADED);
    }

    /** 資産索引から集めたエントリの受け入れ可否 (旧 scanPackRoot の除外条件と同等)。 */
    private static boolean isAllowedContainer(Path container) {
        String lowerName = container.getFileName() == null ? "" : container.getFileName().toString().toLowerCase(Locale.ROOT);
        if (GENERATED_SOUND_PACK_DIR.equalsIgnoreCase(lowerName)) {
            return false;
        }
        // ユーザー要望により RTM 公式車両アセット (RTM-Official-Assets.zip 等) からは
        // 車両定義をロードしない。 信号 / レール / 自動車などは別 loader が読む。
        if (lowerName.contains("rtm-official-assets")) {
            return false;
        }
        // KaizPatchX-compat.zip は RTM 公式デフォルト車両を内包するためロードしない
        // (ユーザー要望「デフォルト車両を全部消す」)。
        if (lowerName.contains("kaizpatchx")) {
            return false;
        }
        // 自分自身の mod jar は loadFromModJar が読むので除外 (二重登録防止)。
        if (lowerName.contains("realtrainmodunofficial") && !Files.isDirectory(container)) {
            try {
                var modFile = ModList.get().getModFileById(RealTrainModUnofficial.MODID);
                if (modFile != null && modFile.getFile().getFilePath() != null
                        && Files.isSameFile(container, modFile.getFile().getFilePath())) {
                    return false;
                }
            } catch (Exception ignored) {
                return false;
            }
        }
        // README 同意ゲート: 未同意/拒否のパックはロードしない (タイトル画面で同意を取る)。
        // zip / jar のみ対象 (ディレクトリ展開パックは README 同梱の概念が無いので従来どおり)。
        if (!Files.isDirectory(container)
                && !com.portofino.realtrainmodunofficial.pack.PackConsent.isAllowed(container)) {
            return false;
        }
        return true;
    }

    /** 同梱 (クラスパス内) デフォルトモデル。本家はクラスパスも資産索引に含むが、
     *  RTMU の NGTFileLoader は外部パックのみ索引するため jar 直読みを維持する。 */
    private static void loadFromModJar() {
        try {
            var modFileEntry = ModList.get().getModFileById(RealTrainModUnofficial.MODID);
            if (modFileEntry == null) return;
            var modFile = modFileEntry.getFile();
            Path jsonDir = modFile.findResource("assets", "minecraft", "models", "json");
            if (jsonDir == null || !Files.isDirectory(jsonDir)) return;
            try (var stream = Files.list(jsonDir)) {
                stream.filter(Files::isRegularFile)
                    .forEach(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        if (!n.endsWith(".json")) {
                            return;
                        }
                        if (!n.startsWith("modeltrain_") && !n.startsWith("modelvehicle_")) {
                            return;
                        }
                        try {
                            byte[] bytes = Files.readAllBytes(p);
                            parseTrainJsonNow(bytes, RealTrainModUnofficial.MODID, p.getFileName().toString());
                        } catch (Exception e) {
                            LOGGER.warn("Failed to load bundled vehicle {}", p, e);
                        }
                    });
            }
        } catch (Throwable e) {
            // FML 未初期化環境 (テスト) でも落ちないように Error も握りつぶす
            LOGGER.warn("Could not load bundled car definitions", e);
        }
    }

    public static synchronized void reload() {
        loaded = false;
        load();
    }

    /**
     * 資産索引エントリ名が車両定義 json かどうか (本家の "Model.*\\.json" パターン相当)。
     * ModelTrain_ / ModelVehicle_ プレフィックスのほか、レガシー名 (train_ / vehicle_) と
     * json ディレクトリ内の無プレフィックスファイル (trainName / vehicleType キーで判別) を含む。
     */
    private static boolean isTrainJson(String entryName) {
        String normalized = normalize(entryName).toLowerCase(Locale.ROOT);
        if (!normalized.endsWith(".json")) {
            return false;
        }
        String leaf = normalized.substring(normalized.lastIndexOf('/') + 1);
        boolean rtmStyleName = leaf.startsWith("modeltrain_") || leaf.startsWith("train_");
        boolean rtmVehicleStyleName = leaf.startsWith("modelvehicle_") || leaf.startsWith("vehicle_");
        boolean rtmStyleDir = normalized.contains("/json/") || normalized.contains("/models/json/");
        return rtmStyleName || rtmVehicleStyleName || rtmStyleDir;
    }

    private static String normalize(String raw) {
        return raw.replace('\\', '/');
    }

    private static void parseTrainJson(byte[] bytes, String packName, String sourcePath) {
        parseTrainJsonNow(bytes, packName, sourcePath);
    }
    // ★KaizPatchX と同じ処理: JSON は jp.ngt.rtm.modelpack.ModelPackManager.registerModelset
    //  (NGTJson.getObjectFromJson による Gson リフレクション) で本家 cfg クラスへ読み込み、
    //  登録済み ModelSet から VehicleDefinition を組み立てる。
    //  旧来の「手書き JSON パース (parseTrainJsonNow + parse* ヘルパ群)」は削除済み。
    private static void parseTrainJsonNow(byte[] bytes, String packName, String sourcePath) {
        try {
            String json = PackTextDecoder.decodeJson(bytes);
            String type = resolveModelType(normalize(sourcePath), json);
            if (type == null) {
                return;// 車両定義ではない (レール / 信号 / 設置物等は各レイヤが読む)
            }
            // 本家 KaizPatchX: ModelPackManager.INSTANCE.registerModelset(type, json, sourceFile)
            //   → Gson リフレクションで cfg クラスへ読み込み、cfg.init() 後 ModelSet を登録
            String name = jp.ngt.rtm.modelpack.ModelPackManager.INSTANCE.registerModelset(type, json, packName + ":" + sourcePath);
            jp.ngt.rtm.modelpack.modelset.ModelSetBase<?> set =
                jp.ngt.rtm.modelpack.ModelPackManager.INSTANCE.getModelSet(type, name);
            publishDefinitionFromModelSet(set, packName, sourcePath, fallbackTrainId(sourcePath));
        } catch (Exception e) {
            LOGGER.warn("Failed to parse model json {} in {}: {}", sourcePath, packName, e.getMessage());
        }
    }

    /**
     * ファイル名 / JSON キーから本家のモデルタイプを決める。
     * KaizPatchX はファイル名の "_" 前でタイプを切る (ModelTrain_x.json → "ModelTrain")。
     * レガシー名 (train_* / vehicle_*) や /json/ ディレクトリ内の無 prefixes ファイルは
     * JSON のキーで判別する。
     */
    private static String resolveModelType(String normalizedPath, String json) {
        String leaf = normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        String stem = leaf.endsWith(".json") ? leaf.substring(0, leaf.length() - 5) : leaf;
        String prefix = stem.split("_", 2)[0];
        switch (prefix) {
            case "modeltrain", "train" -> { return "ModelTrain"; }
            case "modelvehicle", "vehicle" -> { return "ModelVehicle"; }
            default -> { }
        }
        // /json/ ディレクトリ等の prefix 無しファイルは中身で判別
        if (normalizedPath.contains("/json/") || normalizedPath.contains("/models/json/")) {
            if (json.contains("\"trainName\"")) return "ModelTrain";
            if (json.contains("\"vehicleType\"")) return "ModelVehicle";
        }
        return null;
    }

    private static void publishDefinitionFromModelSet(
            jp.ngt.rtm.modelpack.modelset.ModelSetBase set,
            String packName, String sourcePath, String fallbackId) {
        jp.ngt.rtm.modelpack.cfg.ModelConfig cfg = (jp.ngt.rtm.modelpack.cfg.ModelConfig) set.getConfig();
        boolean isTrain = cfg instanceof jp.ngt.rtm.modelpack.cfg.TrainConfig;
        jp.ngt.rtm.modelpack.cfg.TrainConfig trainCfg = isTrain ? (jp.ngt.rtm.modelpack.cfg.TrainConfig) cfg : null;
        jp.ngt.rtm.modelpack.cfg.VehicleConfig vehicleCfg =
            cfg instanceof jp.ngt.rtm.modelpack.cfg.VehicleConfig vc ? vc : null;
        jp.ngt.rtm.modelpack.cfg.VehicleBaseConfig vb = cfg instanceof jp.ngt.rtm.modelpack.cfg.VehicleBaseConfig v ? v : null;

        String id = cfg.getName();
        if (id == null || id.isBlank()) {
            id = fallbackId != null && !fallbackId.isBlank() ? fallbackId : "train";
        }
        String displayName = id;

        // 車体モデル: ModelTrain → trainModel2 / ModelVehicle → model
        jp.ngt.rtm.modelpack.cfg.ModelConfig.ModelSource model =
            isTrain ? trainCfg.getModel() : (vehicleCfg != null ? vehicleCfg.model : null);
        String modelFile = model == null ? null : model.modelFile;
        if (modelFile == null || modelFile.isBlank()) {
            throw new jp.ngt.rtm.modelpack.ModelPackException("Can't load model (no modelFile)", sourcePath);
        }

        String buttonTexture = cfg.buttonTexture;
        Map<String, String> tex = encodeTextures(model.textures);
        // 本家 ModelConfig (=JSON のルート) の offset/scale
        Vec3 offset = toVec3(cfg.offset);
        float scale = cfg.scale;
        // rendererPath は ModelSource (trainModel2 / model) の項目
        String scriptPath = model.rendererPath;
        if (scriptPath != null && scriptPath.isBlank()) {
            scriptPath = null;
        }
        String soundScriptPath = vb != null ? vb.soundScriptPath : null;

        // ★車両種別 (アイテムの振り分けに使う)
        //   ModelVehicle: vehicleType (Car/Plane/Ship)
        //   ModelTrain  : trainType (EC/DC/EL/DL/SL/CC/TC/N) — 無いパックは電車扱いに落ちる
        String vehicleType = isTrain
            ? firstNonBlank(trainCfg.trainType, "")
            : firstNonBlank(vehicleCfg.vehicleType, "Car");

        // 本家に doorType は無い
        String doorType = null;

        // 台車 (ModelTrain のみ)。本家 bogieModel3 {front, back} + bogiePos {x,y,z}
        List<VehicleDefinition.BogieDefinition> bogies = new ArrayList<>();
        if (isTrain) {
            float[][] bogiePos = trainCfg.getBogiePos();
            for (int i = 0; i < 2; i++) {
                jp.ngt.rtm.modelpack.cfg.ModelConfig.ModelSource bogieModel;
                try {
                    bogieModel = trainCfg.getBogieModel(i);
                } catch (RuntimeException ex) {
                    continue;
                }
                if (bogieModel == null || bogieModel.modelFile == null || bogieModel.modelFile.isBlank()) {
                    continue;
                }
                Vec3 position = (bogiePos != null && i < bogiePos.length && bogiePos[i] != null && bogiePos[i].length >= 3)
                    ? new Vec3(bogiePos[i][0], bogiePos[i][1], bogiePos[i][2]) : Vec3.ZERO;
                bogies.add(new VehicleDefinition.BogieDefinition(
                    bogieModel.modelFile,
                    encodeTextures(bogieModel.textures),
                    position,
                    bogieModel.rendererPath == null ? "" : bogieModel.rendererPath));
            }
        }

        // 座席: 本家 slotPos {x, y, z, type} (ブロック単位) / playerPos {x, y, z} (運転席)
        List<VehicleDefinition.SeatMarker> seatMarkers = new ArrayList<>();
        if (vb != null && vb.playerPos != null) {
            for (float[] p : vb.playerPos) {
                if (p != null && p.length >= 3) {
                    seatMarkers.add(new VehicleDefinition.SeatMarker(
                        new Vec3(p[0], p[1], p[2]), VehicleDefinition.SEAT_TYPE_DRIVER_CAB, true));
                }
            }
        }
        List<float[]> slotPositions = new ArrayList<>();
        List<Vec3> seats = new ArrayList<>();
        if (vb != null && vb.getSlotPos() != null) {
            for (float[] p : vb.getSlotPos()) {
                if (p == null || p.length < 3) continue;
                float type = p.length >= 4 ? p[3] : 2.0F;
                Vec3 pos = new Vec3(p[0], p[1], p[2]);
                slotPositions.add(new float[]{p[0], p[1], p[2], type});
                seatMarkers.add(new VehicleDefinition.SeatMarker(pos, Math.round(type), false));
                seats.add(pos);
            }
        }
        List<Vec3> playerPositions = new ArrayList<>();
        if (vb != null && vb.playerPos != null) {
            for (float[] p : vb.playerPos) {
                if (p != null && p.length >= 3) {
                    playerPositions.add(new Vec3(p[0], p[1], p[2]));
                }
            }
        }
        List<VehicleDefinition.SeatMarker> rideableSeatMarkers = new ArrayList<>();
        for (VehicleDefinition.SeatMarker seatMarker : seatMarkers) {
            if (seatMarker.isRideable()) {
                rideableSeatMarkers.add(seatMarker);
            }
        }

        Vec3 seatOffset = !playerPositions.isEmpty() ? playerPositions.get(0) : (!seats.isEmpty() ? seats.get(0) : null);
        // 本家 TrainConfig.trainDistance (車体長の半分)。ModelVehicle は本家に無い概念なので従来既定値。
        float trainDistance = isTrain ? trainCfg.trainDistance : 4.5F;
        // 本家に driverSeatIndex は無い (運転席は playerPos から決まる)
        int driverSeatIndex = 0;
        int frontDriverSeatIndex = resolveFrontDriverSeatIndex(rideableSeatMarkers, driverSeatIndex);
        int rearDriverSeatIndex = resolveRearDriverSeatIndex(rideableSeatMarkers, frontDriverSeatIndex);

        // ドア / パンタ: 本家 VehicleBaseConfig.door_left / door_right (VehicleParts[])
        List<VehicleDefinition.DoorAnimationDefinition> leftDoors = toDoorAnimations(vb == null ? null : vb.door_left);
        List<VehicleDefinition.DoorAnimationDefinition> rightDoors = toDoorAnimations(vb == null ? null : vb.door_right);

        // ★速度性能は本家 TrainConfig の項目そのまま
        List<Float> notchMaxSpeeds = toFloatList(isTrain ? trainCfg.maxSpeed : null);
        List<Float> notchAccelerations = toFloatList(isTrain ? trainCfg.accelerateions : null);
        List<Float> decelerations = toFloatList(isTrain ? trainCfg.deccelerations : null);

        List<String> rollsignNames = toStringList(vb == null ? null : vb.rollsignNames);
        List<List<String>> customButtonOptions = toCustomButtonOptions(vb);
        List<String> customButtonNames = toCustomButtonNames(customButtonOptions, vb);
        String rollsignTexture = vb == null ? null : vb.rollsignTexture;
        List<VehicleDefinition.RollsignDefinition> rollsigns = toRollsigns(vb == null ? null : vb.rollsigns);
        // 種別幕 (方向幕と同じ書式・別項目)。typeSignNames / typeSignTexture / typeSigns。
        List<String> typeSignNames = toStringList(vb == null ? null : vb.typeSignNames);
        String typeSignTexture = vb == null ? null : vb.typeSignTexture;
        List<VehicleDefinition.RollsignDefinition> typeSigns = toRollsigns(vb == null ? null : vb.typeSigns);

        // 車灯: 本家 VehicleBaseConfig.headLights / tailLights / interiorLights
        List<VehicleDefinition.LightDefinition> headLights = toLights(vb == null ? null : vb.headLights);
        List<VehicleDefinition.LightDefinition> tailLights = toLights(vb == null ? null : vb.tailLights);
        List<VehicleDefinition.LightDefinition> interiorLights = toLights(vb == null ? null : vb.interiorLights);

        String hornSound = vb == null ? null : vb.sound_Horn;
        // 本家 sound_Announcement [[名前, 音], ...] の音側
        List<String> announcementSounds = new ArrayList<>();
        List<String> announcementNames = new ArrayList<>();
        if (vb != null && vb.sound_Announcement != null) {
            for (String[] entry : vb.sound_Announcement) {
                if (entry == null || entry.length < 2 || entry[1] == null || entry[1].isBlank()) continue;
                announcementSounds.add(entry[1]);
                announcementNames.add(entry[0] == null ? "" : entry[0]);
            }
        }
        announcementSounds = List.copyOf(announcementSounds);
        announcementNames = List.copyOf(announcementNames);

        // ★本家のキーは綴りミスのまま accelerateion
        float acceleration = isTrain ? trainCfg.accelerateion : 0.0F;
        boolean useVariableAcceleration = isTrain && trainCfg.useVariableAcceleration;
        boolean useVariableDeceleration = isTrain && trainCfg.useVariableDeceleration;
        boolean smoothing = vb != null && vb.smoothing;
        boolean doCulling = vb != null && vb.doCulling;
        boolean hasConfiguredLights = !headLights.isEmpty() || !tailLights.isEmpty() || !interiorLights.isEmpty();
        boolean renderLight = (vb != null && vb.renderLight) || hasConfiguredLights;
        boolean notDisplayCab = vb != null && vb.notDisplayCab;
        boolean singleTrain = isTrain && trainCfg.isSingleTrain;

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
        // 登録済みの本家 cfg をそのまま保持する (旧 setLegacyConfig(JsonObject) の代替)。
        definition.setModelConfig(cfg);
        definition.setServerScriptPath(cfg.serverScriptPath == null ? "" : cfg.serverScriptPath);
        definition.setGuiScriptPath(cfg.guiScriptPath == null ? "" : cfg.guiScriptPath);
        definition.setGuiTexture(cfg.guiTexture == null ? "" : cfg.guiTexture);
        // 本家 KaizPatchX の customIconTexture。持ち物欄でのアイテムの絵を差し替える。
        definition.setCustomIconTexture(cfg.customIconTexture == null ? "" : cfg.customIconTexture);
        // 本家 sound_Announcement の表示名 ([[名前, 音]] の名前側)
        definition.setAnnouncementNames(announcementNames);
        definition.setSlotPositions(slotPositions);
        definition.setJsonRunningSounds(
            vb == null ? "" : nvl(vb.sound_Stop),
            vb == null ? "" : nvl(vb.sound_S_A),
            vb == null ? "" : nvl(vb.sound_Acceleration),
            vb == null ? "" : nvl(vb.sound_Deceleration),
            vb == null ? "" : nvl(vb.sound_D_S)
        );
        definition.setBrakeReleaseSounds(
            isTrain && trainCfg.sound_BrakeRelease != null ? trainCfg.sound_BrakeRelease : "",
            isTrain && trainCfg.sound_BrakeRelease2 != null ? trainCfg.sound_BrakeRelease2 : "");
        definition.setAtsSounds(
            vb == null || vb.sound_ATSChime == null ? "" : vb.sound_ATSChime,
            vb == null || vb.sound_ATSBell == null ? "" : vb.sound_ATSBell);
        definition.setDoorSounds(
            vb == null || vb.sound_DoorOpen == null ? "" : vb.sound_DoorOpen,
            vb == null || vb.sound_DoorClose == null ? "" : vb.sound_DoorClose);
        definition.setTypeSign(typeSignNames, typeSignTexture, typeSigns);
        definition.setSpeedProfile(notchAccelerations, decelerations,
                useVariableAcceleration, useVariableDeceleration);
        // 本家と同じ: 各ワーカーから直接登録する (順序整列はしない。VehicleRegistry が重複を畳む)
        synchronized (LOADED) {
            LOADED.add(definition);
        }
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
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

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static Vec3 toVec3(float[] v) {
        return (v != null && v.length >= 3) ? new Vec3(v[0], v[1], v[2]) : Vec3.ZERO;
    }

    private static List<Float> toFloatList(float[] values) {
        if (values == null) {
            return List.of();
        }
        List<Float> out = new ArrayList<>(values.length);
        for (float v : values) {
            out.add(v);
        }
        return out;
    }

    private static List<String> toStringList(String[] values) {
        if (values == null) {
            return List.of();
        }
        return List.of(values);
    }

    /** 本家 ModelSource.textures String[][] → マテリアル名 → テクスチャデスクリプタ。 */
    private static Map<String, String> encodeTextures(String[][] textures) {
        if (textures == null) {
            return Map.of();
        }
        Map<String, String> overrides = new HashMap<>();
        for (String[] entry : textures) {
            if (entry == null || entry.length < 2) continue;
            String mat = entry[0];
            String path = entry[1];
            if (mat != null && !mat.isBlank() && path != null && !path.isBlank()) {
                overrides.put(mat, com.portofino.realtrainmodunofficial.modelpack.LegacyTextureDescriptor.encode(entry));
            }
        }
        return overrides;
    }

    /** 本家 customButtons String[][] → [ボタン][状態名] のグリッド (空要素を除く)。 */
    private static List<List<String>> toCustomButtonOptions(jp.ngt.rtm.modelpack.cfg.VehicleBaseConfig vb) {
        if (vb == null || vb.customButtons == null) {
            return List.of();
        }
        List<List<String>> result = new ArrayList<>();
        for (String[] row : vb.customButtons) {
            if (row == null) continue;
            List<String> values = new ArrayList<>();
            for (String option : row) {
                if (option != null && !option.isBlank()) {
                    values.add(option);
                }
            }
            if (!values.isEmpty()) {
                result.add(values);
            }
        }
        return result;
    }

    private static List<String> toCustomButtonNames(List<List<String>> options, jp.ngt.rtm.modelpack.cfg.VehicleBaseConfig vb) {
        if (!options.isEmpty()) {
            List<String> labels = new ArrayList<>();
            for (List<String> optionList : options) {
                labels.add(optionList.isEmpty() ? "" : optionList.get(0));
            }
            return labels;
        }
        if (vb != null && vb.customButtonNames != null) {
            return toStringList(vb.customButtonNames);
        }
        if (vb != null && vb.buttonNames != null) {
            return toStringList(vb.buttonNames);
        }
        if (vb != null && vb.buttons != null) {
            return toStringList(vb.buttons);
        }
        return List.of();
    }

    /** 本家 VehicleBaseConfig.Rollsign → RollsignDefinition。 */
    private static List<VehicleDefinition.RollsignDefinition> toRollsigns(jp.ngt.rtm.modelpack.cfg.VehicleBaseConfig.Rollsign[] rollsigns) {
        if (rollsigns == null) {
            return List.of();
        }
        List<VehicleDefinition.RollsignDefinition> values = new ArrayList<>();
        for (jp.ngt.rtm.modelpack.cfg.VehicleBaseConfig.Rollsign rollsign : rollsigns) {
            if (rollsign == null || rollsign.uv == null || rollsign.uv.length < 4
                    || rollsign.pos == null || rollsign.pos.length == 0) {
                continue;
            }
            values.add(new VehicleDefinition.RollsignDefinition(
                rollsign.uv,
                rollsign.pos,
                rollsign.doAnimation,
                rollsign.disableLighting
            ));
        }
        return values;
    }

    /** 本家 VehicleBaseConfig.Light → LightDefinition。 */
    private static List<VehicleDefinition.LightDefinition> toLights(jp.ngt.rtm.modelpack.cfg.VehicleBaseConfig.Light[] lights) {
        if (lights == null) {
            return List.of();
        }
        List<VehicleDefinition.LightDefinition> values = new ArrayList<>();
        for (jp.ngt.rtm.modelpack.cfg.VehicleBaseConfig.Light light : lights) {
            if (light == null || light.pos == null || light.pos.length < 3) {
                continue;
            }
            values.add(new VehicleDefinition.LightDefinition(
                light.type,
                light.color != 0 ? light.color : 0xFFFFFF,
                new Vec3(light.pos[0], light.pos[1], light.pos[2]),
                light.r != 0.0F ? light.r : 0.25F,
                light.reverse
            ));
        }
        return values;
    }

    /**
     * 本家 VehicleBaseConfig.door_left / door_right (VehicleParts[]) → DoorAnimationDefinition。
     * 本家 BasicVehiclePartsRenderer.getParts と同じ読み取り。
     * childParts を再帰で辿り、transform は全要素を並び順のまま保持する。
     */
    private static List<VehicleDefinition.DoorAnimationDefinition> toDoorAnimations(jp.ngt.rtm.modelpack.cfg.VehicleBaseConfig.VehicleParts[] partsArray) {
        if (partsArray == null) {
            return List.of();
        }
        List<VehicleDefinition.DoorAnimationDefinition> out = new ArrayList<>();
        for (jp.ngt.rtm.modelpack.cfg.VehicleBaseConfig.VehicleParts parts : partsArray) {
            VehicleDefinition.DoorAnimationDefinition def = toDoorAnimation(parts);
            if (def != null) {
                out.add(def);
            }
        }
        return out;
    }

    private static VehicleDefinition.DoorAnimationDefinition toDoorAnimation(jp.ngt.rtm.modelpack.cfg.VehicleBaseConfig.VehicleParts door) {
        if (door == null) {
            return null;
        }
        List<String> objects = new ArrayList<>();
        if (door.objects != null) {
            for (String name : door.objects) {
                if (name != null && !name.isBlank()) {
                    objects.add(name);
                }
            }
        }
        List<VehicleDefinition.DoorAnimationDefinition> children = new ArrayList<>();
        if (door.childParts != null) {
            for (jp.ngt.rtm.modelpack.cfg.VehicleBaseConfig.VehicleParts child : door.childParts) {
                VehicleDefinition.DoorAnimationDefinition childDef = toDoorAnimation(child);
                if (childDef != null) {
                    children.add(childDef);
                }
            }
        }
        if (objects.isEmpty() && children.isEmpty()) {
            return null;
        }
        Vec3 pos = toVec3(door.pos);
        List<float[]> transforms = new ArrayList<>();
        if (door.transform != null) {
            // 本家 VehicleParts.transform: 要素数 3 = 平行移動、4 = 回転。
            // 長さをそのまま保持する — 4 要素を平行移動として読むと回転式のドアが
            // 明後日の方向へ飛ぶ。
            for (float[] entry : door.transform) {
                if (entry != null && (entry.length == 3 || entry.length == 4)) {
                    transforms.add(entry.clone());
                }
            }
        }
        return new VehicleDefinition.DoorAnimationDefinition(
                objects, pos, firstTranslation(transforms), transforms, children);
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

    private static int resolveFrontDriverSeatIndex(List<VehicleDefinition.SeatMarker> seats, int fallback) {
        if (seats.isEmpty()) {
            return fallback;
        }

        int driverExtreme = findExtremeDriverSeatIndexByZ(seats, true);
        if (driverExtreme >= 0) {
            return driverExtreme;
        }

        return findExtremeSeatIndexByZ(seats, true);
    }

    private static int resolveRearDriverSeatIndex(List<VehicleDefinition.SeatMarker> seats, int fallbackFrontIndex) {
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

    public static VehicleDefinition getVehicleDefinitionById(String id) {
        return VehicleRegistry.getById(id);
    }

    public static InputStream openPackStream(VehicleDefinition definition) throws IOException {
        if (definition == null) return null;
        Path p = RailPackLoader.resolvePackPath(definition.getPackName());
        return p == null ? null : Files.newInputStream(p);
    }

    public static String readScriptContent(VehicleDefinition definition) {
        return readScriptContent(definition, definition == null ? null : definition.getScriptPath());
    }

    /**
     * 任意パスのスクリプト読込 (rendererPath / guiScriptPath / soundScriptPath 共通)。
     * 解決規則 (パック内 → 前提パック横断索引) は rendererPath と同一。
     */
    public static String readScriptContent(VehicleDefinition definition, String path) {
        if (definition == null || path == null || path.isBlank()) {
            return null;
        }
        String scriptPath = normalize(path);
        Path packPath = RailPackLoader.resolvePackPath(definition.getPackName());
        if (packPath == null) {
            return readScriptFromGlobalIndex(scriptPath);
        }
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
            LOGGER.warn("Failed to read vehicle script {} from pack {}", path, definition.getPackName(), e);
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

}
