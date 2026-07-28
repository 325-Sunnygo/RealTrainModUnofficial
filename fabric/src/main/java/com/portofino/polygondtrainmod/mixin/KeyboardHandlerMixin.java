package com.portofino.polygondtrainmod.mixin;

import net.minecraft.client.KeyboardHandler;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code InputEvent.Key} の発火点。
 *
 * <p>NeoForge はキー入力をイベントとして流すが、Fabric API に相当物が無い。RTMU は
 * マスコン・ブレーキ・レバーサ・エディタ操作を全部このイベントで受けているので、
 * これが無いと<b>起動はするが運転できない</b>状態になる。
 *
 * <p>NeoForge と同じく「ウィンドウのキーイベントを受けた時点」で流す。画面が開いている
 * ときも流れる点まで同じ (購読側が {@code mc.screen != null} で弾いている)。
 */
@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("HEAD"))
    private void rtmu$postKeyEvent(long window, int key, int scanCode, int action, int modifiers,
                                   CallbackInfo ci) {
        //自分のウィンドウ以外 (デバッグ用の別ウィンドウ等) は無視する
        if (window != net.minecraft.client.Minecraft.getInstance().getWindow().getWindow()) {
            return;
        }
        NeoForge.EVENT_BUS.post(new InputEvent.Key(key, scanCode, action, modifiers));
    }
}
