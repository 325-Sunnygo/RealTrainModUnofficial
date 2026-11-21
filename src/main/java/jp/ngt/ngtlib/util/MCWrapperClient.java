package jp.ngt.ngtlib.util;

import jp.ngt.mccompat.PlayerCompat;
import jp.ngt.mccompat.WorldCompat;
import net.minecraft.client.Minecraft;

/** 本家 jp.ngt.ngtlib.util.MCWrapperClient のスクリプト互換。 */
@SuppressWarnings("unused")
public final class MCWrapperClient {
    private MCWrapperClient() {
    }

    public static PlayerCompat getPlayer() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        PlayerCompat compat = PlayerCompat.of(player);
        compat.refresh();
        return compat;
    }

    public static WorldCompat getWorld() {
        var level = Minecraft.getInstance().level;
        return level != null ? new WorldCompat(level) : null;
    }

    /** 本家 (1.7.10) MCWrapperClient.playSound: クライアントで音を鳴… */
    public static void playSound(Object world, String name, double x, double y, double z,
                                 float volume, float pitch, boolean distanceDelay) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || name == null || name.isBlank()) {
                return;
            }
            net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(name);
            if (id == null || !net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.containsKey(id)) {
                return;
            }
            net.minecraft.sounds.SoundEvent event =
                net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(id);
            mc.level.playLocalSound(x, y, z, event, net.minecraft.sounds.SoundSource.NEUTRAL, volume, pitch, false);
        } catch (Throwable ignored) {
        }
    }

    /** 本家 (1.7.10) MCWrapperClient.spawnParticle。 */
    public static void spawnParticle(Object world, String name, double x, double y, double z,
                                     double speedX, double speedY, double speedZ) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || name == null) {
                return;
            }
            net.minecraft.core.particles.ParticleOptions particle = toParticle(name);
            if (particle == null) {
                return;
            }
            mc.level.addParticle(particle, x, y, z, speedX, speedY, speedZ);
        } catch (Throwable ignored) {
        }
    }

    /** 1.7.10 のパーティクル名 → 1.21。未知は null (何も出さない)。 */
    private static net.minecraft.core.particles.ParticleOptions toParticle(String rawName) {
        String name = rawName.toLowerCase(java.util.Locale.ROOT);
        if (name.contains(":")) {
            name = name.substring(name.indexOf(':') + 1);
        }
        String key = switch (name) {
            case "largeexplode", "hugeexplosion" -> "explosion_emitter";
            case "explode", "explosion_normal" -> "explosion";
            case "smoke", "largesmoke" -> "large_smoke";
            case "smoke_normal", "townaura" -> "smoke";
            case "flame" -> "flame";
            case "fireworksspark", "fireworks_spark" -> "firework";
            case "snowballpoof", "snowshovel" -> "item_snowball";
            case "splash" -> "splash";
            case "cloud" -> "cloud";
            case "crit" -> "crit";
            case "reddust", "redstone" -> "dust";
            case "lava" -> "lava";
            case "heart" -> "heart";
            default -> null;
        };
        if (key == null) {
            return null;
        }
        net.minecraft.resources.ResourceLocation id =
            net.minecraft.resources.ResourceLocation.withDefaultNamespace(key);
        if (!net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.containsKey(id)) {
            return null;
        }
        net.minecraft.core.particles.ParticleType<?> type =
            net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.get(id);
        return type instanceof net.minecraft.core.particles.ParticleOptions options ? options : null;
    }

    /** 本家 MCWrapperClient.execCommand: チャットコマンドを実行する。 */
    public static void execCommand(String command) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || command == null || command.isBlank()) {
                return;
            }
            if (mc.player.connection == null) {
                return;
            }
            String cmd = command.startsWith("/") ? command.substring(1) : command;
            mc.player.connection.sendCommand(cmd);
        } catch (Throwable ignored) {
        }
    }
}
