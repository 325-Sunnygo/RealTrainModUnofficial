package com.portofino.realtrainmodunofficial.client;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.client.screen.ModelSelectScreen;
import com.portofino.realtrainmodunofficial.client.screen.SignSelectGridScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

/**
 * モデル選択 / 標識・看板選択画面を開いている間は、ミニマップ等の <b>MOD 製 HUD レイヤー</b>を隠す。
 * <p>
 * JourneyMap などのミニマップは {@code net.minecraft.client.Options#hideGui} (F1) を無視して
 * 選択画面の上に被さる。ミニマップは NeoForge の GUI レイヤー ({@link RenderGuiLayerEvent}) として
 * 描画されるので、選択画面が開いている間はその <b>Pre をキャンセル</b>して描かせないようにする。
 * vanilla (minecraft 名前空間) のレイヤーは対象外 (hideGui 側で処理済み)。
 */
@EventBusSubscriber(modid = RealTrainModUnofficial.MODID, value = Dist.CLIENT)
public final class SelectScreenHudHider {
    private SelectScreenHudHider() {
    }

    @SubscribeEvent
    public static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof ModelSelectScreen) && !(mc.screen instanceof SignSelectGridScreen)) {
            return;
        }
        ResourceLocation layer = event.getName();
        if (layer != null && !"minecraft".equals(layer.getNamespace())) {
            event.setCanceled(true);
        }
    }
}
