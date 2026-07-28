package com.portofino.realtrainmodunofficial.client.screen;

import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.network.ChangeOffsetPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 本家 GuiChangeOffset の 1.21.1 移植。バールで設置物を右クリックすると開く。
 * Offset X/Y/Z (ブロック単位・小数可 = ミリ単位調整)、Rotation Roll/Pitch/Yaw、Scale。
 */
public class ChangeOffsetScreen extends Screen {
    private final InstalledObjectBlockEntity blockEntity;
    private EditBox fieldOffsetX;
    private EditBox fieldOffsetY;
    private EditBox fieldOffsetZ;
    private EditBox fieldRoll;
    private EditBox fieldPitch;
    private EditBox fieldYaw;
    private EditBox fieldScale;

    public ChangeOffsetScreen(InstalledObjectBlockEntity blockEntity) {
        super(Component.literal("Change Offset"));
        this.blockEntity = blockEntity;
    }

    @Override
    protected void init() {
        int x = this.width - 70 - 30;
        this.fieldOffsetX = addNumberField(x, 20, (float) blockEntity.getRenderOffset().x);
        this.fieldOffsetY = addNumberField(x, 50, (float) blockEntity.getRenderOffset().y);
        this.fieldOffsetZ = addNumberField(x, 80, (float) blockEntity.getRenderOffset().z);
        this.fieldRoll = addNumberField(x, 110, blockEntity.getAdjustRoll());
        this.fieldPitch = addNumberField(x, 140, blockEntity.getAdjustPitch());
        this.fieldYaw = addNumberField(x, 170, blockEntity.getAdjustYaw());
        this.fieldScale = addNumberField(x, 200, blockEntity.getAdjustScale());

        addRenderableWidget(Button.builder(Component.literal("適用して閉じる"), b -> {
            send();
            onClose();
        }).bounds(this.width / 2 - 158, this.height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("適用"), b -> send())
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("キャンセル"), b -> onClose())
                .bounds(this.width / 2 + 58, this.height - 28, 100, 20).build());
    }

    private EditBox addNumberField(int x, int y, float value) {
        EditBox box = new EditBox(this.font, x, y, 60, 20, Component.empty());
        box.setValue(trim(value));
        box.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d{0,5}(\\.\\d{0,5})?"));
        return addRenderableWidget(box);
    }

    private static String trim(float v) {
        return v == (long) v ? String.valueOf((long) v) : String.valueOf(v);
    }

    private float parse(EditBox box, float fallback) {
        try {
            return Float.parseFloat(box.getValue().trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private void send() {
        PacketDistributor.sendToServer(new ChangeOffsetPayload(
                blockEntity.getBlockPos(),
                parse(fieldOffsetX, 0), parse(fieldOffsetY, 0), parse(fieldOffsetZ, 0),
                parse(fieldRoll, 0), parse(fieldPitch, 0), parse(fieldYaw, 0),
                parse(fieldScale, 1)));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int x = this.width - 70;
        graphics.drawCenteredString(this.font, "Offset X", x, 10, 0xFFFFFF);
        graphics.drawCenteredString(this.font, "Offset Y", x, 40, 0xFFFFFF);
        graphics.drawCenteredString(this.font, "Offset Z", x, 70, 0xFFFFFF);
        graphics.drawCenteredString(this.font, "Rotation Roll", x, 100, 0xFFFFFF);
        graphics.drawCenteredString(this.font, "Rotation Pitch", x, 130, 0xFFFFFF);
        graphics.drawCenteredString(this.font, "Rotation Yaw", x, 160, 0xFFFFFF);
        graphics.drawCenteredString(this.font, "Scale", x, 190, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
