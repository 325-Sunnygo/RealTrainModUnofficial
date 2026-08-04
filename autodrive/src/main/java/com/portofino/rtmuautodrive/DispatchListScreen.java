package com.portofino.rtmuautodrive;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * 自動運転装置を使うと出る画面。
 * 置いてある列車運転スポナーの名前が並び、右の「発車」を押すとその編成が走り出す。
 */
public class DispatchListScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final int LIST_WIDTH = 240;
    private static final int LAUNCH_WIDTH = 56;

    private final List<AutoDriveNetwork.Entry> entries;
    private int scroll;

    public DispatchListScreen(List<AutoDriveNetwork.Entry> entries) {
        super(Component.translatable("screen.rtmuautodrive.dispatch_list"));
        this.entries = entries;
    }

    private int visibleRows() {
        return Math.max(1, (this.height - 80) / ROW_HEIGHT);
    }

    @Override
    protected void init() {
        this.clearWidgets();
        int rows = Math.min(this.visibleRows(), this.entries.size() - this.scroll);
        int left = (this.width - LIST_WIDTH) / 2;
        int top = 40;
        for (int i = 0; i < rows; i++) {
            AutoDriveNetwork.Entry entry = this.entries.get(this.scroll + i);
            int y = top + i * ROW_HEIGHT;
            Button launch = Button.builder(
                    Component.translatable("screen.rtmuautodrive.launch"),
                    b -> {
                        PacketDistributor.sendToServer(new AutoDriveNetwork.Launch(entry.pos()));
                        this.onClose();
                    })
                    .bounds(left + LIST_WIDTH - LAUNCH_WIDTH, y, LAUNCH_WIDTH, 20)
                    .build();
            //編成アイテムが入っていない / 近くにレールが無いスポナーは押せない
            launch.active = entry.ready();
            this.addRenderableWidget(launch);
        }
        //スクロール
        if (this.entries.size() > this.visibleRows()) {
            this.addRenderableWidget(Button.builder(Component.literal("▲"), b -> {
                this.scroll = Math.max(0, this.scroll - 1);
                this.init();
            }).bounds(left + LIST_WIDTH + 6, top, 20, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("▼"), b -> {
                this.scroll = Math.min(this.entries.size() - this.visibleRows(), this.scroll + 1);
                this.init();
            }).bounds(left + LIST_WIDTH + 6, top + 24, 20, 20).build());
        }
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
        if (this.entries.isEmpty()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.rtmuautodrive.empty"), this.width / 2, 60, 0xA0A0A0);
            return;
        }
        int rows = Math.min(this.visibleRows(), this.entries.size() - this.scroll);
        int left = (this.width - LIST_WIDTH) / 2;
        for (int i = 0; i < rows; i++) {
            AutoDriveNetwork.Entry entry = this.entries.get(this.scroll + i);
            int y = 40 + i * ROW_HEIGHT;
            graphics.fill(left, y, left + LIST_WIDTH - LAUNCH_WIDTH - 4, y + 20, 0x60000000);
            graphics.drawString(this.font, entry.name(), left + 6, y + 6,
                    entry.ready() ? 0xFFFFFF : 0x808080, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
