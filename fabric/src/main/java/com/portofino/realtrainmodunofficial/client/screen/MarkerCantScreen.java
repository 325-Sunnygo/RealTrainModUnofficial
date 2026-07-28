package com.portofino.realtrainmodunofficial.client.screen;

import com.portofino.realtrainmodunofficial.network.MarkerAnchorPayload;
import jp.ngt.rtm.rail.TileEntityMarker;
import jp.ngt.rtm.rail.util.RailPosition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * レールのカント設定 (レンチでマーカーをシフト右クリック)。
 * 本家 1122 のカント線ドラッグの代わりに数値入力式 (度)。
 * cantEdge = このマーカー端のカント、cantCenter = 中央のカント。
 */
public class MarkerCantScreen extends Screen {
    private final TileEntityMarker marker;
    private EditBox fieldCantEdge;
    private EditBox fieldCantCenter;

    public MarkerCantScreen(TileEntityMarker marker) {
        super(Component.literal("カント設定"));
        this.marker = marker;
    }

    @Override
    protected void init() {
        RailPosition rp = marker.getMarkerRP();
        int cx = this.width / 2;
        int cy = this.height / 2;
        this.fieldCantEdge = addNumberField(cx + 10, cy - 30, rp != null ? rp.cantEdge : 0.0F);
        this.fieldCantCenter = addNumberField(cx + 10, cy - 5, rp != null ? rp.cantCenter : 0.0F);
        addRenderableWidget(Button.builder(Component.literal("適用"), b -> {
            apply();
            onClose();
        }).bounds(cx - 105, cy + 25, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("キャンセル"), b -> onClose())
                .bounds(cx + 5, cy + 25, 100, 20).build());
    }

    private EditBox addNumberField(int x, int y, float value) {
        EditBox box = new EditBox(this.font, x, y, 80, 20, Component.empty());
        box.setValue(value == (long) value ? String.valueOf((long) value) : String.valueOf(value));
        box.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d{0,3}(\\.\\d{0,4})?"));
        return addRenderableWidget(box);
    }

    private void apply() {
        RailPosition rp = marker.getMarkerRP();
        if (rp == null) {
            return;
        }
        try {
            rp.cantEdge = Float.parseFloat(fieldCantEdge.getValue().trim());
        } catch (Exception ignored) {
        }
        try {
            rp.cantCenter = Float.parseFloat(fieldCantCenter.getValue().trim());
        } catch (Exception ignored) {
        }
        //ローカルのプレビュー再構築 + サーバーへ RP 全体を送信 (アンカー編集と同じ経路)
        marker.onChangeRailShape();
        PacketDistributor.sendToServer(new MarkerAnchorPayload(marker.getBlockPos(), rp.writeToNBT()));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int cx = this.width / 2;
        int cy = this.height / 2;
        graphics.drawCenteredString(this.font, "カント設定 (度)", cx, cy - 55, 0xFFFFFF);
        graphics.drawString(this.font, "カント (端)", cx - 105, cy - 24, 0xFFFFFF);
        graphics.drawString(this.font, "カント (中央)", cx - 105, cy + 1, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
