package jp.ngt.rtm.sound;

import jp.ngt.ngtlib.io.ScriptUtil;
import jp.ngt.rtm.RTMConfig;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.entity.train.util.EnumNotch;
import jp.ngt.rtm.modelpack.cfg.TrainConfig;
import jp.ngt.rtm.modelpack.modelset.ModelSetBase;
import jp.ngt.rtm.modelpack.modelset.ModelSetVehicleBaseClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 本家 jp.ngt.rtm.sound.SoundUpdaterVehicle + SoundUp… */
public class SoundUpdaterTrain {
    private final Entity train;

    protected boolean silent;
    protected ResourceLocation prevSoundResource;
    protected MovingSoundVehicle prevSound;
    protected final List<MovingSoundVehicle> playingSounds = new ArrayList<>();
    protected Map<Integer, Object> dataMap;

    /** 本家 {sound1, bell}。 */
    public final MovingSoundEntity[] atsSound = new MovingSoundEntity[2];
    private int currentAtsSignal;
    private String atsModelName;

    /** ブロック/tick → km/h (1 block = 1m, 20tick = 1s → 3.6… */
    public static final float SPEED_TO_KMH = 72.0F;

    public SoundUpdaterTrain(Entity train) {
        this.train = train;
    }

    public SoundUpdaterTrain(Object soundHandler, Entity train) {
        this(train);
    }

    private static ResourceLocation normalize(ResourceLocation sound) {
        return sound == null ? null
                : com.portofino.realtrainmodunofficial.client.sound.LegacyScriptSoundManager
                .resolveLegacySoundId(sound.toString());
    }

    private static net.minecraft.client.sounds.SoundManager soundManager() {
        return Minecraft.getInstance().getSoundManager();
    }

    /** 本家 SoundUpdaterVehicle.update。 */
    public void update() {
        if (this.train == null || !this.train.level().isClientSide()) {
            return;
        }
        this.updateAts();

        ModelSetVehicleBaseClient<?> modelSet = this.modelSet();
        if (modelSet != null && modelSet.se != null) {
            // 自動走行音からスクリプト走行音へ切り替わったとき、古いループを残さない。
            this.stopAutomaticSound();
            ScriptUtil.doScriptIgnoreError(modelSet.se, "onUpdate", this);
            return;
        }

        if (this.train.isRemoved()) {
            this.stopAutomaticSound();
            return;
        }
        ResourceLocation newSound = this.getSound(modelSet);
        if (newSound == null || !newSound.equals(this.prevSoundResource)) {
            this.stopAutomaticSound();
            this.prevSoundResource = newSound;
            this.silent = true;
        }
        if (this.silent && this.prevSoundResource != null
                && (this.prevSound == null || !soundManager().isActive(this.prevSound))) {
            MovingSoundVehicle sound = createMovingSound(this.train, this.prevSoundResource,
                    true, this.changePitch());
            soundManager().play(sound);
            this.prevSound = sound;
            this.silent = false;
        }
    }

    /** 本家 SoundUpdaterTrain.update の ATS チャイム/ベル。 */
    public void updateAts() {
        if (this.train == null || !this.train.level().isClientSide()) {
            return;
        }
        ModelSetVehicleBaseClient<?> modelSet = this.modelSet();
        if (modelSet == null) {
            return;
        }
        int signal = this.signalOf(this.train);
        if (this.isControlCar(this.train) && Math.abs(speedOf(this.train)) > 0.0F) {
            switch (signal) {
                case 1 -> {
                    if (this.currentAtsSignal != 1) {
                        stopAlarm(0);
                        stopAlarm(1);
                        ResourceLocation chime = normalize(modelSet.sound_ATSChime);
                        ResourceLocation bell = normalize(modelSet.sound_ATSBell);
                        if (chime != null) {
                            this.atsSound[0] = new MovingSoundEntity(atsSource(), chime, true);
                            soundManager().play(this.atsSound[0]);
                        }
                        if (bell != null) {
                            this.atsSound[1] = new MovingSoundEntity(atsSource(), bell, true);
                            soundManager().play(this.atsSound[1]);
                        }
                        this.currentAtsSignal = 1;
                    }
                }
                case -1 -> {
                    if (this.currentAtsSignal != -1) {
                        stopAlarm(1);
                        this.currentAtsSignal = -1;
                    }
                }
                default -> {
                }
            }
        } else if (signal != -1 && this.currentAtsSignal != 0) {
            stopAlarm(0);
            stopAlarm(1);
            this.currentAtsSignal = 0;
        }
    }

    private void stopAlarm(int index) {
        if (this.atsSound[index] != null) {
            this.atsSound[index].stop();
            this.atsSound[index] = null;
        }
    }

    /** ATS 音を鳴らす位置。本家は台車に追従させる。 */
    private Entity atsSource() {
        if (this.train instanceof EntityTrainBase t) {
            var bogie = t.getBogie(t.getTrainDirection());
            if (bogie != null) {
                return bogie;
            }
        }
        return this.train;
    }

    protected MovingSoundVehicle createMovingSound(Entity vehicle, ResourceLocation sound,
                                                    boolean repeat, boolean changePitch) {
        if (vehicle instanceof EntityTrainBase || isTrainEntity(vehicle)) {
            return new MovingSoundTrain(vehicle, sound, repeat, changePitch);
        }
        return new MovingSoundVehicle(vehicle, sound, repeat, changePitch);
    }

    /** 本家 SoundUpdaterTrain.getSound。 */
    protected ResourceLocation getSound(ModelSetVehicleBaseClient<?> modelSet) {
        if (modelSet == null) {
            return null;
        }
        TrainConfig cfg = this.trainConfig();
        float speed = Math.abs(speedOf(this.train));
        if (speed <= 0.0F) {
            return normalize(modelSet.sound_Stop);
        }
        if (cfg != null && cfg.maxSpeed != null && cfg.maxSpeed.length > 0) {
            float acceleration = EnumNotch.getAcceleration(notchOf(this.train), speed);
            if (speed < cfg.maxSpeed[0]) {
                return normalize(acceleration > 0.0F ? modelSet.sound_S_A : modelSet.sound_D_S);
            }
            return normalize(acceleration > 0.0F ? modelSet.sound_Acceleration : modelSet.sound_Deceleration);
        }
        return normalize(speed > 0.0F ? modelSet.sound_Acceleration : modelSet.sound_Stop);
    }

    /** 本家 SoundUpdaterTrain.changePitch。 */
    protected boolean changePitch() {
        TrainConfig cfg = this.trainConfig();
        if (cfg == null || cfg.maxSpeed == null || cfg.maxSpeed.length == 0) {
            return false;
        }
        return Math.abs(speedOf(this.train)) >= cfg.maxSpeed[0];
    }

    private void stopAutomaticSound() {
        if (this.prevSound != null) {
            this.prevSound.stop();
            this.prevSound = null;
        }
        this.silent = true;
    }

    public Entity getEntity() {
        return this.train;
    }

    /** 本家 SoundUpdaterVehicle.getSpeed (km/h)。 */
    public float getSpeed() {
        return Math.abs(speedOf(this.train)) * SPEED_TO_KMH;
    }

    /** 本家 SoundUpdaterTrain.getMaxSpeed (km/h)。 */
    public float getMaxSpeed() {
        TrainConfig cfg = this.trainConfig();
        if (cfg == null || cfg.maxSpeed == null || cfg.maxSpeed.length == 0) {
            return 0.0F;
        }
        return cfg.maxSpeed[cfg.maxSpeed.length - 1] * SPEED_TO_KMH;
    }

    /** 本家 SoundUpdaterTrain.getNotch: 編成のノッチ。 */
    public int getNotch() {
        return notchOf(this.train);
    }

    /** 本家 SoundUpdaterTrain.getState。 */
    public byte getState(int id) {
        if (this.train instanceof EntityTrainBase t) {
            return (byte) t.getTrainStateData(id);
        }
        if (this.train instanceof com.portofino.realtrainmodunofficial.entity.TrainEntity t) {
            return (byte) t.getTrainStateData(id);
        }
        return 0;
    }

    public boolean isComplessorActive() {
        if (this.train instanceof EntityTrainBase t) {
            return t.complessorActive;
        }
        if (this.train instanceof com.portofino.realtrainmodunofficial.entity.TrainEntity t) {
            return t.complessorActive;
        }
        return false;
    }

    public int complessorCount() {
        if (this.train instanceof EntityTrainBase t) {
            return t.brakeAirCount - EntityTrainBase.MIN_AIR_COUNT;
        }
        if (this.train instanceof com.portofino.realtrainmodunofficial.entity.TrainEntity t) {
            return t.brakeAirCount - com.portofino.realtrainmodunofficial.entity.TrainEntity.MIN_AIR_COUNT;
        }
        return 0;
    }

    public boolean isCompressorActive() {
        return this.isComplessorActive();
    }

    public int compressorCount() {
        return this.complessorCount();
    }

    /** 本家 SoundUpdaterVehicle.playSound: 登録済みなら音量/ピッチだけ更… */
    public void playSound(String domain, String path, float volume, float pitch) {
        this.playSound(domain, path, volume, pitch, true);
    }

        /** ★GraalJS 互換: Double→float の暗黙変換が無いため double 受けを追加。 */
    public void playSound(String domain, String path, double volume, double pitch, boolean repeat) {
        this.playSound(domain, path, (float) volume, (float) pitch, repeat);
    }

    /** ★GraalJS 互換。 */
    public void playSound(String domain, String path, double volume, double pitch) {
        this.playSound(domain, path, (float) volume, (float) pitch, true);
    }

    public void playSound(String domain, String path, float volume, float pitch, boolean repeat) {
        if (this.train == null || !this.train.level().isClientSide() || path == null || path.isBlank()) {
            return;
        }
        String resolvedDomain = domain == null || domain.isBlank() ? "minecraft"
                : com.portofino.realtrainmodunofficial.client.sound.ExternalSoundPackBridge
                .sanitizeSoundPath(domain);
        ResourceLocation resource = ResourceLocation.fromNamespaceAndPath(
                resolvedDomain, normalizePath(path));

        // 本家 SoundUpdaterVehicle.playSound と同じ: 同じ domain/p…
        if (!repeat) {
            // 一発音は本家 playSoundAtEntity と同じく、呼ばれた回数だけ重ねて鳴らす。
            MovingSoundVehicle once = new MovingSoundVehicle(
                    this.train, resource, false, false, RTMConfig.trainRunningSoundRange);
            once.setVolume(volume);
            once.setPitch(pitch);
            this.playingSounds.removeIf(MovingSoundEntity::isStopped);
            this.playingSounds.add(once);
            soundManager().play(once);
            return;
        }
        MovingSoundVehicle sound = this.getPlayingSound(domain, path);
        boolean flag = sound == null;
        if (flag) {
            sound = new MovingSoundVehicle(
                    this.train, resource, true, false, RTMConfig.trainRunningSoundRange);
        }
        sound.setVolume(volume);
        sound.setPitch(pitch);
        if (flag) {
            soundManager().play(sound);
            this.playingSounds.add(sound);
        }
    }

    private static String normalizePath(String path) {
        String value = com.portofino.realtrainmodunofficial.client.sound.ExternalSoundPackBridge
                .sanitizeSoundPath(path == null ? "" : path.replace('\\', '/'));
        if (value.startsWith("sounds/")) {
            value = value.substring("sounds/".length());
        } else if (value.startsWith("sound/")) {
            value = value.substring("sound/".length());
        }
        if (value.endsWith(".ogg")) {
            value = value.substring(0, value.length() - 4);
        }
        if (value.indexOf('/') >= 0) {
            value = value.replace('/', '.');
        }
        return value;
    }

    public void stopSound(String domain, String path) {
        MovingSoundVehicle playing = this.getPlayingSound(domain, path);
        if (playing != null) {
            playing.stop();
            this.playingSounds.remove(playing);
        }
    }

    public void stopAllSounds() {
        this.playingSounds.forEach(MovingSoundEntity::stop);
        this.playingSounds.clear();
        this.stopAutomaticSound();
    }

    private MovingSoundVehicle getPlayingSound(String domain, String path) {
        String normalized = normalizePath(path == null ? "" : path);
        for (MovingSoundVehicle sound : this.playingSounds) {
            if (sound.isStopped()) {
                continue;
            }
            ResourceLocation resource = sound.getLocation();
            String resolvedDomain = domain == null || domain.isBlank() ? "minecraft"
                    : com.portofino.realtrainmodunofficial.client.sound.ExternalSoundPackBridge
                    .sanitizeSoundPath(domain);
            if (resource.getNamespace().equals(resolvedDomain)
                    && resource.getPath().equals(normalized)) {
                return sound;
            }
        }
        return null;
    }

    /** 本家 SoundUpdaterVehicle.getData / setData。 */
    public Object getData(int id) {
        if (this.dataMap == null) {
            this.dataMap = new HashMap<>();
        }
        return this.dataMap.getOrDefault(id, 0);
    }

    public void setData(int id, Object value) {
        if (this.dataMap == null) {
            this.dataMap = new HashMap<>();
        }
        this.dataMap.put(id, value);
    }

    public boolean inTunnel() {
        if (this.train == null) {
            return false;
        }
        int x = net.minecraft.util.Mth.floor(this.train.getX());
        int y = net.minecraft.util.Mth.floor(this.train.getY());
        int z = net.minecraft.util.Mth.floor(this.train.getZ());
        return !this.train.level().canSeeSky(new net.minecraft.core.BlockPos(x + 1, y, z + 1))
                && !this.train.level().canSeeSky(new net.minecraft.core.BlockPos(x - 1, y, z + 1))
                && !this.train.level().canSeeSky(new net.minecraft.core.BlockPos(x + 1, y, z - 1))
                && !this.train.level().canSeeSky(new net.minecraft.core.BlockPos(x - 1, y, z - 1));
    }

    public void onModelChanged() {
        this.stopAllSounds();
    }

    // ---- 共通ヘルパー (2 系統の列車エンティティ対応) ----

    static boolean isTrainEntity(Entity entity) {
        return entity instanceof com.portofino.realtrainmodunofficial.entity.TrainEntity;
    }

    static float speedOf(Entity entity) {
        if (entity instanceof EntityTrainBase t) {
            return t.getSpeed();
        }
        if (entity instanceof com.portofino.realtrainmodunofficial.entity.TrainEntity t) {
            return t.getSpeed();
        }
        return 0.0F;
    }

    static int notchOf(Entity entity) {
        if (entity instanceof EntityTrainBase t) {
            var formation = t.getFormation();
            return formation != null ? formation.getNotch() : (t.isControlCar() ? t.getNotch() : 0);
        }
        if (entity instanceof com.portofino.realtrainmodunofficial.entity.TrainEntity t) {
            var formation = t.getFormation();
            return formation != null ? formation.getNotch() : (t.isControlCar() ? t.getNotch() : 0);
        }
        return 0;
    }

    static boolean isControlCar(Entity entity) {
        if (entity instanceof EntityTrainBase t) {
            return t.isControlCar();
        }
        if (entity instanceof com.portofino.realtrainmodunofficial.entity.TrainEntity t) {
            return t.isControlCar();
        }
        return true;
    }

    static int signalOf(Entity entity) {
        if (entity instanceof EntityTrainBase t) {
            return t.getSignal();
        }
        if (entity instanceof com.portofino.realtrainmodunofficial.entity.TrainEntity t) {
            return t.getSignal();
        }
        return 0;
    }

    static TrainConfig configOf(Entity entity) {
        Object set = null;
        if (entity instanceof EntityTrainBase t) {
            set = t.getModelSet();
        } else if (entity instanceof com.portofino.realtrainmodunofficial.entity.TrainEntity t) {
            set = t.getModelSet();
        }
        return set instanceof ModelSetBase<?> base && base.getConfig() instanceof TrainConfig cfg ? cfg : null;
    }

    private ModelSetVehicleBaseClient<?> modelSet() {
        Object set = null;
        if (this.train instanceof EntityTrainBase t) {
            set = t.getModelSet();
        } else if (this.train instanceof com.portofino.realtrainmodunofficial.entity.TrainEntity t) {
            set = t.getModelSet();
        }
        return set instanceof ModelSetVehicleBaseClient<?> client ? client : null;
    }

    private TrainConfig trainConfig() {
        return configOf(this.train);
    }
}
