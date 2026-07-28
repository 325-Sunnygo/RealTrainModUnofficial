package com.portofino.polygondtrainmod.mixin;

import com.portofino.realtrainmodunofficial.client.FreeCameraState;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * フリーカメラ中はスニーク (シフト) で列車から降りないようにする。
 * <p>
 * バニラは {@code LocalPlayer.rideTick()} で {@code wantsToStopRiding()} が true なら
 * 降車する。フリーカメラ中はシフトをカメラ下降に使うため、ここで false を返して
 * 降車をキャンセルする ({@code V} キーでフリーカメラを抜けるまで座席に残る)。
 * <p>
 * {@link FreeCameraState} は client 依存の無いフラグホルダーなので、専用サーバーでも
 * 安全にロードできる (サーバーでは常に false = 影響なし)。
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
