package com.portofino.realtrainmodunofficial.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * 看板の文字に使う OS フォントを選ぶ画面。
 * <p>
 * 本家 GuiSignboard は Swing の JFrame + JComboBox を出していたが、Minecraft は
 * {@code java.awt.headless=true} で起動されることがあり (特に macOS)、その状態では
 * ウィンドウを開けない。フォントの一覧取得自体は headless でもできるので、
 * ゲーム内のリストとして作り直してある。
 */
public class FontSelectScreen extends Screen {
    private static final int ROW_H = 14;
    private static final int LIST_TOP = 50;

    /**
     * OS のフォント一覧は取得が重いので一度だけ読む。
     */
    private static List<String> allFonts;

    private final Screen parent;
    private final String current;
    private final Consumer<String> onSelected;

    private final List<String> filtered = new ArrayList<>();
    private EditBox search;
    private int scroll;

    public FontSelectScreen(Screen parent, String current, Consumer<String> onSelected) {
        super(Component.translatable("screen.realtrainmodunofficial.signboard.select_font"));
        this.parent = parent;
        this.current = current == null ? "" : current;
        this.onSelected = onSelected;
    }

    private static List<String> fonts() {
        if (allFonts == null) {
            List<String> names = new ArrayList<>();
            try {
                //headless でも取得できる (ウィンドウを作らないため)
                java.util.Collections.addAll(names,
                        GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
            } catch (Throwable t) {
                names.add("Dialog");
            }
            allFonts = names;
        }
        return allFonts;
    }

    @Override
    protected void init() {
        search = addRenderableWidget(new EditBox(this.font, this.width / 2 - 100, 24, 200, 20, Component.empty()));
        search.setHint(Component.translatable("screen.realtrainmodunofficial.signboard.search_font"));
        search.setResponder(s -> {
            scroll = 0;
            refilter();
        });
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(this.width / 2 - 50, this.height - 26, 100, 20).build());
        refilter();
    }

    private void refilter() {
        filtered.clear();
        String q = search == null ? "" : search.getValue().toLowerCase(Locale.ROOT).trim();
        for (String name : fonts()) {
            if (q.isEmpty() || name.toLowerCase(Locale.ROOT).contains(q)) {
                filtered.add(name);
            }
        }
    }

    private int visibleRows() {
        return Math.max(1, (this.height - LIST_TOP - 36) / ROW_H);
    }

    private int maxScroll() {
        return Math.max(0, filtered.size() - visibleRows());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.clamp(scroll - (int) scrollY, 0, maxScroll());
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0 || mouseY < LIST_TOP) {
            return false;
        }
        int row = (int) ((mouseY - LIST_TOP) / ROW_H) + scroll;
        if (row < 0 || row >= filtered.size()) {
            return false;
        }
        onSelected.accept(filtered.get(row));
        Minecraft.getInstance().setScreen(parent);
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);

        int rows = visibleRows();
        int left = this.width / 2 - 140;
        int right = this.width / 2 + 140;
        for (int i = 0; i < rows; i++) {
            int index = i + scroll;
            if (index >= filtered.size()) {
                break;
            }
            String name = filtered.get(index);
            int y = LIST_TOP + i * ROW_H;
            boolean hovered = mouseX >= left && mouseX <= right && mouseY >= y && mouseY < y + ROW_H;
            if (hovered) {
                graphics.fill(left, y, right, y + ROW_H, 0x44FFFFFF);
            }
            //選択中のフォントは強調する
            int color = name.equals(current) ? 0xFFFF55 : 0xFFFFFF;
            graphics.drawString(this.font, name, left + 4, y + 3, color, false);
        }

        if (maxScroll() > 0) {
            graphics.drawString(this.font,
                    (scroll + 1) + " / " + (maxScroll() + 1), right - 40, this.height - 24, 0xAAAAAA, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
