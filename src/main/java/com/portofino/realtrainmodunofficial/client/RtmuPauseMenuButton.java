package com.portofino.realtrainmodunofficial.client;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.client.screen.RtmuSettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * ポーズメニュー (Esc) に「RTMU設定」ボタンを追加する。
 * 押すと {@link RtmuSettingsScreen} を開く。
 */
@EventBusSubscriber(modid = RealTrainModUnofficial.MODID, value = Dist.CLIENT)
public final class RtmuPauseMenuButton {

    private RtmuPauseMenuButton() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof PauseScreen pause)) {
            return;
        }
        //中央の既存ボタン列と重ならないよう左下に配置する。
        int x = 8;
        int y = pause.height - 28;
        Button button = Button.builder(Component.literal("RTMU設定"),
                b -> Minecraft.getInstance().setScreen(new RtmuSettingsScreen(pause)))
            .bounds(x, y, 120, 20)
            .build();
        event.addListener(button);
    }
}
