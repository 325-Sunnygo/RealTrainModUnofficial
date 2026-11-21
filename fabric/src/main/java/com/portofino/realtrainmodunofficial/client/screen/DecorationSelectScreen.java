package com.portofino.realtrainmodunofficial.client.screen;

import com.portofino.realtrainmodunofficial.client.renderer.DecorationRenderer;
import jp.ngt.rtm.block.decoration.DecorationModel;
import jp.ngt.rtm.block.decoration.DecorationStore;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 装飾ブロック用の選択画面。本家 {@code GuiSelectIcon} の移植。
 *
 * <p>TEXTURE: 全ブロックテクスチャ + RTM の deco テクスチャの格子 (本家 getAllIcon)。
 * 本家はブロックアトラスを列挙するが 1.21 は使った分しか繋がないので、
 * リソースの {@code textures/block/} と {@code textures/decoration/} の png を列挙する。
 * MODEL: 登録済み DecorationModel の格子 (回転プレビュー)。
 */
public class DecorationSelectScreen extends Screen {

    public enum Mode {
        TEXTURE(32),
        MODEL(64);

        final int cellSize;

        Mode(int cellSize) {
            this.cellSize = cellSize;
        }
    }

    private final DecorationEditScreen parent;
    private final Mode mode;

    /** TEXTURE 用: Face.texture 形式 ("ns:block/x")。 */
    private final List<String> textures = new ArrayList<>();
    /** MODEL 用。 */
    private final List<DecorationModel> models = new ArrayList<>();

    private double scroll;

    public DecorationSelectScreen(DecorationEditScreen parent, Mode mode) {
        super(Component.literal("Select"));
        this.parent = parent;
        this.mode = mode;
    }

    @Override
    protected void init() {
        if (this.mode == Mode.TEXTURE && this.textures.isEmpty()) {
            //本家 DecorationStore.getAllIcon 相当
            var manager = this.minecraft.getResourceManager();
            for (String dir : new String[]{"textures/block", "textures/decoration"}) {
                manager.listResources(dir, loc -> loc.getPath().endsWith(".png")).keySet().forEach(loc -> {
                    String path = loc.getPath();
                    path = path.substring("textures/".length(), path.length() - ".png".length());
                    this.textures.add(loc.getNamespace() + ":" + path);
                });
            }
            this.textures.sort(String::compareTo);
        }
        if (this.mode == Mode.MODEL) {
            this.models.clear();
            this.models.addAll(DecorationStore.INSTANCE.getModels());
        }
    }

    private int columns() {
        return Math.max(1, (this.width - 20) / (this.mode.cellSize + 4));
    }

    private int count() {
        return this.mode == Mode.TEXTURE ? this.textures.size() : this.models.size();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        int rows = (this.count() + this.columns() - 1) / this.columns();
        int contentHeight = rows * (this.mode.cellSize + 4) + 20;
        this.scroll = Math.max(0, Math.min(this.scroll - dy * 24, Math.max(0, contentHeight - this.height)));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int index = this.hitIndex(mouseX, mouseY);
            if (index >= 0 && index < this.count()) {
                if (this.mode == Mode.TEXTURE) {
                    this.parent.setFaceTexture(this.textures.get(index));
                } else {
                    this.parent.applyLoadedModel(this.models.get(index));
                }
                this.minecraft.setScreen(this.parent);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int hitIndex(double mouseX, double mouseY) {
        int cell = this.mode.cellSize + 4;
        int col = (int) ((mouseX - 10) / cell);
        int row = (int) ((mouseY - 10 + this.scroll) / cell);
        if (col < 0 || col >= this.columns() || mouseX - 10 < 0) {
            return -1;
        }
        return row * this.columns() + col;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int cell = this.mode.cellSize + 4;
        int cols = this.columns();
        int hover = this.hitIndex(mouseX, mouseY);

        for (int i = 0; i < this.count(); ++i) {
            int x = 10 + (i % cols) * cell;
            int y = 10 + (i / cols) * cell - (int) this.scroll;
            if (y + cell < 0 || y > this.height) {
                continue;
            }
            if (hover == i) {
                graphics.renderOutline(x - 1, y - 1, this.mode.cellSize + 2, this.mode.cellSize + 2, 0xFFFFA000);
            }
        }

        //テクスチャセルの中身 (直バインド描画)
        if (this.mode == Mode.TEXTURE) {
            MultiBufferSource.BufferSource buffer = this.minecraft.renderBuffers().bufferSource();
            var pose = graphics.pose().last();
            for (int i = 0; i < this.textures.size(); ++i) {
                int x = 10 + (i % cols) * cell;
                int y = 10 + (i / cols) * cell - (int) this.scroll;
                if (y + cell < 0 || y > this.height) {
                    continue;
                }
                ResourceLocation tex = DecorationRenderer.toTexture(this.textures.get(i));
                var consumer = buffer.getBuffer(RenderType.text(tex));
                int s = this.mode.cellSize;
                int light = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
                consumer.addVertex(pose, x, y, 0).setColor(255, 255, 255, 255).setUv(0, 0).setLight(light);
                consumer.addVertex(pose, x, y + s, 0).setColor(255, 255, 255, 255).setUv(0, 1).setLight(light);
                consumer.addVertex(pose, x + s, y + s, 0).setColor(255, 255, 255, 255).setUv(1, 1).setLight(light);
                consumer.addVertex(pose, x + s, y, 0).setColor(255, 255, 255, 255).setUv(1, 0).setLight(light);
            }
            buffer.endBatch();
        } else {
            //モデルの回転プレビュー (本家 IconElementModel: 20/45 度傾け + 常時回転)
            float rotation = (Util.getMillis() % 4000L) / 4000.0F * 360.0F;
            for (int i = 0; i < this.models.size(); ++i) {
                int x = 10 + (i % cols) * cell;
                int y = 10 + (i / cols) * cell - (int) this.scroll;
                if (y + cell < 0 || y > this.height) {
                    continue;
                }
                float s = this.mode.cellSize * 0.4F;
                DecorationEditScreen.drawModelPreview(graphics, this.models.get(i),
                    x + this.mode.cellSize / 2, y + this.mode.cellSize / 2, s,
                    20.0F, 45.0F + rotation);
                //モデル名
                graphics.drawString(this.font, this.models.get(i).name, x, y + this.mode.cellSize - 8, 0xFFFFFF);
            }
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
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
