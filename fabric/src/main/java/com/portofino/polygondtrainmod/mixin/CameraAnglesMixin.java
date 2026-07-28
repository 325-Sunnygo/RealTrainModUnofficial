package com.portofino.polygondtrainmod.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ViewportEvent.ComputeCameraAngles と
 * CalculateDetachedCameraDistanceEvent の発火点。
 * 乗車時の視点追従 (揺れの補間・首振り) がこのイベントで角度を書き換えている。
 * ★CameraMixin は setup の TAIL でフリーカメラの位置を上書きする別物。
 */
@Mixin(Camera.class)
public abstract class CameraAnglesMixin {

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    private float xRot;

    @Shadow
    private float yRot;

    @Inject(method = "setup", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/Camera;setRotation(FF)V", shift = At.Shift.AFTER))
    private void rtmu$computeCameraAngles(BlockGetter level, Entity entity, boolean detached,
                                          boolean thirdPersonReverse, float partialTick,
                                          CallbackInfo ci) {
        ViewportEvent.ComputeCameraAngles event = new ViewportEvent.ComputeCameraAngles(
            (Camera) (Object) this, partialTick, this.yRot, this.xRot, 0.0F);
        NeoForge.EVENT_BUS.post(event);
        this.setRotation(event.getYaw(), event.getPitch());
    }

    @Inject(method = "getMaxZoom", at = @At("RETURN"), cancellable = true)
    private void rtmu$detachedDistance(float distance, CallbackInfoReturnable<Float> cir) {
        CalculateDetachedCameraDistanceEvent event =
            new CalculateDetachedCameraDistanceEvent((Camera) (Object) this, cir.getReturnValue());
        NeoForge.EVENT_BUS.post(event);
        cir.setReturnValue(event.getDistance());
    }
}
