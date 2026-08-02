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

        /** 使い回す記録 (通常パス / 発光パス)。 */
        private static final ThreadLocal<GLRecorder> SCRATCH0 = ThreadLocal.withInitial(GLRecorder::new);
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
                return renderInner(be, partialTick, poseStack, buffer, packedLight, packedOverlay, model);
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
