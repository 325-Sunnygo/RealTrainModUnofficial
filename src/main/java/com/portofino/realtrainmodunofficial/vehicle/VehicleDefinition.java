package com.portofino.realtrainmodunofficial.vehicle;

import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public class VehicleDefinition {
    public static final int SEAT_TYPE_DISABLED = 0;
    public static final int SEAT_TYPE_DRIVER_CAB = -1;

    public record BogieDefinition(String modelFile, Map<String, String> textureOverrides, Vec3 position, String scriptPath) {
        public BogieDefinition(String modelFile, Map<String, String> textureOverrides, Vec3 position) {
            this(modelFile, textureOverrides, position, "");
        }
    }

    public record SeatMarker(Vec3 position, int type, boolean driverCab) {
        public boolean isRideable() {
            return this.driverCab || this.type != SEAT_TYPE_DISABLED;
        }
    }

    /**
     * 本家 VehicleBaseConfig.VehicleParts と同じ形。
     * ドア・パンタグラフなど「開度 (0..1) に応じて動く部品」の定義。
     */
    public record DoorAnimationDefinition(List<String> objects, Vec3 closedPosition, Vec3 openTranslation,
                                          List<float[]> transforms, List<DoorAnimationDefinition> childParts) {
        public DoorAnimationDefinition {
            objects = objects == null ? List.of() : List.copyOf(objects);
            transforms = transforms == null ? List.of() : List.copyOf(transforms);
            childParts = childParts == null ? List.of() : List.copyOf(childParts);
        }
    }

    public record RollsignDefinition(float[] uv, float[][][] pos, boolean doAnimation, boolean disableLighting) {
    }

    public record LightDefinition(byte type, int color, Vec3 position, float radius, boolean reverse) {
    }

    private final String id;
    private final String displayName;
    private final String packName;
    private final String modelFile;
    private final String buttonTexture;
    /**
     * 本家 KaizPatchX の customIconTexture (ModelConfig)。
     * 持ち物欄のアイテムの絵をこの画像に差し替える。未設定 (空) ならバニラのアイテムモデル。
     */
    private String customIconTexture = "";
    private final Map<String, String> textureOverrides;
    private final Vec3 modelOffset;
    private final float modelScale;
    private final List<BogieDefinition> bogies;
    private final List<SeatMarker> seatMarkers;
    private final List<Vec3> seatPositions;
    private final List<Vec3> playerPositions;
    private final Vec3 seatOffset;
    private final String scriptPath;
    private final String soundScriptPath;
    // SuperRailBuilder3 などのサーバ側スクリプト（オプション。後付け設定）
    private String serverScriptPath = "";
    private final String vehicleType;
    private final String doorType;
    private final float trainDistance;
    private final int driverSeatIndex;
    private final int frontDriverSeatIndex;
    private final int rearDriverSeatIndex;
    private final List<DoorAnimationDefinition> leftDoors;
    private final List<DoorAnimationDefinition> rightDoors;
    private final List<Float> notchMaxSpeeds;
    private final float acceleration;
    private final boolean smoothing;
    private final List<String> rollsignNames;
    private final List<String> customButtonNames;
    private final List<List<String>> customButtonOptions;
    private final String rollsignTexture;
    private final List<RollsignDefinition> rollsigns;
    private final List<LightDefinition> headLights;
    private final List<LightDefinition> tailLights;
    private final List<LightDefinition> interiorLights;
    private final String hornSound;
    private final List<String> announcementSounds;
    /** sound_Announcement の表示名 (Loader が JSON 読込後に設定)。 */
    private List<String> announcementNames = List.of();
    private String soundStop = "";
    private String soundStartAcceleration = "";
    private String soundAcceleration = "";
    private String soundDeceleration = "";
    private String soundDecelerationStop = "";
    /** 本家 sound_BrakeRelease: ブレーキ → 力行に入れた瞬間の緩解音 (強)。 */
    private String soundBrakeRelease = "";
    /** 本家 sound_BrakeRelease2: 同上 (弱、ノッチ -1 以外から戻したとき)。 */
    private String soundBrakeRelease2 = "";
    /**
     */
    private String soundDoorOpen = "";
    private String soundDoorClose = "";
    private final boolean doCulling;
    private final boolean renderLight;
    private final boolean notDisplayCab;
    private final boolean singleTrain;

    public VehicleDefinition(
        String id,
        String displayName,
        String packName,
        String modelFile,
        String buttonTexture,
        Map<String, String> textureOverrides,
        Vec3 modelOffset,
        float modelScale,
        List<BogieDefinition> bogies,
        List<Vec3> seatPositions,
        List<Vec3> playerPositions,
        Vec3 seatOffset,
        String scriptPath,
        String soundScriptPath,
        String vehicleType,
        String doorType,
        float trainDistance,
        int driverSeatIndex,
        int frontDriverSeatIndex,
        int rearDriverSeatIndex,
        List<DoorAnimationDefinition> leftDoors,
        List<DoorAnimationDefinition> rightDoors,
        List<Float> notchMaxSpeeds,
        float acceleration,
        boolean smoothing,
        List<String> rollsignNames,
        List<String> customButtonNames,
        List<List<String>> customButtonOptions,
        String rollsignTexture,
        List<RollsignDefinition> rollsigns,
        List<LightDefinition> headLights,
        List<LightDefinition> tailLights,
        List<LightDefinition> interiorLights,
        String hornSound,
        List<String> announcementSounds,
        boolean doCulling,
        boolean renderLight,
        boolean notDisplayCab,
        boolean singleTrain
    ) {
        this(
            id,
            displayName,
            packName,
            modelFile,
            buttonTexture,
            textureOverrides,
            modelOffset,
            modelScale,
            bogies,
            buildSeatMarkers(seatPositions, playerPositions),
            seatPositions,
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
    }

    public VehicleDefinition(
        String id,
        String displayName,
        String packName,
        String modelFile,
        String buttonTexture,
        Map<String, String> textureOverrides,
        Vec3 modelOffset,
        float modelScale,
        List<BogieDefinition> bogies,
        List<SeatMarker> seatMarkers,
        List<Vec3> seatPositions,
        List<Vec3> playerPositions,
        Vec3 seatOffset,
        String scriptPath,
        String soundScriptPath,
        String vehicleType,
        String doorType,
        float trainDistance,
        int driverSeatIndex,
        int frontDriverSeatIndex,
        int rearDriverSeatIndex,
        List<DoorAnimationDefinition> leftDoors,
        List<DoorAnimationDefinition> rightDoors,
        List<Float> notchMaxSpeeds,
        float acceleration,
        boolean smoothing,
        List<String> rollsignNames,
        List<String> customButtonNames,
        List<List<String>> customButtonOptions,
        String rollsignTexture,
        List<RollsignDefinition> rollsigns,
        List<LightDefinition> headLights,
        List<LightDefinition> tailLights,
        List<LightDefinition> interiorLights,
        String hornSound,
        List<String> announcementSounds,
        boolean doCulling,
        boolean renderLight,
        boolean notDisplayCab,
        boolean singleTrain
    ) {
        this.id = id;
        this.displayName = displayName;
        this.packName = packName;
        this.modelFile = modelFile;
        this.buttonTexture = buttonTexture == null ? "" : buttonTexture;
        this.textureOverrides = textureOverrides == null ? Map.of() : Map.copyOf(textureOverrides);
        this.modelOffset = modelOffset == null ? Vec3.ZERO : modelOffset;
        this.modelScale = modelScale <= 0 ? 1.0F : modelScale;
        this.bogies = bogies == null ? List.of() : List.copyOf(bogies);
        this.seatMarkers = seatMarkers == null ? List.of() : List.copyOf(seatMarkers);
        this.seatPositions = seatPositions == null ? List.of() : List.copyOf(seatPositions);
        this.playerPositions = playerPositions == null ? List.of() : List.copyOf(playerPositions);
        this.seatOffset = seatOffset;
        this.scriptPath = scriptPath == null ? "" : scriptPath;
        this.soundScriptPath = soundScriptPath == null ? "" : soundScriptPath;
        this.vehicleType = vehicleType == null ? "Train" : vehicleType;
        this.doorType = doorType == null ? "manual" : doorType;
        this.trainDistance = trainDistance > 0 ? trainDistance : 4.5F;
        this.driverSeatIndex = driverSeatIndex;
        this.frontDriverSeatIndex = frontDriverSeatIndex;
        this.rearDriverSeatIndex = rearDriverSeatIndex;
        this.leftDoors = leftDoors == null ? List.of() : List.copyOf(leftDoors);
        this.rightDoors = rightDoors == null ? List.of() : List.copyOf(rightDoors);
        this.notchMaxSpeeds = notchMaxSpeeds == null ? List.of() : List.copyOf(notchMaxSpeeds);
        this.acceleration = acceleration > 0 ? acceleration : 0.00243F;
        this.smoothing = smoothing;
        this.rollsignNames = rollsignNames == null ? List.of() : List.copyOf(rollsignNames);
        this.customButtonNames = customButtonNames == null ? List.of() : List.copyOf(customButtonNames);
        this.customButtonOptions = customButtonOptions == null ? List.of() : toImmutableNestedList(customButtonOptions);
        this.rollsignTexture = rollsignTexture == null ? "" : rollsignTexture;
        this.rollsigns = rollsigns == null ? List.of() : List.copyOf(rollsigns);
        this.headLights = headLights == null ? List.of() : List.copyOf(headLights);
        this.tailLights = tailLights == null ? List.of() : List.copyOf(tailLights);
        this.interiorLights = interiorLights == null ? List.of() : List.copyOf(interiorLights);
        this.hornSound = hornSound == null ? "" : hornSound;
        this.announcementSounds = announcementSounds == null ? List.of() : List.copyOf(announcementSounds);
        this.doCulling = doCulling;
        this.renderLight = renderLight;
        this.notDisplayCab = notDisplayCab;
        this.singleTrain = singleTrain;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getPackName() { return packName; }
    public String getModelFile() { return modelFile; }
    public String getButtonTexture() { return buttonTexture; }

    /** #customIconTexture。空なら既定のアイコン。 */
    public String getCustomIconTexture() { return customIconTexture; }

    public void setCustomIconTexture(String customIconTexture) {
        this.customIconTexture = customIconTexture == null ? "" : customIconTexture.trim();
    }
    public Map<String, String> getTextureOverrides() { return textureOverrides; }
    public Vec3 getModelOffset() { return modelOffset; }
    public float getModelScale() { return modelScale; }
    public List<BogieDefinition> getBogies() { return bogies; }
    public List<Vec3> getBogiePositions() { 
        List<Vec3> positions = new ArrayList<>();
        for (BogieDefinition bogie : bogies) {
            positions.add(bogie.position);
        }
        return positions;
    }
    public List<Vec3> getSeatPositions() { return seatPositions; }
    public List<Vec3> getPlayerPositions() { return playerPositions; }
    public List<SeatMarker> getSeatMarkers() { return seatMarkers; }

    public List<SeatMarker> getRideableSeatMarkers() {
        if (seatMarkers.isEmpty()) {
            return List.of();
        }
        List<SeatMarker> rideable = new ArrayList<>(seatMarkers.size());
        for (SeatMarker marker : seatMarkers) {
            if (marker.isRideable()) {
                rideable.add(marker);
            }
        }
        return List.copyOf(rideable);
    }

    public boolean isDriverSeatIndex(int index) {
        List<SeatMarker> rideable = getRideableSeatMarkers();
        return index >= 0 && index < rideable.size() && rideable.get(index).driverCab();
    }

    public List<Vec3> getAllSeatPositions() {
        if (seatMarkers.isEmpty()) {
            if (playerPositions.isEmpty()) {
                return seatPositions;
            }
            if (seatPositions.isEmpty()) {
                return playerPositions;
            }
            List<Vec3> seats = new ArrayList<>(playerPositions.size() + seatPositions.size());
            seats.addAll(playerPositions);
            seats.addAll(seatPositions);
            return List.copyOf(seats);
        }
        List<Vec3> seats = new ArrayList<>(seatMarkers.size());
        for (SeatMarker marker : seatMarkers) {
            seats.add(marker.position());
        }
        return List.copyOf(seats);
    }

    public List<Vec3> getRideableSeatPositions() {
        List<SeatMarker> rideable = getRideableSeatMarkers();
        if (rideable.isEmpty()) {
            return List.of();
        }
        List<Vec3> positions = new ArrayList<>(rideable.size());
        for (SeatMarker marker : rideable) {
            positions.add(marker.position());
        }
        return List.copyOf(positions);
    }

    public boolean hasSeatOffset() {
        return seatOffset != null;
    }

    public Vec3 getSeatOffset() {
        return seatOffset == null ? Vec3.ZERO : seatOffset;
    }

    public boolean hasScript() {
        return scriptPath != null && !scriptPath.isBlank();
    }

    public String getScriptPath() {
        return scriptPath;
    }

    public boolean hasSoundScript() {
        return soundScriptPath != null && !soundScriptPath.isBlank();
    }

    public String getSoundScriptPath() {
        return soundScriptPath;
    }

    public String getServerScriptPath() {
        return serverScriptPath;
    }

    public boolean hasServerScript() {
        return serverScriptPath != null && !serverScriptPath.isBlank();
    }

    /** Loader が JSON 読込後に設定する用。コンストラクタ大量改修を避けるため後付け。 */
    public void setServerScriptPath(String path) {
        this.serverScriptPath = path == null ? "" : path;
    }

    /** 本家 slotPos: {x, y, z, seatType} (ブロック単位)。Loader が JSON 読込後に設定。 */
    private java.util.List<float[]> slotPositions = java.util.List.of();

    public java.util.List<float[]> getSlotPositions() {
        return slotPositions;
    }

    public void setSlotPositions(java.util.List<float[]> slots) {
        this.slotPositions = slots == null ? java.util.List.of() : java.util.List.copyOf(slots);
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public boolean isCarType() {
        return "car".equalsIgnoreCase(vehicleType);
    }

    public String getDoorType() {
        return doorType;
    }

    public boolean hasAutomaticDoor() {
        return "automatic".equalsIgnoreCase(doorType) || "auto".equalsIgnoreCase(doorType);
    }

    public float getTrainDistance() {
        return trainDistance;
    }

    public int getDriverSeatIndex() {
        return driverSeatIndex;
    }

    public int getFrontDriverSeatIndex() {
        return frontDriverSeatIndex;
    }

    public int getRearDriverSeatIndex() {
        return rearDriverSeatIndex;
    }

    public List<DoorAnimationDefinition> getLeftDoors() {
        return leftDoors;
    }

    public List<DoorAnimationDefinition> getRightDoors() {
        return rightDoors;
    }

    public boolean hasDoorAnimations() {
        return !leftDoors.isEmpty() || !rightDoors.isEmpty();
    }

    public List<Float> getNotchMaxSpeeds() {
        return notchMaxSpeeds;
    }

    public float getAcceleration() {
        return acceleration;
    }

    public boolean isSmoothing() {
        return smoothing;
    }

    public List<String> getRollsignNames() {
        return rollsignNames;
    }

    public List<String> getCustomButtonNames() {
        return customButtonNames;
    }

    public List<List<String>> getCustomButtonOptions() {
        return customButtonOptions;
    }

    public String getRollsignTexture() {
        return rollsignTexture;
    }

    public List<RollsignDefinition> getRollsigns() {
        return rollsigns;
    }

    // --- RTMU 追加: 種別幕 (方向幕/rollsign とは別レイヤー) ---
    // 既存の巨大コンストラクタを崩さないよう、構築後に setTypeSign で注入する
    // (setWireAttachPos と同じ後付けパターン)。パネル定義は rollsign と同じ RollsignDefinition を流用。
    private List<String> typeSignNames = List.of();
    private String typeSignTexture = "";
    private List<RollsignDefinition> typeSigns = List.of();

    public void setTypeSign(List<String> names, String texture, List<RollsignDefinition> signs) {
        this.typeSignNames = names == null ? List.of() : List.copyOf(names);
        this.typeSignTexture = texture == null ? "" : texture;
        this.typeSigns = signs == null ? List.of() : List.copyOf(signs);
    }

    public List<String> getTypeSignNames() {
        return typeSignNames;
    }

    public String getTypeSignTexture() {
        return typeSignTexture;
    }

    public List<RollsignDefinition> getTypeSigns() {
        return typeSigns;
    }

    public List<LightDefinition> getHeadLights() {
        return headLights;
    }

    public List<LightDefinition> getTailLights() {
        return tailLights;
    }

    public List<LightDefinition> getInteriorLights() {
        return interiorLights;
    }

    public String getHornSound() {
        return hornSound;
    }

    public List<String> getAnnouncementSounds() {
        return announcementSounds;
    }

    /**
     * 本家 sound_Announcement の表示名 ([[表示名, 音声パス], ...] の表示名側)。
     * announcementSounds と同じ順・同じ長さ。名前未指定のエントリは空文字。
     */
    public List<String> getAnnouncementNames() {
        return announcementNames;
    }

    public void setAnnouncementNames(List<String> names) {
        this.announcementNames = names == null ? List.of() : List.copyOf(names);
    }

    public String getSoundStop() {
        return soundStop;
    }

    public String getSoundStartAcceleration() {
        return soundStartAcceleration;
    }

    public String getSoundAcceleration() {
        return soundAcceleration;
    }

    public String getSoundDeceleration() {
        return soundDeceleration;
    }

    public String getSoundDecelerationStop() {
        return soundDecelerationStop;
    }

    public String getSoundBrakeRelease() {
        return soundBrakeRelease;
    }

    public String getSoundBrakeRelease2() {
        return soundBrakeRelease2;
    }

    public void setBrakeReleaseSounds(String brakeRelease, String brakeRelease2) {
        this.soundBrakeRelease = brakeRelease == null ? "" : brakeRelease;
        this.soundBrakeRelease2 = brakeRelease2 == null ? "" : brakeRelease2;
    }

    public String getSoundDoorOpen() {
        return soundDoorOpen;
    }

    public String getSoundDoorClose() {
        return soundDoorClose;
    }

    public void setDoorSounds(String doorOpen, String doorClose) {
        this.soundDoorOpen = doorOpen == null ? "" : doorOpen;
        this.soundDoorClose = doorClose == null ? "" : doorClose;
    }

    public boolean hasJsonRunningSounds() {
        return !soundStop.isBlank()
            || !soundStartAcceleration.isBlank()
            || !soundAcceleration.isBlank()
            || !soundDeceleration.isBlank()
            || !soundDecelerationStop.isBlank();
    }

    public void setJsonRunningSounds(String soundStop, String soundStartAcceleration,
                                     String soundAcceleration, String soundDeceleration,
                                     String soundDecelerationStop) {
        this.soundStop = soundStop == null ? "" : soundStop;
        this.soundStartAcceleration = soundStartAcceleration == null ? "" : soundStartAcceleration;
        this.soundAcceleration = soundAcceleration == null ? "" : soundAcceleration;
        this.soundDeceleration = soundDeceleration == null ? "" : soundDeceleration;
        this.soundDecelerationStop = soundDecelerationStop == null ? "" : soundDecelerationStop;
    }

    public boolean isDoCulling() {
        return doCulling;
    }

    public boolean isRenderLight() {
        return renderLight;
    }

    public boolean isNotDisplayCab() {
        return notDisplayCab;
    }

    public boolean isSingleTrain() {
        return singleTrain;
    }

    private static List<List<String>> toImmutableNestedList(List<List<String>> options) {
        if (options.isEmpty()) {
            return List.of();
        }
        List<List<String>> copy = new ArrayList<>(options.size());
        for (List<String> option : options) {
            copy.add(option == null ? List.of() : List.copyOf(option));
        }
        return List.copyOf(copy);
    }

    private static List<SeatMarker> buildSeatMarkers(List<Vec3> seatPositions, List<Vec3> playerPositions) {
        List<SeatMarker> markers = new ArrayList<>();
        if (playerPositions != null) {
            for (Vec3 playerPos : playerPositions) {
                markers.add(new SeatMarker(playerPos, SEAT_TYPE_DRIVER_CAB, true));
            }
        }
        if (seatPositions != null) {
            for (Vec3 seatPos : seatPositions) {
                markers.add(new SeatMarker(seatPos, 1, false));
            }
        }
        return List.copyOf(markers);
    }
}
