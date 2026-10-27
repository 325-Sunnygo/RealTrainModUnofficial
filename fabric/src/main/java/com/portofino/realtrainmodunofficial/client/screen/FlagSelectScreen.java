package com.portofino.realtrainmodunofficial.client.screen;

import com.portofino.realtrainmodunofficial.client.renderer.TextureFlagRenderer;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectDefinition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.function.Consumer;

/**
 * 旗を選ぶ画面。本家 {@code jp.ngt.rtm.gui.GuiSelectTexture} の移植。
 *
 * <p>本家は「モデル選択」ではなく<b>テクスチャ選択</b>で、
 * {@code TextureConfig.getUCountInGui()/getVCountInGui()} = <b>横 4 × 縦 2</b> の升目に
 * 旗のテクスチャそのものを<b>縦横比を保って</b>並べる。ホイールで 1 行ずつスクロール。
 * 標識も本家では同じ画面 (RTMU は既に本家式の {@link SignSelectGridScreen} を持っている)。
 */
public class FlagSelectScreen extends Screen {

    /** 本家 TextureConfig.getUCountInGui。 */
    private static final int U_COUNT = 4;
    /** 本家 TextureConfig.getVCountInGui。 */
    private static final int V_COUNT = 2;

    private final List<InstalledObjectDefinition> flags;
    private final Consumer<String> onSelect;
    private final String currentId;

    /** 本家 currentScroll: 何行ぶん送ったか。 */
    private int currentScroll;

    public FlagSelectScreen(Component title, List<InstalledObjectDefinition> flags,
                            Consumer<String> onSelect, String currentId) {
        super(title);
        this.flags = flags;
        this.onSelect = onSelect;
        this.currentId = currentId == null ? "" : currentId;
    }

    private int cellWidth() {
        return this.width / U_COUNT;
    }

    private int cellHeight() {
        return this.height / V_COUNT;
    }

    /** 本家 initGui: 縦横比を保ったまま升目に収める。 */
    private int[] cellRect(int index) {
        int cw = this.cellWidth();
        int ch = this.cellHeight();
        int u = index % U_COUNT;
        int v = index / U_COUNT - this.currentScroll;

        InstalledObjectDefinition.FlagParams params = this.flags.get(index).getFlagParams();
        float pw = params != null ? params.width() : 1.0F;
        float ph = params != null ? params.height() : 1.0F;
        if (pw <= 0.0F) {
            pw = 1.0F;
        }
        if (ph <= 0.0F) {
            ph = 1.0F;
        }
        //本家: 横長なら幅で、縦長なら高さで合わせる
        float f0 = pw > ph ? cw / pw : ch / ph;
        int w = (int) (pw * f0);
        int h = (int) (ph * f0);
        int x = cw * u + (cw - w) / 2;
        int y = ch * v + (ch - h) / 2;
        return new int[]{x, y, w, h};
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        //本家: ホイールで 1 行ずつ。最終行より下へは送らない
        int rows = Math.max(1, (this.flags.size() + U_COUNT - 1) / U_COUNT);
        this.currentScroll = Math.max(0, Math.min(this.currentScroll - (int) Math.signum(dy), rows - 1));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = 0; i < this.flags.size(); ++i) {
                int[] r = this.cellRect(i);
                if (mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3]) {
                    this.onSelect.accept(this.flags.get(i).getId());
                    this.onClose();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        //背景 (renderBlurredBackground を殺してあるので、バニラの暗いグラデーション =
        //本家 drawDefaultBackground と同じ 0xC0101010→0xD0101010 だけが敷かれる)。
        //★セルより先に描くこと。後に回すと旗の上に暗幕が乗る。
        super.render(graphics, mouseX, mouseY, partialTick);

        for (int i = 0; i < this.flags.size(); ++i) {
            int[] r = this.cellRect(i);
            if (r[1] + r[3] < 0 || r[1] > this.height) {
                continue;
            }
            InstalledObjectDefinition def = this.flags.get(i);
            InstalledObjectDefinition.FlagParams params = def.getFlagParams();
            ResourceLocation tex = TextureFlagRenderer.resolveTexture(
                def.getPackName(), params != null ? params.texture() : def.getButtonTexture());
            //本家 GuiButtonSelectTexture.draw: テクスチャ全面 (UV 0〜1) を升目いっぱいに貼る
            graphics.blit(tex, r[0], r[1], r[2], r[3], 0.0F, 0.0F, 16, 16, 16, 16);

            //選択中と、カーソルが乗っている物に枠を出す (本家は枠を出さないが、
            //本家は押した瞬間に閉じるので、どれを選ぶか分かるようにする)
            boolean hovered = mouseX >= r[0] && mouseX < r[0] + r[2]
                && mouseY >= r[1] && mouseY < r[1] + r[3];
            if (def.getId().equals(this.currentId)) {
                graphics.renderOutline(r[0], r[1], r[2], r[3], 0xFFFFA000);
            } else if (hovered) {
                graphics.renderOutline(r[0], r[1], r[2], r[3], 0xFFFFFFFF);
            }
            if (hovered) {
                graphics.drawString(this.font, def.getDisplayName(), r[0] + 2, r[1] + 2, 0xFFFFFF);
            }
        }

        graphics.drawString(this.font, this.title, 8, 8, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 1.21 のメニューぼかしを無効化 (本家 1.7.10 の GUI にぼかしは無い)。 */
    @Override
    protected void renderBlurredBackground(float partialTick) {
    }
}
