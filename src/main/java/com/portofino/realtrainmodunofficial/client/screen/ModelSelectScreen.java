package com.portofino.realtrainmodunofficial.client.screen;

import com.portofino.realtrainmodunofficial.client.PackButtonTextureCache;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * 本家 RTM (KaizPatchX {@code GuiSelectModel}) と同じ挙動のモデル選択画面。
 * <ul>
 *   <li>左に<b>中央寄せスクロール</b>の一覧 (選択中が縦中央)。各項目 160×32 にモデルのボタン画像。</li>
 *   <li><b>スクロールバーは画面右端</b>。ホイール/上下キー/Home/End/PgUp/PgDn でも移動。</li>
 *   <li>右上に<b>コンパクトな2列の入力群</b> (本家配置): Custom Name / Search / DataMap / Color。</li>
 *   <li>決定時に modelId と併せて dataMap/name/color を {@link SelectionResult} に載せる。</li>
 * </ul>
 * 本家の argField と DataMap は本家では別物だが、RTMU でスクリプトに渡るパラメータは
 * DataMap 一本なので「引数 = DataMap」に統合している。
 */
@OnlyIn(Dist.CLIENT)
public class ModelSelectScreen extends Screen {
    /** 選択結果。旧呼び出し互換のため (modelId, dataMapValue) のコンストラクタも残す。 */
    public record SelectionResult(String modelId, String dataMapValue, String customName, int color) {
        public SelectionResult(String modelId, String dataMapValue) {
            this(modelId, dataMapValue, "", 0xFFFFFF);
        }
    }

    public record ModelInfo(String id, String displayName, String packName, String buttonTexture, String category) {
        public ModelInfo(String id, String displayName, String packName, String buttonTexture) {
            this(id, displayName, packName, buttonTexture, "");
        }
    }

    // 一覧項目 (5:1)。本家は 160×32 だが「項目でかい」との指摘で少し小さめに。
    private static final int BTN_W = 140;
    private static final int BTN_H = 28;
    private static final int LIST_LEFT = 8;
    private static final int SCROLLBAR_W = 6;
    private static final int FIELD_H = 18;

    private final List<ModelInfo> allModels;
    private final Consumer<SelectionResult> onSelected;
    private final String initialSelectedId;
    private final String initialDataMapValue;
    private final String initialName;
    private final int initialColor;

    /** 検索で絞り込んだ後の表示対象 (フラット)。 */
    private List<ModelInfo> filtered = new ArrayList<>();
    /** 縦中央に来る項目の index。 */
    private int currentScroll = 0;
    private String selectedId = null;

    private EditBox searchField;
    private EditBox nameField;
    private EditBox colorField;
    private EditBox dataMapField;
    private String lastSearch = "";

    /** 右上入力群の左端 x (init で確定)。ホイール判定などに使う。 */
    private int clusterLeftX;

    private boolean draggingScrollbar = false;
    /** 選択画面を開いている間は F1 相当で HUD を隠す。閉じたら戻す退避値。 */
    private boolean prevHideGui;

    public ModelSelectScreen(Component title, List<ModelInfo> models, Consumer<SelectionResult> onSelected) {
        this(title, models, onSelected, null, "", "", 0xFFFFFF);
    }

    public ModelSelectScreen(Component title, List<ModelInfo> models, Consumer<SelectionResult> onSelected,
                             String initialSelectedId, String initialDataMapValue) {
        this(title, models, onSelected, initialSelectedId, initialDataMapValue, "", 0xFFFFFF);
    }

    public ModelSelectScreen(Component title, List<ModelInfo> models, Consumer<SelectionResult> onSelected,
                             String initialSelectedId, String initialDataMapValue,
                             String initialName, int initialColor) {
        super(title);
        // 本家どおり名前順ソート (カテゴリを名前より優先)
        this.allModels = models.stream()
            .sorted(Comparator
                .comparing((ModelInfo i) -> safe(i.category()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(i -> safe(i.displayName()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(i -> safe(i.id()), String.CASE_INSENSITIVE_ORDER))
            .toList();
        this.onSelected = onSelected;
        this.initialSelectedId = initialSelectedId;
        this.initialDataMapValue = initialDataMapValue == null ? "" : initialDataMapValue;
        this.initialName = initialName == null ? "" : initialName;
        this.initialColor = initialColor;
    }

    private static String safe(String v) { return v == null ? "" : v; }

    // ---- レイアウト ----
    private int scrollbarX() { return width - SCROLLBAR_W - 3; }   // 画面右端
    private int listRight() { return LIST_LEFT + BTN_W; }
    private int centerY() { return height / 2 - BTN_H / 2; }

    @Override
    protected void init() {
        // 選択画面を開いている間は HUD (ホットバー/手/照準など) を全て隠す (F1相当)。
        this.prevHideGui = minecraft != null && minecraft.options.hideGui;
        if (minecraft != null) minecraft.options.hideGui = true;
        // JourneyMap は hideGui を無視するので、ミニマップを一時的にオフにする。
        com.portofino.realtrainmodunofficial.client.JourneyMapCompat.setSuppressed(true);

        // 本家配置: 右上にコンパクトな2列。スクロールバー(右端)の左に収める。
        // 一覧と重ならないよう clusterLeftX >= listArea を先に確定してから幅を出す。
        int fieldsRight = scrollbarX() - 6;
        int listArea = listRight() + 12;
        this.clusterLeftX = Math.max(listArea, fieldsRight - 220);
        int clusterW = Math.max(60, fieldsRight - clusterLeftX);
        int gap = 6;
        int colW = (clusterW - gap) / 2;
        int c1 = clusterLeftX;
        int c2 = clusterLeftX + colW + gap;
        int fy = 8;   // もっと右上へ

        nameField = addBox(c1, fy, colW, "Custom Name", initialName);
        searchField = addBox(c2, fy, colW, "Search", "");
        fy += FIELD_H + 8;
        dataMapField = addBox(c1, fy, colW, "DataMap", initialDataMapValue);
        colorField = addBox(c2, fy, Math.max(48, colW - 22), "Color", String.format("0x%06X", initialColor & 0xFFFFFF));

        // 完了・キャンセルは画面下・中央 (真ん中)。一覧側は下マージンを空けて重なりを避ける。
        int bw = Math.min(100, Math.max(70, width / 5));
        int bh = 20, bgap = 8;
        int totalW = bw * 2 + bgap;
        int bx = (width - totalW) / 2;
        int by = height - bh - 2;   // もう少し下げる
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onDone())
            .bounds(bx, by, bw, bh).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
            .bounds(bx + bw + bgap, by, bw, bh).build());

        // 再オープン時は現在のモデルを選択済みに (これは正しい既定選択)。
        // 以降スクロールしても選択は動かず、クリックした時だけ変わる。
        this.selectedId = initialSelectedId;
        rebuildFiltered();
    }

    private EditBox addBox(int x, int y, int w, String hint, String value) {
        EditBox box = new EditBox(font, x, y, w, FIELD_H, Component.literal(hint));
        box.setMaxLength(96);
        box.setValue(value == null ? "" : value);
        box.setHint(Component.literal(hint));
        addRenderableWidget(box);
        return box;
    }

    /** 検索でフィルタし、選択中を中央スクロール位置へ合わせる。 */
    private void rebuildFiltered() {
        String kw = searchField == null ? "" : searchField.getValue().trim().toLowerCase(Locale.ROOT);
        List<ModelInfo> list = new ArrayList<>();
        for (ModelInfo m : allModels) {
            if (kw.isEmpty()
                || safe(m.displayName()).toLowerCase(Locale.ROOT).contains(kw)
                || safe(m.id()).toLowerCase(Locale.ROOT).contains(kw)
                || safe(m.category()).toLowerCase(Locale.ROOT).contains(kw)) {
                list.add(m);
            }
        }
        this.filtered = list;
        // 表示位置だけ選択中に合わせる。選択(selectedId)はここでは変えない
        // (スクロール/検索で勝手に選択が動かないように。選択はクリック時のみ)。
        int idx = indexOf(selectedId);
        currentScroll = idx >= 0 ? idx : 0;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        // 本家式: 1.21 の背景すりガラス(ぼかし)も全面暗転もしない。世界はクッキリのまま。
        // 全面塗りはボタンにも被って暗く見えるので<b>何も描かない</b> (背景は素の世界)。
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        renderBackground(g, mouseX, mouseY, pt);

        // タイトルは影付きで、明るい空を背景にしても読めるように。
        g.drawString(font, getTitle(), LIST_LEFT, 8, 0xFFFFFF, true);

        // 一覧 (中央寄せスクロール)。可視範囲だけ描く。下部は完了/キャンセルの帯を空ける。
        int cy = centerY();
        int listBottom = height - 30;
        for (int i = 0; i < filtered.size(); i++) {
            int y = cy + BTN_H * (i - currentScroll);
            if (y <= -BTN_H || y >= listBottom) continue;
            boolean isSel = filtered.get(i).id().equals(selectedId);
            drawItem(g, filtered.get(i), LIST_LEFT, y, isSel, mouseX, mouseY);
        }

        drawScrollbar(g);

        // 色プレビュー (color 欄の右)
        if (colorField != null) {
            int c = parseColor(colorField.getValue(), initialColor);
            int cx = colorField.getX() + colorField.getWidth() + 4;
            int cyy = colorField.getY();
            g.fill(cx, cyy, cx + FIELD_H, cyy + FIELD_H, 0xFF000000 | (c & 0xFFFFFF));
            g.renderOutline(cx, cyy, FIELD_H, FIELD_H, 0xFFFFFFFF);
        }

        super.render(g, mouseX, mouseY, pt);

        if (filtered.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("screen.realtrainmodunofficial.no_models"),
                LIST_LEFT + BTN_W / 2, cy + 12, 0xAAAAAA);
        }
    }

    private void drawItem(GuiGraphics g, ModelInfo m, int left, int top, boolean selected, int mouseX, int mouseY) {
        boolean hovered = mouseX >= left && mouseX < left + BTN_W && mouseY >= top && mouseY < top + BTN_H;
        g.fill(left, top, left + BTN_W, top + BTN_H, 0xFF1A1A2E);
        if (selected) {
            g.fill(left, top, left + BTN_W, top + BTN_H, 0x66FFFFFF);
        } else if (hovered) {
            g.fill(left, top, left + BTN_W, top + BTN_H, 0x22FFFFFF);
        }
        // ぼやけ対策: buttonTexture を「GUIサイズ × guiScale」へニアレスト焼き直しし、1:1 で描く。
        // こうするとスケーリングが起きず (テクセル=画面ピクセル)、GUI 描画経路の線形補間に関わらず鮮明。
        int scale = Math.max(1, (int) Math.round(
            net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale()));
        net.minecraft.resources.ResourceLocation crisp =
            PackButtonTextureCache.getCrisp(m.packName(), m.buttonTexture(), BTN_W * scale, BTN_H * scale);
        if (crisp != null) {
            int tw = BTN_W * scale;
            int th = BTN_H * scale;
            g.blit(crisp, left, top, BTN_W, BTN_H, 0.0F, 0.0F, tw, th, tw, th);
        } else {
            String name = safe(m.displayName()).isBlank() ? m.id() : m.displayName();
            g.drawString(font, name, left + 4, top + (BTN_H - 8) / 2, 0xFFFFFFFF, false);
        }
        g.renderOutline(left, top, BTN_W, BTN_H, selected ? 0xFFFFFFFF : 0x55FFFFFF);
    }

    private void drawScrollbar(GuiGraphics g) {
        int x = scrollbarX();
        int top = 8, bottom = height - 8;
        g.fill(x, top, x + SCROLLBAR_W, bottom, 0xFF303030);
        if (filtered.size() <= 1) return;
        int trackH = bottom - top;
        int thumbH = Math.max(16, trackH / Math.max(1, filtered.size()));
        int thumbY = top + (int) ((long) currentScroll * (trackH - thumbH) / (filtered.size() - 1));
        g.fill(x, thumbY, x + SCROLLBAR_W, thumbY + thumbH, 0xFF6A9AE0);
        g.renderOutline(x, thumbY, SCROLLBAR_W, thumbH, 0xFFFFFFFF);
    }

    // ---- 入力 ----
    private int itemIndexAt(double mx, double my) {
        if (mx < LIST_LEFT || mx >= LIST_LEFT + BTN_W) return -1;
        int cy = centerY();
        int listBottom = height - 30;
        for (int i = 0; i < filtered.size(); i++) {
            int y = cy + BTN_H * (i - currentScroll);
            if (y >= listBottom) break;
            if (my >= y && my < y + BTN_H) return i;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int x = scrollbarX();
            if (mx >= x && mx < x + SCROLLBAR_W && my >= 8 && my < height - 8) {
                draggingScrollbar = true;
                scrollTo(scrollbarValue(my));
                return true;
            }
            int idx = itemIndexAt(mx, my);
            if (idx >= 0) {
                selectedId = filtered.get(idx).id();   // クリックした時だけ選択する
                setFocused(null);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingScrollbar && button == 0) {
            scrollTo(scrollbarValue(my));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && draggingScrollbar) { draggingScrollbar = false; return true; }
        return super.mouseReleased(mx, my, button);
    }

    private int scrollbarValue(double my) {
        int top = 8, bottom = height - 8;
        double frac = (my - top) / (double) Math.max(1, bottom - top);
        return (int) Math.round(frac * Math.max(0, filtered.size() - 1));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        // 一覧側 (入力群より左) はホイールでスクロール。
        if (!filtered.isEmpty() && mx < clusterLeftX) {
            scrollTo(currentScroll - (int) Math.signum(sy));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (getFocused() instanceof EditBox) {
            boolean r = super.keyPressed(key, scan, mods);
            refreshSearch();
            return r;
        }
        switch (key) {
            case GLFW.GLFW_KEY_HOME -> { scrollTo(0); return true; }
            case GLFW.GLFW_KEY_END -> { scrollTo(filtered.size() - 1); return true; }
            case GLFW.GLFW_KEY_PAGE_UP -> { scrollTo(currentScroll - Math.max(1, (height - 16) / BTN_H)); return true; }
            case GLFW.GLFW_KEY_PAGE_DOWN -> { scrollTo(currentScroll + Math.max(1, (height - 16) / BTN_H)); return true; }
            case GLFW.GLFW_KEY_UP -> { scrollTo(currentScroll - 1); return true; }
            case GLFW.GLFW_KEY_DOWN -> { scrollTo(currentScroll + 1); return true; }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> { onDone(); return true; }
            default -> { }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        boolean r = super.charTyped(c, mods);
        refreshSearch();
        return r;
    }

    private void refreshSearch() {
        if (searchField != null && !searchField.getValue().equals(lastSearch)) {
            lastSearch = searchField.getValue();
            rebuildFiltered();
        }
    }

    /** 表示位置(currentScroll)だけ動かす。選択(selectedId)は変えない = スクロールで選択しない。 */
    private void scrollTo(int idx) {
        if (filtered.isEmpty()) { currentScroll = 0; return; }
        currentScroll = Mth.clamp(idx, 0, filtered.size() - 1);
    }

    private int indexOf(String id) {
        if (id == null) return -1;
        for (int i = 0; i < filtered.size(); i++) {
            if (filtered.get(i).id().equals(id)) return i;
        }
        return -1;
    }

    private void onDone() {
        if (selectedId == null || filtered.isEmpty()) { onClose(); return; }
        int color = parseColor(colorField.getValue(), initialColor);
        onSelected.accept(new SelectionResult(
            selectedId,
            dataMapField == null ? "" : dataMapField.getValue(),
            nameField == null ? "" : nameField.getValue(),
            color));
        onClose();
    }

    private static int parseColor(String s, int fallback) {
        if (s == null) return fallback;
        s = s.trim();
        if (s.isEmpty()) return fallback;
        try {
            if (s.startsWith("0x") || s.startsWith("0X")) return Integer.parseInt(s.substring(2), 16) & 0xFFFFFF;
            if (s.startsWith("#")) return Integer.parseInt(s.substring(1), 16) & 0xFFFFFF;
            return Integer.parseInt(s, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }


    @Override
    public void removed() {
        // 画面を閉じたら HUD 表示 / ミニマップを元に戻す。
        if (minecraft != null) minecraft.options.hideGui = prevHideGui;
        com.portofino.realtrainmodunofficial.client.JourneyMapCompat.setSuppressed(false);
        super.removed();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
