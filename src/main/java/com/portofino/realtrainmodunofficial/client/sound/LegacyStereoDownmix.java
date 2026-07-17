package com.portofino.realtrainmodunofficial.client.sound;

import com.mojang.blaze3d.audio.SoundBuffer;
import com.portofino.polygondtrainmod.mixin.SoundBufferAccessor;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.minecraft.resources.ResourceLocation;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * レガシー音源パック (MugenSoundLib 等) の<b>ステレオ音源をロード時にモノラルへ落とす</b>。
 * <p>
 * <b>なぜ必要か:</b> OpenAL は<b>ステレオ音源を距離減衰・3D定位しない</b> (モノラルのみ)。
 * MugenSoundLib は 399 音中 385 音がステレオ録音で、減衰設定 (LINEAR/16blk) が正しくても
 * 「どんなに離れても両耳フル音量で聞こえる」状態だった。バニラの定位音が全てモノラルなのは
 * このため。ここで L/R 平均のモノラルに変換すると、通常の距離減衰がそのまま効く。
 * <p>
 * 対象は生成サウンドパックの名前空間 ({@link ExternalSoundPackBridge#generatedNamespaces()})
 * のみ。BGM・レコード等 (minecraft:) はストリーム再生でここを通らず、通ってもステレオのまま。
 * 変換は同じ ByteBuffer 内で行う (モノラルは半分のサイズなので前詰め) — 追加確保なし。
 * <p>
 * {@code SoundBufferLibrary} のキャッシュは<b>元の (ステレオの) future</b> を保持し続けるため、
 * 呼び出しごとに thenApply すると同じバッファを二重変換して壊す。ここで ResourceLocation
 * キーの自前キャッシュを持ち、変換は 1 音につき 1 回だけ行う。
 */
public final class LegacyStereoDownmix {

    private static final Map<ResourceLocation, CompletableFuture<SoundBuffer>> CACHE = new ConcurrentHashMap<>();

    private LegacyStereoDownmix() {
    }

    /** {@code SoundBufferLibrary.getCompleteBuffer} の戻り値を包む (Mixin から)。 */
    public static CompletableFuture<SoundBuffer> wrap(ResourceLocation id, CompletableFuture<SoundBuffer> original) {
        if (id == null || original == null || !isLegacySoundNamespace(id.getNamespace())) {
            return original;
        }
        return CACHE.computeIfAbsent(id, key -> original.thenApply(buffer -> downmixIfStereo(key, buffer)));
    }

    /** {@code SoundBufferLibrary.clear} に合わせて自前キャッシュも破棄 (リソースリロード時)。 */
    public static void clearCache() {
        for (CompletableFuture<SoundBuffer> future : CACHE.values()) {
            if (future.isDone() && !future.isCompletedExceptionally()) {
                try {
                    SoundBuffer buffer = future.join();
                    if (buffer != null) {
                        buffer.discardAlBuffer();
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        CACHE.clear();
    }

    private static boolean isLegacySoundNamespace(String namespace) {
        Set<String> generated = ExternalSoundPackBridge.generatedNamespaces();
        if (!generated.isEmpty()) {
            return generated.contains(namespace);
        }
        //パック未生成 (初回起動前など) のフォールバック: 既知のレガシー命名だけ対象にする
        return "rtm".equals(namespace) || namespace.startsWith("sound_");
    }

    private static SoundBuffer downmixIfStereo(ResourceLocation id, SoundBuffer buffer) {
        if (buffer == null) {
            return null;
        }
        try {
            AudioFormat format = ((SoundBufferAccessor) buffer).rtmu$format();
            ByteBuffer data = ((SoundBufferAccessor) buffer).rtmu$data();
            if (format == null || data == null
                    || format.getChannels() != 2 || format.getSampleSizeInBits() != 16) {
                return buffer; //モノラル or 想定外フォーマットはそのまま
            }
            data.order(format.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
            int base = data.position();
            int frames = data.remaining() / 4; //16bit × 2ch = 4 bytes/frame
            for (int i = 0; i < frames; i++) {
                short left = data.getShort(base + i * 4);
                short right = data.getShort(base + i * 4 + 2);
                data.putShort(base + i * 2, (short) ((left + right) / 2));
            }
            data.limit(base + frames * 2);
            data.position(base);
            AudioFormat mono = new AudioFormat(format.getSampleRate(), 16, 1, true, format.isBigEndian());
            RealTrainModUnofficial.LOGGER.debug("Downmixed legacy stereo sound to mono: {}", id);
            return new SoundBuffer(data, mono);
        } catch (Throwable t) {
            RealTrainModUnofficial.LOGGER.warn("Failed to downmix legacy stereo sound {}", id, t);
            return buffer;
        }
    }
}
