package com.portofino.polygondtrainmod.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ViewportEvent.ComputeFov の発火点。
 * RTMU のカメラはレンズ交換で画角を変える。NeoForge はこのイベントで書き換えていた。
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    // ★getFov が返すのは double。Float で受けると ClassCastException で落ちる。
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void rtmu$computeFov(Camera camera, float partialTick, boolean useConfigured,
                                 CallbackInfoReturnable<Double> cir) {
        ViewportEvent.ComputeFov event = new ViewportEvent.ComputeFov(
            camera, partialTick, cir.getReturnValue(), useConfigured);
        NeoForge.EVENT_BUS.post(event);
        cir.setReturnValue(event.getFOV());
    }
}
