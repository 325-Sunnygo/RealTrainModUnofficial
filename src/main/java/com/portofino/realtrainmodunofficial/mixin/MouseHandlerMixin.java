package com.portofino.realtrainmodunofficial.mixin;

import com.portofino.realtrainmodunofficial.client.FreeCameraController;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** フリーカメラ (FreeCameraController) 中はマウスの視点移動をプレイヤーではな… */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Redirect(
            method = "turnPlayer",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void rtmu$redirectTurn(LocalPlayer player, double dyaw, double dpitch) {
        if (FreeCameraController.isActive()) {
            FreeCameraController.addLook(dyaw, dpitch);
        } else {
            player.turn(dyaw, dpitch);
        }
    }
}
