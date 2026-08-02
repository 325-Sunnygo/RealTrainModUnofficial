package com.portofino.realtrainmodunofficial.client.screen;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.portofino.realtrainmodunofficial.client.PackButtonTextureCache;
import com.portofino.realtrainmodunofficial.RtmuSettings;
import com.portofino.realtrainmodunofficial.client.model.MqoModelLoader;
import com.portofino.realtrainmodunofficial.client.renderer.BogieRenderer;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectRegistry;
import com.portofino.realtrainmodunofficial.rail.RailRegistry;
import com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition;
import com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 本家 RTM (KaizPatchX GuiSelectModel) と同じ挙動のモデル選択画面。
 * 左に中央寄せスクロールの一覧 (選択中が縦中央)。各項目 160×32 にモデルのボタン画像。
 */
@OnlyIn(Dist.CLIENT)
public class ModelSelectScreen extends Screen {
    /** 選択結果。旧呼び出し互換のため (modelId, dataMapValue) のコンストラクタも残す。 */
    public record SelectionResult(String modelId, String dataMapValue, String customName, int color) {
        public SelectionResult(String modelId, String dataMapValue) {
            this(modelId, dataMapValue, "", 0xFFFFFF);
        }
    }

    public record ModelInfo(String id, String displayName, String packName, String buttonTexture, String category) {
        public ModelInfo(String id, String displayName, String packName, String buttonTexture) {
            this(id, displayName, packName, buttonTexture, "");
        }
    }

    // 一覧項目 (5:1)。本家は 160×32 だが「項目でかい」との指摘で少し小さめに。
    private static final int BTN_W = 140;
    private static final int BTN_H = 28;
    private static final int LIST_LEFT = 8;
    private static final int SCROLLBAR_W = 6;
    private static final int FIELD_H = 18;

    private final List<ModelInfo> allModels;
    private final Consumer<SelectionResult> onSelected;
    private final String initialSelectedId;
    private final String initialDataMapValue;
    private final String initialName;
    private final int initialColor;

    /** 検索で絞り込んだ後の表示対象 (フラット)。 */
    private List<ModelInfo> filtered = new ArrayList<>();
    /** 縦中央に来る項目の index。 */
    private int currentScroll = 0;
    private String selectedId = null;

    private EditBox searchField;
    private EditBox nameField;
    private EditBox colorField;
    private EditBox dataMapField;
    private String lastSearch = "";

    /** 右上入力群の左端 x (init で確定)。ホイール判定などに使う。 */
    private int clusterLeftX;

    private boolean draggingScrollbar = false;

    // ---- モデルプレビュー ----
    /** ロード済みモデル (画面をまたいで使い回す)。 */
    private static final Map<String, MqoModelLoader.MqoModel> MODEL_CACHE = new ConcurrentHashMap<>();
    /** 読めなかった id (毎フレーム探しに行かないように覚えておく)。 */
    private static final Set<String> MISSING_MODEL_CACHE = ConcurrentHashMap.newKeySet();

    /** スクリプト車両をプレビューするための、ワールド未追加の一時エンティティ。 */
    private com.portofino.realtrainmodunofficial.entity.TrainEntity previewEntity;
    private String previewEntityId;
    /** 選択画面を開いている間は F1 相当で HUD を隠す。閉じたら戻す退避値。 */
    private boolean prevHideGui;

    public ModelSelectScreen(Component title, List<ModelInfo> models, Consumer<SelectionResult> onSelected) {
        this(title, models, onSelected, null, "", "", 0xFFFFFF);
    }

    public ModelSelectScreen(Component title, List<ModelInfo> models, Consumer<SelectionResult> onSelected,
                             String initialSelectedId, String initialDataMapValue) {
        this(title, models, onSelected, initialSelectedId, initialDataMapValue, "", 0xFFFFFF);
    }

    public ModelSelectScreen(Component title, List<ModelInfo> models, Consumer<SelectionResult> onSelected,
                             String initialSelectedId, String initialDataMapValue,
                             String initialName, int initialColor) {
        super(title);
        // 本家どおり名前順ソート (カテゴリを名前より優先)
        // ★同梱モデルを隠す設定が ON なら、ここで一覧から落とす。
        // 落とすのは<b>表示だけ</b>で、読み込みは通常どおり行う
        // (既に設置されている物やパックからの参照が壊れないようにするため)。
        // 選択中の物だけは、隠す設定でも消さない (今何を選んでいるか分からなくなる)。
        this.allModels = models.stream()
            .filter(i -> !RtmuSettings.hideBundledModels
                || !RtmuSettings.isBundledPack(i.packName())
                || (initialSelectedId != null && initialSelectedId.equals(i.id())))
            .sorted(Comparator
                .comparing((ModelInfo i) -> safe(i.category()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(i -> safe(i.displayName()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(i -> safe(i.id()), String.CASE_INSENSITIVE_ORDER))
            .toList();
        this.onSelected = onSelected;
        this.initialSelectedId = initialSelectedId;
        this.initialDataMapValue = initialDataMapValue == null ? "" : initialDataMapValue;
        this.initialName = initialName == null ? "" : initialName;
        this.initialColor = initialColor;
    }

    private static String safe(String v) { return v == null ? "" : v; }

    // ---- レイアウト ----
    private int scrollbarX() { return width - SCROLLBAR_W - 3; }   // 画面右端
    private int listRight() { return LIST_LEFT + BTN_W; }
    private int centerY() { return height / 2 - BTN_H / 2; }


    @Override
    protected void init() {
        // 選択画面を開いている間は HUD (ホットバー/手/照準など) を全て隠す (F1相当)。
        this.prevHideGui = minecraft != null && minecraft.options.hideGui;
        if (minecraft != null) minecraft.options.hideGui = true;
        // JourneyMap は hideGui を無視するので、ミニマップを一時的にオフにする。
        com.portofino.realtrainmodunofficial.client.JourneyMapCompat.setSuppressed(true);

        // 本家配置: 右上にコンパクトな2列。スクロールバー(右端)の左に収める。
        // 一覧と重ならないよう clusterLeftX >= listArea を先に確定してから幅を出す。
        int fieldsRight = scrollbarX() - 6;
        int listArea = listRight() + 12;
        this.clusterLeftX = Math.max(listArea, fieldsRight - 220);
        int clusterW = Math.max(60, fieldsRight - clusterLeftX);
        int gap = 6;
        int colW = (clusterW - gap) / 2;
        int c1 = clusterLeftX;
        int c2 = clusterLeftX + colW + gap;
        int fy = 8;   // もっと右上へ

        nameField = addBox(c1, fy, colW, "Custom Name", initialName);
        searchField = addBox(c2, fy, colW, "Search", "");
        fy += FIELD_H + 8;
        dataMapField = addBox(c1, fy, colW, "DataMap", initialDataMapValue);
        colorField = addBox(c2, fy, Math.max(48, colW - 22), "Color", String.format("0x%06X", initialColor & 0xFFFFFF));

        // 完了・キャンセルは画面下・中央 (真ん中)。一覧側は下マージンを空けて重なりを避ける。
        int bw = Math.min(100, Math.max(70, width / 5));
        int bh = 20, bgap = 8;
        int totalW = bw * 2 + bgap;
        int bx = (width - totalW) / 2;
        int by = height - bh - 2;   // もう少し下げる
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onDone())
            .bounds(bx, by, bw, bh).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
            .bounds(bx + bw + bgap, by, bw, bh).build());

        // 再オープン時は現在のモデルを選択済みに (これは正しい既定選択)。
        // 以降スクロールしても選択は動かず、クリックした時だけ変わる。
        this.selectedId = initialSelectedId;
        rebuildFiltered();
    }

    private EditBox addBox(int x, int y, int w, String hint, String value) {
        EditBox box = new EditBox(font, x, y, w, FIELD_H, Component.literal(hint));
        box.setMaxLength(96);
        box.setValue(value == null ? "" : value);
        box.setHint(Component.literal(hint));
        addRenderableWidget(box);
        return box;
    }

    /** 検索でフィルタし、選択中を中央スクロール位置へ合わせる。 */
    private void rebuildFiltered() {
        String kw = searchField == null ? "" : searchField.getValue().trim().toLowerCase(Locale.ROOT);
        List<ModelInfo> list = new ArrayList<>();
        for (ModelInfo m : allModels) {
            if (kw.isEmpty()
                || safe(m.displayName()).toLowerCase(Locale.ROOT).contains(kw)
                || safe(m.id()).toLowerCase(Locale.ROOT).contains(kw)
                || safe(m.category()).toLowerCase(Locale.ROOT).contains(kw)) {
                list.add(m);
            }
        }
        this.filtered = list;
        // 表示位置だけ選択中に合わせる。選択(selectedId)はここでは変えない
        // (スクロール/検索で勝手に選択が動かないように。選択はクリック時のみ)。
        int idx = indexOf(selectedId);
        currentScroll = idx >= 0 ? idx : 0;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        // ここには黒を描かない。
        // もう一度呼ぶため、ここに黒を置くと「ボタンを描いた後」にもう一枚黒が重なり、
        // ボタンだけ暗くなる (field は後描画なので明るいまま、という不具合)。
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        // 全画面「半透明の黒」を一番下に一度だけ敷く (world はうっすら透ける)。
        // renderBackground には置かない — super.render がそれを再度呼び、ボタンの上に黒が重なって
        // ボタンだけ暗くなるため。ここで先に敷けば、この後に描くボタン/フィールドは黒に載る=暗くならない。
        g.fill(0, 0, width, height, 0xB0000000);

        // タイトルは影付きで、明るい空を背景にしても読めるように。
        g.drawString(font, getTitle(), LIST_LEFT, 8, 0xFFFFFF, true);

        // 一覧 (中央寄せスクロール)。可視範囲だけ描く。下部は完了/キャンセルの帯を空ける。
        int cy = centerY();
        int listBottom = height - 30;
        for (int i = 0; i < filtered.size(); i++) {
            int y = cy + BTN_H * (i - currentScroll);
            if (y <= -BTN_H || y >= listBottom) continue;
            boolean isSel = filtered.get(i).id().equals(selectedId);
            drawItem(g, filtered.get(i), LIST_LEFT, y, isSel, mouseX, mouseY);
        }

        drawScrollbar(g);

        // 色プレビュー (color 欄の右)
        if (colorField != null) {
            int c = parseColor(colorField.getValue(), initialColor);
            int cx = colorField.getX() + colorField.getWidth() + 4;
            int cyy = colorField.getY();
            g.fill(cx, cyy, cx + FIELD_H, cyy + FIELD_H, 0xFF000000 | (c & 0xFFFFFF));
            g.renderOutline(cx, cyy, FIELD_H, FIELD_H, 0xFFFFFFFF);
        }

        // 一覧のボタンをクリックして選んだモデルを右手に浮かべる
        renderSelectedPreview(g);

        super.render(g, mouseX, mouseY, pt);

        if (filtered.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("screen.realtrainmodunofficial.no_models"),
                LIST_LEFT + BTN_W / 2, cy + 12, 0xAAAAAA);
        }
    }


    // ============================================================ モデルプレビュー
    // 本家 GuiSelectModel.renderModel + ModelSet*Client.renderModelInGui の移植。
    // 本家は「一覧のボタンにマウスを乗せている間だけ」画面右手にモデルを浮かべる。

    /** 本家 gluPerspective(80, 1.0, 5, 1000)。アスペクトを 1.0 にするのも本家どおり。 */
    private static final float PREVIEW_FOV_DEG = 80.0F;
    private static final float PREVIEW_NEAR = 5.0F;
    private static final float PREVIEW_FAR = 1000.0F;

    private void renderSelectedPreview(GuiGraphics g) {
        ModelInfo info = null;
        for (ModelInfo m : filtered) {
            if (m.id().equals(selectedId)) {
                info = m;
                break;
            }
        }
        if (info == null) {
            return;
        }
        MqoModelLoader.MqoModel model = getOrLoadModel(info.id(), info.packName());
        if (model == null) {
            return;
        }
        VehicleDefinition vehicleDef = VehicleRegistry.getById(info.id());

        // ここまでに積んだ GUI の頂点を吐き出してから投影を差し替える
        g.flush();

        RenderSystem.backupProjectionMatrix();
        org.joml.Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try {
            RenderSystem.setProjectionMatrix(
                new org.joml.Matrix4f().perspective(
                    (float) Math.toRadians(PREVIEW_FOV_DEG), 1.0F, PREVIEW_NEAR, PREVIEW_FAR),
                com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);

            // ★本家 glLoadIdentity 相当。これが要る。
            // 1.21 の GUI は modelview に translate(0,0,-11000) が入っており、そこへ
            // near=5 / far=1000 の透視投影を張ると全部が遠クリップされて何も出ない。
            modelView.identity();
            RenderSystem.applyModelViewMatrix();

            // 機種ごとの配置は PoseStack 側に積む (頂点は CPU で変換される)
            PoseStack ps = new PoseStack();
            applyGuiPlacement(ps, info.id(), vehicleDef);

            // 本家 RenderHelper.enableStandardItemLighting + GL_DEPTH_TEST
            Lighting.setupFor3DItems();
            // 平行投影で描いた GUI の深度値とは尺度が違うので、いったん深度を流す
            RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            RenderSystem.enableDepthTest();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            MultiBufferSource.BufferSource buf = Minecraft.getInstance().renderBuffers().bufferSource();
            Object previewEnt = (vehicleDef != null && (model.hasRenderScript() || vehicleDef.hasScript()))
                ? getOrCreatePreviewEntity(vehicleDef, model) : null;
            renderPreviewModel(model, vehicleDef, ps, buf, previewEnt);
            buf.endBatch();

            RenderSystem.disableDepthTest();
            // 以降の GUI (ボタン/文字) が透視投影の深度に負けないよう戻しておく
            RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            Lighting.setupFor3DItems();
        } catch (Throwable t) {
            // プレビューが失敗しても選択画面自体は使えるようにする
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    /** 本家 renderModelInGui の機種別配置。X:右が+, Z:手前が+ (本家のコメントどおり)。 */
    private static void applyGuiPlacement(PoseStack ps, String id, VehicleDefinition vehicleDef) {
        if (vehicleDef != null) {
            // ModelSetVehicleBaseClient
            ps.translate(11.0F, -1.0F, -12.0F);
            ps.mulPose(Axis.YP.rotationDegrees(-65.0F));
            ps.scale(1.2F, 1.2F, 1.2F);
            return;
        }
        if (RailRegistry.getById(id) != null) {
            // ModelSetRailClient
            ps.translate(3.0F, -2.0F, -6.0F);
            ps.mulPose(Axis.ZP.rotationDegrees(10.0F));
            ps.mulPose(Axis.YP.rotationDegrees(-50.0F));
            ps.scale(1.5F, 1.5F, 1.5F);
            return;
        }
        // ModelSetMachineClient / ModelSetOrnamentClient (設置物はこちら)
        ps.translate(3.0F, -1.0F, -10.0F);
        ps.mulPose(Axis.YP.rotationDegrees(-60.0F));
    }

    /**
     * 本家は model.render(null, cfg, 0/1, 0) で pass0/pass1 を描く。
     * RTMU はスクリプト車両の描画経路が別なので、そちらを優先し、無ければベイク経路で 2 パス描く。
     */
    private static void renderPreviewModel(MqoModelLoader.MqoModel model, VehicleDefinition vehicleDef,
                                           PoseStack poseStack, MultiBufferSource.BufferSource buffer,
                                           Object previewEnt) {
        poseStack.pushPose();
        try {
            if (vehicleDef != null) {
                Vec3 offset = vehicleDef.getModelOffset();
                poseStack.translate(offset.x, offset.y, offset.z);
                // ボクセルモデルは形状に scale が焼き込んであるので掛けない (在ワールドと同じ)。
                if (model == null || !model.isVoxelModel()) {
                    float modelScale = vehicleDef.getModelScale();
                    poseStack.scale(modelScale, modelScale, modelScale);
                }
            }

            boolean rendered = false;
            if (vehicleDef != null && vehicleDef.hasScript()) {
                try {
                    com.portofino.realtrainmodunofficial.client.render.VehicleScriptRenderers.Scripted scripted =
                        com.portofino.realtrainmodunofficial.client.render.VehicleScriptRenderers.get(vehicleDef);
                    rendered = scripted != null && scripted.render(previewEnt, 0.0F, poseStack, buffer,
                        LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, model);
                } catch (Throwable ignored) {
                    rendered = false;
                }
            }
            if (!rendered) {
                // 本家 pass0 → pass1 の順。entity を渡さないと doCulling も発光判定も引けない。
                MqoModelLoader.renderModelWithoutScript(model, poseStack, buffer,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, false, null, null, previewEnt);
                MqoModelLoader.renderModelWithoutScript(model, poseStack, buffer,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, true, null, null, previewEnt);
            }

            // 本家 renderPartsInGui 相当: 台車も一緒に出す
            if (vehicleDef != null) {
                // 在ワールド (RtmBogieRenderer) と同じ判定にする: スクリプトで走り装置を
                // 動かす車両だけ、車体側に一本化する。
                boolean selfDrawsRunningGear = model.hasOwnWheelGroups()
                    && vehicleDef != null && vehicleDef.hasScript();
                List<VehicleDefinition.BogieDefinition> bogies = vehicleDef.getBogies();
                for (int i = 0; i < bogies.size(); i++) {
                    VehicleDefinition.BogieDefinition bogieDef = bogies.get(i);
                    if (skipPreviewBogie(selfDrawsRunningGear, bogieDef)) {
                        continue;
                    }
                    try {
                        BogieRenderer.renderBogie(poseStack, i, bogieDef, vehicleDef,
                            null, buffer, LightTexture.FULL_BRIGHT, 0.0F, 1.0F);
                    } catch (Throwable ignored) {
                        // 台車 1 つの失敗で車体プレビューまで消さない
                    }
                }
            }
        } finally {
            poseStack.popPose();
        }
    }

    private static boolean skipPreviewBogie(boolean selfDrawsRunningGear, VehicleDefinition.BogieDefinition bogieDef) {
        if (bogieDef == null || bogieDef.modelFile() == null || bogieDef.modelFile().isBlank()) {
            return true;
        }
        if (BogieRenderer.isDummyBogieModel(bogieDef.modelFile())) {
            return true;
        }
        // 本家 RenderBogie と同じ: ダミー指定以外は必ず描く。
        return false;
    }

    /** 車両 / 設置物 / レールのどれかとして id からモデルを引く。失敗は覚えて再探索しない。 */
    private MqoModelLoader.MqoModel getOrLoadModel(String id, String packName) {
        if (id == null || id.isBlank() || MISSING_MODEL_CACHE.contains(id)) {
            return null;
        }
        MqoModelLoader.MqoModel cached = MODEL_CACHE.get(id);
        if (cached != null) {
            return cached;
        }
        MqoModelLoader.MqoModel model = null;
        try {
            VehicleDefinition vd = VehicleRegistry.getById(id);
            if (vd != null && vd.getModelFile() != null && !vd.getModelFile().isBlank()) {
                model = MqoModelLoader.loadModelForVehicle(vd);
            }
            if (model == null) {
                var iod = InstalledObjectRegistry.getById(id);
                if (iod != null && iod.getModelFile() != null && !iod.getModelFile().isBlank()) {
                    model = MqoModelLoader.loadModelFromPack(
                        iod.getPackName(), iod.getModelFile(), iod.getTextureOverrides(), null, iod.isSmoothing());
                }
            }
            if (model == null) {
                var rd = RailRegistry.getById(id);
                if (rd != null && rd.getModelFile() != null && !rd.getModelFile().isBlank()) {
                    model = MqoModelLoader.loadModelFromPack(
                        rd.getPackName(), rd.getModelFile(), rd.getTextureOverrides(), null, false);
                }
            }
        } catch (Exception ignored) {
            model = null;
        }
        if (model != null) {
            MODEL_CACHE.put(id, model);
        } else {
            MISSING_MODEL_CACHE.add(id);
        }
        return model;
    }

    /**
     * スクリプトを適用するための、ワールドへ追加していない一時車両。
     * スクリプトは entity の状態を読んで本体やドア・ライトを配置するので、null だと
     * 例外を投げて「スクリプト無し描画」に落ちる車両が多い。
     */
    private com.portofino.realtrainmodunofficial.entity.TrainEntity getOrCreatePreviewEntity(
            VehicleDefinition def, MqoModelLoader.MqoModel model) {
        if (def == null || def.getId() == null) {
            return null;
        }
        if (previewEntity != null && def.getId().equals(previewEntityId)) {
            return previewEntity;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return null;
            }
            com.portofino.realtrainmodunofficial.entity.TrainEntity e =
                com.portofino.realtrainmodunofficial.entity.TrainEntity.create(
                    mc.level, def.getId(), 0.0D, 0.0D, 0.0D, 0.0F, def.getTrainDistance());
            if (e == null) {
                return null;
            }
            if (model.getScriptEngine() != null) {
                e.setScriptEngine(model.getScriptEngine());
            }
            previewEntity = e;
            previewEntityId = def.getId();
            return e;
        } catch (Throwable t) {
            return null;
        }
    }

    private void drawItem(GuiGraphics g, ModelInfo m, int left, int top, boolean selected, int mouseX, int mouseY) {
        boolean hovered = mouseX >= left && mouseX < left + BTN_W && mouseY >= top && mouseY < top + BTN_H;
        // 土台は renderBackground の全画面「半透明の黒」に任せ、ここではその上に RTM ボタンテクスチャを重ねる。
        // 選択/ホバー時だけ明るみを足す。
        if (selected) {
            g.fill(left, top, left + BTN_W, top + BTN_H, 0x66FFFFFF);
        } else if (hovered) {
            g.fill(left, top, left + BTN_W, top + BTN_H, 0x22FFFFFF);
        }
        // ぼやけ対策: buttonTexture を「GUIサイズ × guiScale」へニアレスト焼き直しし、1:1 で描く。
        // こうするとスケーリングが起きず (テクセル=画面ピクセル)、GUI 描画経路の線形補間に関わらず鮮明。
        int scale = Math.max(1, (int) Math.round(
            net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale()));
        net.minecraft.resources.ResourceLocation crisp =
            PackButtonTextureCache.getCrisp(m.packName(), m.buttonTexture(), BTN_W * scale, BTN_H * scale);
        if (crisp != null) {
            int tw = BTN_W * scale;
            int th = BTN_H * scale;
            g.blit(crisp, left, top, BTN_W, BTN_H, 0.0F, 0.0F, tw, th, tw, th);
        } else {
            String name = safe(m.displayName()).isBlank() ? m.id() : m.displayName();
            g.drawString(font, name, left + 4, top + (BTN_H - 8) / 2, 0xFFFFFFFF, false);
        }
        g.renderOutline(left, top, BTN_W, BTN_H, selected ? 0xFFFFFFFF : 0x55FFFFFF);
    }

    private void drawScrollbar(GuiGraphics g) {
        int x = scrollbarX();
        int top = 8, bottom = height - 8;
        g.fill(x, top, x + SCROLLBAR_W, bottom, 0xFF303030);
        if (filtered.size() <= 1) return;
        int trackH = bottom - top;
        int thumbH = Math.max(16, trackH / Math.max(1, filtered.size()));
        int thumbY = top + (int) ((long) currentScroll * (trackH - thumbH) / (filtered.size() - 1));
        g.fill(x, thumbY, x + SCROLLBAR_W, thumbY + thumbH, 0xFF6A9AE0);
        g.renderOutline(x, thumbY, SCROLLBAR_W, thumbH, 0xFFFFFFFF);
    }

    // ---- 入力 ----
    private int itemIndexAt(double mx, double my) {
        if (mx < LIST_LEFT || mx >= LIST_LEFT + BTN_W) return -1;
        int cy = centerY();
        int listBottom = height - 30;
        for (int i = 0; i < filtered.size(); i++) {
            int y = cy + BTN_H * (i - currentScroll);
            if (y >= listBottom) break;
            if (my >= y && my < y + BTN_H) return i;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int x = scrollbarX();
            if (mx >= x && mx < x + SCROLLBAR_W && my >= 8 && my < height - 8) {
                draggingScrollbar = true;
                scrollTo(scrollbarValue(my));
                return true;
            }
            int idx = itemIndexAt(mx, my);
            if (idx >= 0) {
                selectedId = filtered.get(idx).id();   // クリックした時だけ選択する
                setFocused(null);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingScrollbar && button == 0) {
            scrollTo(scrollbarValue(my));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && draggingScrollbar) { draggingScrollbar = false; return true; }
        return super.mouseReleased(mx, my, button);
    }

    private int scrollbarValue(double my) {
        int top = 8, bottom = height - 8;
        double frac = (my - top) / (double) Math.max(1, bottom - top);
        return (int) Math.round(frac * Math.max(0, filtered.size() - 1));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        // 一覧側 (入力群より左) はホイールでスクロール。
        if (!filtered.isEmpty() && mx < clusterLeftX) {
            scrollTo(currentScroll - (int) Math.signum(sy));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (getFocused() instanceof EditBox) {
            boolean r = super.keyPressed(key, scan, mods);
            refreshSearch();
            return r;
        }
        switch (key) {
            case GLFW.GLFW_KEY_HOME -> { scrollTo(0); return true; }
            case GLFW.GLFW_KEY_END -> { scrollTo(filtered.size() - 1); return true; }
            case GLFW.GLFW_KEY_PAGE_UP -> { scrollTo(currentScroll - Math.max(1, (height - 16) / BTN_H)); return true; }
            case GLFW.GLFW_KEY_PAGE_DOWN -> { scrollTo(currentScroll + Math.max(1, (height - 16) / BTN_H)); return true; }
            case GLFW.GLFW_KEY_UP -> { scrollTo(currentScroll - 1); return true; }
            case GLFW.GLFW_KEY_DOWN -> { scrollTo(currentScroll + 1); return true; }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> { onDone(); return true; }
            default -> { }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        boolean r = super.charTyped(c, mods);
        refreshSearch();
        return r;
    }

    private void refreshSearch() {
        if (searchField != null && !searchField.getValue().equals(lastSearch)) {
            lastSearch = searchField.getValue();
            rebuildFiltered();
        }
    }

    /** 表示位置(currentScroll)だけ動かす。選択(selectedId)は変えない = スクロールで選択しない。 */
    private void scrollTo(int idx) {
        if (filtered.isEmpty()) { currentScroll = 0; return; }
        currentScroll = Mth.clamp(idx, 0, filtered.size() - 1);
    }

    private int indexOf(String id) {
        if (id == null) return -1;
        for (int i = 0; i < filtered.size(); i++) {
            if (filtered.get(i).id().equals(id)) return i;
        }
        return -1;
    }

    /**
     * 決定/キャンセルの後に戻る画面。null なら従来どおりプレイ画面へ戻る。
     *
     * <p>★{@link #onDone()} は「選択を通知 → onClose()」の順なので、通知の中で
     * 画面を開いても直後に閉じられてしまう。戻り先はここに持たせて onClose に決めさせる。
     */
    private java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> returnScreen;

    /** 決定/キャンセルの後にこの画面へ戻る (編成の編集画面などから使う)。 */
    public ModelSelectScreen withReturnScreen(
            java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> factory) {
        this.returnScreen = factory;
        return this;
    }

    @Override
    public void onClose() {
        java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> factory = this.returnScreen;
        this.returnScreen = null;
        if (factory != null && this.minecraft != null) {
            net.minecraft.client.gui.screens.Screen next = factory.get();
            if (next != null) {
                this.minecraft.setScreen(next);
                return;
            }
        }
        super.onClose();
    }

    private void onDone() {
        if (selectedId == null || filtered.isEmpty()) { onClose(); return; }
        int color = parseColor(colorField.getValue(), initialColor);
        onSelected.accept(new SelectionResult(
            selectedId,
            dataMapField == null ? "" : dataMapField.getValue(),
            nameField == null ? "" : nameField.getValue(),
            color));
        onClose();
    }

    private static int parseColor(String s, int fallback) {
        if (s == null) return fallback;
        s = s.trim();
        if (s.isEmpty()) return fallback;
        try {
            if (s.startsWith("0x") || s.startsWith("0X")) return Integer.parseInt(s.substring(2), 16) & 0xFFFFFF;
            if (s.startsWith("#")) return Integer.parseInt(s.substring(1), 16) & 0xFFFFFF;
            return Integer.parseInt(s, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }


    @Override
    public void removed() {
        // 画面を閉じたら HUD 表示 / ミニマップを元に戻す。
        if (minecraft != null) minecraft.options.hideGui = prevHideGui;
        com.portofino.realtrainmodunofficial.client.JourneyMapCompat.setSuppressed(false);
        super.removed();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
