package com.portofino.realtrainmodunofficial.client.screen;

import com.portofino.realtrainmodunofficial.network.BindSignalReceiverPayload;
import com.portofino.realtrainmodunofficial.blockentity.SignalRemoteBlockEntity;
import com.portofino.realtrainmodunofficial.blockentity.SignalStateBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class SignalReceiverScreen extends Screen {
    private final BlockPos pos;
    private EditBox channelBox;

    public SignalReceiverScreen(BlockPos pos) {
        super(Component.literal("受信機"));
        this.pos = pos.immutable();
    }

    @Override
    protected void init() {
        int boxWidth = 140;
        int x = (width - boxWidth) / 2;
        int y = height / 2 - 18;
        channelBox = new EditBox(font, x, y, boxWidth, 20, Component.literal("番号"));
        channelBox.setMaxLength(10);
        readCurrentValue();
        addRenderableWidget(channelBox);
        setInitialFocus(channelBox);

        addRenderableWidget(Button.builder(Component.literal("接続"), button -> submit())
            .bounds(width / 2 - 75, y + 30, 70, 20)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
            .bounds(width / 2 + 5, y + 30, 70, 20)
            .build());
    }

    private void readCurrentValue() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        if (minecraft.level.getBlockEntity(pos) instanceof SignalRemoteBlockEntity blockEntity && blockEntity.getLinkedChannel() > 0) {
            channelBox.setValue(Integer.toString(blockEntity.getLinkedChannel()));
        } else if (minecraft.level.getBlockEntity(pos) instanceof SignalStateBlockEntity blockEntity && blockEntity.getLinkedChannel() > 0) {
            channelBox.setValue(Integer.toString(blockEntity.getLinkedChannel()));
        }
    }

    private void submit() {
        try {
            int channel = Integer.parseInt(channelBox.getValue().trim());
            PacketDistributor.sendToServer(new BindSignalReceiverPayload(pos, channel));
            onClose();
        } catch (NumberFormatException ignored) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(Component.literal("番号を入力してください"), true);
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 40, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.literal("通信機で表示した番号を入力"), width / 2, height / 2 - 28, 0xAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
