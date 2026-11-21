package com.portofino.realtrainmodunofficial.client.screen;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.pack.PackConsent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * パック README の初回同意画面 (タイトル画面上に表示)。
 * PackConsent が集めた「README あり・未決定」のパックを 1 つずつ表示し、README を表示して
 * 「同意する / 同意しない」を選ばせる。
 */
public final class PackConsentScreen extends Screen {

    private final Screen parent;
    private final List<PackConsent.Pending> queue;
    private int index;
    private boolean anyAgreed;

    private int viewTop;
    private int viewBottom;
    /** README を画面幅で折り返した行 (行単位で描画・スクロールする)。 */
    private List<FormattedCharSequence> lines = List.of();
    /** 先頭に表示する行番号。 */
    private int scrollLine;

    public PackConsentScreen(Screen parent) {
        super(Component.literal("パックの README (初回のみ)"));
        this.parent = parent;
        this.queue = PackConsent.getPending();
    }

    /** 未決パックがあれば同意画面を返す。無ければ null。 */
    public static PackConsentScreen createIfPending(Screen parent) {
        return PackConsent.hasPending() ? new PackConsentScreen(parent) : null;
    }

    @Override
    protected void init() {
        this.viewTop = 46;
        this.viewBottom = this.height - 44;
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        this.scrollLine = 0;
        PackConsent.Pending cur = current();

        // README を画面幅で折り返す (\n も尊重される)。描画は render で行単位に行う。
        this.lines = cur == null ? List.of()
                : this.font.split(Component.literal(cur.readme()), this.width - 52);

        addRenderableWidget(new StringWidget(0, 12, this.width, 12,
                Component.literal("新しいパックの README を確認してください"), this.font).alignCenter());
        String progress = (index + 1) + " / " + queue.size() + "   —   " + (cur == null ? "" : cur.fileName())
                + (isScrollable() ? "   (マウスホイールでスクロール)" : "");
        addRenderableWidget(new StringWidget(0, 28, this.width, 12,
                Component.literal(progress), this.font).alignCenter());

        int by = this.height - 32;
        addRenderableWidget(Button.builder(Component.literal("同意する"), b -> decideCurrent(true))
                .bounds(this.width / 2 - 160, by, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("同意しない"), b -> decideCurrent(false))
                .bounds(this.width / 2 + 10, by, 150, 20).build());
    }

    /** 枠内に一度に表示できる行数。 */
    private int visibleLineCount() {
        return Math.max(1, (viewBottom - viewTop) / (this.font.lineHeight + 1));
    }

    private boolean isScrollable() {
        return lines.size() > visibleLineCount();
    }

    private int maxScrollLine() {
        return Math.max(0, lines.size() - visibleLineCount());
    }

    private PackConsent.Pending current() {
        return index >= 0 && index < queue.size() ? queue.get(index) : null;
    }

    private void decideCurrent(boolean agreed) {
        PackConsent.Pending cur = current();
        if (cur != null) {
            PackConsent.decide(cur.fileName(), agreed);
            anyAgreed |= agreed;
        }
        index++;
        if (index >= queue.size()) {
            finish();
        } else {
            rebuild();
        }
    }

    private void finish() {
        // 同意したパックがあれば pack を reload して即反映する (タイトル画面=ワールド未ロードなので安全)。
        if (anyAgreed) {
            try {
                com.portofino.realtrainmodunofficial.vehicle.VehiclePackLoader.reload();
                com.portofino.realtrainmodunofficial.rail.RailPackLoader.reload();
                // ★車両/レールだけでなくサウンドも作り直す。
                // いなかったため、README 同意が必要な SL パック等では「車両は出るのに走行音だけ
                // 鳴らない」状態になっていた。
                com.portofino.realtrainmodunofficial.client.sound.ExternalSoundPackBridge.rebuild();
                this.minecraft.setScreen(this.parent);
                this.minecraft.reloadResourcePacks();
                return;
            } catch (Throwable t) {
                RealTrainModUnofficial.LOGGER.warn("[PackConsent] 同意後の再読み込みに失敗: {}", t.toString());
            }
        }
        this.minecraft.setScreen(this.parent);
    }

    /**
     * ブラー無効化。既定の Screen.renderBackground はブラー(ぼかし)ポストエフェクトを適用し、
     * その時点までに描いた文字までぼやけさせる (super.render が内部で自動的に呼ぶため、
     * render 内で先に描いた README が全てぼやけていた)。不透明の暗色塗りだけに置き換える。
     */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xFF0A0A0A);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // super.render = renderBackground(上のオーバーライド=ブラー無し) + ウィジェット(見出し/ボタン)。
        super.render(g, mouseX, mouseY, partialTick);

        // README はウィジェットの後に描く。枠内に完全に収まる行だけ描くのでボタンとは重ならない。
        g.fill(20, viewTop - 6, this.width - 20, viewBottom + 6, 0xFF161616);
        g.renderOutline(20, viewTop - 6, this.width - 40, viewBottom - viewTop + 12, 0xFF666666);
        int lineH = this.font.lineHeight + 1;
        int y = viewTop;
        for (int i = scrollLine; i < lines.size(); i++) {
            if (y + this.font.lineHeight > viewBottom) {
                break;
            }
            g.drawString(this.font, lines.get(i), 26, y, 0xF0F0F0, false);
            y += lineH;
        }
        // スクロール位置の表示 (長い README のとき)
        if (isScrollable()) {
            String pos = (scrollLine + 1) + "-" + Math.min(lines.size(), scrollLine + visibleLineCount())
                    + " / " + lines.size() + " 行";
            g.drawString(this.font, pos, this.width - 26 - this.font.width(pos), viewBottom + 8, 0x999999, false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isScrollable()) {
            return false;
        }
        this.scrollLine = Math.max(0, Math.min(maxScrollLine(), this.scrollLine - (int) Math.signum(scrollY) * 3));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 矢印キー/PageUp/PageDown でもスクロールできるようにする。
        if (isScrollable()) {
            switch (keyCode) {
                case 264 -> { scrollLine = Math.min(maxScrollLine(), scrollLine + 3); return true; }          //DOWN
                case 265 -> { scrollLine = Math.max(0, scrollLine - 3); return true; }                        //UP
                case 267 -> { scrollLine = Math.min(maxScrollLine(), scrollLine + visibleLineCount()); return true; } //PageDown
                case 266 -> { scrollLine = Math.max(0, scrollLine - visibleLineCount()); return true; }       //PageUp
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    /** 1.21 のメニューぼかしを無効化 (本家 1.7.10 の GUI にぼかしは無い)。 */
    @Override
    protected void renderBlurredBackground(float partialTick) {
    }
}
