package com.portofino.realtrainmodunofficial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.client.model.MqoModelLoader;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectDefinition;
import jp.ngt.ngtlib.io.NGTFileLoader;
import jp.ngt.ngtlib.io.ScriptUtil;
import jp.ngt.ngtlib.renderer.GLRecorder;
import jp.ngt.ngtlib.renderer.model.Material;
import jp.ngt.ngtlib.renderer.model.ModelLoader;
import jp.ngt.ngtlib.renderer.model.PolygonModel;
import jp.ngt.ngtlib.renderer.model.TextureSet;
import jp.ngt.rtm.render.TileEntityPartsRenderer;
import jp.ngt.rtm.render.ModelObject;
import net.minecraft.client.renderer.MultiBufferSource;

import javax.script.ScriptEngine;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本家式の設置物 (踏切等) スクリプト描画。
 * ModelMachine_*.json の rendererPath (RenderCrossingGate01.js 等) を Nashorn で実行し、
 * renderClass (jp.ngt.rtm.render.MachinePartsRenderer) を毎フレーム記録→再生する。
 */
public final class MachineScriptRenderers {


    private static final Map<String, Scripted> CACHE = new ConcurrentHashMap<>();
    private static final Scripted INVALID = new Scripted(null, null, false);

    private MachineScriptRenderers() {
    }

    public static Scripted get(InstalledObjectDefinition def) {
        if (def == null || def.getScriptPath() == null || def.getScriptPath().isBlank()) {
            return null;
        }
        Scripted s = CACHE.computeIfAbsent(def.getId(), id -> create(def));
        return s == INVALID ? null : s;
    }

    private static Scripted create(InstalledObjectDefinition def) {
        try {
            byte[] bytes = NGTFileLoader.findAsset(def.getScriptPath());
            if (bytes == null) {
                RealTrainModUnofficial.LOGGER.warn("Machine script not found: {} ({})", def.getId(), def.getScriptPath());
                return INVALID;
            }
            // Shift_JIS のパックがあるため必ず PackTextDecoder を通す (生 UTF-8 だと構文ごと壊れる)
            String source = com.portofino.realtrainmodunofficial.util.PackTextDecoder.decodeText(bytes);

            // 機械/信号スクリプトも列車と同じフル・プレリュード + 互換リマップを使う。
            // 以前は GL11/GL12/MathHelper だけの最小プレリュードだったため、信号のブロック検知が
            // 使う Blocks (jp.ngt.mccompat.init.Blocks) や NGTMath 等が未定義で
            // "ReferenceError: Blocks is not defined" となりスクリプトが落ち、素モデルで全レンズが
            // 描画されていた ([[rtmu-block-detection-signals]])。
            ScriptEngine se = ScriptUtil.doScript(
                    com.portofino.realtrainmodunofficial.script.PackScriptSource.PRELUDE
                        + com.portofino.realtrainmodunofficial.script.PackScriptSource.prepare(source, def.getScriptPath()));
            Object rcName = se.get("renderClass");
            if (rcName == null) {
                return INVALID;
            }
            Class<?> rc = Class.forName(rcName.toString(), true, ScriptUtil.class.getClassLoader());
            Object instance;
            try {
                instance = rc.getConstructor(String[].class).newInstance(new Object[]{new String[0]});
            } catch (NoSuchMethodException e) {
                instance = rc.getDeclaredConstructor().newInstance();
            }
            // 踏切/改札 (MachinePartsRenderer) に加え、信号 (SignalPartsRenderer) も
            // 共通基底 TileEntityPartsRenderer なので受け入れる。
            if (!(instance instanceof TileEntityPartsRenderer renderer)) {
                RealTrainModUnofficial.LOGGER.warn("renderClass {} is not a TileEntityPartsRenderer ({})", rcName, def.getId());
                return INVALID;
            }
            renderer.setScript(se);
            //どのパックのスクリプトが落ちたか追えるように名前を持たせる
            renderer.scriptName = def.getId() + " (" + def.getScriptPath() + ")";
            se.put("renderer", renderer);

            // ModelObject: テクスチャ + モデルグラフ
            List<TextureSet> sets = new ArrayList<>();
            if (def.getTextureOverrides() != null) {
                for (String path : def.getTextureOverrides().values()) {
                    int meta = path.indexOf("|ptmeta=");
                    String clean = meta >= 0 ? path.substring(0, meta) : path;
                    sets.add(new TextureSet(new Material(new jp.ngt.mccompat.ResourceLocation("minecraft", clean))));
                }
            }
            if (sets.isEmpty()) {
                sets.add(new TextureSet(new Material(null)));
            }
            ModelObject mo = new ModelObject(sets.toArray(new TextureSet[0]));
            byte[] modelBytes = NGTFileLoader.findAsset("models/" + def.getModelFile());
            if (modelBytes == null) {
                modelBytes = NGTFileLoader.findAsset(def.getModelFile());
            }
            mo.model = modelBytes != null ? ModelLoader.parse(modelBytes, def.getModelFile()) : new PolygonModel();

            // getModelName は本家 config.getName 相当、つまり 素のモデル名
            // ("CrossingGate01R" / "Point01A") を返さなければならない。
            // 以前は def.getId ("crossing:pack名:CrossingGate01R") を入れていたため、
            // RenderCrossingGate01.js : getModelName.equals("CrossingGate01R")
            // RenderPoint01.js        : getModelName.equals("Point01A")
            // がどちらも常に false になり、右用の踏切が左用と同じ向きに描かれ、
            // 自動転轍機がモーターでなくレバーで描かれていた。
            jp.ngt.rtm.modelpack.cfg.TrainConfig cfg = new jp.ngt.rtm.modelpack.cfg.TrainConfig();
            cfg.trainName = def.getDisplayName();
            cfg.init();
            renderer.init(new jp.ngt.rtm.modelpack.modelset.ModelSetCompat(cfg), mo);

            return new Scripted(renderer, mo, source.contains("searchBlockAndMeta"));
        } catch (Throwable t) {
            RealTrainModUnofficial.LOGGER.warn("Failed to init machine script renderer for {}", def.getId(), t);
            return INVALID;
        }
    }

    public static final class Scripted {
        private final TileEntityPartsRenderer renderer;
        private final ModelObject modelObject;
        /** スクリプトが searchBlockAndMeta で真下のブロックから現示を決めるブロック検知型か。 */
        private final boolean blockDetection;

        /** 使い回す記録 (通常パス / 透過パス / 発光パス)。 */
        private static final ThreadLocal<GLRecorder> SCRATCH0 = ThreadLocal.withInitial(GLRecorder::new);
        private static final ThreadLocal<GLRecorder> SCRATCH1 = ThreadLocal.withInitial(GLRecorder::new);
        private static final ThreadLocal<GLRecorder> SCRATCH2 = ThreadLocal.withInitial(GLRecorder::new);

        // ★焼き込みキャッシュは ObjectMeshCache が持つ (本家 te.glLists 相当)。
        // 以前ここに valid/sig/rec を持つ Cache があったが、判定側が消えていて一度も
        // 読まれておらず、毎フレーム「記録して即再生」する純オーバーヘッドになっていた。

        Scripted(TileEntityPartsRenderer renderer, ModelObject modelObject, boolean blockDetection) {
            this.renderer = renderer;
            this.modelObject = modelObject;
            this.blockDetection = blockDetection;
        }

        /** ブロック検知型 (searchBlockAndMeta) の信号か。true なら RTMU の点灯 overlay を掛けない。 */
        public boolean isBlockDetection() {
            return this.blockDetection;
        }

        /** @return true = 描画を担当した */
        /**
         * モデル選択画面のプレビュー用。BlockEntity が無い状態でスクリプトを走らせる。
         *
         * <p>本家のスクリプトは entity が null のときを「アイテム/GUI 表示」として扱っており
         * (RenderConnectablePole.js が明示的に分岐している)、ランプ等は既定の見た目で描かれる。
         * これを通さないと、在ワールドでは点くライトがプレビューに出ない。
         */
        public boolean renderForPreview(PoseStack poseStack, MultiBufferSource buffer,
                                        int packedLight, int packedOverlay,
                                        MqoModelLoader.MqoModel model) {
            return renderInner(null, 0.0F, poseStack, buffer, packedLight, packedOverlay, model);
        }

        public boolean render(InstalledObjectBlockEntity be, float partialTick, PoseStack poseStack,
                              MultiBufferSource buffer, int packedLight, int packedOverlay,
                              MqoModelLoader.MqoModel model) {
            // replay 経路では renderNamedGroups に entity が渡らないため、遅延判定用に記録する
            // (色付きレンズ越しのレール/地形に色を乗せるにはガラスをレールの後に描く必要がある)。
            com.portofino.realtrainmodunofficial.client.DeferredTranslucentRenderer.setCurrentVehicle(be);
            try {
                // ActionParts の ID 解決用に一覧を記録する (色ピッキングは renderInner が行う)。
                com.portofino.realtrainmodunofficial.client.ActionPartsPicker.record(be, null, this.renderer);
                return renderInner(be, partialTick, poseStack, buffer, packedLight, packedOverlay, model);
            } finally {
                com.portofino.realtrainmodunofficial.client.DeferredTranslucentRenderer.setCurrentVehicle(null);
            }
        }

        /**
         * 本家 {@code RenderEntityInstalledObject} 準拠: レール上設置物エンティティ
         * (ATC / 列車検知器 / 車止め) のスクリプト描画。
         *
         * <p>本家は {@code MinecraftForgeClient.getRenderPass()} をそのまま渡し、モデルを
         * <b>pass 0 (NORMAL) と pass 1 (TRANSPARENT) の 2 回</b>描く。スクリプトはこの pass で
         * 分岐する。たとえば既定の列車検知器モデル Torii の {@code RenderTorii.js} は
         * NORMAL で鳥居本体 (hashira/nuki/shimagi/kasagi) だけを描き、TRANSPARENT で
         * 回転灯・時計・UI を色/α 付きで描く。
         *
         * <p>以前はエンティティ経路にスクリプトが無く、素のモデルを 1 回描くだけだったため、
         * 本来 TRANSPARENT 専用のパーツ (歯車・リング・UI 板) まで素で出て
         * 「鳥居に余計な物が付いて見える」状態になっていた。
         *
         * <p>エンティティは台数が少なく、しかもスクリプトが時刻で回す (回転灯) ため
         * メッシュ焼き込みはしない。本家も pass ごとに毎フレーム描いている。
         *
         * @return true = 描画を担当した (false なら呼び出し側が素モデルで描く)
         */
        public boolean renderEntity(jp.ngt.rtm.entity.EntityInstalledObject entity, float partialTick,
                                    PoseStack poseStack, MultiBufferSource buffer,
                                    int packedLight, int packedOverlay, MqoModelLoader.MqoModel model) {
            PolygonModel graph = this.modelObject != null ? this.modelObject.model : null;
            com.portofino.realtrainmodunofficial.client.DeferredTranslucentRenderer.setCurrentVehicle(entity);
            try {
                GLRecorder rec0 = SCRATCH0.get();
                rec0.clear();
                GLRecorder.activate(rec0);
                try {
                    this.renderer.currentMatId = 0;
                    this.renderer.render(entity, jp.ngt.rtm.render.RenderPass.NORMAL.id, partialTick);
                } finally {
                    GLRecorder.deactivate();
                }
                // スクリプトが何も描かずに落ちた場合は isEmpty ではなく hasGeometry で見る
                // (行列操作だけ残ると「描画済み」と誤判定して素モデルが出なくなる)。
                if (!rec0.hasGeometry()) {
                    return false;
                }

                GLRecorder rec1 = SCRATCH1.get();
                rec1.clear();
                GLRecorder.activate(rec1);
                try {
                    this.renderer.currentMatId = 0;
                    this.renderer.render(entity, jp.ngt.rtm.render.RenderPass.TRANSPARENT.id, partialTick);
                } finally {
                    GLRecorder.deactivate();
                    this.renderer.consumeScriptFailure();
                }

                // ★ActionParts 対話: 本家 PartsRenderer は描画中に当たりを取る。
                //   鳥居は ActionParts (monitor_main / key×10) を TRANSPARENT パスで描くため、
                //   両パスを 1 回の色ピッキングにまとめる。
                com.portofino.realtrainmodunofficial.client.ActionPartsPicker.record(entity, null, this.renderer);
                boolean pick = com.portofino.realtrainmodunofficial.client.ActionPartsPicker
                        .shouldCapture(entity, this.renderer);
                if (pick) {
                    com.portofino.realtrainmodunofficial.client.render.ActionPartsPickBuffer.begin(this.renderer);
                }
                try {
                    VehicleScriptRenderers.replay(rec0, poseStack, buffer, packedLight, packedOverlay, model, graph,
                            jp.ngt.rtm.render.RenderPass.NORMAL.id, null);
                    if (rec1.hasGeometry()) {
                        // pass1 = 透過。replay 側が window/α ブレンドを有効にする。
                        VehicleScriptRenderers.replay(rec1, poseStack, buffer, packedLight, packedOverlay, model, graph,
                                jp.ngt.rtm.render.RenderPass.TRANSPARENT.id, null);
                    }
                } finally {
                    if (pick) {
                        int pickedId = com.portofino.realtrainmodunofficial.client.render.ActionPartsPickBuffer.finish();
                        com.portofino.realtrainmodunofficial.client.ActionPartsPicker.setHoveredId(entity, pickedId);
                    }
                }
                return true;
            } finally {
                com.portofino.realtrainmodunofficial.client.DeferredTranslucentRenderer.setCurrentVehicle(null);
            }
        }

        private boolean renderInner(InstalledObjectBlockEntity be, float partialTick, PoseStack poseStack,
                              MultiBufferSource buffer, int packedLight, int packedOverlay,
                              MqoModelLoader.MqoModel model) {
            PolygonModel graph = this.modelObject != null ? this.modelObject.model : null;

            // ★スクリプトの実行回数は RTMU では制御しない (スクリプト任せ)。
            // 信号の点滅・踏切の警報・改札の矢印はスクリプトが自前で進めるので、
            // RTMU が知っている状態 (renderStateSignature) では動きを検出できない。

            // 本家 ModelObject.render の 2 段構成をそのまま再現する:
            // pass 0 = 通常テクスチャで本体
            // pass 2 = Light テクスチャ (***_light0.png) で発光部のみ・フルブライト
            // ★pass ごとに別レコーダーに録る。
            // ★毎フレーム new しない (設置物と同じ理由)。rec0/rec2 は同時に生きるので 2 本持つ。
            GLRecorder rec0 = SCRATCH0.get();
            rec0.clear();
            GLRecorder.activate(rec0);
            try {
                this.renderer.currentMatId = 0;
                this.renderer.render(be, 0, partialTick);
            } finally {
                GLRecorder.deactivate();
            }
            GLRecorder rec2 = SCRATCH2.get();
            rec2.clear();
            GLRecorder.activate(rec2);
            try {
                // 本家 GLHelper.setLightmapMaxBrightness 相当 (発光はフルブライト)
                rec2.brightness(0xF000F0);
                this.renderer.render(be, 2, partialTick);
            } finally {
                GLRecorder.deactivate();
                this.renderer.consumeScriptFailure();
            }
            // ★ isEmpty ではなく hasGeometry。スクリプトが何も描かずに落ちると行列操作だけが
            // 残って isEmpty==false になり、「描画済み」と誤判定して素のモデル描画が
            // スキップされ、設置物が透明になる。
            boolean drew = rec0.hasGeometry();
            if (!drew) {
                return false;
            }

            // ★色ピッキング: 本家 PartsRenderer は通常描画のたびに当たりを取る。
            //   焼き込み経路 (ObjectMeshCache) だと再生が焼き直し時にしか走らないため、
            //   ホバーが出なかった。ピッキング候補のときは焼かずに生 replay して当たりを取る。
            com.portofino.realtrainmodunofficial.client.ActionPartsHost pickHost =
                com.portofino.realtrainmodunofficial.client.ActionPartsPicker.hostOf(be);
            if (pickHost != null && com.portofino.realtrainmodunofficial.client.ActionPartsPicker.shouldCapture(be)) {
                com.portofino.realtrainmodunofficial.client.render.ActionPartsPickBuffer.begin(pickHost);
                try {
                    VehicleScriptRenderers.replay(rec0, poseStack, buffer, packedLight, packedOverlay, model, graph);
                    if (rec2.hasGeometry()) {
                        VehicleScriptRenderers.replay(rec2, poseStack, buffer, packedLight, packedOverlay, model, graph,
                                jp.ngt.rtm.render.RenderPass.LIGHT.id, null);
                    }
                } finally {
                    int pickedId = com.portofino.realtrainmodunofficial.client.render.ActionPartsPickBuffer.finish();
                    com.portofino.realtrainmodunofficial.client.ActionPartsPicker.setHoveredId(be, pickedId);
                }
                return true;
            }

            // ★視点依存の光エフェクト (normal 付き renderLightEffect) はカメラを動かすだけで
            //   形が変わる (本家も毎フレーム円錐を計算する)。これを焼き込みキーに混ぜると、
            //   ミラーボール/サーチライト等が「毎フレーム別物」になり、形 (pass0) まで焼けなくなる。
            //   → 視点依存があるときだけ、形 (rec0) を焼いて光 (rec2) は生で描く。
            if (rec2.isViewDependent()) {
                int key0 = jp.ngt.ngtlib.renderer.GLRecorder.mixKey(rec0.contentKey());
                boolean baked0 = ObjectMeshCache.draw(be, poseStack, key0, buf -> {
                    PoseStack local = new PoseStack();
                    VehicleScriptRenderers.replay(rec0, local, buf, packedLight, packedOverlay, model, graph);
                });
                if (!baked0) {
                    VehicleScriptRenderers.replay(rec0, poseStack, buffer, packedLight, packedOverlay, model, graph);
                }
                if (rec2.hasGeometry()) {
                    VehicleScriptRenderers.replay(rec2, poseStack, buffer, packedLight, packedOverlay, model,
                            graph, jp.ngt.rtm.render.RenderPass.LIGHT.id, null);
                }
                return true;
            }

            // ★本家 RailPartsRenderer.renderRailStatic と同じ流れ:
            // 内容キーが同じなら焼き直さず、GPU に置いた頂点をそのまま描く。
            // ★2 つのパスのキーは撹拌してから混ぜること。
            int key = 31 * (31 * jp.ngt.ngtlib.renderer.GLRecorder.mixKey(rec0.contentKey())
                    + jp.ngt.ngtlib.renderer.GLRecorder.mixKey(rec2.contentKey()))
                    + packedLight;
            boolean baked = ObjectMeshCache.draw(be, poseStack, key, buf -> {
                // 焼くときは単位行列で再生する (カメラ相対の pose で焼くと視点に付いてくる)。
                PoseStack local = new PoseStack();
                VehicleScriptRenderers.replay(rec0, local, buf, packedLight, packedOverlay, model, graph);
                if (rec2.hasGeometry()) {
                    VehicleScriptRenderers.replay(rec2, local, buf, packedLight, packedOverlay, model,
                            graph, jp.ngt.rtm.render.RenderPass.LIGHT.id, null);
                }
            });
            if (!baked) {
                // シェーダーパック使用中など、焼き込みを使えないときは従来どおり CPU で提出
                VehicleScriptRenderers.replay(rec0, poseStack, buffer, packedLight, packedOverlay, model, graph);
                if (rec2.hasGeometry()) {
                    VehicleScriptRenderers.replay(rec2, poseStack, buffer, packedLight, packedOverlay, model,
                            graph, jp.ngt.rtm.render.RenderPass.LIGHT.id, null);
                }
            }
            return true;
        }
    }
}
