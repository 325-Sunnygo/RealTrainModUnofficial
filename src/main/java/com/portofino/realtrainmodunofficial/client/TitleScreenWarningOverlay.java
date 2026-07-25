package com.portofino.realtrainmodunofficial.client;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.List;

@EventBusSubscriber(modid = RealTrainModUnofficial.MODID, value = Dist.CLIENT)
public final class TitleScreenWarningOverlay {
    private TitleScreenWarningOverlay() {
    }

    //同意画面をこのセッションで一度開いたか (タイトルへ戻る度に開き直さない)。
    private static boolean consentOpened;

    //前提パック不足の警告をこのセッションで一度出したか。
    private static boolean warningOpened;

    /**
     * タイトル画面が開いたときの順番:
     * <ol>
     *   <li>README 未同意のパックがあれば、まず同意画面を出す</li>
     *   <li>同意が全部片付いたら、前提パックが足りないものを警告画面 (OK) で知らせる</li>
     * </ol>
     * 同意画面は閉じるとタイトルへ戻るので、そこでこのイベントがもう一度走り、
     * 未決が無くなった時点で警告へ進む。同意画面が無い環境では最初の 1 回で警告が出る。
     * (パック読み込みはタイトル画面より前に済んでいるので、この時点で一覧は揃っている)
     */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (com.portofino.realtrainmodunofficial.pack.PackConsent.hasPending()) {
            if (consentOpened) {
                return;
            }
            consentOpened = true;
            //init 中の setScreen 再入を避けて次tickで開く。
            mc.execute(() -> {
                if (mc.screen instanceof TitleScreen) {
                    var screen = com.portofino.realtrainmodunofficial.client.screen.PackConsentScreen
                            .createIfPending(mc.screen);
                    if (screen != null) {
                        mc.setScreen(screen);
                    }
                }
            });
            return;
        }
        if (warningOpened || !com.portofino.realtrainmodunofficial.pack.PackPrerequisiteCheck.hasMissing()) {
            return;
        }
        warningOpened = true;
        mc.execute(() -> {
            if (mc.screen instanceof TitleScreen) {
                mc.setScreen(new com.portofino.realtrainmodunofficial.client.screen.PackWarningScreen(
                    mc.screen,
                    com.portofino.realtrainmodunofficial.pack.PackPrerequisiteCheck.getMissing()));
            }
        });
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        List<String> warnings = PackRequirementWarnings.getWarnings();
        if (warnings.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        GuiGraphics graphics = event.getGuiGraphics();
        int x = 8;
        int y = 8;
        int maxWidth = 0;
        for (String warning : warnings) {
            maxWidth = Math.max(maxWidth, minecraft.font.width(warning));
        }
        int height = warnings.size() * (minecraft.font.lineHeight + 2) + 6;
        graphics.fill(x - 4, y - 4, x + maxWidth + 6, y + height, 0xB0200000);
        int lineY = y;
        for (String warning : warnings) {
            graphics.drawString(minecraft.font, warning, x, lineY, 0xFFFF66, false);
            lineY += minecraft.font.lineHeight + 2;
        }
    }
}
