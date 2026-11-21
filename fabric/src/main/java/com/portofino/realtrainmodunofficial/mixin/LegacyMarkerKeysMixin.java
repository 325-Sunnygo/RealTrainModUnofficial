package com.portofino.realtrainmodunofficial.mixin;

import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class LegacyMarkerKeysMixin {
    @Inject(method="keyPress",at=@At("HEAD"),cancellable=true)
    private void rtmu$legacyAnchorKeys(long window,int key,int scanCode,int action,int modifiers,CallbackInfo callback) {
        if(modifiers==0 && com.portofino.realtrainmodunofficial.client.renderer.MarkerBlockEntityRenderer.onLegacyNumberKey(key,action))callback.cancel();
    }
}
