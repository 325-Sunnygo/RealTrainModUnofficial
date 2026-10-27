package com.portofino.realtrainmodunofficial.client.screen;

import com.portofino.realtrainmodunofficial.network.StationNamePayload;
import jp.ngt.rtm.block.tileentity.TileEntityStation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 駅名の編集。本家 {@code jp.ngt.rtm.gui.GuiStation} の移植。
 * 本家と同じで「駅名 1 行 + 完了 / キャンセル」だけ。
 */
public class StationCoreScreen extends Screen {
    private final BlockPos pos;
    private final String initialName;
    private EditBox stationName;

    public StationCoreScreen(BlockPos pos, String initialName) {
        super(Component.translatable("screen.realtrainmodunofficial.station"));
        this.pos = pos;
        this.initialName = initialName == null ? "" : initialName;
    }

    @Override
    protected void init() {
        int hw = this.width / 2;
        this.stationName = new EditBox(this.font, hw - 10, 80, 100, 20,
            Component.translatable("screen.realtrainmodunofficial.station_name"));
        this.stationName.setMaxLength(255);
        this.stationName.setValue(this.initialName);
        this.addRenderableWidget(this.stationName);
        this.setInitialFocus(this.stationName);

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> {
            PacketDistributor.sendToServer(new StationNamePayload(this.pos, this.stationName.getValue()));
            this.onClose();
        }).bounds(hw - 155, this.height - 28, 150, 20).build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> this.onClose())
            .bounds(hw + 5, this.height - 28, 150, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font,
            Component.translatable("screen.realtrainmodunofficial.station_name"),
            this.width / 2 - 90, 86, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** BE から今の名前を読んで開く。 */
    public static void open(BlockPos pos) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        String name = mc.level.getBlockEntity(pos) instanceof TileEntityStation station
            ? station.getStationName() : "";
        mc.setScreen(new StationCoreScreen(pos, name));
    }

    /** 1.21 のメニューぼかしを無効化 (本家 1.7.10 の GUI にぼかしは無い)。 */
    @Override
    protected void renderBlurredBackground(float partialTick) {
    }
}
