package com.portofino.realtrainmodunofficial.client.screen;

import com.portofino.realtrainmodunofficial.blockentity.SignalRemoteBlockEntity;
import com.portofino.realtrainmodunofficial.network.BindSignalReceiverPayload;
import com.portofino.realtrainmodunofficial.network.SetSignalValuePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class SignalValueScreen extends Screen {
    private final BlockPos pos;
    private EditBox channelBox;
    private EditBox valueBox;
    private int linkedChannel = -1;

    public SignalValueScreen(BlockPos pos) {
        super(Component.literal("受信機(signal値)"));
        this.pos = pos.immutable();
    }

    @Override
    protected void init() {
        linkedChannel = readLinkedChannel();
        int boxWidth = 150;
        int x = (width - boxWidth) / 2;
        int baseY = height / 2 - 30;

        if (linkedChannel > 0) {
            valueBox = new EditBox(font, x, baseY, boxWidth, 20, Component.literal("signal値"));
            valueBox.setMaxLength(10);
            addRenderableWidget(valueBox);
            setInitialFocus(valueBox);
        } else {
            channelBox = new EditBox(font, x, baseY, boxWidth, 20, Component.literal("信号番号"));
            channelBox.setMaxLength(10);
            addRenderableWidget(channelBox);
            setInitialFocus(channelBox);
        }

        addRenderableWidget(Button.builder(Component.literal("決定"), button -> submit())
            .bounds(width / 2 - 75, baseY + 40, 70, 20)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
            .bounds(width / 2 + 5, baseY + 40, 70, 20)
            .build());
    }

    private void submit() {
        try {
            if (linkedChannel > 0) {
                int signalValue = Integer.parseInt(valueBox.getValue().trim());
                PacketDistributor.sendToServer(new SetSignalValuePayload(pos, signalValue));
            } else {
                int channel = Integer.parseInt(channelBox.getValue().trim());
                PacketDistributor.sendToServer(new BindSignalReceiverPayload(pos, channel));
            }
            onClose();
        } catch (NumberFormatException ignored) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(Component.literal(
                    linkedChannel > 0 ? "signal値を入力してください" : "信号番号を入力してください"
                ), true);
            }
        }
    }

    private int readLinkedChannel() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return -1;
        }
        if (minecraft.level.getBlockEntity(pos) instanceof SignalRemoteBlockEntity blockEntity) {
            return blockEntity.getLinkedChannel();
        }
        return -1;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 70, 0xFFFFFF);
        if (linkedChannel > 0) {
            graphics.drawCenteredString(font, Component.literal("登録済み信号番号: " + linkedChannel), width / 2, height / 2 - 58, 0xAAAAAA);
            graphics.drawCenteredString(font, Component.literal("今回は signal値 のみ入力"), width / 2, height / 2 - 46, 0xAAAAAA);
        } else {
            graphics.drawCenteredString(font, Component.literal("先に信号番号を登録"), width / 2, height / 2 - 58, 0xAAAAAA);
            graphics.drawCenteredString(font, Component.literal("次回開いたときに signal値 を入力"), width / 2, height / 2 - 46, 0xAAAAAA);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
