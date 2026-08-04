package com.portofino.rtmuautodrive;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * 詳細設定。レールで繋がっている駅を順番に並べ、駅ごとに
 * <b>停車 / 通過</b> と <b>ドアをどちら側に開けるか</b> を決める。
 *
 * <p>ここに出るのは<b>スポナーからレールが途切れずに繋がっている駅だけ</b>。
 * 線路が切れていたり 1 ブロックでもずれていれば出てこない
 * (出てこない = 繋がっていない、の目印になる)。
 */
public class RouteScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final int LIST_WIDTH = 320;
    private static final int STOP_WIDTH = 60;
    private static final int DOOR_WIDTH = 84;

    private final BlockPos dispatcher;
    private final List<AutoDriveNetwork.RouteEntry> entries;
    private int scroll;

    public RouteScreen(BlockPos dispatcher, List<AutoDriveNetwork.RouteEntry> entries) {
        super(Component.translatable("screen.rtmuautodrive.route"));
        this.dispatcher = dispatcher;
        this.entries = new ArrayList<>(entries);
    }

    private int visibleRows() {
        return Math.max(1, (this.height - 90) / ROW_HEIGHT);
    }

    /** 変更をサーバーへ送る (停車/通過とドアは同じ 1 通で送る)。 */
    private void send(int index) {
        AutoDriveNetwork.RouteEntry e = this.entries.get(index);
        PacketDistributor.sendToServer(
                new AutoDriveNetwork.SetStop(this.dispatcher, e.pos(), e.stop(), e.door()));
    }

    @Override
    protected void init() {
        this.clearWidgets();
        int rows = Math.min(this.visibleRows(), this.entries.size() - this.scroll);
        int left = (this.width - LIST_WIDTH) / 2;
        int top = 46;
        for (int i = 0; i < rows; i++) {
            int index = this.scroll + i;
            int y = top + i * ROW_HEIGHT;

            //停車 / 通過
            Button stopButton = Button.builder(stopLabel(this.entries.get(index).stop()), b -> {
                AutoDriveNetwork.RouteEntry now = this.entries.get(index);
                AutoDriveNetwork.RouteEntry next = new AutoDriveNetwork.RouteEntry(
                        now.pos(), now.name(), !now.stop(), now.door());
                this.entries.set(index, next);
                this.send(index);
                b.setMessage(stopLabel(next.stop()));
                this.init(); //通過にしたらドアのボタンを押せなくする
            }).bounds(left + LIST_WIDTH - STOP_WIDTH - DOOR_WIDTH - 4, y, STOP_WIDTH, 20).build();
            this.addRenderableWidget(stopButton);

            //ドア: 両側 → 左のみ → 右のみ
            Button doorButton = Button.builder(doorLabel(this.entries.get(index).door()), b -> {
                AutoDriveNetwork.RouteEntry now = this.entries.get(index);
                AutoDriveNetwork.RouteEntry next = new AutoDriveNetwork.RouteEntry(
                        now.pos(), now.name(), now.stop(), (now.door() + 1) % 3);
                this.entries.set(index, next);
                this.send(index);
                b.setMessage(doorLabel(next.door()));
            }).bounds(left + LIST_WIDTH - DOOR_WIDTH, y, DOOR_WIDTH, 20).build();
            //通過する駅ではドアを開けないので触らせない
            doorButton.active = this.entries.get(index).stop();
            this.addRenderableWidget(doorButton);
        }
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

    private static Component stopLabel(boolean stop) {
        return stop
                ? Component.translatable("screen.rtmuautodrive.stop").withStyle(net.minecraft.ChatFormatting.GREEN)
                : Component.translatable("screen.rtmuautodrive.pass").withStyle(net.minecraft.ChatFormatting.GRAY);
    }

    private static Component doorLabel(int door) {
        return Component.translatable(switch (door) {
            case 1 -> "screen.rtmuautodrive.door_left";
            case 2 -> "screen.rtmuautodrive.door_right";
            default -> "screen.rtmuautodrive.door_both";
        });
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.translatable("screen.rtmuautodrive.route_hint"), this.width / 2, 30, 0x909090);
        if (this.entries.isEmpty()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.rtmuautodrive.route_empty"), this.width / 2, 70, 0xA0A0A0);
            return;
        }
        int rows = Math.min(this.visibleRows(), this.entries.size() - this.scroll);
        int left = (this.width - LIST_WIDTH) / 2;
        for (int i = 0; i < rows; i++) {
            AutoDriveNetwork.RouteEntry entry = this.entries.get(this.scroll + i);
            int y = 46 + i * ROW_HEIGHT;
            graphics.fill(left, y, left + LIST_WIDTH - STOP_WIDTH - DOOR_WIDTH - 8, y + 20, 0x60000000);
            graphics.drawString(this.font, (this.scroll + i + 1) + ".", left + 6, y + 6, 0x808080, false);
            graphics.drawString(this.font, entry.name(), left + 24, y + 6,
                    entry.stop() ? 0xFFFFFF : 0x909090, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
