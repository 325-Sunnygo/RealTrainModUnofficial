package com.portofino.realtrainmodunofficial.mixin;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.*;
@Mixin(Minecraft.class)
public abstract class MirrorTargetMixin implements com.portofino.realtrainmodunofficial.client.mirror.MirrorTargetAccess {
    @org.spongepowered.asm.mixin.injection.Inject(method="useShaderTransparency",at=@org.spongepowered.asm.mixin.injection.At("HEAD"),cancellable=true)
    private static void rtmu$singleReflectionTarget(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> callback) {
        if(com.portofino.realtrainmodunofficial.client.mirror.MirrorRenderer.rendering())callback.setReturnValue(false);
    }
    @Shadow @Final @Mutable private RenderTarget mainRenderTarget;
    @Override public RenderTarget rtmu$getMainTarget(){return mainRenderTarget;}
    @Override public void rtmu$setMainTarget(RenderTarget target){mainRenderTarget=target;}
}
