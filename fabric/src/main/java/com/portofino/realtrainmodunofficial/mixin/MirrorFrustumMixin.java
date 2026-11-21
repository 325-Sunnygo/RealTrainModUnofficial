package com.portofino.realtrainmodunofficial.mixin;

import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla's camera-cube offset loop assumes a centered frustum; mirrors are off-axis. */
@Mixin(Frustum.class)
public abstract class MirrorFrustumMixin {
    @Inject(method="offsetToFullyIncludeCameraCube",at=@At("HEAD"),cancellable=true)
    private void rtmu$offAxisFrustum(int size,CallbackInfoReturnable<Frustum> callback) {
        if(com.portofino.realtrainmodunofficial.client.mirror.MirrorRenderer.rendering())callback.setReturnValue((Frustum)(Object)this);
    }
}
