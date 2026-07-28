package com.portofino.realtrainmodunofficial.client.screen;

import com.portofino.realtrainmodunofficial.item.PainterItem;
import com.portofino.realtrainmodunofficial.network.PainterSettingsPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** ペインターの設定画面 (neo mcte)。本家 MCTE GuiPainter 相当。 */
public class PainterSettingsScreen extends Screen {

    private final InteractionHand hand;
    private final ItemStack stack;

    private EditBox fieldBlock;
    private EditBox fieldSize;
    private boolean sphere;
    private boolean onlySolid;

    public PainterSettingsScreen(InteractionHand hand, ItemStack stack) {
        super(Component.translatable("gui.realtrainmodunofficial.painter.title"));
        this.hand = hand;
        this.stack = stack;
        CompoundTag tag = PainterItem.getTag(stack);
        this.sphere = !PainterItem.SHAPE_CUBE.equals(tag.getString(PainterItem.KEY_SHAPE));
        this.onlySolid = tag.getBoolean(PainterItem.KEY_ONLY_SOLID);
    }

    @Override
    protected void init() {
        CompoundTag tag = PainterItem.getTag(stack);
        int hw = this.width / 2;
        int y = 50;

        fieldBlock = new EditBox(this.font, hw - 20, y, 140, 20, Component.empty());
        fieldBlock.setMaxLength(128);
        fieldBlock.setValue(tag.getString(PainterItem.KEY_BLOCK));
        fieldBlock.setHint(Component.translatable("gui.realtrainmodunofficial.painter.offhand"));
        addRenderableWidget(fieldBlock);
        y += 24;

        fieldSize = new EditBox(this.font, hw - 20, y, 60, 20, Component.empty());
        fieldSize.setValue(String.valueOf(tag.contains(PainterItem.KEY_SIZE) ? tag.getInt(PainterItem.KEY_SIZE) : 1));
        addRenderableWidget(fieldSize);
        y += 24;

        addRenderableWidget(Button.builder(shapeLabel(), b -> {
            sphere = !sphere;
            b.setMessage(shapeLabel());
        }).bounds(hw - 20, y, 140, 20).build());
        y += 24;

        addRenderableWidget(Button.builder(solidLabel(), b -> {
            onlySolid = !onlySolid;
            b.setMessage(solidLabel());
        }).bounds(hw - 20, y, 140, 20).build());
        y += 30;

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> {
            apply();
            onClose();
        }).bounds(hw - 100, this.height - 28, 95, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
            .bounds(hw + 5, this.height - 28, 95, 20).build());
    }

    private Component shapeLabel() {
        return Component.translatable(sphere
            ? "gui.realtrainmodunofficial.painter.shape.sphere"
            : "gui.realtrainmodunofficial.painter.shape.cube");
    }

    private Component solidLabel() {
        return Component.translatable("gui.realtrainmodunofficial.painter.only_solid")
            .append(": ").append(onlySolid ? "ON" : "OFF");
    }

    private void apply() {
        int size;
        try {
            size = Integer.parseInt(fieldSize.getValue().trim());
        } catch (Exception e) {
            size = 1;
        }
        PacketDistributor.sendToServer(new PainterSettingsPayload(
            hand == InteractionHand.OFF_HAND, fieldBlock.getValue(), size,
            sphere ? PainterItem.SHAPE_SPHERE : PainterItem.SHAPE_CUBE, onlySolid));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        int hw = this.width / 2;
        g.drawCenteredString(this.font, this.title, hw, 20, 0xFFFFFF);
        label(g, hw - 24, 50, "gui.realtrainmodunofficial.painter.block");
        label(g, hw - 24, 74, "gui.realtrainmodunofficial.painter.size");
    }

    private void label(GuiGraphics g, int right, int y, String key) {
        Component c = Component.translatable(key);
        g.drawString(this.font, c, right - this.font.width(c), y + 6, 0xC0C0C0, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
