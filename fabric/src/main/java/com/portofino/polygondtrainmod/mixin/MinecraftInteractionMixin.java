package com.portofino.polygondtrainmod.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * InputEvent.InteractionKeyMappingTriggered の発火点。
 * 攻撃・使用・アイテムピックのキー割り当てが引かれた瞬間に流れるイベントで、
 * 取り消すとその操作自体が起きない。
 */
@Mixin(Minecraft.class)
public class MinecraftInteractionMixin {

    // ★startAttack は boolean を返す (「実際に殴ったか」)。取り消すときは false を返す。
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void rtmu$attack(CallbackInfoReturnable<Boolean> cir) {
        // button 0 = 攻撃。手はメインハンド固定 (バニラの攻撃はメインのみ)。
        if (post(0, InteractionHand.MAIN_HAND)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void rtmu$use(CallbackInfo ci) {
        if (post(1, InteractionHand.MAIN_HAND)) {
            ci.cancel();
        }
    }

    @Inject(method = "pickBlock", at = @At("HEAD"), cancellable = true)
    private void rtmu$pick(CallbackInfo ci) {
        if (post(2, InteractionHand.MAIN_HAND)) {
            ci.cancel();
        }
    }

    /** @return true = 購読側が取り消した */
    private static boolean post(int button, InteractionHand hand) {
        InputEvent.InteractionKeyMappingTriggered event =
            new InputEvent.InteractionKeyMappingTriggered(button, hand);
        NeoForge.EVENT_BUS.post(event);
        return event.isCanceled();
    }
}
