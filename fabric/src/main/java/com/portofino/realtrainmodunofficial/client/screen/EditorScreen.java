package com.portofino.realtrainmodunofficial.client.screen;

import com.portofino.realtrainmodunofficial.client.ClientSelection;
import com.portofino.realtrainmodunofficial.network.EditorPointPayload;
import com.portofino.realtrainmodunofficial.network.EditorSlotPayload;
import com.portofino.realtrainmodunofficial.network.RunFilterPayload;
import jp.ngt.mcte.editor.filter.EditFilter;
import jp.ngt.mcte.editor.filter.FilterConfig;
import jp.ngt.mcte.editor.filter.Filters;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * エディタの画面 (neo mcte)。
 *
 * <p><b>本家 / MCTEU の写しではなく作り直したもの。</b>あちらは 1.12 時代の名残で
 * 座標欄・操作ボタン・変形ボタンが画面の左右へ散らばっており、どれが何に効くのか
 * 分かりづらい。ここでは<b>「選ぶ → 設定する → 実行する」</b>の順に並べ替えた。
 *
 * <pre>
 *  ┌───────────────────────────────────────────────┐
 *  │ neo mcte                      42x8x30 = 10080 │ ← いまの選択範囲
 *  ├──────────────┬────────────────────────────────┤
 *  │ [検索_______]│  Fill                          │
 *  │ ▸ Fill       │  選択範囲をブロックで埋める      │
 *  │   Replace    │                                │
 *  │   Delete     │  Block   [__________]  ▣       │ ← 設定はその場で
 *  │   …          │  OnlyAir [ON]                  │
 *  │              │            [ 実行 ]            │
 *  ├──────────────┴────────────────────────────────┤
 *  │ 始点 1 2 3   終点 4 5 6        [元に戻す]      │
 *  └───────────────────────────────────────────────┘
 * </pre>
 *
 * <p>フィルタは<b>一覧から選び、その場で設定して実行</b>する。画面を渡り歩かない。
 * 検索できるので数が増えても探せる。
 */
public class EditorScreen extends Screen {

    private static final int PANEL_W = 380;
    private static final int PANEL_H = 220;
    private static final int LIST_W = 120;
    private static final int ROW_H = 14;
    /** 設定欄のラベル列の幅。これを超える文字は省略記号で詰める。 */
    private static final int LABEL_W = 82;

    /** ブロック見本の見た目 (サーバの中身はクライアントに無いので置いた物を控える)。 */
    private static final ItemStack[] SLOT_VIEW = {ItemStack.EMPTY, ItemStack.EMPTY};

    private int left;
    private int top;

    private EditBox search;
    private final List<EditFilter> shown = new ArrayList<>();
    private int selected;
    private int scroll;

    private final Map<String, AbstractWidget> paramWidgets = new LinkedHashMap<>();
    private final EditBox[] fieldStart = new EditBox[3];
    private final EditBox[] fieldEnd = new EditBox[3];

    public EditorScreen() {
        super(Component.literal("neo mcte"));
    }

    @Override
    protected void init() {
        left = (this.width - PANEL_W) / 2;
        top = (this.height - PANEL_H) / 2;

        search = new EditBox(this.font, left + 6, top + 22, LIST_W - 12, 14, Component.empty());
        search.setHint(Component.translatable("gui.realtrainmodunofficial.editor.search"));
        search.setResponder(v -> {
            refreshList();
            scroll = 0;
            rebuildParams();
        });
        addRenderableWidget(search);

        refreshList();
        buildFooter();
        rebuildParams();
    }

    /** 検索語で一覧を絞る。 */
    private void refreshList() {
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        shown.clear();
        for (EditFilter f : Filters.REGISTRY) {
            if (q.isEmpty() || f.name().toLowerCase(Locale.ROOT).contains(q)) {
                shown.add(f);
            }
        }
        if (selected >= shown.size()) {
            selected = 0;
        }
    }

    private EditFilter current() {
        return shown.isEmpty() ? null : shown.get(Math.max(0, Math.min(selected, shown.size() - 1)));
    }

    /** 選択中フィルタの設定欄を組み直す。 */
    private void rebuildParams() {
        for (AbstractWidget w : paramWidgets.values()) {
            removeWidget(w);
        }
        paramWidgets.clear();

        EditFilter f = current();
        if (f == null) {
            return;
        }
        int x = left + LIST_W + 12;
        int y = top + 52;
        for (FilterConfig.Parameter p : f.config().parameters()) {
            AbstractWidget w;
            if (p.type == FilterConfig.Type.BOOLEAN) {
                boolean init = Boolean.parseBoolean(
                    com.portofino.realtrainmodunofficial.client.EditorPrefs.get(f.name(), p.name, p.toString()));
                w = Button.builder(Component.literal(init ? "ON" : "OFF"), btn ->
                        btn.setMessage(Component.literal(
                            "ON".equals(btn.getMessage().getString()) ? "OFF" : "ON")))
                    .bounds(x + LABEL_W, y, 40, 14).build();
            } else {
                EditBox box = new EditBox(this.font, x + LABEL_W, y, 110, 14, Component.empty());
                box.setMaxLength(128);
                box.setValue(com.portofino.realtrainmodunofficial.client.EditorPrefs.get(f.name(), p.name, p.toString()));
                if (p.type == FilterConfig.Type.STRING) {
                    box.setHint(Component.translatable("gui.realtrainmodunofficial.editor.hint_slot"));
                }
                w = box;
            }
            paramWidgets.put(p.name, addRenderableWidget(w));
            y += 18;
        }

        int by = top + PANEL_H - 56;
        addRenderableWidget(Button.builder(Component.translatable("gui.realtrainmodunofficial.editor.undo"),
            b -> PacketDistributor.sendToServer(new RunFilterPayload(RunFilterPayload.UNDO, "")))
            .bounds(x, by, 68, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.realtrainmodunofficial.editor.redo"),
            b -> PacketDistributor.sendToServer(new RunFilterPayload(RunFilterPayload.REDO, "")))
            .bounds(x + 70, by, 68, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.realtrainmodunofficial.editor.run"),
            b -> run()).bounds(x + 140, by, 88, 18).build());
    }

    private void buildFooter() {
        int y = top + PANEL_H - 26;
        BlockPos a = ClientSelection.pos1() != null ? ClientSelection.pos1() : BlockPos.ZERO;
        BlockPos b = ClientSelection.pos2() != null ? ClientSelection.pos2() : a;
        for (int i = 0; i < 3; i++) {
            fieldStart[i] = coord(left + 34 + i * 36, y, comp(a, i));
            fieldEnd[i] = coord(left + 174 + i * 36, y, comp(b, i));
        }

    }

    private EditBox coord(int x, int y, int value) {
        EditBox box = new EditBox(this.font, x, y, 32, 14, Component.empty());
        box.setValue(String.valueOf(value));
        box.setMaxLength(8);
        return addRenderableWidget(box);
    }

    private static int comp(BlockPos p, int i) {
        return i == 0 ? p.getX() : i == 1 ? p.getY() : p.getZ();
    }

    private void run() {
        EditFilter f = current();
        if (f == null) {
            return;
        }
        BlockPos a = new BlockPos(parse(fieldStart[0]), parse(fieldStart[1]), parse(fieldStart[2]));
        BlockPos b = new BlockPos(parse(fieldEnd[0]), parse(fieldEnd[1]), parse(fieldEnd[2]));
        ClientSelection.setStartKeepEnd(a);
        ClientSelection.setEnd(b);
        PacketDistributor.sendToServer(new EditorPointPayload(0, a));
        PacketDistributor.sendToServer(new EditorPointPayload(1, b));

        StringBuilder sb = new StringBuilder();
        for (FilterConfig.Parameter p : f.config().parameters()) {
            AbstractWidget w = paramWidgets.get(p.name);
            if (w == null) {
                continue;
            }
            String v = w instanceof EditBox box ? box.getValue()
                : String.valueOf("ON".equals(w.getMessage().getString()));
            sb.append(p.name).append('=').append(v).append('\n');
            //次に同じフィルタを開いたときにそのまま出す
            com.portofino.realtrainmodunofficial.client.EditorPrefs.put(f.name(), p.name, v);
        }
        com.portofino.realtrainmodunofficial.client.EditorPrefs.save();
        PacketDistributor.sendToServer(new RunFilterPayload(f.name(), sb.toString()));
        onClose();
    }

    private static int parse(EditBox box) {
        try {
            return Integer.parseInt(box.getValue().trim());
        } catch (Exception e) {
            return 0;
        }
    }

    // ---- 入力 ----

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int lx = left + 6;
        int ly = top + 40;
        int rows = (PANEL_H - 76) / ROW_H;
        if (mx >= lx - 2 && mx < lx + LIST_W - 12 && my >= ly && my < ly + rows * ROW_H) {
            int idx = scroll + (int) ((my - ly) / ROW_H);
            if (idx >= 0 && idx < shown.size()) {
                selected = idx;
                rebuildParams();
                return true;
            }
        }
        int sx = left + LIST_W + 12 + LABEL_W + 114;
        for (int i = 0; i < 2; i++) {
            int sy = top + 52 + i * 18;
            if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16) {
                PacketDistributor.sendToServer(new EditorSlotPayload(i, button == 1));
                SLOT_VIEW[i] = button == 1 ? ItemStack.EMPTY : heldBlock();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private ItemStack heldBlock() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return ItemStack.EMPTY;
        }
        ItemStack src = this.minecraft.player.getMainHandItem();
        if (!(src.getItem() instanceof BlockItem)) {
            src = this.minecraft.player.getOffhandItem();
        }
        if (src.getItem() instanceof BlockItem) {
            ItemStack copy = src.copy();
            copy.setCount(1);
            return copy;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        int rows = (PANEL_H - 76) / ROW_H;
        if (mx < left + LIST_W && shown.size() > rows) {
            scroll = Math.max(0, Math.min(shown.size() - rows, scroll - (int) Math.signum(dy)));
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    //★ワールドを見ながら使うのでぼかしも暗転もしない
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        drawPanel(g);
        super.render(g, mouseX, mouseY, partialTick);
        drawList(g);
        drawDetail(g);
        drawFooter(g);
    }

    private void drawPanel(GuiGraphics g) {
        g.fill(left, top, left + PANEL_W, top + PANEL_H, 0xE0101014);
        g.fill(left, top, left + PANEL_W, top + 18, 0xF01A1A20);
        g.fill(left + LIST_W, top + 18, left + LIST_W + 1, top + PANEL_H, 0x40FFFFFF);
        g.fill(left, top + PANEL_H - 34, left + PANEL_W, top + PANEL_H - 33, 0x40FFFFFF);

        g.drawString(this.font, Component.literal("neo mcte").withStyle(ChatFormatting.AQUA),
            left + 6, top + 5, 0xFFFFFF, false);

        //いまの選択範囲を見出しに常時出す (別画面へ行かなくても分かるように)
        var box = ClientSelection.box();
        String info;
        if (box == null) {
            info = Component.translatable("gui.realtrainmodunofficial.editor.no_selection").getString();
        } else {
            int sx = (int) box.getXsize(), sy = (int) box.getYsize(), sz = (int) box.getZsize();
            info = sx + "x" + sy + "x" + sz + " = " + ((long) sx * sy * sz);
        }
        g.drawString(this.font, info, left + PANEL_W - 6 - this.font.width(info), top + 5, 0xC0C0C0, false);
    }

    private void drawList(GuiGraphics g) {
        int lx = left + 6;
        int ly = top + 40;
        int rows = (PANEL_H - 76) / ROW_H;
        for (int i = 0; i < rows && scroll + i < shown.size(); i++) {
            int idx = scroll + i;
            int y = ly + i * ROW_H;
            boolean sel = idx == selected;
            if (sel) {
                g.fill(lx - 2, y - 1, lx + LIST_W - 12, y + ROW_H - 2, 0x6033AAFF);
            }
            g.drawString(this.font, shown.get(idx).name(), lx, y + 2, sel ? 0xFFFFFF : 0xB0B0B0, false);
        }
        if (shown.isEmpty()) {
            g.drawString(this.font, "—", lx, ly + 2, 0x808080, false);
        }
    }

    private void drawDetail(GuiGraphics g) {
        EditFilter f = current();
        if (f == null) {
            return;
        }
        int x = left + LIST_W + 12;
        g.drawString(this.font, Component.literal(f.name()), x, top + 24, 0xFFFFFF, false);
        g.drawString(this.font, Component.translatable("filter.desc.realtrainmodunofficial."
            + f.name().toLowerCase(Locale.ROOT)), x, top + 36, 0x909090, false);

        int y = top + 52;
        for (FilterConfig.Parameter p : f.config().parameters()) {
            //★ラベル列に収まらない文字は省略する。以前は入力欄へ重なって読めなかった。
            drawTruncated(g, Component.translatable("filter.param.realtrainmodunofficial."
                + p.name.toLowerCase(Locale.ROOT)).getString(), x, y + 3, LABEL_W - 4, 0xC0C0C0);
            y += 18;
        }

        //ブロック見本 (左クリックで手持ちを登録 / 右クリックで空に)
        int sx = x + LABEL_W + 114;
        for (int i = 0; i < 2; i++) {
            int sy = top + 52 + i * 18;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF303030);
            if (!SLOT_VIEW[i].isEmpty()) {
                g.renderItem(SLOT_VIEW[i], sx, sy);
            }
        }
    }

    /** 幅に収まらない文字を「…」で詰めて描く。 */
    private void drawTruncated(GuiGraphics g, String text, int x, int y, int maxWidth, int color) {
        String t = text;
        if (this.font.width(t) > maxWidth) {
            while (!t.isEmpty() && this.font.width(t + "…") > maxWidth) {
                t = t.substring(0, t.length() - 1);
            }
            t = t + "…";
        }
        g.drawString(this.font, t, x, y, color, false);
    }

    private void drawFooter(GuiGraphics g) {
        int y = top + PANEL_H - 23;
        g.drawString(this.font, Component.translatable("gui.realtrainmodunofficial.editor.start"),
            left + 6, y, 0xFF6060, false);
        g.drawString(this.font, Component.translatable("gui.realtrainmodunofficial.editor.end"),
            left + 146, y, 0x6060FF, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
