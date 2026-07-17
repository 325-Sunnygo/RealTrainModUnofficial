package com.portofino.polygondtrainmod.mixin;

import com.portofino.realtrainmodunofficial.client.sound.SoundReverb;
import com.mojang.blaze3d.audio.Channel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * OpenAL EFX 反響 ({@link SoundReverb}) を各音源へ接続する。
 * {@code Channel} は OpenAL のサウンドスレッドで動くので、ここでの EFX 呼び出しは
 * context が current な正しいスレッドで実行される。
 */
@Mixin(Channel.class)
public abstract class ChannelMixin {

    @Shadow @org.spongepowered.asm.mixin.Final
    private int source;

    /** 減衰無効 (BGM/UI 等の非定位音) には反響を掛けない。 */
    private boolean rtmu$noReverb;

    @Inject(method = "disableAttenuation", at = @At("TAIL"))
    private void rtmu$markNoReverb(CallbackInfo ci) {
        this.rtmu$noReverb = true;
    }

    @Inject(method = "play", at = @At("TAIL"))
    private void rtmu$attachReverb(CallbackInfo ci) {
        if (!this.rtmu$noReverb) {
            SoundReverb.attachSource(this.source);
        }
    }

    @Inject(method = "setSelfPosition", at = @At("TAIL"))
    private void rtmu$updateReverb(Vec3 pos, CallbackInfo ci) {
        if (!this.rtmu$noReverb) {
            SoundReverb.tickApply();
        }
    }
}
