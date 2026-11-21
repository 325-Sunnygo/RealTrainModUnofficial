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

    private static final java.util.Set<String> NOTIFIED=new java.util.HashSet<>();
    private static Object notifiedLevel;
    @SubscribeEvent
    public static void notifyPackUpdates(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        if(notifiedLevel!=mc.level){NOTIFIED.clear();notifiedLevel=mc.level;}
        if(mc.player==null)return;
        for(var update:jp.ngt.ngtlib.util.VersionChecker.getUpdates()) {
            if(!NOTIFIED.add(update.name()+"\n"+update.version()))continue;
            var message=net.minecraft.network.chat.Component.translatable("message.version",update.name()).append(" : "+update.version());
            try {
                var uri=java.net.URI.create(update.homepage());
                if("https".equalsIgnoreCase(uri.getScheme())||"http".equalsIgnoreCase(uri.getScheme()))
                    message.append(net.minecraft.network.chat.Component.literal("  Download here").withStyle(style->style.withColor(net.minecraft.ChatFormatting.GOLD).withUnderlined(true).withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.OPEN_URL,uri.toString()))));
            }catch(RuntimeException ignored){}
            mc.player.displayClientMessage(message,false);
        }
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
