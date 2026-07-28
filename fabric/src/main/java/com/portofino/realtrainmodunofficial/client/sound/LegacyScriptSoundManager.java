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
    // 本家 SoundUpdaterVehicle.playingSounds の移植:
    // (列車UUID|サウンドID) → 追跡中サウンド。
    private static final Map<String, TrainScriptSound> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<UUID, AutoRunningSoundState> AUTO_RUNNING = new ConcurrentHashMap<>();
    // スピーカー等 playAt の在世界音を位置キーで保持(ブロック破壊時に stopAt で停止するため)。
    private static final Map<String, SimpleSoundInstance> SPEAKER_SOUNDS = new ConcurrentHashMap<>();
    // 消えた列車の登録を掃除する頻度 (play 呼び出し回数)
    private static int pruneCounter;

    /**
     * 音量を安全化。NaN → 0 (無音)。
     * パックの音スクリプトの音量補間 (fadeCon 等) がゼロ除算で NaN / ±Infinity を返すことがあり、
     * Mth#clamp は NaN をそのまま素通しする (Math.max(NaN,0)==NaN)。
     */
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

    private LegacyScriptSoundManager() {
    }

    // ---- 列車エンティティの両対応 ----
    // RTMU には列車エンティティが 2 系統ある:

    /** その Entity が列車か。 */
    public static boolean isTrain(Entity e) {
        return e instanceof TrainEntity || e instanceof jp.ngt.rtm.entity.train.EntityTrainBase;
    }

    /**
     * クライアントのカメラ (プレイヤー視点) から distance ブロックより遠いか。
     * 可聴距離外の列車のサウンド処理 (毎tick Nashorn) をスキップする軽量化判定用。
     */
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
        if (legacySoundId == null || legacySoundId.isBlank()) {
            return;
        }
        String namespace = "rtm";
        String soundName = legacySoundId;
        int separator = legacySoundId.indexOf(':');
        if (separator >= 0) {
            namespace = legacySoundId.substring(0, separator);
            soundName = legacySoundId.substring(separator + 1);
        }
        play(train, namespace, soundName, volume, pitch, looping, bypassOneShotSuppression);
    }

    public static void play(Entity train, String namespace, String soundName, float volume, float pitch, boolean looping) {
        play(train, namespace, soundName, volume, pitch, looping, false);
    }

    /**
     * ノッチ (マスコン/ブレーキハンドル) 操作音か。この音だけはラッチ (登録制) の対象外で、
     * 呼ばれた回数だけ鳴らす (連続ノッチ操作のガタガタ音は本家挙動)。
     */
    private static boolean isNotchSound(ResourceLocation soundId) {
        String path = soundId.getPath().toLowerCase(java.util.Locale.ROOT);
        return path.contains("lever") || path.contains("notch");
    }

    /**
     * サーバー発の離散イベント音 (マスコンのレバー音・警笛など) 用。スクリプトが毎tick要求する
     * 一発音 (コンプレッサ等) と違い、送られてきた回数だけ鳴ってよい
     * (連続ノッチ操作でレバー音がガタガタ鳴るのが正: 本家挙動)。
     * @param bypassOneShotSuppression true = 一発音の「再生中は鳴らし直さない」抑制とデバウンスを無視する。
     */
    public static void play(Entity train, String namespace, String soundName, float volume, float pitch,
                            boolean looping, boolean bypassOneShotSuppression) {
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
        // ノッチ (マスコン/ブレーキハンドル) のレバー音はラッチ対象外:
        // 連続ノッチ操作で操作した回数だけガタガタ鳴るのが正 (本家挙動)。
        boolean notchSound = !looping && isNotchSound(soundId);
        // サーバー発の離散イベント音 (レバー音・警笛など) は追跡せず毎回そのまま鳴らす
        // (本家もこれらは SoundUpdater ではなく都度 playSound)。
        if (!looping && (bypassOneShotSuppression || notchSound)) {
            minecraft.getSoundManager().play(new SimpleSoundInstance(
                soundId,
                SoundSource.NEUTRAL,
                safeVolume(volume, 8.0F),
                safePitch(pitch),
                SoundInstance.createUnseededRandom(),
                false,
                0,
                SoundInstance.Attenuation.LINEAR,
                train.getX(),
                train.getY(),
                train.getZ(),
                false
            ));
            return;
        }
        // ---- 本家 SoundUpdaterVehicle.playSound の忠実移植 ----
        // 既に登録済み (playingSounds 相当) なら、ループ/一発音を問わず音量・ピッチ更新のみ。
        // 一発音は鳴り終わっても登録が残るので、毎 tick 呼ばれても再発火しない (= 本家のラッチ)。
        String key = key(train.getUUID(), soundId);
        TrainScriptSound sound = ACTIVE.get(key);
        if (sound != null && !sound.isStopped()) {
            sound.update(volume, pitch);
            return;
        }
        if (sound != null) {
            // 明示 stop 済み / 列車消滅で止まった残骸 → 作り直す
            ACTIVE.remove(key, sound);
        }
        // 新規作成: サニタイズ後の音量が 0 以下なら登録も再生もしない。
        // NaN/±Infinity や fadeIn 開始点の 0 で「音量0のまま SoundEngine にスキップされ、
        // チャンネルが無いのに ACTIVE に居座って二度と復活しない」のを防ぐ。
        float sanVol = safeVolume(volume, 1.0F);
        if (sanVol <= 0.0F) {
            return;
        }
        sound = new TrainScriptSound(train, soundId, looping);
        sound.update(volume, pitch);
        ACTIVE.put(key, sound);
        minecraft.getSoundManager().play(sound);
        // 消えた列車の登録をたまに掃除 (ラッチは isStopped=false なので消えない)
        if (++pruneCounter >= 256) {
            pruneCounter = 0;
            ACTIVE.entrySet().removeIf(entry -> !entry.getValue().train.isAlive());
        }
    }

    /**
     * 任意のワールド座標で 1 回サウンドを鳴らす（スピーカー用）。
     * soundIdStr は "namespace:path" 形式のサウンドイベントID。
     */
    public static void playAt(double x, double y, double z, String soundIdStr, float volume, float pitch) {
        if (soundIdStr == null || soundIdStr.isBlank()) {
            return;
        }
        ResourceLocation soundId = ResourceLocation.tryParse(soundIdStr.trim().toLowerCase(java.util.Locale.ROOT));
        if (soundId == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSoundManager() == null) {
            return;
        }
        SimpleSoundInstance instance = new SimpleSoundInstance(
            soundId,
            SoundSource.RECORDS,
            safeVolume(volume, 16.0F),
            safePitch(pitch),
            SoundInstance.createUnseededRandom(),
            false,
            0,
            SoundInstance.Attenuation.LINEAR,
            x,
            y,
            z,
            false
        );
        // 位置キーで保持し、ブロック破壊時に stopAt で止められるようにする
        // (スピーカーの長い音がブロックを壊しても鳴り続ける問題の対策)。
        String key = posKey(x, y, z);
        SimpleSoundInstance prev = SPEAKER_SOUNDS.put(key, instance);
        if (prev != null) {
            minecraft.getSoundManager().stop(prev);
        }
        minecraft.getSoundManager().play(instance);
    }

    /** 位置キー(整数ブロック座標)。同一ブロックの再生を1つに保つ。 */
    private static String posKey(double x, double y, double z) {
        return (int) Math.floor(x) + "," + (int) Math.floor(y) + "," + (int) Math.floor(z);
    }

    /** 指定位置(ブロック)で playAt した音を停止する。スピーカーブロック破壊時に呼ぶ。 */
    public static void stopAt(double x, double y, double z) {
        SimpleSoundInstance s = SPEAKER_SOUNDS.remove(posKey(x, y, z));
        if (s != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().stop(s);
            }
        }
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

        // 本家 MovingSoundEntity は音量を速度で変えない (作成時の値のまま)。
        float volume = 1.0F;
        float pitch = runningSoundPitch(definition, speed);
        play(train, soundId.getNamespace(), soundId.getPath(), volume, pitch, true);
    }

    /**
     * 本家 SoundUpdaterTrain.getSound の移植。
     * speed > 0 なら acceleration = EnumNotch.getAcceleration(notch, speed)
     * speed < maxSpeed[0] : acceleration > 0 ? sound_S_A : sound_D_S
     * それ以外            : acceleration > 0 ? sound_Acceleration : sound_Deceleration
     * speed == 0 なら sound_Stop
     */
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

    /**
     * 本家 MovingSoundTrain.func_73660_a のピッチ。
     * (speed - maxSpeed[0]) / (maxSpeed[4] - maxSpeed[0]) + 1.0。
     */
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

    /**
     * 本家 SoundUpdaterVehicle.stopSound: 登録から外して停止。
     * 一発音の場合はラッチ解除でもあり、次の playSound でまた 1 回鳴らせるようになる。
     */
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

    /** その列車が鳴らしている音を全部止める (本家 SoundUpdater.stopAllSounds 相当)。 */
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
        // 最短間隔(70ms)のデバウンスで連続スパムを抑える(1段ずつの操作は普通に鳴る)。
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
        // 生成側 (ExternalSoundPackBridge) と同じ規則で空白・大文字を安全化してから ResourceLocation 化する。
        String resolvedNamespace = namespace == null || namespace.isBlank() ? "minecraft" : ExternalSoundPackBridge.sanitizeSoundPath(namespace);
        String resolvedPath = ExternalSoundPackBridge.sanitizeSoundPath(soundName.trim().replace('\\', '/'));
        if (resolvedPath.startsWith("sounds/")) {
            resolvedPath = resolvedPath.substring("sounds/".length());
        } else if (resolvedPath.startsWith("sound/")) {
            // 1.7.10 のアセットは sounds/ ではなく sound/ 配下。
            // ("sound/train/DoorOpn" 等) はこの綴りで来るので同じように剥がす。
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

    /**
     * 本家 MovingSoundEntity の移植: 列車に追従する MovingSound。
     * repeat=true でループ、false で一発音 (鳴り終わっても isStopped は false のままなので
     * ACTIVE の登録が残り続け、stopSound されるまで再発火しない = 本家のラッチ)。
     */
    private static final class TrainScriptSound extends AbstractTickableSoundInstance {
        private final Entity train;
        private final boolean repeat;
        /** 最後にスクリプトから再生要求された時刻 (ms)。ループ音の鳴りっぱなし対策に使う。 */
        private volatile long lastRequestMs;

        // 列車の車体音の可聴距離。実際の減衰は sounds.json の attenuation_distance=45
        // (ExternalSoundPackBridge が生成時に付与) × max(音量,1) で決まり、45 ブロックかけて
        // 線形にゼロへ落ちる。SoundEvent の range は減衰計算に使われないが、値は合わせておく。
        private static final float TRAIN_SOUND_RANGE = 45.0F;

        private TrainScriptSound(Entity train, ResourceLocation soundId, boolean repeat) {
            super(SoundEvent.createFixedRangeEvent(soundId, TRAIN_SOUND_RANGE),
                    SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.train = train;
            this.repeat = repeat;
            this.looping = repeat;
            this.delay = 0;
            this.volume = 0.0F;
            this.pitch = 1.0F;
            this.relative = false;
            this.lastRequestMs = System.currentTimeMillis();
            this.x = train.getX();
            this.y = train.getY();
            this.z = train.getZ();
        }

        private void update(float volume, float pitch) {
            // 実減衰距離は max(音量,1.0) × attenuation_distance(45)。音量を 1.0 に制限すると
            // きっかり 45 ブロックで線形にゼロへ落ちる (音量>1 は近くの大きさを変えず範囲だけ
            // 伸ばすため、制限しても近距離の聞こえ方は変わらない)。ループ・一発音とも。
            float maxVol = 1.0F;
            this.volume = safeVolume(volume, maxVol);
            this.pitch = safePitch(pitch);
            this.lastRequestMs = System.currentTimeMillis();
            this.x = train.getX();
            this.y = train.getY();
            this.z = train.getZ();
        }

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
            // ループ音がスクリプトから要求されなくなったら (チャンク遠方・非描画で走行スクリプトが
            // 回らなくなった等) 止める。本家 SoundUpdaterVehicle は「今 update で要求されない音は止める」
            // 方式。要求が途絶えて 400ms 経ったら停止 (鳴りっぱなしの走行音を消す)。
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
