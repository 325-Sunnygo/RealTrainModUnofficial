package com.portofino.polygondtrainmod.mixin;

import com.portofino.realtrainmodunofficial.client.FreeCameraController;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * フリーカメラ (FreeCameraController) 中はマウスの視点移動をプレイヤーではなく
 * カメラへ振り向ける。MouseHandler.turnPlayer 末尾の
 * player.turn(dyaw, dpitch) を横取りし、フリーカメラが有効なら体を回さずに
 * カメラ独自の向きだけを動かす (体は列車内で固定・視点追従オフ)。
 */
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
