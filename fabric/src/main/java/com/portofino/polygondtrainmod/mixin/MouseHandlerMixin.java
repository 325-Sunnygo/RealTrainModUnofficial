package com.portofino.polygondtrainmod.mixin;

import com.portofino.realtrainmodunofficial.client.FreeCameraController;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

    /**
     * InputEvent.MouseButton.Pre の発火点。
     * RTMU はクリックの横取り (エディタの範囲選択、カメラのシャッター) に使う。
     */
    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void rtmu$postMouseButton(long window, int button, int action, int modifiers,
                                      CallbackInfo ci) {
        if (window != net.minecraft.client.Minecraft.getInstance().getWindow().getWindow()) {
            return;
        }
        var event = new net.neoforged.neoforge.client.event.InputEvent.MouseButton.Pre(
            button, action, modifiers);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            ci.cancel();
        }
    }

    /**
     * InputEvent.MouseScrollingEvent の発火点。
     * レバーサ操作、エディタのスニーク+ホイールによる面の伸縮に使う。
     */
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void rtmu$postMouseScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (window != net.minecraft.client.Minecraft.getInstance().getWindow().getWindow()) {
            return;
        }
        var mc = net.minecraft.client.Minecraft.getInstance();
        var event = new net.neoforged.neoforge.client.event.InputEvent.MouseScrollingEvent(
            xOffset, yOffset, mc.mouseHandler.xpos(), mc.mouseHandler.ypos());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            ci.cancel();
        }
    }
}
