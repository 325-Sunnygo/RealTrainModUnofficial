package com.portofino.polygondtrainmod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code RenderHandEvent} の発火点。
 *
 * <p>RTMU は乗車中・カメラ使用中に手 (と持ち物) を消すためにこのイベントを取り消している。
 * 無いと運転中ずっと手が画面に出る。
 */
@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void rtmu$renderHand(net.minecraft.client.player.AbstractClientPlayer player,
                                 float partialTick, float pitch, InteractionHand hand,
                                 float swingProgress, net.minecraft.world.item.ItemStack stack,
                                 float equipProgress, PoseStack poseStack,
                                 net.minecraft.client.renderer.MultiBufferSource buffer,
                                 int packedLight, CallbackInfo ci) {
        RenderHandEvent event = new RenderHandEvent(hand, poseStack, partialTick);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            ci.cancel();
        }
    }
}
