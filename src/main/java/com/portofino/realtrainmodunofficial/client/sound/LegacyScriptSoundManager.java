package com.portofino.realtrainmodunofficial.client.sound;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.entity.TrainEntity;
import net.minecraft.world.entity.Entity;
import com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition;
import com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LegacyScriptSoundManager {
    // 本家 SoundUpdaterVehicle.playingSounds の移植: (列車UUID…
    private static final Map<String, TrainScriptSound> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<UUID, AutoRunningSoundState> AUTO_RUNNING = new ConcurrentHashMap<>();
    // スピーカー等 playOriginal の在世界音を位置キーで保持 (stopAt で停止)。
    private static final Map<String, jp.ngt.ngtlib.sound.MovingSoundBase> SPEAKER_SOUNDS = new ConcurrentHashMap<>();
    // 消えた列車の登録を掃除する頻度 (play 呼び出し回数)
    private static int pruneCounter;

    /** 音量を安全化。NaN → 0 (無音)。 パックの音スクリプトの音量補間 (fadeCon 等)… */
    static float safeVolume(float v, float max) {
        if (Float.isNaN(v)) {
            return 0.0F;
        }
        return Mth.clamp(v, 0.0F, max);
    }

    /** ピッチを安全化。NaN → 1.0。 */
    static float safePitch(float p) {
        if (Float.isNaN(p)) {
            return 1.0F;
        }
        return Mth.clamp(p, 0.05F, 4.0F);
    }

    public static float rangeVolume(float volume,float master,double distance,float range) {
        if(!(range>0) || !Float.isFinite(range) || !Double.isFinite(distance))return 0;
        return safeVolume(volume*master,8F)*(float)Math.max(0,1-Math.max(0,distance)/range);
    }

    private LegacyScriptSoundManager() {
    }

    // ---- 列車エンティティの両対応 ---- RTMU には列車エンティティが 2 系統ある:

    /** その Entity が列車か。 */
    public static boolean isTrain(Entity e) {
        return e instanceof TrainEntity || e instanceof jp.ngt.rtm.entity.train.EntityTrainBase;
    }

    /** クライアントのカメラ (プレイヤー視点) から distance ブロックより遠いか。 */
    public static boolean beyondCameraRange(Entity entity, double distance) {
        Minecraft mc = Minecraft.getInstance();
        Entity camera = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
        if (camera == null || entity == null) {
            return false;
        }
        return entity.distanceToSqr(camera) > distance * distance;
    }

    /** 車両定義 ID (パックの ModelTrain_*.json の name)。 */
    private static String vehicleIdOf(Entity e) {
        if (e instanceof TrainEntity t) {
            return t.getVehicleId();
        }
        if (e instanceof jp.ngt.rtm.entity.train.EntityTrainBase t) {
            // 本家側は getModelName が定義 ID にあたる
            return t.getModelName();
        }
        return "";
    }

    /** ノッチ (負=ブレーキ, 正=力行)。 */
    private static int notchOf(Entity e) {
        if (e instanceof TrainEntity t) {
            return t.getNotch();
        }
        if (e instanceof jp.ngt.rtm.entity.train.EntityTrainBase t) {
            return t.getNotch();
        }
        return 0;
    }

    /** 速度。 */
    private static float speedOf(Entity e) {
        if (e instanceof TrainEntity t) {
            return t.getSpeed();
        }
        if (e instanceof jp.ngt.rtm.entity.train.EntityTrainBase t) {
            return t.getSpeed();
        }
        return 0.0F;
    }

    public static void play(Entity train, String namespace, String soundName, float volume, float pitch) {
        play(train, namespace, soundName, volume, pitch, true);
    }

    public static void playLegacyId(Entity train, String legacySoundId, float volume, float pitch, boolean looping) {
        playLegacyId(train, legacySoundId, volume, pitch, looping, false);
    }

    public static void playLegacyId(Entity train, String legacySoundId, float volume, float pitch,
                                    boolean looping, boolean bypassOneShotSuppression) {
        playLegacyId(train,legacySoundId,volume,pitch,looping,bypassOneShotSuppression,jp.ngt.rtm.RTMConfig.trainRunningSoundRange);
    }
    public static void playLegacyId(Entity train,String legacySoundId,float volume,float pitch,boolean looping,boolean bypassOneShotSuppression,float range) {
        if (legacySoundId == null || legacySoundId.isBlank()) return;
        String namespace = "rtm";
        String soundName = legacySoundId;
        int separator = legacySoundId.indexOf(':');
        if (separator >= 0) {
            namespace = legacySoundId.substring(0, separator);
            soundName = legacySoundId.substring(separator + 1);
        }
        play(train, namespace, soundName, volume, pitch, looping, bypassOneShotSuppression,range);
    }

    public static void play(Entity train, String namespace, String soundName, float volume, float pitch, boolean looping) {
        play(train, namespace, soundName, volume, pitch, looping, false);
    }

    /** ノッチ (マスコン/ブレーキハンドル) 操作音か。 */
    private static boolean isNotchSound(ResourceLocation soundId) {
        String path = soundId.getPath().toLowerCase(java.util.Locale.ROOT);
        return path.contains("lever") || path.contains("notch");
    }

    /** サーバー発の離散イベント音 (マスコンのレバー音・警笛など) 用。 */
    public static void play(Entity train, String namespace, String soundName, float volume, float pitch,
                            boolean looping, boolean bypassOneShotSuppression) {
        play(train,namespace,soundName,volume,pitch,looping,bypassOneShotSuppression,jp.ngt.rtm.RTMConfig.trainRunningSoundRange);
    }
    public static void play(Entity train,String namespace,String soundName,float volume,float pitch,boolean looping,boolean bypassOneShotSuppression,float audibleRange) {
        if (train == null || !train.level().isClientSide()) {
            return;
        }
        ResourceLocation soundId = toSoundId(namespace, soundName);
        if (soundId == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSoundManager() == null) {
            return;
        }
        // 本家の離散イベント音 (レバー/警笛/ドア/緩解音など) は、 呼ばれた回数だけ重ねて鳴らす。
        if (!looping) {
            TrainScriptSound once = new TrainScriptSound(train, soundId, false, audibleRange);
            once.update(volume, pitch, audibleRange);
            minecraft.getSoundManager().play(once);
            return;
        }
        // ---- 本家 SoundUpdaterVehicle.playSound の忠実移植 ----…
        String key = key(train.getUUID(), soundId);
        TrainScriptSound sound = ACTIVE.get(key);
        if (sound != null && !sound.isStopped()) {
            sound.update(volume, pitch,audibleRange);
            return;
        }
        if (sound != null) {
            // 明示 stop 済み / 列車消滅で止まった残骸 → 作り直す
            ACTIVE.remove(key, sound);
        }
        // 新規作成: 音量 0 (無音) なら登録も再生もしない。
        if (!TrainScriptSound.audible(volume, audibleRange)) {
            return;
        }
        sound = new TrainScriptSound(train, soundId, looping, audibleRange);
        sound.update(volume, pitch,audibleRange);
        ACTIVE.put(key, sound);
        minecraft.getSoundManager().play(sound);
        // 消えた列車の登録をたまに掃除 (ラッチは isStopped=false なので消えない)
        if (++pruneCounter >= 256) {
            pruneCounter = 0;
            ACTIVE.entrySet().removeIf(entry -> !entry.getValue().train.isAlive());
        }
    }

    /** 本家 ClientProxy.playSound のクライアント側処理: エンティティなら Mov… */
    public static void playOriginal(int entityId, double x, double y, double z,
                                    String soundIdStr, float volume, float pitch, float range) {
        if (soundIdStr == null || soundIdStr.isBlank()) {
            return;
        }
        ResourceLocation soundId = ResourceLocation.tryParse(soundIdStr.trim().toLowerCase(java.util.Locale.ROOT));
        if (soundId == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSoundManager() == null || minecraft.level == null) {
            return;
        }
        float safeRange = Float.isFinite(range) ? Mth.clamp(range, 0.0F, 256.0F) : 16.0F;
        jp.ngt.ngtlib.sound.MovingSoundBase instance;
        if (entityId >= 0) {
            Entity entity = minecraft.level.getEntity(entityId);
            if (entity == null) {
                return;
            }
            instance = new jp.ngt.rtm.sound.MovingSoundEntity(entity, soundId, false, safeRange);
        } else {
            var blockEntity = minecraft.level
                    .getBlockEntity(net.minecraft.core.BlockPos.containing(x, y, z));
            if (blockEntity == null) {
                return;
            }
            instance = new jp.ngt.rtm.sound.MovingSoundTileEntity(blockEntity, soundId, false, safeRange);
        }
        if (volume >= 0.0F && instance instanceof jp.ngt.rtm.sound.MovingSoundCustom<?> moving) {
            moving.setVolume(volume);
            moving.setPitch(pitch);
        }
        String key = posKey(x, y, z);
        jp.ngt.ngtlib.sound.MovingSoundBase prev = SPEAKER_SOUNDS.put(key, instance);
        if (prev != null && !prev.isStopped()) {
            prev.stop();
        }
        minecraft.getSoundManager().play(instance);
    }

    /** 指定位置(ブロック)で playOriginal した音を停止する。スピーカーブロック破壊時に呼ぶ。 */
    public static void stopAt(double x, double y, double z) {
        jp.ngt.ngtlib.sound.MovingSoundBase s = SPEAKER_SOUNDS.remove(posKey(x, y, z));
        if (s != null) {
            s.stop();
        }
    }

    /** 位置キー(整数ブロック座標)。同一ブロックの再生を1つに保つ。 */
    private static String posKey(double x, double y, double z) {
        return (int) Math.floor(x) + "," + (int) Math.floor(y) + "," + (int) Math.floor(z);
    }

    public static void tickJsonRunningSound(Entity train) {
        if (train == null || !train.level().isClientSide()) {
            return;
        }
        VehicleDefinition definition = VehicleRegistry.getById(vehicleIdOf(train));
        if (definition == null || definition.hasSoundScript() || !definition.hasJsonRunningSounds()) {
            stopAutoRunningSound(train);
            return;
        }

        AutoRunningSoundState state = AUTO_RUNNING.computeIfAbsent(train.getUUID(), ignored -> new AutoRunningSoundState());
        float speed = Math.abs(speedOf(train));
        boolean moving = speed > 0.0025F;
        boolean powering = notchOf(train) > 0;
        boolean accelerating = powering || speed > state.previousSpeed + 0.0005F;
        String sound = selectJsonRunningSound(definition, train, speed, moving, accelerating);
        state.previousSpeed = speed;

        if (sound == null || sound.isBlank()) {
            stopAutoRunningSound(train);
            return;
        }
        ResourceLocation soundId = toSoundIdFromLegacyString(sound);
        if (soundId == null) {
            stopAutoRunningSound(train);
            return;
        }
        if (state.currentSoundId != null && !state.currentSoundId.equals(soundId)) {
            stop(train, state.currentSoundId);
        }
        state.currentSoundId = soundId;

        // 本家の自動走行音は setVolume しない: 音量 = range/16 のまま。
        float pitch = runningSoundPitch(definition, speed);
        play(train, soundId.getNamespace(), soundId.getPath(), -1.0F, pitch, true);
    }

    /** 本家 SoundUpdaterTrain.getSound の移植。 */
    private static String selectJsonRunningSound(VehicleDefinition definition, Entity train,
                                                 float speed, boolean moving, boolean ignoredAccelerating) {
        if (!moving) {
            return definition.getSoundStop();
        }
        float acceleration = jp.ngt.rtm.entity.train.util.EnumNotch.getAcceleration(notchOf(train), speed);
        if (speed < maxSpeedAt(definition, 0)) {
            return acceleration > 0.0F ? definition.getSoundStartAcceleration() : definition.getSoundDecelerationStop();
        }
        return acceleration > 0.0F ? definition.getSoundAcceleration() : definition.getSoundDeceleration();
    }

    /** 本家 MovingSoundTrain.func_73660_a のピッチ。 */
    private static float runningSoundPitch(VehicleDefinition definition, float speed) {
        float low = maxSpeedAt(definition, 0);
        if (speed < low) {
            return 1.0F;
        }
        float high = maxSpeedAt(definition, 4);
        float range = high - low;
        if (!(range > 0.0F)) {
            return 1.0F;
        }
        return (speed - low) / range + 1.0F;
    }

    /** JSON の maxSpeed[i]。本家は blocks/tick のまま速度と直接比較する。 */
    private static float maxSpeedAt(VehicleDefinition definition, int index) {
        if (definition == null) {
            return 0.0F;
        }
        java.util.List<Float> speeds = definition.getNotchMaxSpeeds();
        if (speeds == null || speeds.isEmpty()) {
            return 0.0F;
        }
        Float value = speeds.get(Math.min(index, speeds.size() - 1));
        return value == null ? 0.0F : value;
    }


    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    /** 本家 SoundUpdaterVehicle.stopSound: 登録から外して停止。 */
    public static void stop(Entity train, String namespace, String soundName) {
        if (train == null) {
            return;
        }
        stop(train, toSoundId(namespace, soundName));
    }

    private static void stop(Entity train, ResourceLocation soundId) {
        if (train == null || soundId == null) {
            return;
        }
        TrainScriptSound sound = ACTIVE.remove(key(train.getUUID(), soundId));
        if (sound != null) {
            sound.requestStop();
        }
    }

    /** その列車が鳴らしている音を全部止める (本家 SoundUpdater.stopAllSounds… */
    public static void stopAll(Entity train) {
        if (train == null) {
            return;
        }
        String prefix = train.getUUID() + "|";
        ACTIVE.entrySet().removeIf(entry -> {
            if (!entry.getKey().startsWith(prefix)) {
                return false;
            }
            entry.getValue().requestStop();
            return true;
        });
        stopAutoRunningSound(train);
    }

    public static void stopAutoRunningSound(Entity train) {
        if (train == null) {
            return;
        }
        AutoRunningSoundState state = AUTO_RUNNING.remove(train.getUUID());
        if (state != null && state.currentSoundId != null) {
            stop(train, state.currentSoundId);
        }
    }

    private static long lastLeverClickMs = 0L;
    private static final long LEVER_CLICK_DEBOUNCE_MS = 70L;

    public static void playLeverClick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSoundManager() == null) {
            return;
        }
        // 何らかの経路でノッチ操作が毎tick/毎フレーム発火すると、レバー音が「だだだだ」と高速連続する。
        long now = System.currentTimeMillis();
        if (now - lastLeverClickMs < LEVER_CLICK_DEBOUNCE_MS) {
            return;
        }
        lastLeverClickMs = now;
        ResourceLocation soundId = ResourceLocation.fromNamespaceAndPath("rtm", "train.lever");
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(soundId), 1.0F, 0.55F));
    }

    private static String key(UUID trainId, ResourceLocation soundId) {
        return trainId + "|" + soundId;
    }

    private static ResourceLocation toSoundId(String namespace, String soundName) {
        if (soundName == null || soundName.isBlank()) {
            return null;
        }
        // 生成側 (ExternalSoundPackBridge) と同じ規則で空白・大文字を安全化してか…
        String resolvedNamespace = namespace == null || namespace.isBlank() ? "minecraft" : ExternalSoundPackBridge.sanitizeSoundPath(namespace);
        String resolvedPath = ExternalSoundPackBridge.sanitizeSoundPath(soundName.trim().replace('\\', '/'));
        if (resolvedPath.startsWith("sounds/")) {
            resolvedPath = resolvedPath.substring("sounds/".length());
        } else if (resolvedPath.startsWith("sound/")) {
            // 1.7.10 のアセットは sounds/ ではなく sound/ 配下。
            resolvedPath = resolvedPath.substring("sound/".length());
        }
        if (resolvedPath.endsWith(".ogg")) {
            resolvedPath = resolvedPath.substring(0, resolvedPath.length() - ".ogg".length());
        }
        if (resolvedNamespace.equals("rtm") && resolvedPath.indexOf('/') >= 0) {
            resolvedPath = resolvedPath.replace('/', '.');
        }
        try {
            return ResourceLocation.fromNamespaceAndPath(resolvedNamespace, resolvedPath);
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("Invalid legacy sound id {}:{}", resolvedNamespace, soundName);
            return null;
        }
    }

    public static ResourceLocation resolveLegacySoundId(String soundId) { return toSoundIdFromLegacyString(soundId); }

    private static ResourceLocation toSoundIdFromLegacyString(String legacySoundId) {
        if (legacySoundId == null || legacySoundId.isBlank()) {
            return null;
        }
        String namespace = "rtm";
        String soundName = legacySoundId;
        int separator = legacySoundId.indexOf(':');
        if (separator >= 0) {
            namespace = legacySoundId.substring(0, separator);
            soundName = legacySoundId.substring(separator + 1);
        }
        return toSoundId(namespace, soundName);
    }

    private static final class AutoRunningSoundState {
        private ResourceLocation currentSoundId;
        private float previousSpeed;
    }

    /** 本家 MovingSoundEntity の移植: 列車に追従する MovingSound。 */
    private static final class TrainScriptSound extends AbstractTickableSoundInstance {
        private final Entity train;
        private final boolean repeat;
        /** 最後にスクリプトから再生要求された時刻 (ms)。ループ音の鳴りっぱなし対策に使う。 */
        private volatile long lastRequestMs;
        private float range;

        private TrainScriptSound(Entity train, ResourceLocation soundId, boolean repeat, float range) {
            super(SoundEvent.createVariableRangeEvent(soundId),
                    SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.train = train;
            this.repeat = repeat;
            this.looping = repeat;
            this.delay = 0;
            this.range = Float.isFinite(range) ? Mth.clamp(range, 0.0F, 256.0F) : 16.0F;
            // 本家 MovingSoundCustom: 初期音量 = range/16 (setVolume…
            this.volume = this.range / 16.0F;
            this.pitch = 1.0F;
            this.relative = false;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.lastRequestMs = System.currentTimeMillis();
            this.x = train.getX();
            this.y = train.getY();
            this.z = train.getZ();
        }

        /** 新規作成してよいか (無音の登録を防ぐ)。volume < 0 は自動音量 (range/16)。 */
        static boolean audible(float volume, float range) {
            float effective = volume < 0.0F
                    ? (Float.isFinite(range) ? Mth.clamp(range, 0.0F, 256.0F) : 16.0F) / 16.0F
                    : volume * jp.ngt.rtm.RTMConfig.trainSoundVol;
            return Float.isFinite(effective) && effective > 0.0F;
        }

        private void update(float volume, float pitch, float range) {
            this.range = Float.isFinite(range) ? Mth.clamp(range, 0.0F, 256.0F) : 0.0F;
            this.pitch = safePitch(pitch);
            this.lastRequestMs = System.currentTimeMillis();
            if (volume >= 0.0F && Float.isFinite(volume)) {
                // 本家 setVolume: volume = volume * trainSoundVol。
                this.volume = Math.max(volume * jp.ngt.rtm.RTMConfig.trainSoundVol, 0.0F);
            }
            this.x = train.getX();
            this.y = train.getY();
            this.z = train.getZ();
        }

        @Override public boolean canStartSilent() { return true; }

        private void requestStop() {
            stop();
        }

        @Override
        public void tick() {
            // 本家 MovingSoundEntity.update: 列車が消えたら停止、生きていれば追従
            if (!train.isAlive()) {
                ACTIVE.remove(key(train.getUUID(), this.getLocation()), this);
                AUTO_RUNNING.remove(train.getUUID());
                stop();
                return;
            }
            // ループ音が要求されなくなったら止める (チャンク遠方等の鳴りっぱなし対策)。
            if (this.repeat && System.currentTimeMillis() - this.lastRequestMs > 400L) {
                ACTIVE.remove(key(train.getUUID(), this.getLocation()), this);
                stop();
                return;
            }
            this.x = train.getX();
            this.y = train.getY();
            this.z = train.getZ();
        }
    }
}
