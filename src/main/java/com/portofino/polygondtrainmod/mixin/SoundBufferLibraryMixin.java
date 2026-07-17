package com.portofino.polygondtrainmod.mixin;

import com.mojang.blaze3d.audio.SoundBuffer;
import com.portofino.realtrainmodunofficial.client.sound.LegacyStereoDownmix;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/**
 * レガシー音源パックのステレオ音をロード時にモノラルへ変換する ({@link LegacyStereoDownmix})。
 * OpenAL はステレオ音源を距離減衰しないため、そのままだと走行音が全域で最大音量になる。
 */
@Mixin(SoundBufferLibrary.class)
public abstract class SoundBufferLibraryMixin {

    @Inject(method = "getCompleteBuffer", at = @At("RETURN"), cancellable = true)
    private void rtmu$downmixLegacyStereo(ResourceLocation soundID,
                                          CallbackInfoReturnable<CompletableFuture<SoundBuffer>> cir) {
        cir.setReturnValue(LegacyStereoDownmix.wrap(soundID, cir.getReturnValue()));
    }

    @Inject(method = "clear", at = @At("TAIL"))
    private void rtmu$clearDownmixCache(CallbackInfo ci) {
        LegacyStereoDownmix.clearCache();
    }
}
