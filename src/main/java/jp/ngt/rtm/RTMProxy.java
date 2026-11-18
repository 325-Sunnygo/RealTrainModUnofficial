package jp.ngt.rtm;

import com.portofino.realtrainmodunofficial.client.sound.LegacyScriptSoundManager;
import com.portofino.realtrainmodunofficial.network.SpeakerPlayPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

/** 本家 RTMCore.proxy のスクリプト互換。 */
public class RTMProxy {

    public void playSound(Object target, Object sound, float volume, float pitch) {
        this.playSound(target, sound, volume, pitch, 16.0F);
    }

    public void playSound(Object target, Object sound, float volume, float pitch, float range) {
        if (target == null || sound == null) {
            return;
        }
        String soundId = toSoundId(sound);
        int entityId = -1;
        double x;
        double y;
        double z;
        if (target instanceof Entity entity) {
            entityId = entity.getId();
            x = entity.getX();
            y = entity.getY();
            z = entity.getZ();
        } else if (target instanceof BlockEntity blockEntity && blockEntity.getLevel() != null) {
            var pos = blockEntity.getBlockPos();
            x = pos.getX() + 0.5D;
            y = pos.getY() + 0.5D;
            z = pos.getZ() + 0.5D;
        } else {
            return;
        }
        float r = range > 0.0F ? range : 16.0F;
        Level level = target instanceof Entity entity ? entity.level() : ((BlockEntity) target).getLevel();
        if (level == null) {
            return;
        }
        if (level instanceof ServerLevel serverLevel) {
            // 本家 PacketPlaySound: 256 ブロック以内の全プレイヤーへ送る。
            for (ServerPlayer player : serverLevel.players()) {
                if (player.distanceToSqr(x, y, z) <= 256.0D * 256.0D) {
                    PacketDistributor.sendToPlayer(player,
                        new SpeakerPlayPayload(entityId, x, y, z, soundId, volume, pitch, r));
                }
            }
        } else {
            LegacyScriptSoundManager.playOriginal(entityId, x, y, z, soundId, volume, pitch, r);
        }
    }

    /** mccompat / 実 ResourceLocation / 文字列のいずれも "ns:path… */
    private static String toSoundId(Object sound) {
        if (sound instanceof net.minecraft.resources.ResourceLocation rl) {
            return rl.toString();
        }
        if (sound instanceof jp.ngt.mccompat.ResourceLocation rl) {
            return rl.getResourceDomain() + ":" + rl.getResourcePath();
        }
        return String.valueOf(sound);
    }
}
