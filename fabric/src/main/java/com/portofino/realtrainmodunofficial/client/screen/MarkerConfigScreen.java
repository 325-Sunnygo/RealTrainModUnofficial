package com.portofino.realtrainmodunofficial.client.screen;

import com.portofino.realtrainmodunofficial.blockentity.MarkerBlockEntity;
import com.portofino.realtrainmodunofficial.network.ConfigureMarkerPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * マーカー設定GUI (RTM の GuiRailMarker 相当)。
 * カント(傾き)とアンカー(曲線制御)を設定する。設定後にレールアイテムで右クリックすると反映される。
 */
public class MarkerConfigScreen extends Screen {
    private final BlockPos pos;
    private EditBox cantCenterBox;
    private EditBox cantEdgeBox;
    private EditBox anchorYawBox;
    private EditBox anchorLenHBox;

    private float anchorYaw, anchorPitch, anchorLenH = -1.0F, anchorLenV, cantCenter, cantEdge, cantRandom;

    public MarkerConfigScreen(BlockPos pos) {
        super(Component.literal("マーカー設定 (カント/曲線)"));
        this.pos = pos.immutable();
    }

    @Override
    protected void init() {
        readState();
        int boxW = 90;
        int x = width / 2 - boxW / 2;
        int y = height / 2 - 60;

        cantCenterBox = labeledBox(x, y, boxW, String.valueOf(cantCenter));
        cantEdgeBox = labeledBox(x, y + 34, boxW, String.valueOf(cantEdge));
        anchorYawBox = labeledBox(x, y + 68, boxW, String.valueOf(anchorYaw));
        anchorLenHBox = labeledBox(x, y + 102, boxW, String.valueOf(anchorLenH));
        setInitialFocus(cantCenterBox);

        addRenderableWidget(Button.builder(Component.literal("保存"), b -> submit())
            .bounds(width / 2 - 80, y + 140, 75, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
            .bounds(width / 2 + 5, y + 140, 75, 20).build());
    }

    private EditBox labeledBox(int x, int y, int w, String value) {
        EditBox box = new EditBox(font, x, y + 12, w, 18, Component.empty());
        box.setMaxLength(12);
        box.setValue(value);
        addRenderableWidget(box);
        return box;
    }

    private void readState() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof MarkerBlockEntity m) {
            anchorYaw = m.getAnchorYaw();
            anchorPitch = m.getAnchorPitch();
            anchorLenH = m.getAnchorLengthHorizontal();
            anchorLenV = m.getAnchorLengthVertical();
            cantCenter = m.getCantCenter();
            cantEdge = m.getCantEdge();
            cantRandom = m.getCantRandom();
        }
    }

    private float parse(EditBox box, float def) {
        try {
            String s = box.getValue().trim();
            return s.isEmpty() ? def : Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private void submit() {
        cantCenter = parse(cantCenterBox, cantCenter);
        cantEdge = parse(cantEdgeBox, cantEdge);
        anchorYaw = parse(anchorYawBox, anchorYaw);
        anchorLenH = parse(anchorLenHBox, anchorLenH);
        PacketDistributor.sendToServer(new ConfigureMarkerPayload(pos, anchorYaw, anchorPitch,
            anchorLenH, anchorLenV, cantCenter, cantEdge, cantRandom));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        super.render(g, mx, my, pt);
        int x = width / 2 - 45;
        int y = height / 2 - 60;
        g.drawCenteredString(font, title, width / 2, y - 24, 0xFFFFFF);
        g.drawString(font, Component.literal("カント中心 (度)"), x, y, 0xFFFFFF);
        g.drawString(font, Component.literal("カント端 (度)"), x, y + 34, 0xFFFFFF);
        g.drawString(font, Component.literal("アンカー方位 (度)"), x, y + 68, 0xFFFFFF);
        g.drawString(font, Component.literal("アンカー長 (-1=直線)"), x, y + 102, 0xAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
