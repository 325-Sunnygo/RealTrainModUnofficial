package com.portofino.realtrainmodunofficial.client.screen;

import com.portofino.realtrainmodunofficial.client.ClientItemHelper;
import com.portofino.realtrainmodunofficial.formation.TrainFormation;
import com.portofino.realtrainmodunofficial.formation.TrainFormationData;
import com.portofino.realtrainmodunofficial.network.SetFormationPayload;
import com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition;
import com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 編成アイテムの編集画面。
 *
 * <p>並んでいるのは<b>先頭から順の車両ボタン</b>と、末尾の<b>＋ボタン</b>だけ。
 * <ul>
 *   <li>＋ → モデル選択画面。選ぶとここへ戻り、末尾にその車両のボタンが増える</li>
 *   <li>車両ボタン → モデル選択画面。選ぶとその 1 両だけ差し替わる</li>
 *   <li>横の <b>−</b> ボタン → その 1 両を編成から外す (車両ボタンの右クリックでも同じ)</li>
 *   <li>保存 / キャンセル</li>
 * </ul>
 *
 * <p>編集中の編成は<b>画面が持っている</b> (アイテムには保存を押すまで書かない)。
 * モデル選択画面へ行って戻る間も保持したいので、静的な下書きに預けて往復する。
 */
@OnlyIn(Dist.CLIENT)
public class FormationEditScreen extends Screen {

    /** ボタンの大きさと行間。縦 1 列に並べる。 */
    private static final int BTN_W = 200;
    private static final int BTN_H = 20;
    /** 行の右端に置く「−」ボタンの幅と、車両ボタンとの間隔。 */
    private static final int MINUS_W = 20;
    private static final int MINUS_GAP = 4;
    /** 車両ボタンの幅 (「−」のぶんを引く)。 */
    private static final int CAR_W = BTN_W - MINUS_W - MINUS_GAP;
    private static final int ROW_STEP = 22;
    /** 1 行目の Y (タイトルと説明の下)。 */
    private static final int TOP = 46;

    /**
     * モデル選択画面へ行って戻る間の下書き。
     * 画面インスタンスは作り直されるので、ここに預けないと編集中の内容が消える。
     */
    private static TrainFormation draft;
    private static ItemStack draftStack = ItemStack.EMPTY;

    private final ItemStack stack;
    private TrainFormation formation;
    /** 一覧の先頭に出す行番号 (長い編成でホイールスクロールする)。 */
    private int scroll;
    /** 画面に入りきらなかった行数。0 ならスクロール不要。 */
    private int hiddenRows;

    public FormationEditScreen(ItemStack stack) {
        super(Component.translatable("screen.realtrainmodunofficial.formation.title"));
        this.stack = stack;
        // 同じアイテムで戻ってきたなら編集中の内容を引き継ぐ
        if (draft != null && ItemStack.isSameItemSameComponents(draftStack, stack)) {
            this.formation = draft;
        } else {
            TrainFormation saved = TrainFormationData.getFormation(stack);
            this.formation = saved == null ? new TrainFormation() : saved.copy();
        }
        draft = null;
        draftStack = ItemStack.EMPTY;
    }

    /**
     * モデル選択画面で決めた 1 両を反映して、編集画面へ戻る。
     *
     * @param index -1 なら末尾へ追加、それ以外はその番号を差し替え
     */
    public static void applySelection(ItemStack stack, int index, String modelId) {
        TrainFormation editing = draft;
        if (editing == null) {
            TrainFormation saved = TrainFormationData.getFormation(stack);
            editing = saved == null ? new TrainFormation() : saved.copy();
        }
        if (modelId != null && !modelId.isBlank()) {
            if (index < 0) {
                editing.addVehicle(modelId);
            } else {
                editing.setVehicle(index, modelId);
            }
        }
        // 下書きへ入れるだけ。編集画面へ戻すのは ModelSelectScreen の onClose
        // (withReturnScreen) の役目。ここで setScreen すると直後の onClose に潰される。
        draft = editing;
        draftStack = stack;
    }

    @Override
    protected void init() {
        int count = this.formation.getCarCount();
        boolean canAdd = !this.formation.isFull();
        int totalRows = count + (canAdd ? 1 : 0);

        // 下の「保存 / キャンセル」は常に画面内。その上を一覧に使う。
        int bottomY = this.height - 28;
        int viewRows = Math.max(1, (bottomY - 8 - TOP) / ROW_STEP);
        this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, totalRows - viewRows)));

        int x = this.width / 2 - BTN_W / 2;
        for (int row = this.scroll; row < Math.min(totalRows, this.scroll + viewRows); row++) {
            int y = TOP + (row - this.scroll) * ROW_STEP;
            if (row < count) {
                final int index = row;
                addRenderableWidget(new CarButton(x, y, carLabel(row), row));
                // 横の「−」でその 1 両を消す
                addRenderableWidget(Button.builder(Component.literal("-"), b -> removeCar(index))
                    .bounds(x + CAR_W + MINUS_GAP, y, MINUS_W, BTN_H).build());
            } else {
                // ＋は一覧の末尾。車両が増えたぶんだけ下へ下がる
                addRenderableWidget(Button.builder(Component.literal("+"), b -> pickFor(-1))
                    .bounds(x, y, CAR_W, BTN_H).build());
            }
        }
        this.hiddenRows = Math.max(0, totalRows - viewRows);

        addRenderableWidget(Button.builder(
                Component.translatable("screen.realtrainmodunofficial.formation.save"), b -> save())
            .bounds(this.width / 2 - 154, bottomY, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
            .bounds(this.width / 2 + 4, bottomY, 150, 20).build());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (this.hiddenRows > 0) {
            this.scroll = Math.max(0, this.scroll - (int) Math.signum(deltaY));
            rebuild();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    private Component carLabel(int index) {
        String id = this.formation.getVehicle(index);
        VehicleDefinition def = VehicleRegistry.getById(id);
        String name = def != null ? def.getDisplayName() : id;
        if (name == null || name.isBlank()) {
            name = "?";
        }
        return Component.literal((index + 1) + ": " + name);
    }

    /**
     * モデル選択画面を開く。
     * @param index 差し替える車両の番号。-1 なら末尾へ追加
     */
    private void pickFor(int index) {
        draft = this.formation;
        draftStack = this.stack;
        ClientItemHelper.openTrainSelectForFormation(this.stack, index);
    }

    private void removeCar(int index) {
        this.formation.removeVehicle(index);
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private void save() {
        // 手元の見た目を先に合わせ、サーバーへも送って持ち物へ書き込む
        TrainFormationData.setFormation(this.stack, this.formation);
        PacketDistributor.sendToServer(new SetFormationPayload(this.formation.getAllVehicles()));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
            Component.translatable("screen.realtrainmodunofficial.formation.hint"),
            this.width / 2, 28, 0xFFA0A0A0);
        if (this.formation.isEmpty()) {
            graphics.drawCenteredString(this.font,
                Component.translatable("screen.realtrainmodunofficial.formation.empty"),
                this.width / 2, TOP + ROW_STEP + 6, 0xFFAAAAAA);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 車両 1 両ぶんのボタン。左クリックで差し替え、右クリックで削除。 */
    private class CarButton extends Button {
        private final int index;

        CarButton(int x, int y, Component label, int index) {
            super(x, y, CAR_W, BTN_H, label, b -> { }, DEFAULT_NARRATION);
            this.index = index;
        }

        @Override
        public void onPress() {
            pickFor(this.index);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 1 && this.active && this.visible && this.isMouseOver(mouseX, mouseY)) {
                removeCar(this.index);   //右クリックで 1 両外す
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
    }
}
