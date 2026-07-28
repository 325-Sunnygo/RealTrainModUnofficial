package com.portofino.realtrainmodunofficial.client.screen;

import com.portofino.realtrainmodunofficial.network.SignalControllerPayload;
import jp.masa.signalcontrollermod.SignalType;
import jp.masa.signalcontrollermod.TileEntitySignalController;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * SignalControllerMod (作者: masa300, https://github.com/masa300/SignalControllerMod)
 * の GUISignalController 1.21.1 移植。ボタン配置は 1.7.10 原作と同じ:
 *   SignalType [切替ボタン]
 *   Option     [x]last [ ]repeat [ ]Reduced Speed
 *   ("above" は未使用かつ画面からはみ出すため非表示。値は保持したまま送り返す)
 *                x      y      z
 *   nextSignal0 [___] [___] [___] [+]
 *   displayPos0 [___] [___] [___] [+]
 * 座標は直接編集可能、[+] で行を追加。閉じた時にサーバーへ反映。
 */
public class SignalControllerScreen extends Screen {
    private final TileEntitySignalController controller;
    private SignalType signalType;
    private boolean above;
    private boolean last;
    private boolean repeat;
    private boolean reducedSpeed;
    private final List<int[]> nextRows = new ArrayList<>();
    private final List<int[]> displayRows = new ArrayList<>();

    private final List<EditBox[]> nextBoxes = new ArrayList<>();
    private final List<EditBox[]> displayBoxes = new ArrayList<>();
    private Checkbox lastBox;
    private Checkbox repeatBox;
    private Checkbox reducedBox;

    public SignalControllerScreen(TileEntitySignalController controller) {
        super(Component.literal("SignalController"));
        this.controller = controller;
        this.signalType = controller.getSignalType();
        this.above = controller.isAbove();
        this.last = controller.isLast();
        this.repeat = controller.isRepeat();
        this.reducedSpeed = controller.isReducedSpeed();
        for (BlockPos p : controller.getNextSignal()) {
            this.nextRows.add(new int[]{p.getX(), p.getY(), p.getZ()});
        }
        for (BlockPos p : controller.getDisplayPos()) {
            this.displayRows.add(new int[]{p.getX(), p.getY(), p.getZ()});
        }
        if (this.nextRows.isEmpty()) {
            this.nextRows.add(new int[]{0, 0, 0});
        }
        if (this.displayRows.isEmpty()) {
            this.displayRows.add(new int[]{0, 0, 0});
        }
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        //SignalType 切替 (原作: 中央の大ボタン)
        addRenderableWidget(Button.builder(typeLabel(), b -> {
            SignalType[] values = SignalType.values();
            this.signalType = values[(this.signalType.ordinal() + 1) % values.length];
            b.setMessage(typeLabel());
        }).bounds(cx - 40, cy - 82, 150, 20).build());

        //Option チェックボックス (原作: last / repeat / Reduced Speed 並び)
        this.lastBox = addRenderableWidget(Checkbox.builder(Component.literal("last"), this.font)
                .pos(cx - 40, cy - 55).selected(this.last)
                .onValueChange((box, value) -> this.last = value).build());
        this.repeatBox = addRenderableWidget(Checkbox.builder(Component.literal("repeat"), this.font)
                .pos(cx + 15, cy - 55).selected(this.repeat)
                .onValueChange((box, value) -> this.repeat = value).build());
        this.reducedBox = addRenderableWidget(Checkbox.builder(Component.literal("Reduced Speed"), this.font)
                .pos(cx + 85, cy - 55).selected(this.reducedSpeed)
                .onValueChange((box, value) -> this.reducedSpeed = value).build());
        //"above" は使われていないうえ、画面外にはみ出して見切れていたので出さない。
        //値そのものは読み込んだままサーバーへ送り返すので、既存の設定は壊れない。

        //座標行 (x/y/z の EditBox ×3 + 最終行の "+" 追加ボタン)
        this.nextBoxes.clear();
        this.displayBoxes.clear();
        int y = cy - 5;
        for (int i = 0; i < this.nextRows.size(); i++) {
            addPosRow(this.nextRows.get(i), this.nextBoxes, cx, y);
            if (i == this.nextRows.size() - 1) {
                addPlusButton(cx, y, true);
            }
            y += 25;
        }
        for (int i = 0; i < this.displayRows.size(); i++) {
            addPosRow(this.displayRows.get(i), this.displayBoxes, cx, y);
            if (i == this.displayRows.size() - 1) {
                addPlusButton(cx, y, false);
            }
            y += 25;
        }
    }

    private void addPosRow(int[] row, List<EditBox[]> boxes, int cx, int y) {
        EditBox[] triple = new EditBox[3];
        for (int c = 0; c < 3; c++) {
            EditBox box = new EditBox(this.font, cx - 40 + c * 55, y, 50, 18, Component.empty());
            box.setValue(String.valueOf(row[c]));
            box.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d{1,8}"));
            triple[c] = addRenderableWidget(box);
        }
        boxes.add(triple);
    }

    private void addPlusButton(int cx, int y, boolean nextList) {
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            applyEdits();
            if (nextList) {
                this.nextRows.add(new int[]{0, 0, 0});
            } else {
                this.displayRows.add(new int[]{0, 0, 0});
            }
            this.rebuildWidgets();
        }).bounds(cx + 130, y, 18, 18).build());
    }

    private Component typeLabel() {
        return Component.translatable("SignalControllerMod.gui.signalType." + this.signalType.toString());
    }

    /**
     * EditBox の現在値を rows に反映
     */
    private void applyEdits() {
        readBoxes(this.nextBoxes, this.nextRows);
        readBoxes(this.displayBoxes, this.displayRows);
    }

    private static void readBoxes(List<EditBox[]> boxes, List<int[]> rows) {
        for (int i = 0; i < boxes.size() && i < rows.size(); i++) {
            for (int c = 0; c < 3; c++) {
                try {
                    rows.get(i)[c] = Integer.parseInt(boxes.get(i)[c].getValue().trim());
                } catch (Exception e) {
                    rows.get(i)[c] = 0;
                }
            }
        }
    }

    /**
     * 閉じる時にサーバーへ一括反映 (原作もパケットで反映)
     */
    @Override
    public void onClose() {
        applyEdits();
        List<BlockPos> next = new ArrayList<>();
        for (int[] r : this.nextRows) {
            if (!(r[0] == 0 && r[1] == 0 && r[2] == 0)) {
                next.add(new BlockPos(r[0], r[1], r[2]));
            }
        }
        List<BlockPos> disp = new ArrayList<>();
        for (int[] r : this.displayRows) {
            if (!(r[0] == 0 && r[1] == 0 && r[2] == 0)) {
                disp.add(new BlockPos(r[0], r[1], r[2]));
            }
        }
        PacketDistributor.sendToServer(new SignalControllerPayload(
                controller.getBlockPos(), signalType.toString(), above, last, repeat, reducedSpeed, next, disp));
        super.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int cx = this.width / 2;
        int cy = this.height / 2;
        //原作の文字配置
        graphics.drawString(this.font, "SignalController", 20, 20, 0xFFFFFF);
        graphics.drawString(this.font, "SignalType", cx - 120, cy - 76, 0xFFFFFF);
        graphics.drawString(this.font, "Option", cx - 120, cy - 50, 0xFFFFFF);
        graphics.drawString(this.font, "x", cx - 17, cy - 20, 0xFFFFFF);
        graphics.drawString(this.font, "y", cx + 38, cy - 20, 0xFFFFFF);
        graphics.drawString(this.font, "z", cx + 93, cy - 20, 0xFFFFFF);
        int y = cy - 5;
        for (int i = 0; i < this.nextRows.size(); i++) {
            graphics.drawString(this.font, "nextSignal" + i, cx - 120, y + 5, 0xFFFFFF);
            y += 25;
        }
        for (int i = 0; i < this.displayRows.size(); i++) {
            graphics.drawString(this.font, "displayPos" + i, cx - 120, y + 5, 0xFFFFFF);
            y += 25;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
