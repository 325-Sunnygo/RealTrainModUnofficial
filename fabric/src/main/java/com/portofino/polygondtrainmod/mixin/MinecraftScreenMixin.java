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
 * {@code ScreenEvent.Opening} の発火点。
 *
 * <p>画面が開く<b>前</b>に横取りできる。RTMU はタイトル画面での警告表示や、
 * 同意前の画面遷移の制御に使っている。
 *
 * <p>★<b>差し替え (setNewScreen) にも対応すること。</b>
 * 取り消しだけ見ていたので、運転席で E を押したときに
 * 「インベントリを運転台 GUI に差し替える」処理が効かず、<b>素のインベントリが開いていた</b>。
 */
@Mixin(Minecraft.class)
public class MinecraftScreenMixin {

    /**
     * 差し替えのために自分で {@code setScreen} を呼び直す間だけ立てる。
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
