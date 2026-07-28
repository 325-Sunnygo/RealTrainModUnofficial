package com.portofino.realtrainmodunofficial.client.sound;

import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectDefinition;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CrossingGateSoundManager {
    private static final ResourceLocation CROSSING_SOUND_ID = ResourceLocation.fromNamespaceAndPath("rtm", "block.crossing_gate");
    private static final Map<String, LoopingCrossingSound> ACTIVE = new ConcurrentHashMap<>();
    /** 踏切音が聞こえる最大距離(ブロック)。これより遠いと鳴らさない/停止する(=どこでも聞こえる不具合対策)。 */
    private static final double MAX_AUDIBLE_DISTANCE = 48.0D;
    private static final double MAX_AUDIBLE_DISTANCE_SQ = MAX_AUDIBLE_DISTANCE * MAX_AUDIBLE_DISTANCE;

    private CrossingGateSoundManager() {
    }

    /** この距離(ブロック)以内は最大音量。これを超えると 0 へ向けて線形フェード。 */
    private static final double FULL_VOLUME_DISTANCE = 12.0D;

    /** クライアントプレイヤーが pos から可聴距離内か。プレイヤー不在時は false(鳴らさない)。 */
    private static boolean playerInRange(BlockPos pos) {
        net.minecraft.client.player.LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        double dx = (pos.getX() + 0.5D) - player.getX();
        double dy = (pos.getY() + 0.5D) - player.getY();
        double dz = (pos.getZ() + 0.5D) - player.getZ();
        return (dx * dx + dy * dy + dz * dz) <= MAX_AUDIBLE_DISTANCE_SQ;
    }

    /** プレイヤーとの距離に応じた音量(近=1.0、FULL_VOLUME_DISTANCE超で線形減衰、MAXで0)。 */
    private static float volumeForDistance(BlockPos pos) {
        net.minecraft.client.player.LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return 0.0F;
        }
        double dx = (pos.getX() + 0.5D) - player.getX();
        double dy = (pos.getY() + 0.5D) - player.getY();
        double dz = (pos.getZ() + 0.5D) - player.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist <= FULL_VOLUME_DISTANCE) {
            return 1.0F;
        }
        if (dist >= MAX_AUDIBLE_DISTANCE) {
            return 0.0F;
        }
        double t = (dist - FULL_VOLUME_DISTANCE) / (MAX_AUDIBLE_DISTANCE - FULL_VOLUME_DISTANCE);
        return (float) (1.0D - t);
    }

    public static void tick(InstalledObjectBlockEntity blockEntity) {
        if (blockEntity == null) {
            return;
        }
        Level level = blockEntity.getLevel();
        if (level == null || !level.isClientSide()) {
            return;
        }
        if (!blockEntity.isPowered()) {
            stop(level, blockEntity.getBlockPos());
            return;
        }
        if (!shouldPlayCrossingSound(blockEntity)) {
            stop(level, blockEntity.getBlockPos());
            return;
        }
        // 可聴距離外なら鳴らさない/停止(=どこでも聞こえる不具合対策)。
        if (!playerInRange(blockEntity.getBlockPos())) {
            stop(level, blockEntity.getBlockPos());
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSoundManager() == null) {
            return;
        }

        String key = key(level, blockEntity.getBlockPos());
        ResourceLocation soundId = resolveSoundId(blockEntity);
        LoopingCrossingSound sound = ACTIVE.get(key);
        if (sound == null || sound.isStopped() || !sound.matches(soundId)) {
            if (sound != null) {
                sound.requestStop();
            }
            sound = new LoopingCrossingSound(blockEntity, soundId);
            ACTIVE.put(key, sound);
            minecraft.getSoundManager().play(sound);
        } else {
            sound.refresh();
        }
    }

    public static void stop(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return;
        }
        LoopingCrossingSound sound = ACTIVE.remove(key(level, pos));
        if (sound != null) {
            sound.requestStop();
        }
    }

    private static String key(Level level, BlockPos pos) {
        return level.dimension().location() + "|" + pos.asLong();
    }

    private static boolean shouldPlayCrossingSound(InstalledObjectBlockEntity blockEntity) {
        InstalledObjectDefinition definition = blockEntity == null ? null : InstalledObjectRegistry.getById(blockEntity.getDefinitionId());
        if (definition == null) {
            return false;
        }
        String runningSound = definition.getRunningSound();
        return runningSound != null && !runningSound.isBlank();
    }

    private static ResourceLocation resolveSoundId(InstalledObjectBlockEntity blockEntity) {
        InstalledObjectDefinition definition = blockEntity == null ? null : InstalledObjectRegistry.getById(blockEntity.getDefinitionId());
        String raw = definition == null ? "" : definition.getRunningSound();
        if (raw == null || raw.isBlank()) {
            return CROSSING_SOUND_ID;
        }
        String normalized = raw.trim().replace('\\', '/');
        if (normalized.endsWith(".ogg")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        if (normalized.startsWith("sounds/")) {
            normalized = normalized.substring("sounds/".length());
        }
        String lowered = normalized.toLowerCase(java.util.Locale.ROOT);
        if (lowered.contains("rtm_crossinggate") || lowered.contains("crossinggate0") || lowered.contains("crossinggate1")) {
            return CROSSING_SOUND_ID;
        }
        try {
            //空白・大文字入りのサウンド名 (hi03 Train Melodies 等) を生成側と同じ規則で安全化する。
            //揃えないと生成した SoundEvent と参照がずれ、踏切のデフォルトベル音に落ちて無音扱いになる。
            if (normalized.contains(":")) {
                String[] split = normalized.split(":", 2);
                String namespace = split[0].isBlank() ? "minecraft" : ExternalSoundPackBridge.sanitizeSoundPath(split[0]);
                String path = ExternalSoundPackBridge.sanitizeSoundPath(split[1]);
                if ("rtm".equals(namespace) && path.indexOf('/') >= 0) {
                    path = path.replace('/', '.');
                }
                return ResourceLocation.fromNamespaceAndPath(namespace, path);
            }
            String path = ExternalSoundPackBridge.sanitizeSoundPath(normalized);
            if (path.indexOf('/') >= 0) {
                path = path.replace('/', '.');
            }
            return ResourceLocation.fromNamespaceAndPath("rtm", path);
        } catch (Exception ignored) {
            return CROSSING_SOUND_ID;
        }
    }

    private static final class LoopingCrossingSound extends AbstractTickableSoundInstance {
        private final InstalledObjectBlockEntity blockEntity;
        private final ResourceLocation soundId;

        private LoopingCrossingSound(InstalledObjectBlockEntity blockEntity, ResourceLocation soundId) {
            super(SoundEvent.createVariableRangeEvent(soundId == null ? CROSSING_SOUND_ID : soundId), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.blockEntity = blockEntity;
            this.soundId = soundId == null ? CROSSING_SOUND_ID : soundId;
            this.looping = true;
            this.delay = 0;
            this.relative = false;
            // 距離減衰は MC 任せにせず手動で音量を調整する(可聴端で急に消えるのを防ぎ徐々にフェード)。
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = 1.0F;
            this.pitch = 1.0F;
            refresh();
        }

        private boolean matches(ResourceLocation other) {
            return soundId.equals(other == null ? CROSSING_SOUND_ID : other);
        }

        private void refresh() {
            BlockPos pos = blockEntity.getBlockPos();
            this.x = pos.getX() + 0.5D;
            this.y = pos.getY() + 0.5D;
            this.z = pos.getZ() + 0.5D;
            // プレイヤーとの距離で音量を滑らかに変える(近=最大、可聴端48mで0)。
            this.volume = volumeForDistance(pos);
        }

        private void requestStop() {
            stop();
        }

        @Override
        public void tick() {
            Level level = blockEntity.getLevel();
            if (level == null
                || !level.isClientSide()
                || blockEntity.isRemoved()
                || !blockEntity.isPowered()
                || !shouldPlayCrossingSound(blockEntity)
                || !playerInRange(blockEntity.getBlockPos())
                || level.getBlockEntity(blockEntity.getBlockPos()) != blockEntity) {
                if (level != null) {
                    ACTIVE.remove(key(level, blockEntity.getBlockPos()), this);
                }
                stop();
                return;
            }
            refresh();
        }
    }
}
