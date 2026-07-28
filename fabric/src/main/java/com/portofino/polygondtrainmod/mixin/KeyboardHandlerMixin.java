package com.portofino.polygondtrainmod.mixin;

import net.minecraft.client.KeyboardHandler;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * InputEvent.Key の発火点。
 * NeoForge はキー入力をイベントとして流すが、Fabric API に相当物が無い。
 */
@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("HEAD"))
    private void rtmu$postKeyEvent(long window, int key, int scanCode, int action, int modifiers,
                                   CallbackInfo ci) {
        // 自分のウィンドウ以外 (デバッグ用の別ウィンドウ等) は無視する
        if (window != net.minecraft.client.Minecraft.getInstance().getWindow().getWindow()) {
            return;
        }
        NeoForge.EVENT_BUS.post(new InputEvent.Key(key, scanCode, action, modifiers));
    }
}
