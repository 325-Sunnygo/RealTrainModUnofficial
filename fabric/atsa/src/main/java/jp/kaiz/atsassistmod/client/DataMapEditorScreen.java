package jp.kaiz.atsassistmod.client;

import jp.ngt.rtm.entity.train.EntityTrainBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 本家 jp.kaiz.atsassistmod.gui.GUIDataMapEditor の移植 (DataMap の一覧表示)。
 * 本家も編集は未実装 (actionPerformed 空) のため閲覧のみ — これが忠実。
 */
public class DataMapEditorScreen extends Screen {

    private EntityTrainBase train;
    private final List<String> lines = new ArrayList<>();
    private int scroll;

    public DataMapEditorScreen() {
        super(Component.literal("DataMapEditor"));
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.player != null && mc.player.getVehicle() instanceof EntityTrainBase riding)) {
            onClose();
            return;
        }
        this.train = riding;
        reload();
        addRenderableWidget(Button.builder(Component.literal("更新"), b -> reload())
                .bounds(this.width / 2 - 130, this.height - 30, 120, 20).build());
        addRenderableWidget(Button.builder(Component.literal("閉じる"), b -> onClose())
                .bounds(this.width / 2 + 10, this.height - 30, 120, 20).build());
    }

    private void reload() {
        lines.clear();
        Map<String, Object> entries = train.getResourceState().getDataMap().getEntries();
        entries.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> lines.add(e.getKey() + " = " + e.getValue()));
        scroll = 0;
    }

    /** ブラー無効化。 */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xE0101010);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        //本家のヘッダ表記
        g.drawString(this.font, "DataMapEditor", this.width / 2 - 100, 20, 0xFFFFFF, true);
        g.drawString(this.font, "Type: ModelTrain", this.width / 2 - 90, 30, 0xFFFFFF, true);
        g.drawString(this.font, "Name: " + this.train.getModelName(), this.width / 2 - 90, 40, 0xFFFFFF, true);

        int top = 56;
        int bottom = this.height - 40;
        int lineH = this.font.lineHeight + 2;
        int visible = (bottom - top) / lineH;
        int from = Math.min(scroll, Math.max(0, lines.size() - visible));
        int y = top;
        for (int i = from; i < Math.min(lines.size(), from + visible); i++) {
            g.drawString(this.font, lines.get(i), 24, y, 0xCCCCCC, false);
            y += lineH;
        }
        if (lines.isEmpty()) {
            g.drawCenteredString(this.font, "(DataMap は空です)", this.width / 2, this.height / 2, 0x999999);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.max(0, scroll - (int) Math.signum(scrollY) * 3);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
