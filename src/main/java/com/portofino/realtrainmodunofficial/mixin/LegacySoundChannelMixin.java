package com.portofino.realtrainmodunofficial.mixin;

import com.mojang.blaze3d.audio.Library;
import jp.ngt.rtm.RTMConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Original RTM channel expansion: 1024 normal and 32 streaming sources. */
@Mixin(Library.class)
public abstract class LegacySoundChannelMixin {
    @ModifyArg(method="init",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/audio/Library$CountingChannelPool;<init>(I)V",ordinal=0),index=0)
    private int rtmu$normalChannels(int vanilla) { return RTMConfig.expandPlayableSoundCount?1024:vanilla; }
    @ModifyArg(method="init",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/audio/Library$CountingChannelPool;<init>(I)V",ordinal=1),index=0)
    private int rtmu$streamingChannels(int vanilla) { return RTMConfig.expandPlayableSoundCount?32:vanilla; }
}
