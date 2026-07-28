package jp.kaiz.atsassistmod.client;

import jp.kaiz.atsassistmod.network.IFTTTPlaySoundPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * IFTTT の音声再生 (本家 ATSASoundPlayer 相当のクライアント側管理)。
 * IFTTT ブロック位置ごとに再生中インスタンスを保持し、finish で停止する。
 * サウンド ID はサウンドイベント (sounds.json) 名。RTM パックの音も
 * RTMU が登録するイベント名で指定できる。
 */
public final class IFTTTClientSounds {

    /** IFTTTブロック位置 (long) → 再生中サウンド。 */
    private static final Map<Long, List<SimpleSoundInstance>> PLAYING = new HashMap<>();

    private IFTTTClientSounds() {
    }

    public static void handle(IFTTTPlaySoundPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() == null) {
            return;
        }
        long key = payload.tilePos().asLong();
        if (payload.finish()) {
            List<SimpleSoundInstance> list = PLAYING.remove(key);
            if (list != null) {
                list.forEach(s -> mc.getSoundManager().stop(s));
            }
            return;
        }
        ResourceLocation soundId = ResourceLocation.tryParse(payload.sound());
        if (soundId == null) {
            return;
        }
        //本家: radius/16f を音量係数に使う (LINEAR 減衰で概ね radius ブロックまで届く)
        float volume = Mth.clamp(payload.radius() / 16.0F, 0.1F, 16.0F);
        SimpleSoundInstance instance = new SimpleSoundInstance(
                soundId,
                SoundSource.RECORDS,
                volume,
                1.0F,
                SoundInstance.createUnseededRandom(),
                payload.repeat(),
                0,
                SoundInstance.Attenuation.LINEAR,
                payload.x() + 0.5, payload.y() + 0.5, payload.z() + 0.5,
                false
        );
        PLAYING.computeIfAbsent(key, k -> new ArrayList<>()).add(instance);
        mc.getSoundManager().play(instance);
    }

    /** ワールド退出などで全停止したい時用。 */
    public static void stopAll() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() != null) {
            PLAYING.values().forEach(list -> list.forEach(s -> mc.getSoundManager().stop(s)));
        }
        PLAYING.clear();
    }

    public static void stopAt(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        List<SimpleSoundInstance> list = PLAYING.remove(pos.asLong());
        if (list != null && mc.getSoundManager() != null) {
            list.forEach(s -> mc.getSoundManager().stop(s));
        }
    }
}
