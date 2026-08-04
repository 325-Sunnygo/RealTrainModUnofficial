package com.portofino.rtmuautodrive;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 駅列車ブロックの設定。<b>駅名だけ</b>。
 * ドアをどちら側に開けるかは運転ごとに変わるので、列車運転スポナーの詳細設定で持つ。
 */
public class StationNameScreen extends Screen {

    private final BlockPos pos;
    private final String initialName;
    private EditBox nameBox;

    public StationNameScreen(BlockPos pos, String name) {
        super(Component.translatable("screen.rtmuautodrive.station_name"));
        this.pos = pos;
        this.initialName = name == null ? "" : name;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int top = this.height / 2 - 20;

        this.nameBox = new EditBox(this.font, cx - 100, top, 200, 20,
                Component.translatable("screen.rtmuautodrive.station_name"));
        this.nameBox.setMaxLength(32);
        this.nameBox.setValue(this.initialName);
        this.nameBox.setHint(Component.translatable("screen.rtmuautodrive.station_name_hint"));
        this.addRenderableWidget(this.nameBox);
        this.setInitialFocus(this.nameBox);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> {
            PacketDistributor.sendToServer(
                    new AutoDriveNetwork.SetStationName(this.pos, this.nameBox.getValue()));
            this.onClose();
        }).bounds(cx - 50, top + 34, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 44, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
