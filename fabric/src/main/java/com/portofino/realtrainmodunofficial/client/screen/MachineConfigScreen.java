package com.portofino.realtrainmodunofficial.client.screen;

import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectDefinition;
import com.portofino.realtrainmodunofficial.network.SetBlockDataMapPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 本家 BlockMachineBase の DataForm GUI ({@code customForm}) の移植。
 * 機械を右クリックすると開き、customForm の各フィールドを入力して DataMap に保存する。
 */
public class MachineConfigScreen extends Screen {

    private final BlockPos pos;
    private final InstalledObjectDefinition.CustomForm form;
    private final Map<String, String> values;
    private final Map<String, EditBox> boxes = new LinkedHashMap<>();

    public MachineConfigScreen(BlockPos pos, InstalledObjectDefinition.CustomForm form,
                               Map<String, String> values) {
        super(Component.literal(form != null && form.title() != null ? form.title() : "DataMap"));
        this.pos = pos;
        this.form = form;
        this.values = values == null ? Map.of() : values;
    }

    @Override
    protected void init() {
        if (this.form == null) {
            return;
        }
        int bx = this.width / 2 - 100;
        for (InstalledObjectDefinition.FormField field : this.form.fields()) {
            int x = bx + field.column() * 205;
            int y = 50 + field.row() * 32;
            EditBox box = new EditBox(this.font, x, y, 200, 18,
                Component.literal(field.label() == null ? field.key() : field.label()));
            box.setValue(this.values.getOrDefault(field.key(), ""));
            box.setMaxLength(512);
            this.addRenderableWidget(box);
            this.boxes.put(field.key(), box);
        }
        this.addRenderableWidget(Button.builder(Component.literal("保存 / Save"), b -> this.save())
            .bounds(this.width / 2 - 50, this.height - 40, 100, 20).build());
    }

    private void save() {
        Map<String, String> out = new LinkedHashMap<>();
        this.boxes.forEach((key, box) -> out.put(key, box.getValue()));
        PacketDistributor.sendToServer(new SetBlockDataMapPayload(this.pos, out));
        this.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        if (this.form != null) {
            for (InstalledObjectDefinition.FormField field : this.form.fields()) {
                int x = this.width / 2 - 100 + field.column() * 205;
                int y = 50 + field.row() * 32 - 11;
                g.drawString(this.font, field.label() == null ? field.key() : field.label(), x, y, 0xA0A0A0);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
