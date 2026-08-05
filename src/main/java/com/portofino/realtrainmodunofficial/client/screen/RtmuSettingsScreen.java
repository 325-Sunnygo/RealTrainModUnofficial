package com.portofino.realtrainmodunofficial.client.screen;

import com.portofino.realtrainmodunofficial.RtmuSettings;
import com.portofino.realtrainmodunofficial.network.RtmuSettingsPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * RTMU 設定画面 (ポーズメニューの「RTMU設定」ボタンから開く)。
 * カーブ自動カント: ON / OFF
 * レール自動高さ: 1〜5 (レンチ高さ単位)
 */
public class RtmuSettingsScreen extends Screen {

    private final Screen parent;

    public RtmuSettingsScreen(Screen parent) {
        super(Component.literal("RTMU設定"));
        this.parent = parent;
    }

    /** 見出し 1 つ (y は init で確定し、render がそのまま描く)。 */
    private record Header(int y, String title, String note, int color) {
    }

    private final List<Header> headers = new java.util.ArrayList<>();
    /** スクロールする中身 (見出し以外)。描画も当たり判定もこちらで面倒を見る。 */
    private final List<net.minecraft.client.gui.components.AbstractWidget> rows =
        new java.util.ArrayList<>();
    /** 「完了」だけは常に画面下へ固定する (小さい窓でも押せるように)。 */
    private Button doneButton;

    /** 項目の幅と縦の刻み。ここだけ直せば全体の間隔が揃う。 */
    private static final int ROW_W = 220;
    private static final int ROW_H = 20;
    private static final int ROW_GAP = 24;
    private static final int SECTION_GAP = 30;
    private static final int HEADER_GAP = 14;
    /** 見出し・項目の上下に空ける余白。 */
    private static final int PAD = 6;

    private int cursorY;
    private int rowLeft;
    /** 中身の総高さ (スクロール量の上限を出すのに使う)。 */
    private int contentHeight;
    /** 今どれだけ下へスクロールしているか (0 = 先頭)。 */
    private int scroll;

    /** 中身を映す窓の上端・下端。題名と「完了」の分を除いた範囲。 */
    private int viewportTop() {
        return 30;
    }

    private int viewportBottom() {
        return this.height - 32;
    }

    private int maxScroll() {
        return Math.max(0, this.contentHeight - (viewportBottom() - viewportTop()));
    }

    /** スクロール量を丸めて、各項目の実座標へ反映する。 */
    private void applyScroll() {
        this.scroll = Math.max(0, Math.min(maxScroll(), this.scroll));
        for (int i = 0; i < this.rows.size(); i++) {
            this.rows.get(i).setY(viewportTop() + this.rowContentY.get(i) - this.scroll);
        }
    }

    /** 各項目の「中身座標での y」。実座標はスクロール量を引いて出す。 */
    private final List<Integer> rowContentY = new java.util.ArrayList<>();

    /** 見出しを 1 つ置いて、次の行まで送る。 */
    private void section(String title, String note, int color) {
        if (!this.headers.isEmpty()) {
            this.cursorY += SECTION_GAP - ROW_GAP;
        }
        this.headers.add(new Header(this.cursorY, title, note, color));
        this.cursorY += HEADER_GAP;
    }

    /** 項目を 1 つ置いて、次の行まで送る。 */
    private <T extends net.minecraft.client.gui.components.AbstractWidget> void row(
            java.util.function.BiFunction<Integer, Integer, T> factory) {
        T widget = factory.apply(this.rowLeft, this.cursorY);
        // ★addRenderableWidget ではなく addWidget。描画は窓の中へ切り取って自前で行う
        // (そのまま任せると、スクロールで外へ出た項目が題名や「完了」の上に描かれる)。
        addWidget(widget);
        this.rows.add(widget);
        this.rowContentY.add(this.cursorY);
        this.cursorY += ROW_GAP;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        this.rowLeft = cx - ROW_W / 2;
        this.headers.clear();
        this.rows.clear();
        this.rowContentY.clear();
        // 中身は 0 起点で積む。画面上のどこに出るかは applyScroll が決める。
        this.cursorY = PAD;

        section("敷設", "", 0xFFD0A040);
        row((x, y) -> CycleButton.onOffBuilder(RtmuSettings.autoCant)
            .create(x, y, ROW_W, ROW_H, Component.literal("カーブ自動カント"),
                (btn, value) -> {
                    RtmuSettings.autoCant = value;
                    RtmuSettings.save();
                    sync();
                }));
        // レール自動高さ: 0=OFF、1〜16=高さ 0〜15 (1/16ブロック)。任意の高さ (9,10 等) を指定できる。
        row((x, y) -> new AutoHeightSlider(x, y, ROW_W, ROW_H));

        section("表示", "モデル選択の一覧から隠すだけ (読み込みは止めない)", 0xFFC0A0FF);
        row((x, y) -> CycleButton.onOffBuilder(RtmuSettings.hideBundledModels)
            .create(x, y, ROW_W, ROW_H, Component.literal("同梱モデルを隠す"),
                (btn, value) -> {
                    RtmuSettings.hideBundledModels = value;
                    RtmuSettings.save();
                }));

        section("軽量化", "遠い車両/レールを間引いて FPS を稼ぐ (既定=無制限)", 0xFF60C0FF);
        // レール描画距離: 64〜512 ブロック。遠くのレールが消えるときはこれを上げる。
        row((x, y) -> new RailDistanceSlider(x, y, ROW_W, ROW_H));
        // 車両描画距離: 0=無制限、32〜256。遠方車両を丸ごと省略して毎フレームのスクリプト実行を削る。
        row((x, y) -> new VehicleDistanceSlider(x, y, ROW_W, ROW_H));

        // ★乗客の人数はここには置かない。駅ブロックを右クリックした GUI で駅ごとに決める。
        // ワールド共通の上限をここに残すと、駅ごとの設定に上から蓋をしてしまう。

        this.contentHeight = this.cursorY - ROW_GAP + ROW_H + PAD;

        // 「完了」は中身に含めない。窓が小さくても必ず押せる位置に置く
        this.doneButton = Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds(cx - 100, this.height - 26, 200, 20).build();
        addWidget(this.doneButton);

        applyScroll();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (maxScroll() > 0 && mouseY >= viewportTop() && mouseY <= viewportBottom()) {
            this.scroll -= (int) Math.round(deltaY * ROW_GAP);
            applyScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 窓の外にはみ出した項目を、見えないまま掴んでしまわないようにする
        if (mouseY < viewportTop() || mouseY > viewportBottom()) {
            return this.doneButton != null && this.doneButton.mouseClicked(mouseX, mouseY, button);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static int clampThrottle(int v) {
        return Math.max(0, Math.min(2, v));
    }

    private static Component throttleLabel(int v) {
        return Component.literal(switch (v) {
            case 1 -> "省エネ";
            case 2 -> "積極";
            default -> "標準";
        });
    }

    /** レール自動高さスライダー (0=OFF、1〜16 → 高さ 0〜15)。 */
    private class AutoHeightSlider extends net.minecraft.client.gui.components.AbstractSliderButton {
        AutoHeightSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(),
                RtmuSettings.clampLevel(RtmuSettings.autoHeightLevel) / 16.0D);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int level = (int) Math.round(this.value * 16.0D);
            setMessage(Component.literal("レール自動高さ: "
                + (level <= 0 ? "OFF" : (level - 1) + "/16")));
        }

        @Override
        protected void applyValue() {
            RtmuSettings.autoHeightLevel = (int) Math.round(this.value * 16.0D);
            RtmuSettings.save();
            sync();
        }
    }

    /** レール描画距離スライダー (64〜512 ブロック、16刻み)。 */
    private class RailDistanceSlider extends net.minecraft.client.gui.components.AbstractSliderButton {
        private static final int MIN = 64;
        private static final int MAX = 512;

        RailDistanceSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(),
                (RtmuSettings.clampRailRenderDistance(RtmuSettings.railRenderDistance) - MIN) / (double) (MAX - MIN));
            updateMessage();
        }

        private int currentDistance() {
            int raw = (int) Math.round(MIN + this.value * (MAX - MIN));
            return Math.max(MIN, Math.min(MAX, (raw / 16) * 16));  //16刻み
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("レール描画距離: " + currentDistance() + "m"));
        }

        @Override
        protected void applyValue() {
            RtmuSettings.railRenderDistance = currentDistance();
            RtmuSettings.save();
            // 描画距離はクライアントのみ (サーバー同期不要)
        }
    }

    /** 車両描画距離スライダー (0=無制限、32〜256、16刻み)。 */
    private static class VehicleDistanceSlider extends net.minecraft.client.gui.components.AbstractSliderButton {
        // 0(無制限) を左端、32〜256 を連続に並べる。内部 value 0.0 = 無制限。
        private static final int MIN = 32;
        private static final int MAX = 256;

        VehicleDistanceSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), initialValue());
            updateMessage();
        }

        private static double initialValue() {
            int d = RtmuSettings.clampVehicleRenderDistance(RtmuSettings.vehicleRenderDistance);
            if (d <= 0) {
                return 0.0D;
            }
            // 無制限のぶん左端に幅を持たせる (value 0〜0.08 = 無制限帯)。
            return 0.08D + (1.0D - 0.08D) * (d - MIN) / (double) (MAX - MIN);
        }

        private int currentDistance() {
            if (this.value < 0.08D) {
                return 0;  //無制限
            }
            double t = (this.value - 0.08D) / (1.0D - 0.08D);
            int raw = (int) Math.round(MIN + t * (MAX - MIN));
            return Math.max(MIN, Math.min(MAX, (raw / 16) * 16));
        }

        @Override
        protected void updateMessage() {
            int d = currentDistance();
            setMessage(Component.literal("車両描画距離: " + (d <= 0 ? "無制限" : d + "m")));
        }

        @Override
        protected void applyValue() {
            RtmuSettings.vehicleRenderDistance = currentDistance();
            RtmuSettings.save();
        }
    }

    private void sync() {
        if (minecraft != null && minecraft.getConnection() != null) {
            PacketDistributor.sendToServer(
                new RtmuSettingsPayload(RtmuSettings.autoCant, RtmuSettings.autoHeightLevel,
                    RtmuSettings.maxPassengers));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);

        int top = viewportTop();
        int bottom = viewportBottom();
        // 窓からはみ出した分は切り落とす
        graphics.enableScissor(0, top, this.width, bottom);
        for (Header h : this.headers) {
            int hy = top + h.y() - this.scroll;
            graphics.drawString(this.font, Component.literal("§l" + h.title()),
                this.rowLeft, hy, h.color(), false);
            if (!h.note().isEmpty()) {
                graphics.drawString(this.font, Component.literal("§7" + h.note()),
                    this.rowLeft + this.font.width("§l" + h.title()) + 8, hy, 0xFF808080, false);
            }
        }
        for (var widget : this.rows) {
            widget.render(graphics, mouseX, mouseY, partialTick);
        }
        graphics.disableScissor();

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        if (this.doneButton != null) {
            this.doneButton.render(graphics, mouseX, mouseY, partialTick);
        }
        renderScrollbar(graphics);
    }

    /** 右端の細いつまみ。中身が収まっているときは出さない。 */
    private void renderScrollbar(GuiGraphics graphics) {
        int max = maxScroll();
        if (max <= 0) {
            return;
        }
        int top = viewportTop();
        int viewH = viewportBottom() - top;
        int x = this.width - 6;
        graphics.fill(x, top, x + 3, top + viewH, 0x40FFFFFF);
        int knobH = Math.max(16, viewH * viewH / this.contentHeight);
        int knobY = top + (viewH - knobH) * this.scroll / max;
        graphics.fill(x, knobY, x + 3, knobY + knobH, 0xC0FFFFFF);
    }

    @Override
    public void onClose() {
        // 閉じるときにも念のため同期
        sync();
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    /** 1.21 のメニューぼかしを無効化 (本家 1.7.10 の GUI にぼかしは無い)。 */
    @Override
    protected void renderBlurredBackground(float partialTick) {
    }
}
