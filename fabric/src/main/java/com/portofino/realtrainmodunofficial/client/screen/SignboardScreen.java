package com.portofino.realtrainmodunofficial.client.screen;

import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.client.model.MqoModelLoader;
import com.portofino.realtrainmodunofficial.client.signboard.FontImage;
import com.portofino.realtrainmodunofficial.client.signboard.SignboardTextRenderer;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectCategory;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectDefinition;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectRegistry;
import com.portofino.realtrainmodunofficial.network.SaveSignboardPayload;
import com.portofino.realtrainmodunofficial.signboard.SignboardAnimeType;
import com.portofino.realtrainmodunofficial.signboard.SignboardText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 本家 GuiSignboard の 1.21.1 移植。看板を素手で右クリックすると開く。
 * <p>
 * 左側が板のプレビュー (実際の見た目と同じ配置)、右側が編集パネル。プレビュー上の文字は
 * ドラッグで移動、四隅/辺をつかむとリサイズできる。
 * <p>
 * 本家は TileEntity の文字リストを直接いじっていて、キャンセルしてもクライアント側は
 * 変更されたままだった。こちらは編集用のコピーを持ち、「完了」でだけサーバへ送る。
 */
public class SignboardScreen extends Screen {
    private static final int PANEL_W = 130;
    /**
     * 掴み判定の太さ(px)。
     */
    private static final float GRAB = 3.0F;
    private static final int COLOR_ON = 0xFFFFFF;
    private static final int COLOR_OFF = 0xA0A0A0;

    private final InstalledObjectBlockEntity blockEntity;
    private final List<SignboardText> texts = new ArrayList<>();

    private String definitionId;
    private String ttSetting;
    @Nullable
    private SignboardText selected;

    private EditBox fieldText;
    private Button buttonFont;
    private EditBox fieldColor;
    private Button buttonBold;
    private Button buttonItalic;
    private EditBox fieldSize;
    private EditBox fieldWidth;
    private EditBox fieldPosU;
    private EditBox fieldPosV;
    private Button buttonAnimeType;
    private EditBox fieldAnimeSpeed;
    private EditBox fieldStation;

    /**
     * 編集中の文字のフォント名/スタイル。EditBox に出さないのでここで持つ。
     * ({@code font} は {@link Screen} 側の描画用 Font なので名前を分ける)
     */
    private String fontName = "Meiryo UI";
    private int style;
    private SignboardAnimeType animeType = SignboardAnimeType.SWITCH;

    /**
     * プレビューの倍率 (px / ブロック)。
     */
    private float scale = 1.0F;
    private int previewW;
    private int previewH;

    private boolean dragging;
    /**
     * 掴んでいる辺のビット: 8=左, 4=右, 2=上, 1=下。0 なら移動。
     */
    private int dragState;
    private double prevDragX;
    private double prevDragY;

    /**
     * ウィジェットの作り直し要求。
     * <p>
     * ボタンの onPress の中で直接 rebuildWidgets() すると、呼び出し元の
     * ContainerEventHandler.mouseClicked が「押されたウィジェット」にフォーカスを
     * セットするのが作り直しの<b>後</b>になるため、破棄済みのボタンがフォーカスを持ち、
     * その後 Enter を押すと同じボタンがもう一度発火してしまう (Del で2件消える等)。
     * そこで作り直しは次の描画まで遅らせる。
     */
    private boolean pendingRebuild;

    public SignboardScreen(InstalledObjectBlockEntity blockEntity) {
        super(Component.translatable("screen.realtrainmodunofficial.signboard"));
        this.blockEntity = blockEntity;
        this.definitionId = blockEntity.getDefinitionId();
        this.ttSetting = blockEntity.getSignTtSetting();
        for (SignboardText text : blockEntity.getSignTexts()) {
            this.texts.add(text.copy());
        }
    }

    @Nullable
    private InstalledObjectDefinition definition() {
        return InstalledObjectRegistry.getById(definitionId);
    }

    @Override
    protected void init() {
        InstalledObjectDefinition def = definition();
        float cfgWidth = def == null ? 1.0F : def.getWidth();
        float cfgHeight = def == null ? 1.0F : def.getHeight();
        int backTexture = def == null ? 0 : def.getBackTexture();

        //本家: backTexture==1 はテクスチャに表裏が並んでいるので、プレビューは倍幅になる。
        float boardWidth = backTexture == 1 ? cfgWidth * 2.0F : cfgWidth;
        int areaW = this.width - PANEL_W;
        int areaH = this.height - 30;
        float ratioTex = cfgHeight / boardWidth;
        float ratioGui = (float) areaH / areaW;
        if (ratioTex > ratioGui) {
            previewW = (int) (areaH / ratioTex);
            previewH = areaH;
        } else {
            previewW = areaW;
            previewH = (int) (areaW * ratioTex);
        }
        this.scale = previewH / cfgHeight;

        if (selected == null && !texts.isEmpty()) {
            selected = texts.get(0);
        }
        if (selected != null) {
            fontName = selected.font;
            style = selected.style;
            animeType = selected.animeType;
        }

        int px = areaW + 5;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> {
            save();
            onClose();
        }).bounds(this.width / 2 - 155, this.height - 25, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(this.width / 2 + 5, this.height - 25, 150, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("screen.realtrainmodunofficial.signboard.select_texture"),
                b -> openTextureSelect()).bounds(px, 5, 120, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.realtrainmodunofficial.signboard.add"),
                b -> addText()).bounds(px, 25, 30, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.realtrainmodunofficial.signboard.copy"),
                b -> copyText()).bounds(px + 30, 25, 30, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.realtrainmodunofficial.signboard.delete"),
                b -> deleteText()).bounds(px + 60, 25, 30, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.realtrainmodunofficial.signboard.redraw"),
                b -> applyFields()).bounds(px + 90, 25, 30, 20).build());

        if (selected == null) {
            return;
        }

        fieldText = addRenderableWidget(new EditBox(this.font, px, 48, 120, 20, Component.empty()));
        fieldText.setMaxLength(256);
        fieldText.setValue(selected.text);
        fieldText.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(
                "screen.realtrainmodunofficial.signboard.text.tip")));

        buttonFont = addRenderableWidget(Button.builder(Component.literal(fontName), b -> openFontSelect())
                .bounds(px, 70, 120, 20).build());

        fieldColor = addRenderableWidget(new EditBox(this.font, px, 100, 80, 20, Component.empty()));
        fieldColor.setValue("0x" + Integer.toHexString(selected.color));
        buttonBold = addRenderableWidget(Button.builder(Component.literal("B"), b -> {
            style ^= 1;
            updateStyleButtons();
        }).bounds(px + 80, 100, 20, 20).build());
        buttonItalic = addRenderableWidget(Button.builder(Component.literal("I"), b -> {
            style ^= 2;
            updateStyleButtons();
        }).bounds(px + 100, 100, 20, 20).build());
        updateStyleButtons();

        fieldSize = addNumberField(px, 130, selected.size);
        fieldWidth = addNumberField(px + 60, 130, selected.width);
        fieldPosU = addNumberField(px, 160, selected.posU);
        fieldPosV = addNumberField(px + 60, 160, selected.posV);

        buttonAnimeType = addRenderableWidget(Button.builder(Component.literal(animeType.toString()), b -> {
            animeType = SignboardAnimeType.byOrdinal((animeType.ordinal() + 1) % SignboardAnimeType.values().length);
            buttonAnimeType.setMessage(Component.literal(animeType.toString()));
        }).bounds(px, 190, 60, 20).build());
        fieldAnimeSpeed = addNumberField(px + 60, 190, selected.animeSpeed);

        fieldStation = addRenderableWidget(new EditBox(this.font, px, 220, 120, 20, Component.empty()));
        fieldStation.setMaxLength(256);
        fieldStation.setValue(ttSetting);
        fieldStation.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(
                "screen.realtrainmodunofficial.signboard.station.tip")));
    }

    private EditBox addNumberField(int x, int y, float value) {
        EditBox box = new EditBox(this.font, x, y, 60, 20, Component.empty());
        box.setValue(trim(value));
        box.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d{0,6}(\\.\\d{0,4})?"));
        return addRenderableWidget(box);
    }

    private static String trim(float v) {
        return v == (long) v ? String.valueOf((long) v) : String.valueOf(v);
    }

    private void updateStyleButtons() {
        buttonBold.setMessage(Component.literal("B").withStyle(s ->
                s.withColor((style & 1) != 0 ? COLOR_ON : COLOR_OFF)));
        buttonItalic.setMessage(Component.literal("I").withStyle(s ->
                s.withColor((style & 2) != 0 ? COLOR_ON : COLOR_OFF)));
    }

    // ---- 編集操作 ----

    /**
     * 入力欄の内容を選択中の文字に反映する (本家 updateText)。
     */
    private void applyFields() {
        if (selected == null || fieldText == null) {
            return;
        }
        selected.text = fieldText.getValue();
        selected.font = fontName;
        selected.style = style;
        selected.color = parseColor(fieldColor.getValue(), selected.color);
        selected.size = parse(fieldSize, selected.size);
        selected.width = parse(fieldWidth, selected.width);
        selected.posU = parse(fieldPosU, selected.posU);
        selected.posV = parse(fieldPosV, selected.posV);
        selected.animeType = animeType;
        selected.animeSpeed = parse(fieldAnimeSpeed, selected.animeSpeed);
        if (selected.size <= 0.0F) {
            selected.size = 0.05F;
        }
        if (selected.width <= 0.0F) {
            selected.width = 0.05F;
        }
        if (selected.animeSpeed <= 0.0F) {
            selected.animeSpeed = 0.1F;
        }
        ttSetting = fieldStation.getValue();
    }

    private void addText() {
        applyFields();
        SignboardText text = new SignboardText();
        texts.add(text);
        selected = text;
        pendingRebuild = true;
    }

    private void copyText() {
        if (selected == null) {
            return;
        }
        applyFields();
        SignboardText copy = selected.copy();
        texts.add(copy);
        selected = copy;
        pendingRebuild = true;
    }

    private void deleteText() {
        if (selected == null) {
            return;
        }
        texts.remove(selected);
        selected = null;
        pendingRebuild = true;
    }

    private void save() {
        applyFields();
        PacketDistributor.sendToServer(new SaveSignboardPayload(
                blockEntity.getBlockPos(), definitionId, ttSetting, texts));
    }

    private void openTextureSelect() {
        applyFields();
        List<ModelSelectScreen.ModelInfo> infos = InstalledObjectRegistry
                .getByCategory(InstalledObjectCategory.SIGNBOARD).stream()
                .map(d -> new ModelSelectScreen.ModelInfo(d.getId(), d.getDisplayName(), d.getPackName(), d.getButtonTexture()))
                .toList();
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new ModelSelectScreen(
                Component.translatable("screen.realtrainmodunofficial.select_signboard"),
                infos,
                selection -> {
                    this.definitionId = selection.modelId();
                    //ModelSelectScreen は選択直後に自分を閉じる (setScreen(null)) ので、
                    //次のティックまで待ってから看板エディタへ戻す。
                    mc.tell(() -> mc.setScreen(this));
                },
                definitionId,
                ""));
    }

    private void openFontSelect() {
        applyFields();
        Minecraft.getInstance().setScreen(new FontSelectScreen(this, fontName, name -> {
            this.fontName = name;
            if (selected != null) {
                selected.font = name;
            }
            if (buttonFont != null) {
                buttonFont.setMessage(Component.literal(name));
            }
        }));
    }

    private float parse(EditBox box, float fallback) {
        try {
            return Float.parseFloat(box.getValue().trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static int parseColor(String s, int fallback) {
        try {
            return Integer.decode(s.trim()) & 0xFFFFFF;
        } catch (Exception e) {
            return fallback;
        }
    }

    // ---- プレビュー上の操作 ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        SignboardText clicked = null;
        for (SignboardText text : texts) {
            if (inBounds(text, mouseX, mouseY)) {
                clicked = text;
                break;
            }
        }
        if (clicked == null) {
            return false;
        }
        boolean wasSelected = selected == clicked;
        if (wasSelected) {
            //本家: 選択済みの文字をもう一度掴むとドラッグ開始。
            dragging = true;
            dragState = grabState(clicked, mouseX, mouseY);
            prevDragX = mouseX;
            prevDragY = mouseY;
        } else {
            applyFields();
            selected = clicked;
            pendingRebuild = true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && selected != null && button == 0) {
            float dx = (float) (mouseX - prevDragX) / scale;
            float dy = (float) (mouseY - prevDragY) / scale;
            if (dragState == 0) {
                selected.posU += dx;
                selected.posV += dy;
            } else {
                if ((dragState & 8) != 0) {
                    selected.posU += dx;
                    selected.width -= dx;
                }
                if ((dragState & 4) != 0) {
                    selected.width += dx;
                }
                if ((dragState & 2) != 0) {
                    selected.posV += dy;
                    selected.size -= dy;
                }
                if ((dragState & 1) != 0) {
                    selected.size += dy;
                }
                selected.width = Math.max(0.05F, selected.width);
                selected.size = Math.max(0.05F, selected.size);
            }
            prevDragX = mouseX;
            prevDragY = mouseY;
            syncFieldsFromSelection();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void syncFieldsFromSelection() {
        if (selected == null || fieldPosU == null) {
            return;
        }
        fieldPosU.setValue(trim(selected.posU));
        fieldPosV.setValue(trim(selected.posV));
        fieldSize.setValue(trim(selected.size));
        fieldWidth.setValue(trim(selected.width));
    }

    private boolean inBounds(SignboardText text, double x, double y) {
        float minX = text.posU * scale;
        float minY = text.posV * scale;
        float maxX = text.width * scale + minX;
        float maxY = text.size * scale + minY;
        return x >= minX - GRAB && x <= maxX + GRAB && y >= minY - GRAB && y <= maxY + GRAB;
    }

    /**
     * 本家 getClickState: 掴んだ辺 (8=左, 4=右, 2=上, 1=下)。
     */
    private int grabState(SignboardText text, double x, double y) {
        float minX = text.posU * scale;
        float minY = text.posV * scale;
        float maxX = text.width * scale + minX;
        float maxY = text.size * scale + minY;
        int state = 0;
        if (near(x, minX)) {
            state += 8;
        }
        if (near(x, maxX)) {
            state += 4;
        }
        if (near(y, minY)) {
            state += 2;
        }
        if (near(y, maxY)) {
            state += 1;
        }
        return state;
    }

    private static boolean near(double target, float center) {
        return target >= center - GRAB && target <= center + GRAB;
    }

    // ---- 描画 ----

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        //ウィジェットの作り直しはここで消化する (クリック処理の途中でやるとフォーカスが
        //破棄済みボタンに残る)。rebuildWidgets() は clearFocus() も行う。
        if (pendingRebuild) {
            pendingRebuild = false;
            rebuildWidgets();
        }
        renderBackground(graphics, mouseX, mouseY, partialTick);

        //板のテクスチャ (backTexture==1 なら表裏が並んで写る)
        InstalledObjectDefinition def = definition();
        ResourceLocation texture = def == null || def.getSignTexture().isBlank()
                ? null
                : MqoModelLoader.resolvePackTexture(def.getPackName(), def.getSignTexture());
        if (texture != null) {
            graphics.blit(texture, 0, 0, previewW, previewH, 0.0F, 0.0F, 256, 256, 256, 256);
        } else {
            graphics.fill(0, 0, previewW, previewH, 0xFF303030);
        }

        for (SignboardText text : texts) {
            drawText(graphics, text);
            if (text == selected) {
                drawSelection(graphics, text, mouseX, mouseY);
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        if (selected != null) {
            int px = this.width - PANEL_W + 5;
            graphics.drawString(this.font, "Color            Style", px, 91, 0xFFFFFF);
            graphics.drawString(this.font, "Height        Width", px, 121, 0xFFFFFF);
            graphics.drawString(this.font, "PosU          PosV", px, 151, 0xFFFFFF);
            graphics.drawString(this.font, "AnimeType   Speed", px, 181, 0xFFFFFF);
            graphics.drawString(this.font, "StationSetting", px, 211, 0xFFFFFF);
        }
    }

    private void drawText(GuiGraphics graphics, SignboardText text) {
        SignboardTextRenderer.Frame frame = SignboardTextRenderer.frameFor(text, ttSetting);
        if (!frame.shouldDraw()) {
            return;
        }
        FontImage image = frame.image();
        int x = (int) (text.posU * scale);
        int y = (int) (text.posV * scale);
        int w = (int) (frame.width() * scale);
        int h = (int) (text.size * scale);
        if (w <= 0 || h <= 0) {
            return;
        }
        int imgW = image.getWidth();
        int imgH = image.getHeight();
        graphics.blit(image.getTexture(), x, y, w, h,
                frame.minU() * imgW, 0.0F,
                Math.max(1, Math.round((frame.maxU() - frame.minU()) * imgW)), imgH,
                imgW, imgH);
    }

    /**
     * 本家: 選択中の文字を赤枠 + 掴みハンドルで示す。
     */
    private void drawSelection(GuiGraphics graphics, SignboardText text, int mouseX, int mouseY) {
        int minX = (int) (text.posU * scale);
        int minY = (int) (text.posV * scale);
        int maxX = (int) (text.width * scale) + minX;
        int maxY = (int) (text.size * scale) + minY;
        int red = 0xFFFF0000;
        graphics.fill(minX, minY, maxX, minY + 1, red);
        graphics.fill(minX, maxY - 1, maxX, maxY, red);
        graphics.fill(minX, minY, minX + 1, maxY, red);
        graphics.fill(maxX - 1, minY, maxX, maxY, red);

        int state = grabState(text, mouseX, mouseY);
        int g = (int) GRAB;
        boolean left = (state & 8) != 0;
        boolean right = (state & 4) != 0;
        boolean top = (state & 2) != 0;
        boolean bottom = (state & 1) != 0;
        if ((left || right) && (top || bottom)) {
            //角: 4隅すべてにハンドルを出す
            handle(graphics, minX, minY, g, red);
            handle(graphics, maxX, minY, g, red);
            handle(graphics, minX, maxY, g, red);
            handle(graphics, maxX, maxY, g, red);
        } else if (left || right || top || bottom) {
            //辺: 各辺の中点にハンドルを出す
            int cx = (minX + maxX) / 2;
            int cy = (minY + maxY) / 2;
            handle(graphics, minX, cy, g, red);
            handle(graphics, maxX, cy, g, red);
            handle(graphics, cx, minY, g, red);
            handle(graphics, cx, maxY, g, red);
        }
    }

    private static void handle(GuiGraphics graphics, int x, int y, int size, int color) {
        graphics.fill(x - size, y - size, x + size, y + size, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
