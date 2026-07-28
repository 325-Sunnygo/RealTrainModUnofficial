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
 * マーカーの位置調整 (レンチのモード「マーカー位置調整」でマーカーを右クリック)。
 * <p>
 * 本家のマーカーはブロック単位でしか置けず、レールの端は必ず
 * 「ブロック中心 + 向きごとの固定オフセット ({@code RailPosition.REVISION})」に来る。
 * そのため線路の位置をブロック未満で詰めることができない。
 * <p>
 * ここで指定した値は {@link RailPosition#freePos} として保存され、
 * {@code posX/posY/posZ} が一次情報になる。曲線 ({@code RailMapBasic}) は元々 posXYZ だけを
 * 見ているので、これだけでレールの端が自由な位置に来る。
 * <p>
 * 単位は <b>1/16 ブロック</b>。マーカーの既定位置からのずれを入れる。
 */
public class MarkerOffsetScreen extends Screen {

    /** 動かせる範囲 (1/16 ブロック単位)。ブロック 1 個ぶんまで。 */
    private static final float LIMIT = 16.0F;

    private final TileEntityMarker marker;
    private EditBox fieldX;
    private EditBox fieldY;
    private EditBox fieldZ;

    public MarkerOffsetScreen(TileEntityMarker marker) {
        super(Component.translatable("screen.realtrainmodunofficial.marker_offset"));
        this.marker = marker;
    }

    /** マーカーの既定位置 (ブロック中心 + 向きごとの補正)。ここからのずれを編集する。 */
    private static double[] defaultPos(RailPosition rp) {
        int dir = rp.direction & 7;
        return new double[]{
            rp.blockX + 0.5D + RailPosition.REVISION[dir][0],
            rp.blockY + (rp.height + 1) * 0.0625D,
            rp.blockZ + 0.5D + RailPosition.REVISION[dir][1]
        };
    }

    @Override
    protected void init() {
        RailPosition rp = marker.getMarkerRP();
        if (rp == null) {
            onClose();
            return;
        }
        double[] base = defaultPos(rp);
        int cx = this.width / 2;
        int cy = this.height / 2;

        this.fieldX = addNumberField(cx + 20, cy - 45, (float) ((rp.posX - base[0]) * 16.0D));
        this.fieldY = addNumberField(cx + 20, cy - 20, (float) ((rp.posY - base[1]) * 16.0D));
        this.fieldZ = addNumberField(cx + 20, cy + 5, (float) ((rp.posZ - base[2]) * 16.0D));

        addRenderableWidget(Button.builder(
                Component.translatable("screen.realtrainmodunofficial.marker_offset.reset"),
                b -> {
                    fieldX.setValue("0");
                    fieldY.setValue("0");
                    fieldZ.setValue("0");
                }).bounds(cx - 105, cy + 35, 100, 20).build());
        addRenderableWidget(Button.builder(
                Component.translatable("gui.done"),
                b -> {
                    apply();
                    onClose();
                }).bounds(cx + 5, cy + 35, 100, 20).build());
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
        double[] base = defaultPos(rp);
        double dx = clamp(parse(fieldX)) * 0.0625D;
        double dy = clamp(parse(fieldY)) * 0.0625D;
        double dz = clamp(parse(fieldZ)) * 0.0625D;

        if (dx == 0.0D && dy == 0.0D && dz == 0.0D) {
            //既定位置に戻す。freePos を降ろせば init() が本家どおり導出する。
            rp.freePos = false;
            rp.init();
        } else {
            //posXYZ を一次情報にする (以後 init() は上書きしない)
            rp.freePos = true;
            rp.posX = base[0] + dx;
            rp.posY = base[1] + dy;
            rp.posZ = base[2] + dz;
        }

        //ローカルのプレビュー再構築 + サーバーへ RP 全体を送信 (カント/アンカー編集と同じ経路)
        marker.onChangeRailShape();
        PacketDistributor.sendToServer(new MarkerAnchorPayload(marker.getBlockPos(), rp.writeToNBT()));
    }

    private static float clamp(float v) {
        return Math.max(-LIMIT, Math.min(LIMIT, v));
    }

    private static float parse(EditBox box) {
        try {
            String s = box.getValue().trim();
            return s.isEmpty() || s.equals("-") ? 0.0F : Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return 0.0F;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int cx = this.width / 2;
        int cy = this.height / 2;
        graphics.drawCenteredString(this.font, this.title, cx, cy - 70, 0xFFFFFF);
        graphics.drawString(this.font, "X", cx - 105, cy - 39, 0xFFFFFF);
        graphics.drawString(this.font, "Y", cx - 105, cy - 14, 0xFFFFFF);
        graphics.drawString(this.font, "Z", cx - 105, cy + 11, 0xFFFFFF);
        graphics.drawString(this.font,
                Component.translatable("screen.realtrainmodunofficial.marker_offset.hint"),
                cx - 105, cy + 62, 0xA0A0A0, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
