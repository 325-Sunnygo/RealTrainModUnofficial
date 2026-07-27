package com.portofino.realtrainmodunofficial.client.screen;

import com.portofino.realtrainmodunofficial.network.MiniatureSettingsPayload;
import jp.ngt.mcte.item.ItemMiniature;
import jp.ngt.ngtlib.block.NGTObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * ミニチュアの設定画面 (neo mcte)。本家 MCTE {@code GuiItemMiniature} の移植。
 *
 * <p>本家と同じ「左に操作ボタン・右に数値入力」の配置にしてある。
 * 本家はスライダーではなく<b>テキスト入力</b>で、任意の値を直接打てる。
 * <pre>
 *   [選択]      縮尺     [____]
 *   [書き出し]  オフセットX [____]
 *   [モード]    オフセットY [____]
 *               オフセットZ [____]
 *   [名前_____] 明るさ    [____]
 *
 *          [決定]        [やめる]
 * </pre>
 *
 * <p>変更対象は<b>いま手に持っているスタックだけ</b>。開いたときの手を覚えておき、
 * 適用時もその手のスタックへ送る。MCTEU のように ID 経由で外部テーブルを触らないので、
 * インベントリ内の他のミニチュアには影響しない。
 */
public class MiniatureSettingsScreen extends Screen {

    private final InteractionHand hand;
    private final ItemStack stack;
    private final NGTObject ngto;

    private ItemMiniature.MiniatureMode mode;

    private EditBox fieldScale;
    private EditBox fieldOffsetX;
    private EditBox fieldOffsetY;
    private EditBox fieldOffsetZ;
    private EditBox fieldLight;
    private EditBox fieldName;
    private Button buttonMode;
    private Button buttonDone;

    public MiniatureSettingsScreen(InteractionHand hand, ItemStack stack) {
        super(Component.translatable("gui.realtrainmodunofficial.miniature.title"));
        this.hand = hand;
        this.stack = stack;
        CompoundTag tag = tagOf(stack);
        this.mode = ItemMiniature.getMode(tag);
        this.ngto = ItemMiniature.getNGTObject(tag);
    }

    @Override
    protected void init() {
        CompoundTag tag = tagOf(stack);
        float scale = ItemMiniature.getScale(tag);
        float[] off = ItemMiniature.getOffset(tag);
        int light = ItemMiniature.getLightValue(tag);

        int hw = this.width / 2;
        int h = 40;
        //本家: 左列 hw-120 (幅80)、右列 hw+40 (幅80)
        int leftX = hw - 120;
        int rightX = hw + 40;

        addRenderableWidget(Button.builder(Component.translatable("gui.realtrainmodunofficial.miniature.select"),
            b -> onSelect()).bounds(leftX, h, 80, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.realtrainmodunofficial.miniature.export"),
            b -> onExport()).bounds(leftX, h + 20, 80, 20).build());
        buttonMode = addRenderableWidget(Button.builder(modeLabel(), b -> {
            mode = ItemMiniature.MiniatureMode.byId((mode.id() + 1) % ItemMiniature.MiniatureMode.values().length);
            b.setMessage(modeLabel());
        }).bounds(leftX, h + 40, 80, 20).build());

        fieldName = addField(leftX, h + 70, 100, stack.getHoverName().getString());
        fieldName.setMaxLength(64);

        fieldScale = addField(rightX, h, 80, trim(scale));
        fieldOffsetX = addField(rightX, h + 20, 80, trim(off[0]));
        fieldOffsetY = addField(rightX, h + 40, 80, trim(off[1]));
        fieldOffsetZ = addField(rightX, h + 60, 80, trim(off[2]));
        fieldLight = addField(rightX, h + 80, 80, String.valueOf(light));

        buttonDone = addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> {
            apply();
            onClose();
        }).bounds(hw - 155, this.height - 28, 150, 20).build());
        //本家: 中身が無いミニチュアは決定できない
        buttonDone.active = ngto != null;

        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
            .bounds(hw + 5, this.height - 28, 150, 20).build());
    }

    private EditBox addField(int x, int y, int w, String value) {
        EditBox box = new EditBox(this.font, x, y, w, 20, Component.empty());
        box.setValue(value);
        return addRenderableWidget(box);
    }

    private Component modeLabel() {
        return Component.translatable("gui.realtrainmodunofficial.miniature.mode." + mode.name().toLowerCase(java.util.Locale.ROOT));
    }

    /** 本家「選択」: 保存済み NGTO の読み込み。 */
    private void onSelect() {
        this.minecraft.setScreen(new MiniatureFileScreen(this, false));
    }

    /** 本家「書き出し」: いまの中身を NGTO ファイルへ保存。 */
    private void onExport() {
        if (ngto == null) {
            return;
        }
        this.minecraft.setScreen(new MiniatureFileScreen(this, true));
    }

    private void apply() {
        PacketDistributor.sendToServer(new MiniatureSettingsPayload(
            hand == InteractionHand.OFF_HAND,
            parseFloat(fieldScale, 1.0F),
            parseFloat(fieldOffsetX, 0.0F),
            parseFloat(fieldOffsetY, 0.0F),
            parseFloat(fieldOffsetZ, 0.0F),
            mode.id(),
            (int) parseFloat(fieldLight, 0.0F),
            fieldName.getValue()));
    }

    /** 中身 (書き出し画面が使う)。 */
    NGTObject ngto() {
        return ngto;
    }

    InteractionHand hand() {
        return hand;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);

        int hw = this.width / 2;
        int h = 40;
        g.drawCenteredString(this.font, this.title, hw, 16, 0xFFFFFF);

        //右列の入力に対する見出し (本家と同じく左側へ小さく出す)
        drawLabel(g, hw - 30, h, "gui.realtrainmodunofficial.miniature.scale");
        drawLabel(g, hw - 30, h + 20, "gui.realtrainmodunofficial.miniature.offset_x");
        drawLabel(g, hw - 30, h + 40, "gui.realtrainmodunofficial.miniature.offset_y");
        drawLabel(g, hw - 30, h + 60, "gui.realtrainmodunofficial.miniature.offset_z");
        drawLabel(g, hw - 30, h + 80, "gui.realtrainmodunofficial.miniature.light");

        //中身の情報 (本家は NGTObject.addInformation をここに出す)
        if (ngto != null) {
            g.drawString(this.font, Component.literal(
                    String.format("%d x %d x %d  (%d)", ngto.xSize, ngto.ySize, ngto.zSize, ngto.blockList.size())),
                hw - 120, h + 100, 0xA0A0A0, false);
        } else {
            g.drawString(this.font, Component.translatable("gui.realtrainmodunofficial.miniature.empty"),
                hw - 120, h + 100, 0xFF8080, false);
        }
    }

    private void drawLabel(GuiGraphics g, int right, int y, String key) {
        Component c = Component.translatable(key);
        g.drawString(this.font, c, right - this.font.width(c) + 60, y + 6, 0xC0C0C0, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static float parseFloat(EditBox box, float fallback) {
        try {
            return Float.parseFloat(box.getValue().trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    /** 末尾の余計な 0 を落とす (本家は String.valueOf そのまま。読みやすさ優先で少しだけ整える)。 */
    private static String trim(float v) {
        String s = String.format("%.4f", v);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "");
            if (s.endsWith(".")) {
                s = s.substring(0, s.length() - 1);
            }
        }
        return s;
    }

    private static CompoundTag tagOf(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null ? data.copyTag() : new CompoundTag();
    }
}
