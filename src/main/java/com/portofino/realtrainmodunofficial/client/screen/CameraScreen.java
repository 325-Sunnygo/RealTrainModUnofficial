package com.portofino.realtrainmodunofficial.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 本家 {@code jp.ngt.rtm.gui.GuiCamera} の移植。
 *
 * <p>本家は {@code ItemCamera.onItemUse} (ブロック右クリック) から開かれ、
 * R/G/B/Hex/Alpha/Radius のラベルと、色見本の四角を描くだけの画面。
 * RTMU で独自実装だった「撮り鉄モード切替」をやめ、本家どおりこの画面を開く。
 */
public class CameraScreen extends Screen {

    public CameraScreen() {
        super(Component.literal("Camera"));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);

        int halfW = this.width / 2;
        // 本家の色 (16711680=赤, 65280=緑, 255=青, 16777215=白)
        g.drawCenteredString(this.font, "R", halfW - 90, 25, 0xFF0000);
        g.drawCenteredString(this.font, "G", halfW - 90, 45, 0x00FF00);
        g.drawCenteredString(this.font, "B", halfW - 90, 65, 0x0000FF);
        g.drawCenteredString(this.font, "Hex", halfW - 90, 90, 0xFFFFFF);
        g.drawCenteredString(this.font, "Alpha", halfW - 95, 115, 0xFFFFFF);
        g.drawCenteredString(this.font, "Radius", halfW + 12, 65, 0xFFFFFF);

        // 本家は NGTTessellator で (halfW+8,20)-(halfW+40,52) に無色の四角を描く。
        g.fill(halfW + 8, 20, halfW + 40, 52, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
