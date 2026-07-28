package com.portofino.polygondtrainmod.mixin;

import com.portofino.realtrainmodunofficial.client.FreeCameraController;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * フリーカメラ (FreeCameraController) 用: Camera.setup の末尾でカメラ位置と
 * 向きを自由位置に上書きする。
 * ViewportEvent.ComputeCameraAngles イベントは setup 内で position を確定させる前に
 * 発火するため、そこで位置を上書きしても直後に setPosition(entity位置) で戻されてしまう。
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "setup", at = @At("TAIL"))
    private void rtmu$freeCamera(BlockGetter level, Entity entity, boolean detached,
                                 boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
        Vec3 p = FreeCameraController.getInterpolatedPosition(partialTick);
        if (p == null) {
            return;
        }
        this.setPosition(p.x, p.y, p.z);
        // 向きはカメラ独自 (体は回さない = 視点追従オフ)。三人称のオフセット回転も打ち消す。
        this.setRotation(FreeCameraController.getYaw(), FreeCameraController.getPitch());
    }
}
