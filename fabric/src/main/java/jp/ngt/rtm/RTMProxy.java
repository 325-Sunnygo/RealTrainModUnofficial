package jp.ngt.rtm;

import com.portofino.realtrainmodunofficial.client.sound.LegacyScriptSoundManager;
import com.portofino.realtrainmodunofficial.network.SpeakerPlayPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 本家 {@code jp.ngt.rtm.CommonProxy}/{@code ClientProxy} のスクリプト互換
 * ({@code RTMCore.proxy})。
 *
 * <p>本家の {@code playSound} は「サーバーでは周囲のクライアントへ
 * {@code PacketPlaySound} を送り、クライアントで鳴らす」実装。ここでも同じく
 * サーバーは範囲内プレイヤーへ {@link SpeakerPlayPayload} を送り、
 * クライアントはその場で鳴らす。
 */
public class RTMProxy {

    public void playSound(Object target, Object sound, float volume, float pitch) {
        this.playSound(target, sound, volume, pitch, 16.0F);
    }

    public void playSound(Object target, Object sound, float volume, float pitch, float range) {
        if (target == null || sound == null) {
            return;
        }
        String soundId = toSoundId(sound);
        Level level;
        double x;
        double y;
        double z;
        if (target instanceof Entity entity) {
            level = entity.level();
            x = entity.getX();
            y = entity.getY();
            z = entity.getZ();
        } else if (target instanceof BlockEntity blockEntity) {
            level = blockEntity.getLevel();
            var pos = blockEntity.getBlockPos();
            x = pos.getX() + 0.5D;
            y = pos.getY() + 0.5D;
            z = pos.getZ() + 0.5D;
        } else {
            return;
        }
        if (level == null) {
            return;
        }
        float r = range > 0.0F ? range : 16.0F;
        if (level instanceof ServerLevel serverLevel) {
            // 本家 CommonProxy: 周囲へ送ってクライアントで鳴らす
            for (ServerPlayer player : serverLevel.players()) {
                if (player.distanceToSqr(x, y, z) <= (double) r * (double) r) {
                    PacketDistributor.sendToPlayer(player,
                        new SpeakerPlayPayload(x, y, z, soundId, volume, pitch));
                }
            }
        } else {
            // クライアント側で直接鳴らす
            LegacyScriptSoundManager.playAt(x, y, z, soundId, volume, pitch);
        }
    }

    /** mccompat / 実 ResourceLocation / 文字列のいずれも "ns:path" へ正規化する。 */
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
