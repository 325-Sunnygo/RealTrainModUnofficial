package com.portofino.realtrainmodunofficial.online;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * オンライン連携のクライアントフック (タイトル画面のアップデート通知のみ)。
 */
@EventBusSubscriber(modid = RealTrainModUnofficial.MODID, value = Dist.CLIENT)
public final class OnlineClientHooks {

    private OnlineClientHooks() {
    }

    /** タイトル画面右上にアップデート通知を出す。 */
    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        String latest = RtmuOnlineServices.getUpdateLatestVersion();
        if (latest == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics g = event.getGuiGraphics();
        String line1 = "RTMU の新しいバージョンがあります: v" + latest;
        String line2 = "GitHub Releases からダウンロードできます";
        int w = Math.max(mc.font.width(line1), mc.font.width(line2));
        int x = event.getScreen().width - w - 12;
        int y = 8;
        g.fill(x - 4, y - 4, x + w + 4, y + 2 * (mc.font.lineHeight + 2) + 2, 0xB0003000);
        g.drawString(mc.font, line1, x, y, 0x7FFF7F, false);
        g.drawString(mc.font, line2, x, y + mc.font.lineHeight + 2, 0xCCFFCC, false);
    }
}
