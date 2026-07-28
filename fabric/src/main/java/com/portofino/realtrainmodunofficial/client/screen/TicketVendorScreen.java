package com.portofino.realtrainmodunofficial.client.screen;

import com.portofino.realtrainmodunofficial.network.BuyTicketPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 本家 GuiTicketVendor + VendorScreenSelectTicket の移植。
 * 券売機を素手で右クリックすると開き、「切符」と「回数券」の2つのボタンだけがある。
 */
@OnlyIn(Dist.CLIENT)
public class TicketVendorScreen extends Screen {

    //本家 VendorScreenSelectTicket のボタン (100x50 を2つ横並び)
    private static final int BTN_W = 100;
    private static final int BTN_H = 50;
    private static final int GAP = 8;

    private final BlockPos pos;

    public TicketVendorScreen(BlockPos pos) {
        super(Component.translatable("screen.realtrainmodunofficial.ticket_vendor"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        int totalWidth = BTN_W * 2 + GAP;
        int left = (this.width - totalWidth) / 2;
        int top = (this.height - BTN_H) / 2;

        addRenderableWidget(Button.builder(
                Component.translatable("screen.realtrainmodunofficial.ticket_vendor.ticket"),
                b -> buy(false))
            .bounds(left, top, BTN_W, BTN_H)
            .build());
        addRenderableWidget(Button.builder(
                Component.translatable("screen.realtrainmodunofficial.ticket_vendor.ticket_book"),
                b -> buy(true))
            .bounds(left + BTN_W + GAP, top, BTN_W, BTN_H)
            .build());
    }

    private void buy(boolean book) {
        PacketDistributor.sendToServer(new BuyTicketPayload(pos, book));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2,
            (this.height - BTN_H) / 2 - 20, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
