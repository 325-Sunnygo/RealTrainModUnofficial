package com.portofino.polygondtrainmod.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ScreenEvent.Opening の発火点。
 * 画面が開く前に横取りできる。
 * ★差し替え (setNewScreen) にも対応すること。
 */
@Mixin(Minecraft.class)
public class MinecraftScreenMixin {

    /**
     * 差し替えのために自分で setScreen を呼び直す間だけ立てる。
     * これが無いと差し替え後の画面でもう一度イベントが飛び、条件次第で無限に潜る。
     */
    private static boolean rtmu$replacing;

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void rtmu$screenOpening(Screen newScreen, CallbackInfo ci) {
        if (rtmu$replacing) {
            return;
        }
        Minecraft mc = (Minecraft) (Object) this;
        ScreenEvent.Opening event = new ScreenEvent.Opening(mc.screen, newScreen);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            ci.cancel();
            return;
        }
        Screen replacement = event.getNewScreen();
        if (replacement != newScreen) {
            ci.cancel();
            rtmu$replacing = true;
            try {
                mc.setScreen(replacement);
            } finally {
                rtmu$replacing = false;
            }
        }
    }
}
