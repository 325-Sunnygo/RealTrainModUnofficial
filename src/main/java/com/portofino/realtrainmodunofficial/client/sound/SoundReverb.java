package com.portofino.realtrainmodunofficial.client.sound;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.EXTEfx;

/**
 * トンネル等の閉空間での音の反響 (RTMU オリジナル、OpenAL EFX)。
 * <p>
 * 距離減衰・ステレオ定位は Minecraft の位置付き再生がすでに行う (列車の音は
 * エンティティ座標に追従)。ここでは EFX の Reverb エフェクトを 1 つ用意し、全音源を
 * その補助エフェクトスロットへ send して「濡れ」を加える。反響の強さ (減衰時間・gain) は
 * リスナー周囲の囲まれ具合から毎 tick 算出し、トンネル内では強く・開けた場所では 0 にする。
 * <p>
 * スレッド分離: ワールドのブロック読み取り (レイキャスト) は<b>メインスレッド</b> (client tick) で
 * 行い、目標パラメータを volatile に格納する。OpenAL の呼び出しは context が current な
 * <b>サウンドスレッド</b> ({@code Channel.play}/{@code setSelfPosition} 経由) でのみ行う。
 */
@EventBusSubscriber(modid = RealTrainModUnofficial.MODID, value = Dist.CLIENT)
public final class SoundReverb {

    // ---- サウンドスレッドが触る EFX ハンドル ----
    private static boolean efxTried;
    private static boolean efxOk;
    private static int effect = -1;
    private static int effectSlot = -1;
    private static float appliedDecay = -1.0F;
    private static float appliedGain = -1.0F;
    private static long lastApplyMs;

    // ---- メインスレッドが算出しサウンドスレッドが読む目標値 ----
    private static volatile float targetDecay = 1.0F;
    private static volatile float targetGain;

    private SoundReverb() {
    }

    // ============ メインスレッド: 囲まれ具合から反響量を算出 ============

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            targetGain = 0.0F;
            return;
        }
        Entity camera = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
        Vec3 origin = camera.getEyePosition();
        float enclosure = measureEnclosure(mc.level, origin, camera);
        // enclosure 0(開けている)〜1(密閉) を反響へマップ。
        // gain: 開けた場所は 0 (反響なし)。少し囲まれてから立ち上げる。
        float gain = enclosure <= 0.35F ? 0.0F : (enclosure - 0.35F) / 0.65F;
        gain = clamp01(gain) * 0.85F;
        // decay: トンネルは長く尾を引く。密閉度で 0.4s〜3.2s。
        float decay = 0.4F + enclosure * 2.8F;
        // 急変を避けて滑らかに寄せる (音が急にワッと変わらないよう)
        targetGain += (gain - targetGain) * 0.2F;
        targetDecay += (decay - targetDecay) * 0.2F;
    }

    /**
     * リスナーから 14 方向へレイを飛ばし、平均到達距離から囲まれ具合 (0=開放, 1=密閉) を得る。
     */
    private static float measureEnclosure(Level level, Vec3 origin, Entity context) {
        final double maxDist = 20.0D;
        final Vec3[] dirs = ENCLOSURE_DIRS;
        double sum = 0.0D;
        for (Vec3 d : dirs) {
            Vec3 end = origin.add(d.x * maxDist, d.y * maxDist, d.z * maxDist);
            BlockHitResult hit = level.clip(new ClipContext(
                    origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, context));
            double dist = hit.getType() == HitResult.Type.BLOCK
                    ? hit.getLocation().distanceTo(origin)
                    : maxDist;
            sum += Math.min(dist, maxDist);
        }
        double avg = sum / dirs.length;
        // avg 小 = 近くに壁 = 密閉。avg=2 で密閉寄り、avg>=14 で開放とみなす。
        double openness = (avg - 2.0D) / 12.0D;
        return 1.0F - clamp01((float) openness);
    }

    private static final Vec3[] ENCLOSURE_DIRS = buildDirs();

    private static Vec3[] buildDirs() {
        double s = 1.0D / Math.sqrt(3.0D);
        return new Vec3[]{
                new Vec3(1, 0, 0), new Vec3(-1, 0, 0),
                new Vec3(0, 1, 0), new Vec3(0, -1, 0),
                new Vec3(0, 0, 1), new Vec3(0, 0, -1),
                new Vec3(s, s, s), new Vec3(-s, s, s), new Vec3(s, s, -s), new Vec3(-s, s, -s),
                new Vec3(s, -s, s), new Vec3(-s, -s, s), new Vec3(s, -s, -s), new Vec3(-s, -s, -s),
        };
    }

    // ============ サウンドスレッド: EFX 初期化・適用・音源接続 ============

    /** {@code Channel.play} から (サウンドスレッド)。音源を反響スロットへ send する。 */
    public static void attachSource(int source) {
        if (!ensureEfx()) {
            return;
        }
        try {
            AL11.alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER, effectSlot, 0, EXTEfx.AL_FILTER_NULL);
            AL10.alGetError();
        } catch (Throwable ignored) {
        }
    }

    /** {@code Channel.setSelfPosition} から (サウンドスレッド、毎フレーム)。反響量を間引いて更新。 */
    public static void tickApply() {
        if (!efxOk) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastApplyMs < 100L) {
            return;
        }
        lastApplyMs = now;
        float decay = targetDecay;
        float gain = targetGain;
        if (Math.abs(decay - appliedDecay) < 0.02F && Math.abs(gain - appliedGain) < 0.01F) {
            return;
        }
        appliedDecay = decay;
        appliedGain = gain;
        try {
            EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_DECAY_TIME, clamp(decay, 0.1F, 20.0F));
            EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_GAIN, clamp(gain, 0.0F, 1.0F));
            EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_GAINHF, 0.8F);
            // パラメータ変更をスロットへ反映
            EXTEfx.alAuxiliaryEffectSloti(effectSlot, EXTEfx.AL_EFFECTSLOT_EFFECT, effect);
            AL10.alGetError();
        } catch (Throwable t) {
            efxOk = false;
        }
    }

    private static boolean ensureEfx() {
        if (efxTried) {
            return efxOk;
        }
        efxTried = true;
        try {
            AL10.alGetError();
            effect = EXTEfx.alGenEffects();
            EXTEfx.alEffecti(effect, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_REVERB);
            effectSlot = EXTEfx.alGenAuxiliaryEffectSlots();
            EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_DECAY_TIME, 1.0F);
            EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_GAIN, 0.0F);
            EXTEfx.alAuxiliaryEffectSloti(effectSlot, EXTEfx.AL_EFFECTSLOT_EFFECT, effect);
            int err = AL10.alGetError();
            efxOk = err == AL10.AL_NO_ERROR && effect >= 0 && effectSlot >= 0;
            if (efxOk) {
                RealTrainModUnofficial.LOGGER.info("RTMU: OpenAL EFX reverb enabled (tunnel echo)");
            } else {
                RealTrainModUnofficial.LOGGER.info("RTMU: OpenAL EFX unavailable (err={}), reverb disabled", err);
            }
        } catch (Throwable t) {
            efxOk = false;
            RealTrainModUnofficial.LOGGER.info("RTMU: OpenAL EFX init failed, reverb disabled", t);
        }
        return efxOk;
    }

    private static float clamp01(float v) {
        return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
