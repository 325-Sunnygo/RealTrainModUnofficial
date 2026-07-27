package com.portofino.realtrainmodunofficial.client.screen;

import com.portofino.realtrainmodunofficial.network.RunFilterPayload;
import jp.ngt.mcte.editor.filter.EditFilter;
import jp.ngt.mcte.editor.filter.FilterConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * フィルタの設定画面 (neo mcte)。本家 MCTE {@code GuiFilterSetting} 相当。
 *
 * <p><b>入力欄はフィルタの宣言から自動で組まれる。</b>
 * フィルタを足すときに画面を書かなくてよいのが本家のこの設計の要点で、
 * neo mcte で機能を足すときにもそのまま効く。
 */
public class FilterSettingScreen extends Screen {

    private final Screen parent;
    private final EditFilter filter;
    private final Map<String, AbstractWidget> widgets = new LinkedHashMap<>();
    private final List<FilterConfig.Parameter> params;

    public FilterSettingScreen(Screen parent, EditFilter filter) {
        super(Component.translatable("filter.realtrainmodunofficial." + filter.name().toLowerCase(java.util.Locale.ROOT)));
        this.parent = parent;
        this.filter = filter;
        this.params = filter.config().parameters();
    }

    @Override
    protected void init() {
        int hw = this.width / 2;
        int y = 44;
        widgets.clear();
        for (FilterConfig.Parameter p : params) {
            AbstractWidget w;
            if (p.type == FilterConfig.Type.BOOLEAN) {
                boolean init = Boolean.parseBoolean(p.toString());
                Button b = Button.builder(boolLabel(p.name, init), btn -> {
                    boolean now = !Boolean.parseBoolean(btn.getMessage().getString().endsWith("ON") ? "true" : "false");
                    btn.setMessage(boolLabel(p.name, now));
                }).bounds(hw - 20, y, 120, 20).build();
                b.setMessage(boolLabel(p.name, init));
                w = b;
            } else {
                EditBox box = new EditBox(this.font, hw - 20, y, 120, 20, Component.empty());
                box.setMaxLength(128);
                box.setValue(p.toString());
                w = box;
            }
            widgets.put(p.name, addRenderableWidget(w));
            y += 22;
        }

        y += 10;
        addRenderableWidget(Button.builder(Component.translatable("gui.realtrainmodunofficial.editor.run"),
            b -> run()).bounds(hw - 100, y, 95, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
            .bounds(hw + 5, y, 95, 20).build());
    }

    private static Component boolLabel(String name, boolean value) {
        return Component.literal(value ? "ON" : "OFF");
    }

    private void run() {
        StringBuilder sb = new StringBuilder();
        for (FilterConfig.Parameter p : params) {
            AbstractWidget w = widgets.get(p.name);
            String v;
            if (w instanceof EditBox box) {
                v = box.getValue();
            } else {
                v = String.valueOf("ON".equals(w.getMessage().getString()));
            }
            sb.append(p.name).append('=').append(v).append('\n');
        }
        PacketDistributor.sendToServer(new RunFilterPayload(filter.name(), sb.toString()));
        this.minecraft.setScreen(null);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        int hw = this.width / 2;
        g.drawCenteredString(this.font, this.title, hw, 16, 0xFFFFFF);

        //各入力欄の左に名前を出す
        int y = 44;
        for (FilterConfig.Parameter p : params) {
            Component label = Component.translatable("filter.param.realtrainmodunofficial." + p.name.toLowerCase(java.util.Locale.ROOT));
            g.drawString(this.font, label, hw - 24 - this.font.width(label), y + 6, 0xC0C0C0, false);
            y += 22;
        }
        if (params.isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable("gui.realtrainmodunofficial.editor.no_params"),
                hw, 60, 0x808080);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
