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
 * pass 0 (通常) と pass 2 (発光: 警報灯) の 2 パスを本家どおり実行する。
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
            String source = new String(bytes, StandardCharsets.UTF_8);

            // 機械/信号スクリプトも列車と同じフル・プレリュード + 互換リマップを使う。
            // 以前は GL11/GL12/MathHelper だけの最小プレリュードだったため、信号のブロック検知が
            // 使う Blocks (jp.ngt.mccompat.init.Blocks) や NGTMath 等が未定義で
            // "ReferenceError: Blocks is not defined" となりスクリプトが落ち、素モデルで全レンズが
            // 描画されていた ([[rtmu-block-detection-signals]])。
            ScriptEngine se = ScriptUtil.doScript(
                    com.portofino.realtrainmodunofficial.script.PackScriptSource.PRELUDE
                        + com.portofino.realtrainmodunofficial.script.PackScriptSource.prepare(source));
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
            //踏切/改札 (MachinePartsRenderer) に加え、信号 (SignalPartsRenderer) も
            //共通基底 TileEntityPartsRenderer なので受け入れる。
            if (!(instance instanceof TileEntityPartsRenderer renderer)) {
                RealTrainModUnofficial.LOGGER.warn("renderClass {} is not a TileEntityPartsRenderer ({})", rcName, def.getId());
                return INVALID;
            }
            renderer.setScript(se);
            se.put("renderer", renderer);

            //ModelObject: テクスチャ + モデルグラフ
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

            //getModelName() は本家 config.getName() 相当、つまり <b>素のモデル名</b>
            //("CrossingGate01R" / "Point01A") を返さなければならない。
            //以前は def.getId() ("crossing:pack名:CrossingGate01R") を入れていたため、
            //  RenderCrossingGate01.js : getModelName().equals("CrossingGate01R")
            //  RenderPoint01.js        : getModelName().equals("Point01A")
            //がどちらも常に false になり、右用の踏切が左用と同じ向きに描かれ、
            //自動転轍機がモーターでなくレバーで描かれていた。
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

        //設置オブジェクトごとのスクリプト描画キャッシュ。信号機/看板は状態が変わらない間
        //Nashorn を再実行せず記録を再生する (172 個の毎フレーム実行が主なコストだった)。
        //シグネチャに含まれない時間依存要素の取りこぼしは REFRESH_FRAMES で救済する。
        private final java.util.Map<Object, Cache> caches =
                java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
        private static final int REFRESH_FRAMES = 8;

        private static final class Cache {
            boolean valid;
            boolean drew;
            long sig;
            int framesSinceRun;
            GLRecorder rec;
        }

        Scripted(TileEntityPartsRenderer renderer, ModelObject modelObject, boolean blockDetection) {
            this.renderer = renderer;
            this.modelObject = modelObject;
            this.blockDetection = blockDetection;
        }

        /** ブロック検知型 (searchBlockAndMeta) の信号か。true なら RTMU の点灯 overlay を掛けない。 */
        public boolean isBlockDetection() {
            return this.blockDetection;
        }

        /**
         * @return true = 描画を担当した
         */
        public boolean render(InstalledObjectBlockEntity be, float partialTick, PoseStack poseStack,
                              MultiBufferSource buffer, int packedLight, int packedOverlay,
                              MqoModelLoader.MqoModel model) {
            //replay 経路では renderNamedGroups に entity が渡らないため、遅延判定用に記録する
            //(色付きレンズ越しのレール/地形に色を乗せるにはガラスをレールの後に描く必要がある)。
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
            Cache c = this.caches.computeIfAbsent(be, k -> new Cache());
            long sig = be.renderStateSignature();
            PolygonModel graph = this.modelObject != null ? this.modelObject.model : null;

            //キャッシュヒット: 記録を再生するだけ (Nashorn 実行なし)。
            if (c.valid && c.sig == sig && c.framesSinceRun < REFRESH_FRAMES) {
                c.framesSinceRun++;
                if (!c.drew) {
                    return false;
                }
                VehicleScriptRenderers.replay(c.rec, poseStack, buffer, packedLight, packedOverlay, model, graph);
                return true;
            }

            //ミス: 実際にスクリプトを実行して記録し、キャッシュへ保存。
            GLRecorder rec = new GLRecorder();
            GLRecorder.activate(rec);
            try {
                this.renderer.currentMatId = 0;
                //本家: pass 0 (通常) → pass 2 (発光)。発光はフルブライトで描く。
                this.renderer.render(be, 0, partialTick);
                rec.brightness(0xF000F0);
                this.renderer.render(be, 2, partialTick);
            } finally {
                GLRecorder.deactivate();
                this.renderer.consumeScriptFailure();
            }
            //★ isEmpty ではなく hasGeometry。スクリプトが何も描かずに落ちると行列操作だけが
            //  残って isEmpty()==false になり、「描画済み」と誤判定して素のモデル描画が
            //  スキップされ、設置物が透明になる。
            boolean drew = rec.hasGeometry();
            c.valid = true;
            c.sig = sig;
            c.framesSinceRun = 0;
            c.drew = drew;
            c.rec = drew ? rec : null;
            if (!drew) {
                return false;
            }
            VehicleScriptRenderers.replay(rec, poseStack, buffer, packedLight, packedOverlay, model, graph);
            return true;
        }
    }
}
