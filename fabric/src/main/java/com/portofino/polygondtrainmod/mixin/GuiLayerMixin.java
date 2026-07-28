package com.portofino.polygondtrainmod.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * RenderGuiLayerEvent.Pre の発火点 (ホットバーとクロスヘア)。
 * NeoForge は HUD を「層」に分けて各層の前後にイベントを流す。
 */
@Mixin(Gui.class)
public class GuiLayerMixin {

    @Inject(method = "renderItemHotbar", at = @At("HEAD"), cancellable = true)
    private void rtmu$hotbarLayer(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        if (post(graphics, VanillaGuiLayers.HOTBAR)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void rtmu$crosshairLayer(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        if (post(graphics, VanillaGuiLayers.CROSSHAIR)) {
            ci.cancel();
        }
    }

    /** @return true = 購読側が取り消した (描かない) */
    private static boolean post(GuiGraphics graphics, net.minecraft.resources.ResourceLocation name) {
        RenderGuiLayerEvent.Pre event = new RenderGuiLayerEvent.Pre(graphics, name);
        NeoForge.EVENT_BUS.post(event);
        return event.isCanceled();
    }
}
