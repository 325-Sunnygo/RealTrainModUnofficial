package com.portofino.realtrainmodunofficial.client.screen;

import com.portofino.realtrainmodunofficial.menu.WorkBenchMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * RTM 専用作業台の画面。本家 {@code GuiRTMWorkBench} 相当。
 *
 * <p>本家は専用の背景画像を使うが、RTMU は同梱していないので枠だけ自前で描く。
 */
public class WorkBenchScreen extends AbstractContainerScreen<WorkBenchMenu> {

    private static final int BG = 0xFFC6C6C6;
    private static final int SLOT = 0xFF8B8B8B;

    public WorkBenchScreen(WorkBenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 204;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, BG);
        graphics.fill(x, y, x + this.imageWidth, y + 1, 0xFFFFFFFF);
        graphics.fill(x, y, x + 1, y + this.imageHeight, 0xFFFFFFFF);
        graphics.fill(x + this.imageWidth - 1, y, x + this.imageWidth, y + this.imageHeight, 0xFF555555);
        graphics.fill(x, y + this.imageHeight - 1, x + this.imageWidth, y + this.imageHeight, 0xFF555555);
        //枠は menu 側のスロット位置に合わせる
        for (var slot : this.menu.slots) {
            graphics.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, SLOT);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    /** 1.21 のメニューぼかしを無効化 (本家 1.7.10 の GUI にぼかしは無い)。 */
    @Override
    protected void renderBlurredBackground(float partialTick) {
    }
}
