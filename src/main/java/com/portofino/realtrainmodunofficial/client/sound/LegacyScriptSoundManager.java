package com.portofino.realtrainmodunofficial.client.sound;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.entity.TrainEntity;
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
    private static final Map<String, LoopingTrainSound> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<UUID, AutoRunningSoundState> AUTO_RUNNING = new ConcurrentHashMap<>();
    private static final Map<String, Long> ONE_SHOT_LAST_PLAY_TICK = new ConcurrentHashMap<>();
    // スピーカー等 playAt の在世界音を位置キーで保持(ブロック破壊時に stopAt で停止するため)。
    private static final Map<String, SimpleSoundInstance> SPEAKER_SOUNDS = new ConcurrentHashMap<>();
    private static final long ONE_SHOT_DEBOUNCE_MS = 180L;

    private LegacyScriptSoundManager() {
    }

    public static void play(TrainEntity train, String namespace, String soundName, float volume, float pitch) {
        play(train, namespace, soundName, volume, pitch, true);
    }

    public static void playLegacyId(TrainEntity train, String legacySoundId, float volume, float pitch, boolean looping) {
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
        play(train, namespace, soundName, volume, pitch, looping);
    }

    public static void play(TrainEntity train, String namespace, String soundName, float volume, float pitch, boolean looping) {
        if (train == null || !train.level().isClientSide()) {
            return;
        }
        ResourceLocation soundId = toSoundId(namespace, soundName);
        if (soundId == null) {
            return;
        }
        if (volume <= 0.001F) {
            if (looping) {
                stop(train, soundId);
            }
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSoundManager() == null) {
            return;
        }
        if (!looping) {
            String oneShotKey = key(train.getUUID(), soundId);
            long now = System.currentTimeMillis();
            Long lastPlay = ONE_SHOT_LAST_PLAY_TICK.get(oneShotKey);
            if (lastPlay != null && now - lastPlay < ONE_SHOT_DEBOUNCE_MS) {
                return;
            }
            ONE_SHOT_LAST_PLAY_TICK.put(oneShotKey, now);
            minecraft.getSoundManager().play(new SimpleSoundInstance(
                soundId,
                SoundSource.NEUTRAL,
                Mth.clamp(volume, 0.0F, 8.0F),
                Mth.clamp(pitch, 0.05F, 4.0F),
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
        String key = key(train.getUUID(), soundId);
        LoopingTrainSound sound = ACTIVE.get(key);
        if (sound == null || sound.isStopped()) {
            if (sound != null) {
                ACTIVE.remove(key, sound);
            }
            sound = new LoopingTrainSound(train, soundId);
            sound.update(volume, pitch);
            ACTIVE.put(key, sound);
            minecraft.getSoundManager().play(sound);
        } else {
            sound.update(volume, pitch);
        }
    }

    /**
     * 任意のワールド座標で 1 回サウンドを鳴らす（スピーカー用）。
     * soundIdStr は "namespace:path" 形式のサウンドイベントID。
     * volume を上げると可聴範囲が広がる（MC の LINEAR 減衰は概ね volume×16 ブロック）。
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
            Mth.clamp(volume, 0.0F, 16.0F),
            Mth.clamp(pitch, 0.05F, 4.0F),
            SoundInstance.createUnseededRandom(),
            false,
            0,
            SoundInstance.Attenuation.LINEAR,
            x,
            y,
            z,
            false
        );
        // 位置キーで保持し、ブロック破壊時に stopAt() で止められるようにする
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

    public static void tickJsonRunningSound(TrainEntity train) {
        if (train == null || !train.level().isClientSide()) {
            return;
        }
        VehicleDefinition definition = VehicleRegistry.getById(train.getVehicleId());
        if (definition == null || definition.hasSoundScript() || !definition.hasJsonRunningSounds()) {
            stopAutoRunningSound(train);
            return;
        }

        AutoRunningSoundState state = AUTO_RUNNING.computeIfAbsent(train.getUUID(), ignored -> new AutoRunningSoundState());
        float speed = Math.abs(train.getSpeed());
        boolean moving = speed > 0.0025F;
        boolean powering = train.getNotch() > 0;
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

        float volume = moving ? Mth.clamp(0.45F + speed * 7.5F, 0.35F, 1.35F) : 0.55F;
        float pitch = shouldPitchJsonRunningSound(definition, speed)
            ? Mth.clamp(0.65F + speed * 5.0F, 0.65F, 1.75F)
            : 1.0F;
        play(train, soundId.getNamespace(), soundId.getPath(), volume, pitch, true);
    }

    private static String selectJsonRunningSound(VehicleDefinition definition, TrainEntity train,
                                                 float speed, boolean moving, boolean accelerating) {
        if (!moving) {
            return definition.getSoundStop();
        }
        float startSpeed = getFirstConfiguredMaxSpeed(definition);
        if (speed < startSpeed) {
            return accelerating
                ? firstNonBlank(definition.getSoundStartAcceleration(), definition.getSoundAcceleration())
                : firstNonBlank(definition.getSoundDecelerationStop(), definition.getSoundDeceleration(), definition.getSoundStop());
        }
        return accelerating
            ? firstNonBlank(definition.getSoundAcceleration(), definition.getSoundStartAcceleration())
            : firstNonBlank(definition.getSoundDeceleration(), definition.getSoundDecelerationStop(), definition.getSoundStop());
    }

    private static boolean shouldPitchJsonRunningSound(VehicleDefinition definition, float speed) {
        float startSpeed = getFirstConfiguredMaxSpeed(definition);
        return speed >= startSpeed;
    }

    private static float getFirstConfiguredMaxSpeed(VehicleDefinition definition) {
        if (definition == null || definition.getNotchMaxSpeeds().isEmpty()) {
            return 0.06F;
        }
        for (Float speed : definition.getNotchMaxSpeeds()) {
            if (speed != null && speed > 0.0F) {
                return Math.max(0.005F, speed / 72.0F);
            }
        }
        return 0.06F;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    public static void stop(TrainEntity train, String namespace, String soundName) {
        if (train == null) {
            return;
        }
        ResourceLocation soundId = toSoundId(namespace, soundName);
        if (soundId == null) {
            return;
        }
        LoopingTrainSound sound = ACTIVE.remove(key(train.getUUID(), soundId));
        if (sound != null) {
            sound.requestStop();
        }
    }

    private static void stop(TrainEntity train, ResourceLocation soundId) {
        if (train == null || soundId == null) {
            return;
        }
        LoopingTrainSound sound = ACTIVE.remove(key(train.getUUID(), soundId));
        if (sound != null) {
            sound.requestStop();
        }
    }

    public static void stopAutoRunningSound(TrainEntity train) {
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
        String resolvedNamespace = namespace == null || namespace.isBlank() ? "minecraft" : namespace.toLowerCase(java.util.Locale.ROOT);
        String resolvedPath = soundName.trim().replace('\\', '/').toLowerCase(java.util.Locale.ROOT);
        if (resolvedPath.startsWith("sounds/")) {
            resolvedPath = resolvedPath.substring("sounds/".length());
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

    private static final class LoopingTrainSound extends AbstractTickableSoundInstance {
        private final TrainEntity train;
        // 一定tick更新(update)されなければ自動停止する。サウンドスクリプトが詰む/無効化される等で
        // 供給が止まった時にループ音が鳴り続ける「無限音」を防ぐ。正常な音は毎tick update されるので無害。
        private int ticksSinceUpdate = 0;

        private LoopingTrainSound(TrainEntity train, ResourceLocation soundId) {
            super(SoundEvent.createVariableRangeEvent(soundId), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.train = train;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.0F;
            this.pitch = 1.0F;
            this.relative = false;
            this.x = train.getX();
            this.y = train.getY();
            this.z = train.getZ();
        }

        private void update(float volume, float pitch) {
            this.volume = Mth.clamp(volume, 0.0F, 8.0F);
            this.pitch = Mth.clamp(pitch, 0.05F, 4.0F);
            this.x = train.getX();
            this.y = train.getY();
            this.z = train.getZ();
            this.ticksSinceUpdate = 0;
        }

        private void requestStop() {
            stop();
        }

        @Override
        public void tick() {
            if (!train.isAlive()) {
                ACTIVE.remove(key(train.getUUID(), this.getLocation()), this);
                AUTO_RUNNING.remove(train.getUUID());
                stop();
                return;
            }
            // 一定tick(=10tick/0.5秒)更新が来なければ自動停止。供給元(JSON/スクリプト)が
            // 正常なら毎tick update() でリセットされるため発火しない。詰んだ/無効化された
            // サウンドスクリプトが鳴らしっぱなしにする「無限音」のフェイルセーフ。
            if (++ticksSinceUpdate > 10) {
                ACTIVE.remove(key(train.getUUID(), this.getLocation()), this);
                AUTO_RUNNING.remove(train.getUUID());
                stop();
                return;
            }
            this.x = train.getX();
            this.y = train.getY();
            this.z = train.getZ();
        }
    }
}
