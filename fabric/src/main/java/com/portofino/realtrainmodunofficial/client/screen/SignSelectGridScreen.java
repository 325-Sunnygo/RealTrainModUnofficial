package com.portofino.realtrainmodunofficial.client.screen;

import com.portofino.realtrainmodunofficial.client.PackButtonTextureCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * 本家 RTM (KaizPatchX GuiSelectTexture) 式の、標識/看板などの選択画面。
 * テクスチャ(ボタン画像)をグリッドに敷き詰めて並べる。
 */
@OnlyIn(Dist.CLIENT)
public class SignSelectGridScreen extends Screen {
    public record Item(String id, String displayName, String packName, String buttonTexture) {}

    private static final int TILE_W = 140;
    private static final int TILE_H = 40;      // 実寸フル画像をアスペクト比保持で収める枠
    private static final int PAD_X = 10;
    private static final int PAD_Y = 8;
    private static final int TOP = 22;

    private final List<Item> items;
    private final Consumer<String> onSelected;
    private final String initialId;

    private int columns = 1;
    private int cellW = TILE_W + PAD_X;
    private int cellH = TILE_H + PAD_Y;
    private int gridLeft = 0;
    private int viewRows = 1;
    private int scrollRow = 0;      // 一番上に見えている行
    private String selectedId;
    private boolean draggingScrollbar = false;
    /** 選択画面を開いている間は F1 相当で HUD を隠す。閉じたら戻す退避値。 */
    private boolean prevHideGui;

    public SignSelectGridScreen(Component title, List<Item> items, Consumer<String> onSelected, String initialId) {
        super(title);
        this.items = items.stream()
            .sorted(Comparator
                .comparing((Item i) -> safe(i.displayName()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(i -> safe(i.id()), String.CASE_INSENSITIVE_ORDER))
            .toList();
        this.onSelected = onSelected;
        this.initialId = initialId;
        this.selectedId = initialId;
    }

    private static String safe(String v) { return v == null ? "" : v; }

    private int totalRows() { return (items.size() + columns - 1) / Math.max(1, columns); }
    private int maxScrollRow() { return Math.max(0, totalRows() - viewRows); }
    private int scrollbarX() { return width - 9; }

    @Override
    protected void init() {
        // 選択画面を開いている間は HUD (ホットバー/手/照準など) を全て隠す (F1相当)。
        this.prevHideGui = minecraft != null && minecraft.options.hideGui;
        if (minecraft != null) minecraft.options.hideGui = true;
        // JourneyMap は hideGui を無視するので、ミニマップを一時的にオフにする。
        com.portofino.realtrainmodunofficial.client.JourneyMapCompat.setSuppressed(true);

        this.cellW = TILE_W + PAD_X;
        this.cellH = TILE_H + PAD_Y;
        this.columns = Math.max(1, (width - 12 - 12) / cellW);
        this.gridLeft = (width - columns * cellW) / 2 + PAD_X / 2;
        int bottom = height - 28;                 // 下のキャンセル帯を空ける
        this.viewRows = Math.max(1, (bottom - TOP) / cellH);
        this.scrollRow = Mth.clamp(scrollRow, 0, maxScrollRow());
        // 選択中(初期)が見えるところまでスクロール。
        int selIdx = indexOf(selectedId);
        if (selIdx >= 0) {
            int row = selIdx / columns;
            if (row < scrollRow || row >= scrollRow + viewRows) {
                scrollRow = Mth.clamp(row, 0, maxScrollRow());
            }
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
            .bounds((width - 100) / 2, height - 24, 100, 20).build());
    }

    private int indexOf(String id) {
        if (id == null) return -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id().equals(id)) return i;
        }
        return -1;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        // モデル選択画面と同様、ぼかしも全面暗転もしない (暗転はタイルにも被って暗く見える指摘があったため)。
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        // モデル選択画面と同じく、全画面「半透明の黒」を先頭で 1 回だけ敷く。
        // renderBackground に置くと super.render の再呼び出しでタイルの上にも重なって暗くなるため。
        g.fill(0, 0, width, height, 0xB0000000);
        g.drawString(font, getTitle(), 12, 8, 0xFFFFFF, true);

        int scale = Math.max(1, (int) Math.round(Minecraft.getInstance().getWindow().getGuiScale()));
        for (int row = scrollRow; row < scrollRow + viewRows; row++) {
            for (int col = 0; col < columns; col++) {
                int idx = row * columns + col;
                if (idx >= items.size()) break;
                int cx = gridLeft + col * cellW;
                int cy = TOP + (row - scrollRow) * cellH;
                drawTile(g, items.get(idx), cx, cy, scale, mouseX, mouseY);
            }
        }

        drawScrollbar(g);
        super.render(g, mouseX, mouseY, pt);

        if (items.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("screen.realtrainmodunofficial.no_models"),
                width / 2, height / 2, 0xAAAAAA);
        }
    }

    private void drawTile(GuiGraphics g, Item it, int left, int top, int scale, int mouseX, int mouseY) {
        boolean hovered = mouseX >= left && mouseX < left + TILE_W && mouseY >= top && mouseY < top + TILE_H;
        boolean selected = it.id().equals(selectedId);
        g.fill(left, top, left + TILE_W, top + TILE_H, 0xFF1A1A2E);
        if (selected) {
            g.fill(left, top, left + TILE_W, top + TILE_H, 0x66FFFFFF);
        } else if (hovered) {
            g.fill(left, top, left + TILE_W, top + TILE_H, 0x33FFFFFF);
        }
        // 本家 GuiSelectTexture 式: 画像全体をアスペクト比保持でタイルに収めて中央に描く (見切れ防止)。
        PackButtonTextureCache.ButtonTextureInfo info =
            PackButtonTextureCache.get(it.packName(), it.buttonTexture());
        if (info != null && info.width() > 0 && info.height() > 0) {
            float f = Math.min((float) TILE_W / info.width(), (float) TILE_H / info.height());
            int dispW = Math.max(1, Math.round(info.width() * f));
            int dispH = Math.max(1, Math.round(info.height() * f));
            int dx = left + (TILE_W - dispW) / 2;
            int dy = top + (TILE_H - dispH) / 2;
            ResourceLocation crisp = PackButtonTextureCache.getCrispFull(
                it.packName(), it.buttonTexture(), dispW * scale, dispH * scale);
            if (crisp != null) {
                int tw = dispW * scale;
                int th = dispH * scale;
                g.blit(crisp, dx, dy, dispW, dispH, 0.0F, 0.0F, tw, th, tw, th);
            }
        }
        g.renderOutline(left, top, TILE_W, TILE_H, selected ? 0xFFFFFFFF : 0x55FFFFFF);
    }

    private void drawScrollbar(GuiGraphics g) {
        int x = scrollbarX();
        int top = 8, bottom = height - 8;
        g.fill(x, top, x + 6, bottom, 0xFF303030);
        int max = maxScrollRow();
        if (max <= 0) return;
        int trackH = bottom - top;
        int thumbH = Math.max(16, trackH * viewRows / Math.max(1, totalRows()));
        int thumbY = top + (int) ((long) scrollRow * (trackH - thumbH) / max);
        g.fill(x, thumbY, x + 6, thumbY + thumbH, 0xFF6A9AE0);
        g.renderOutline(x, thumbY, 6, thumbH, 0xFFFFFFFF);
    }

    private int tileIndexAt(double mx, double my) {
        for (int row = scrollRow; row < scrollRow + viewRows; row++) {
            for (int col = 0; col < columns; col++) {
                int idx = row * columns + col;
                if (idx >= items.size()) break;
                int cx = gridLeft + col * cellW;
                int cy = TOP + (row - scrollRow) * cellH;
                if (mx >= cx && mx < cx + TILE_W && my >= cy && my < cy + TILE_H) {
                    return idx;
                }
            }
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int x = scrollbarX();
            if (mx >= x && mx < x + 6 && my >= 8 && my < height - 8) {
                draggingScrollbar = true;
                scrollToFraction(my);
                return true;
            }
            int idx = tileIndexAt(mx, my);
            if (idx >= 0) {
                // 本家どおり: クリックで確定して閉じる。
                onSelected.accept(items.get(idx).id());
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingScrollbar && button == 0) {
            scrollToFraction(my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && draggingScrollbar) { draggingScrollbar = false; return true; }
        return super.mouseReleased(mx, my, button);
    }

    private void scrollToFraction(double my) {
        int top = 8, bottom = height - 8;
        double frac = (my - top) / (double) Math.max(1, bottom - top);
        scrollRow = Mth.clamp((int) Math.round(frac * maxScrollRow()), 0, maxScrollRow());
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scrollRow = Mth.clamp(scrollRow - (int) Math.signum(sy), 0, maxScrollRow());
        return true;
    }

    @Override
    public void removed() {
        if (minecraft != null) minecraft.options.hideGui = prevHideGui;
        com.portofino.realtrainmodunofficial.client.JourneyMapCompat.setSuppressed(false);
        super.removed();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
