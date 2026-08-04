package com.portofino.rtmuautodrive;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 列車運転スポナーの画面。名前欄 + 編成アイテムの枠 + 詳細設定。
 *
 * <p>★縦の位置は {@link DispatcherMenu} の定数と必ず揃えること。
 * 以前は枠の座標 (35) の上に説明文 (40) を書いていたので<b>文字と枠が重なっていた</b>し、
 * 詳細設定ボタン (58) が「インベントリ」の見出し (72) に被っていた。
 */
public class DispatcherScreen extends AbstractContainerScreen<DispatcherMenu> {

    /** 各段の Y。上から順に並べてあるので、増やすときはここだけ見れば良い。 */
    private static final int TITLE_Y = 6;
    private static final int NAME_Y = 18;
    private static final int NAME_H = 16;
    private static final int SLOT_LABEL_Y = 40;
    private static final int ROLLSIGN_Y = 74;
    private static final int DWELL_Y = 96;
    private static final int DETAIL_Y = 118;
    private static final int DETAIL_H = 20;

    private EditBox nameBox;
    private Button rollsignButton;
    private Button dwellButton;
    private int rollsign;
    private int dwell = 10;

    public DispatcherScreen(DispatcherMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = DispatcherMenu.WIDTH;
        this.imageHeight = DispatcherMenu.HEIGHT;
        this.titleLabelY = TITLE_Y;
        //持ち物の見出しは 3 段の枠のすぐ上
        this.inventoryLabelY = DispatcherMenu.INV_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        this.nameBox = new EditBox(this.font, this.leftPos + 8, this.topPos + NAME_Y,
                this.imageWidth - 16, NAME_H, Component.translatable("screen.rtmuautodrive.name"));
        this.nameBox.setMaxLength(32);
        this.nameBox.setHint(Component.translatable("screen.rtmuautodrive.name_hint"));
        this.nameBox.setResponder(text ->
                PacketDistributor.sendToServer(new AutoDriveNetwork.SetName(this.menu.getPos(), text)));
        if (this.minecraft != null && this.minecraft.level != null
                && this.minecraft.level.getBlockEntity(this.menu.getPos())
                    instanceof TrainDispatcherBlockEntity be) {
            this.nameBox.setValue(be.getDispatcherName());
            this.rollsign = be.getRollsign();
            this.dwell = be.getDwellSeconds();
        }
        this.addRenderableWidget(this.nameBox);
        this.setInitialFocus(this.nameBox);

        //方向幕の番号 (0,1,2,...)
        this.addRenderableWidget(Button.builder(Component.literal("-"),
                b -> this.changeRollsign(-1)).bounds(this.leftPos + 96, this.topPos + ROLLSIGN_Y, 20, 20).build());
        this.rollsignButton = Button.builder(Component.literal(String.valueOf(this.rollsign)),
                b -> this.changeRollsign(1)).bounds(this.leftPos + 118, this.topPos + ROLLSIGN_Y, 30, 20).build();
        this.addRenderableWidget(this.rollsignButton);
        this.addRenderableWidget(Button.builder(Component.literal("+"),
                b -> this.changeRollsign(1)).bounds(this.leftPos + 150, this.topPos + ROLLSIGN_Y, 20, 20).build());

        //停車時間 (秒)
        this.addRenderableWidget(Button.builder(Component.literal("-"),
                b -> this.changeDwell(-1)).bounds(this.leftPos + 96, this.topPos + DWELL_Y, 20, 20).build());
        this.dwellButton = Button.builder(dwellLabel(this.dwell),
                b -> this.changeDwell(1)).bounds(this.leftPos + 118, this.topPos + DWELL_Y, 30, 20).build();
        this.addRenderableWidget(this.dwellButton);
        this.addRenderableWidget(Button.builder(Component.literal("+"),
                b -> this.changeDwell(1)).bounds(this.leftPos + 150, this.topPos + DWELL_Y, 20, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.rtmuautodrive.details"),
                b -> PacketDistributor.sendToServer(
                        new AutoDriveNetwork.RequestRoute(this.menu.getPos())))
                .bounds(this.leftPos + 8, this.topPos + DETAIL_Y, this.imageWidth - 16, DETAIL_H).build());
    }

    private void changeRollsign(int delta) {
        this.rollsign = Math.max(0, Math.min(127, this.rollsign + delta));
        this.rollsignButton.setMessage(Component.literal(String.valueOf(this.rollsign)));
        this.sendConfig();
    }

    private void changeDwell(int delta) {
        this.dwell = Math.max(0, Math.min(600, this.dwell + delta));
        this.dwellButton.setMessage(dwellLabel(this.dwell));
        this.sendConfig();
    }

    private static Component dwellLabel(int seconds) {
        return Component.literal(seconds + "s");
    }

    private void sendConfig() {
        PacketDistributor.sendToServer(
                new AutoDriveNetwork.SetConfig(this.menu.getPos(), this.rollsign, this.dwell));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        //背景と縁 (専用テクスチャは持たない)
        graphics.fill(this.leftPos - 1, this.topPos - 1,
                this.leftPos + this.imageWidth + 1, this.topPos + this.imageHeight + 1, 0xFF2B2B33);
        graphics.fill(this.leftPos, this.topPos,
                this.leftPos + this.imageWidth, this.topPos + this.imageHeight, 0xFF15151C);
        //★枠は menu が持っている全スロットぶん描く。描かないと持ち物欄が真っ暗で
        //  アイテムだけが宙に浮いて見える。
        for (Slot slot : this.menu.slots) {
            int x = this.leftPos + slot.x;
            int y = this.topPos + slot.y;
            graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF8B8B8B);
            graphics.fill(x, y, x + 16, y + 16, 0xFF373737);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, 8, this.titleLabelY, 0xFFFFFF, false);
        //枠の左隣に置く (枠は SLOT_X=80 なので重ならない)
        graphics.drawString(this.font, Component.translatable("screen.rtmuautodrive.put_formation"),
                8, SLOT_LABEL_Y, 0xA0A0A0, false);
        graphics.drawString(this.font, Component.translatable("screen.rtmuautodrive.rollsign"),
                8, ROLLSIGN_Y + 6, 0xA0A0A0, false);
        graphics.drawString(this.font, Component.translatable("screen.rtmuautodrive.dwell"),
                8, DWELL_Y + 6, 0xA0A0A0, false);
        graphics.drawString(this.font, this.playerInventoryTitle, 8, this.inventoryLabelY, 0xA0A0A0, false);
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        //名前欄に文字を打っているときに E で閉じないようにする
        if (this.nameBox != null && this.nameBox.isFocused() && key != 256) {
            return this.nameBox.keyPressed(key, scancode, modifiers)
                    || this.nameBox.canConsumeInput()
                    || super.keyPressed(key, scancode, modifiers);
        }
        return super.keyPressed(key, scancode, modifiers);
    }
}
