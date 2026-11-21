package com.portofino.realtrainmodunofficial.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

/**
 * 前提パックが足りないことを知らせる画面。
 * 読み込み自体は通してあるので遊べるが、スクリプトが解決できなかった車両やレールは
 * 本来の見た目にならない (前面ガラスやドアが崩れる)。
 */
@OnlyIn(Dist.CLIENT)
public class PackWarningScreen extends Screen {
    private final Screen parent;
    private final List<String> lines;
    private EntryList list;

    public PackWarningScreen(Screen parent, List<String> lines) {
        super(Component.literal("前提パックが足りません"));
        this.parent = parent;
        this.lines = lines;
    }

    @Override
    protected void init() {
        this.list = new EntryList(this.minecraft, this.width, this.height - 108, 62, 12);
        for (String line : this.lines) {
            this.list.addEntryLine(line);
        }
        this.addRenderableWidget(this.list);
        this.addRenderableWidget(Button.builder(Component.literal("OK"), b -> onClose())
            .bounds(this.width / 2 - 50, this.height - 32, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        super.render(g, mouseX, mouseY, pt);
        g.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFF55);
        g.drawCenteredString(this.font,
            Component.literal("以下の定義が参照しているスクリプトが、導入済みのどのパックにも見つかりませんでした。"),
            this.width / 2, 36, 0xFFFFFF);
        g.drawCenteredString(this.font,
            Component.literal("該当の車両・レールは本来の見た目になりません (遊ぶことはできます)。"),
            this.width / 2, 48, 0xAAAAAA);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @OnlyIn(Dist.CLIENT)
    private class EntryList extends ObjectSelectionList<EntryList.Line> {
        EntryList(net.minecraft.client.Minecraft mc, int width, int height, int top, int itemHeight) {
            super(mc, width, height, top, itemHeight);
        }

        void addEntryLine(String text) {
            this.addEntry(new Line(text));
        }

        @Override
        public int getRowWidth() {
            return Math.min(this.width - 20, 460);
        }

        @OnlyIn(Dist.CLIENT)
        class Line extends ObjectSelectionList.Entry<Line> {
            private final String text;

            Line(String text) {
                this.text = text;
            }

            @Override
            public void render(GuiGraphics g, int index, int top, int left, int w, int h,
                               int mouseX, int mouseY, boolean hovered, float pt) {
                g.drawString(PackWarningScreen.this.font, this.text, left, top + 1, 0xE0E0E0, false);
            }

            @Override
            public Component getNarration() {
                return Component.literal(this.text);
            }
        }
    }

    /** 1.21 のメニューぼかしを無効化 (本家 1.7.10 の GUI にぼかしは無い)。 */
    @Override
    protected void renderBlurredBackground(float partialTick) {
    }
}
