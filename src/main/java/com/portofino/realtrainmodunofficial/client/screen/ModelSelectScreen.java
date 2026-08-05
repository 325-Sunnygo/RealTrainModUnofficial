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

    // 本家 GuiSelectModel / GuiButtonSelectModel の数値そのまま。
    /** 本家 GuiButtonSelectModel: ボタンは 160×32。 */
    private static final int BTN_W = 160;
    private static final int BTN_H = 32;
    /** 本家 resetModelList: ボタン x=10。 */
    private static final int LIST_LEFT = 10;
    /** 本家 drawScrollBar: つまみは 16×16 (右端 16px がクリック域)。 */
    private static final int SCROLLBAR_W = 16;
    private static final int FIELD_H = 20;
    /** 本家 ButtonBlue (rtm:textures/gui/button_blue.png)。UV は 1/512 規約。 */
    private static final net.minecraft.resources.ResourceLocation BUTTON_BLUE =
        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("rtm", "textures/gui/button_blue.png");

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
    /** 本家: スクロールバーのクリック域は右端 16px (width-16 〜 width)。 */
    private int scrollbarX() { return width - SCROLLBAR_W; }
    private int listRight() { return LIST_LEFT + BTN_W; }
    /** 本家 resetModelList: i0 = height/2 - 16 (=BTN_H/2)。 */
    private int centerY() { return height / 2 - BTN_H / 2; }


    @Override
    protected void init() {
        // 選択画面を開いている間は HUD (ホットバー/手/照準など) を全て隠す (F1相当)。
        this.prevHideGui = minecraft != null && minecraft.options.hideGui;
        if (minecraft != null) minecraft.options.hideGui = true;
        // JourneyMap は hideGui を無視するので、ミニマップを一時的にオフにする。
        com.portofino.realtrainmodunofficial.client.JourneyMapCompat.setSuppressed(true);

        // 本家 initGui の座標そのまま:
        //   nameField   (width-205,  5, 120x20) "Custom Name"
        //   argField    (width-205, 30, 100x20) "Custom Parameters" (RTMU では DataMap)
        //   searchField (width-80,   5,  60x20) "Search Box"
        //   colorButton (width-80,  30,  40x20) + 色見本 (width-40,30)-(width-20,50)
        this.clusterLeftX = width - 205;
        nameField = addBox(width - 205, 5, 120, "Custom Name", initialName);
        searchField = addBox(width - 80, 5, 60, "Search", "");
        dataMapField = addBox(width - 205, 30, 100, "DataMap", initialDataMapValue);
        colorField = addBox(width - 80, 30, 40, "Color", String.format("0x%06X", initialColor & 0xFFFFFF));

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
        // 本家 drawDefaultBackground と同じ暗いグラデーションを一番下に一度だけ敷く。
        // renderBackground には置かない — super.render がそれを再度呼び、ボタンの上に黒が
        // 重なってボタンだけ暗くなるため。
        g.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);

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

        // 本家 drawColorPalet: (width-40,30)-(width-20,50) に選択色を塗る
        if (colorField != null) {
            int c = parseColor(colorField.getValue(), initialColor);
            g.fill(width - 40, 30, width - 20, 50, 0xFF000000 | (c & 0xFFFFFF));
        }

        // 本家: ホバー中のモデルを右手に浮かべる (何も乗っていなければ選択中を出す)
        renderPreview(g, mouseX, mouseY);

        super.render(g, mouseX, mouseY, pt);

        if (filtered.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("screen.realtrainmodunofficial.no_models"),
                LIST_LEFT + BTN_W / 2, cy + 12, 0xAAAAAA);
        }
    }


    // ============================================================ モデルプレビュー
    // 本家 GuiSelectModel.renderModel + ModelSet*Client.renderModelInGui の移植。
    // 本家は「一覧のボタンにマウスを乗せている間だけ」画面右手にモデルを浮かべる。

    /** 本家 GuiButtonSelectModel.preRenderModelInGui: gluPerspective(80, 1.0, 5, 500)。 */
    private static final float PREVIEW_FOV_DEG = 80.0F;
    private static final float PREVIEW_NEAR = 5.0F;
    private static final float PREVIEW_FAR = 500.0F;

    private void renderPreview(GuiGraphics g, int mouseX, int mouseY) {
        //本家 GuiButtonSelectModel: hoverState==2 (マウスが乗っているボタン) のモデルを描く。
        //RTMU はクリック選択式なので、何も乗っていない間は選択中の物を出し続ける (運用上の補完)。
        ModelInfo info = null;
        int hoverIdx = itemIndexAt(mouseX, mouseY);
        if (hoverIdx >= 0) {
            info = filtered.get(hoverIdx);
        } else {
            for (ModelInfo m : filtered) {
                if (m.id().equals(selectedId)) {
                    info = m;
                    break;
                }
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

            // 本家 GuiButtonSelectModel.drawButton の変換そのまま:
            //   translate(5, 1, -18) → scale(10 / 最大寸法) → translate(0, -高さ/2, 0)
            //   → Y 回転 (12 秒で 1 周)。機種別の配置は本家に無い。
            PoseStack ps = new PoseStack();
            ps.translate(5.0F, 1.0F, -18.0F);
            float[] box = model.getSizeBox();
            float sx = box[3] - box[0];
            float sy = box[4] - box[1];
            float sz = box[5] - box[2];
            float maxSize = Math.max(sx, Math.max(sy, sz));
            float fit = 10.0F * (maxSize > 1.0E-4F ? 1.0F / maxSize : 1.0F);
            ps.scale(fit, fit, fit);
            ps.translate(0.0F, -sy * 0.5F, 0.0F);
            float rotation = (System.currentTimeMillis() % 12000L) * 360.0F / 12000.0F;
            ps.mulPose(Axis.YP.rotationDegrees(rotation));

            // 本家 RenderHelper.enableStandardItemLighting + GL_DEPTH_TEST
            Lighting.setupFor3DItems();
            // 平行投影で描いた GUI の深度値とは尺度が違うので、いったん深度を流す
            RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            RenderSystem.enableDepthTest();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            MultiBufferSource.BufferSource buf = Minecraft.getInstance().renderBuffers().bufferSource();
            Object previewEnt = (vehicleDef != null && (model.hasRenderScript() || vehicleDef.hasScript()))
                ? getOrCreatePreviewEntity(vehicleDef, model) : null;
            renderPreviewModel(model, vehicleDef, ps, buf, previewEnt, info.id());
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

    /**
     * 本家は model.render(null, cfg, 0/1, 0) で pass0/pass1 を描く。
     * RTMU はスクリプト車両の描画経路が別なので、そちらを優先し、無ければベイク経路で 2 パス描く。
     */
    private static void renderPreviewModel(MqoModelLoader.MqoModel model, VehicleDefinition vehicleDef,
                                           PoseStack poseStack, MultiBufferSource.BufferSource buffer,
                                           Object previewEnt, String previewDefinitionId) {
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
            // ★設置物 (信号/照明/踏切等) はパックのスクリプトが「どのランプを出すか」を決める。
            // ここでスクリプトを走らせないと、在ワールドでは点くライトがプレビューに出ない。
            // 本家 ModelSetMachineClient.renderModelInGui もスクリプト経由で描く。
            // entity は null。本家スクリプトは null を「アイテム/GUI 表示」として扱う
            // (RenderConnectablePole.js 等が明示的に分岐している)。
            if (vehicleDef == null) {
                try {
                    var objDef = com.portofino.realtrainmodunofficial.installedobject
                        .InstalledObjectRegistry.getById(previewDefinitionId);
                    if (objDef != null && objDef.getScriptPath() != null && !objDef.getScriptPath().isBlank()) {
                        var machineScripted = com.portofino.realtrainmodunofficial.client.render
                            .MachineScriptRenderers.get(objDef);
                        rendered = machineScripted != null && machineScripted.renderForPreview(
                            poseStack, buffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, model);
                    }
                } catch (Throwable ignored) {
                    rendered = false;
                }
            }
            if (!rendered && vehicleDef != null && vehicleDef.hasScript()) {
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
        // 本家 renderButtonOverlay: 選択中/ホバー中は button_blue の (0,32)-(160,64) を
        // 50% ブレンドで重ねる (UV は本家の 1/512 規約 → texW/H=512 で blit すると同じ切り出し)。
        if (selected || hovered) {
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            g.setColor(1.0F, 1.0F, 1.0F, 0.5F);
            g.blit(BUTTON_BLUE, left, top, BTN_W, BTN_H, 0.0F, 32.0F, 160, 32, 512, 512);
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        }
    }

    /** 本家 drawScrollBar: 白い 2px の軌道 (width-9〜width-7, 8〜height-8) + 16×16 の青つまみ。 */
    private void drawScrollbar(GuiGraphics g) {
        if (filtered.isEmpty()) {
            return;
        }
        g.fill(width - 9, 8, width - 7, height - 8, 0xFFFFFFFF);
        int thumbY;
        if (draggingScrollbar) {
            //本家: クリック中はマウス位置そのまま (8〜height-8 に丸めて -8)
            double my = lastDragY;
            thumbY = (int) (my < 8 ? 8 : (my >= height - 8 ? height - 8 : my)) - 8;
        } else if (filtered.size() > 1) {
            thumbY = currentScroll * (height - 16) / (filtered.size() - 1);
        } else {
            thumbY = 0;
        }
        //本家: button_blue の右端 16×16 (UV 0.9375〜1.0, 0〜0.0625 = 512px 換算で 480,0,32,32)
        g.blit(BUTTON_BLUE, width - 16, thumbY, 16, 16, 480.0F, 0.0F, 32, 32, 512, 512);
    }

    /** ドラッグ中のつまみ描画用に最後のマウス Y を覚える。 */
    private double lastDragY;

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
            //本家 drawScreen: クリック域は「右端 16px」全高
            if (mx >= scrollbarX() && mx < width) {
                draggingScrollbar = true;
                lastDragY = my;
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
            lastDragY = my;
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

    /** 本家: i1 = floor(mouseY * (size+1) / (height-16))。 */
    private int scrollbarValue(double my) {
        double y = my < 8 ? 8 : (my >= height ? height : my);
        return (int) Math.floor(y * (filtered.size() + 1) / (double) Math.max(1, height - 16));
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

    /** 1.21 のメニューぼかしを無効化 (本家 1.7.10 の GUI にぼかしは無い)。 */
    @Override
    protected void renderBlurredBackground(float partialTick) {
    }
}
