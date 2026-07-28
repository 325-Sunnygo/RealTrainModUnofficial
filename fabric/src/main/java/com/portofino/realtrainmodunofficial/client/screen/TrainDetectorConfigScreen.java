package com.portofino.realtrainmodunofficial.client.screen;

import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.network.ConfigureDetectorPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 列車検知器の設定画面。検知器を素手で右クリックすると開く。
 * <p>
 * 列車を検知したときに、指定した座標のレッドストーンブロックを「置く」か「消す」かを決める。
 * 列車が居なくなったら逆の動作をするので、実質「その座標のレッドストーン出力の ON/OFF」になる。
 * <p>
 * 安全のため、置くのは対象が<b>空気のときだけ</b>、消すのは対象が<b>レッドストーンブロックの
 * ときだけ</b>で、それ以外のブロックには触れない (建築物を壊さない)。
 */
public class TrainDetectorConfigScreen extends Screen {
    private final InstalledObjectBlockEntity blockEntity;

    private EditBox fieldX;
    private EditBox fieldY;
    private EditBox fieldZ;
    private Button buttonMode;
    private Button buttonEnabled;

    /**
     * true = 検知したら置く / false = 検知したら消す
     */
    private boolean placeOnDetect;
    /**
     * 出力先を設定するか。false なら検知するだけで何もしない。
     */
    private boolean hasTarget;

    public TrainDetectorConfigScreen(InstalledObjectBlockEntity blockEntity) {
        super(Component.translatable("screen.realtrainmodunofficial.train_detector"));
        this.blockEntity = blockEntity;
        this.placeOnDetect = blockEntity.isDetectorPlaceOnDetect();
        this.hasTarget = blockEntity.getDetectorTarget() != null;
    }

    @Override
    protected void init() {
        BlockPos target = blockEntity.getDetectorTarget();
        //未設定なら検知器の1つ下を初期値にしておく (そこから編集してもらう)
        BlockPos initial = target != null ? target : blockEntity.getBlockPos().below();

        int cx = this.width / 2;
        int top = this.height / 2 - 50;

        fieldX = addCoordField(cx - 120, top, initial.getX());
        fieldY = addCoordField(cx - 40, top, initial.getY());
        fieldZ = addCoordField(cx + 40, top, initial.getZ());

        buttonEnabled = addRenderableWidget(Button.builder(enabledLabel(), b -> {
            hasTarget = !hasTarget;
            buttonEnabled.setMessage(enabledLabel());
        }).bounds(cx - 120, top + 34, 110, 20).build());

        buttonMode = addRenderableWidget(Button.builder(modeLabel(), b -> {
            placeOnDetect = !placeOnDetect;
            buttonMode.setMessage(modeLabel());
        }).bounds(cx + 10, top + 34, 110, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> {
            send();
            onClose();
        }).bounds(cx - 120, top + 70, 110, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(cx + 10, top + 70, 110, 20).build());
    }

    private Component enabledLabel() {
        return Component.translatable(hasTarget
                ? "screen.realtrainmodunofficial.train_detector.output_on"
                : "screen.realtrainmodunofficial.train_detector.output_off");
    }

    private Component modeLabel() {
        return Component.translatable(placeOnDetect
                ? "screen.realtrainmodunofficial.train_detector.mode_place"
                : "screen.realtrainmodunofficial.train_detector.mode_remove");
    }

    private EditBox addCoordField(int x, int y, int value) {
        EditBox box = new EditBox(this.font, x, y, 70, 20, Component.empty());
        box.setValue(String.valueOf(value));
        box.setMaxLength(9);
        box.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d{0,8}"));
        return addRenderableWidget(box);
    }

    private int parse(EditBox box, int fallback) {
        try {
            return Integer.parseInt(box.getValue().trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private void send() {
        BlockPos self = blockEntity.getBlockPos();
        BlockPos target = new BlockPos(
                parse(fieldX, self.getX()),
                parse(fieldY, self.below().getY()),
                parse(fieldZ, self.getZ()));
        PacketDistributor.sendToServer(new ConfigureDetectorPayload(self, hasTarget, target, placeOnDetect));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int cx = this.width / 2;
        int top = this.height / 2 - 50;
        graphics.drawCenteredString(this.font, this.title, cx, top - 44, 0xFFFFFF);

        //現在の在線状態 (サーバから同期されている)
        boolean onRail = blockEntity.isDetectorTrainOnRail();
        graphics.drawCenteredString(this.font, Component.translatable(onRail
                        ? "screen.realtrainmodunofficial.train_detector.detected"
                        : "screen.realtrainmodunofficial.train_detector.clear"),
                cx, top - 30, onRail ? 0xFF5555 : 0x55FF55);

        graphics.drawCenteredString(this.font, "X", cx - 85, top - 12, 0xAAAAAA);
        graphics.drawCenteredString(this.font, "Y", cx - 5, top - 12, 0xAAAAAA);
        graphics.drawCenteredString(this.font, "Z", cx + 75, top - 12, 0xAAAAAA);

        graphics.drawCenteredString(this.font,
                Component.translatable("screen.realtrainmodunofficial.train_detector.hint"),
                cx, top + 98, 0x999999);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
