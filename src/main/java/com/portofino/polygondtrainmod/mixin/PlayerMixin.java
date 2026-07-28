package com.portofino.polygondtrainmod.mixin;

import com.portofino.realtrainmodunofficial.client.FreeCameraState;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * フリーカメラ中はスニーク (シフト) で列車から降りないようにする。
 * バニラは LocalPlayer.rideTick で wantsToStopRiding が true なら
 * 降車する。
 */
@Mixin(Player.class)
public abstract class PlayerMixin {

    @Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
    private void rtmu$freeCameraKeepsRiding(CallbackInfoReturnable<Boolean> cir) {
        if (FreeCameraState.active) {
            cir.setReturnValue(false);
        }
    }
}
