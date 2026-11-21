package com.portofino.realtrainmodunofficial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.client.model.MqoModelLoader;
import com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition;
import com.portofino.realtrainmodunofficial.vehicle.VehiclePackLoader;
import jp.ngt.ngtlib.io.NGTFileLoader;
import jp.ngt.ngtlib.io.ScriptUtil;
import jp.ngt.ngtlib.renderer.GLRecorder;
import jp.ngt.ngtlib.renderer.model.Face;
import jp.ngt.ngtlib.renderer.model.GroupObject;
import jp.ngt.ngtlib.renderer.model.Material;
import jp.ngt.ngtlib.renderer.model.ModelLoader;
import jp.ngt.ngtlib.renderer.model.PolygonModel;
import jp.ngt.ngtlib.renderer.model.TextureSet;
import jp.ngt.ngtlib.renderer.model.Vertex;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.entity.train.util.TrainState;
import jp.ngt.rtm.render.ModelObject;
import jp.ngt.rtm.render.RenderPass;
import jp.ngt.rtm.render.VehiclePartsRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.script.ScriptEngine;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 本家式の車両スクリプト描画 (Phase 3 車両編)。 */
public final class VehicleScriptRenderers {

    private static final String PRELUDE = com.portofino.realtrainmodunofficial.script.PackScriptSource.PRELUDE;

    private static final Map<String, Scripted> CACHE = new ConcurrentHashMap<>();
    private static final Scripted INVALID = new Scripted(null, null, null, "<invalid>");

    public static void invalidateAll() {CACHE.clear();FLAT_GROUP_CACHE.clear();}
    private VehicleScriptRenderers() {
    }

    public static Scripted get(VehicleDefinition def) {
        if (def == null || !def.hasScript()) {
            return null;
        }
        Scripted cached = CACHE.get(def.getId());
        if (cached != null) {
            return cached == INVALID ? null : cached;
        }
        Scripted result;
        try {
            result = CACHE.computeIfAbsent(def.getId(), id -> create(def));
        } catch (Throwable t) {
            RealTrainModUnofficial.LOGGER.warn("Failed to compile vehicle script {} ({})",
                    def.getId(), def.getScriptPath(), t);
            CACHE.put(def.getId(), INVALID);
            result = INVALID;
        }
        return result == INVALID ? null : result;
    }

    /** 本家と同じく同期読み込み。非同期コンパイルは行わない。 */
    public static boolean isLoading(VehicleDefinition def) {
        return false;
    }

    private static Scripted create(VehicleDefinition def) {
        try {
            String source = VehiclePackLoader.readScriptContent(def);
            if (source == null || source.isBlank()) {
                throw new IllegalArgumentException("Vehicle script not readable: " + def.getScriptPath());
            }
            // KaizPatchX ModelObject: スクリプトをそのまま評価し、renderClass…
            String expanded = com.portofino.realtrainmodunofficial.script.PackScriptSource.prepare(source, def.getScriptPath());
            ScriptEngine se = ScriptUtil.doScript(PRELUDE + expanded, def.getScriptPath());
            Object rcName = se.get("renderClass");
            if (rcName == null) {
                RealTrainModUnofficial.LOGGER.warn("Vehicle script has no renderClass: {}", def.getId());
                return INVALID;
            }
            Class<?> rc = Class.forName(rcName.toString(), true, ScriptUtil.class.getClassLoader());
            Object instance = newRenderer(rc);
            if (!(instance instanceof VehiclePartsRenderer renderer)) {
                RealTrainModUnofficial.LOGGER.warn("renderClass {} is not a VehiclePartsRenderer ({})", rcName, def.getId());
                return INVALID;
            }
            renderer.setScript(se);
            renderer.scriptName = def.getId() + " (" + def.getScriptPath() + ")";
            se.put("renderer", renderer);

            RealTrainModUnofficial.LOGGER.info("[RTMU] vehicle script init: {} ({})", def.getId(), def.getScriptPath());
            ModelObject modelObject = buildModelObject(def);
            jp.ngt.rtm.modelpack.modelset.ModelSetBase<?> modelSet = def.isCarType()
                    ? jp.ngt.rtm.modelpack.ModelPackManager.INSTANCE.getVehicleModelSet(def.getId())
                    : jp.ngt.rtm.modelpack.ModelPackManager.INSTANCE.getTrainModelSet(def.getId());
            renderer.init(modelSet, modelObject);
            RealTrainModUnofficial.LOGGER.info("[RTMU] vehicle script init done: {}", def.getId());

            return new Scripted(renderer, se, modelObject, def.getId());
        } catch (Throwable t) {
            RealTrainModUnofficial.LOGGER.warn("Failed to init vehicle script renderer for {}", def.getId(), t);
            return INVALID;
        }
    }

    /** 描画中の車両モデルの素テクスチャパス集合 (小文字正規化)。 */
    private static java.util.Set<String> activeDefaultTexPaths = java.util.Set.of();

    // ---- 車両の半透明パス (本家 Forge 描画パス 1) の遅延描画 ---- ★本家 Re…
    private record DeferredTranslucent(GLRecorder rec, org.joml.Matrix4f pose, org.joml.Matrix3f normal,
                                       org.joml.Matrix4f modelViewAtCapture,
                                       int packedLight, int packedOverlay,
                                       MqoModelLoader.MqoModel model, PolygonModel graph,
                                       java.util.Set<String> excluded, Object entity,
                                       java.util.Set<String> defaultTexPaths) {
    }

    private static final java.util.List<DeferredTranslucent> TRANSLUCENT_QUEUE = new java.util.ArrayList<>();

    /** ワールド描画中か。モデル選択画面のプレビューなどワールド描画の外で Scripted.render… */
    private static boolean levelRenderActive;

    private record BakedAlpha(Matrix4f modelView, PoseStack pose,
            java.util.function.BiConsumer<PoseStack,MultiBufferSource> draw,
            jp.ngt.ngtlib.renderer.LegacyRenderState.Snapshot state, Vector3f[] lights,
            InteriorLighting.Snapshot interior) {}
    private static final java.util.List<BakedAlpha> BAKED_ALPHA_QUEUE = new java.util.ArrayList<>();

    /** Unscripted vehicles need the same terrain-before-… */
    public static boolean deferBakedAlpha(PoseStack pose,
            java.util.function.BiConsumer<PoseStack,MultiBufferSource> draw) {
        if (!levelRenderActive || com.portofino.realtrainmodunofficial.client.ShaderCompat.active()) return false;
        PoseStack copy=new PoseStack();
        copy.last().pose().set(pose.last().pose());
        copy.last().normal().set(pose.last().normal());
        BAKED_ALPHA_QUEUE.add(new BakedAlpha(new Matrix4f(
                com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix()),copy,draw,
                jp.ngt.ngtlib.renderer.LegacyRenderState.playback(),RailDrawQueue.currentShaderLights(),
                InteriorLighting.snapshot()));
        return true;
    }

    /** ワールド描画の開始 (RailDrawQueue が早い段階のフックで呼ぶ)。 */
    static void beginLevelRender() {
        levelRenderActive = true;
    }

    private static boolean enqueueTranslucent(GLRecorder rec, PoseStack poseStack, int packedLight, int packedOverlay,
                                           MqoModelLoader.MqoModel model, PolygonModel graph,
                                           java.util.Set<String> excluded, Object entity,
                                           java.util.Set<String> defaultTexPaths) {
        if (!levelRenderActive) {
            return false;
        }
        // ★シェーダーパック使用中は後回しにしない (設定ではなく自動)。
        if (com.portofino.realtrainmodunofficial.client.ShaderCompat.active()) {
            return false;
        }
        PoseStack.Pose pose = poseStack.last();
        TRANSLUCENT_QUEUE.add(new DeferredTranslucent(
            rec,
            new org.joml.Matrix4f(pose.pose()),
            new org.joml.Matrix3f(pose.normal()),
            // 積んだ時点のグローバル行列。再生時に違っていたら差を打ち消す (下の flush 参照)。
            new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix()),
            packedLight, packedOverlay, model, graph,
            excluded == null ? null : java.util.Set.copyOf(excluded),
            entity,
            defaultTexPaths));
        return true;
    }

    /** 積んでおいた車両の半透明パスを描く。RailDrawQueue がレールを描き終えた直後に呼ぶ。 */
    static void flushDeferredTranslucent() {
        levelRenderActive = false;
        if (TRANSLUCENT_QUEUE.isEmpty() && BAKED_ALPHA_QUEUE.isEmpty()) {
            return;
        }
        net.minecraft.client.renderer.MultiBufferSource.BufferSource buffer =
            net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource();
        try {
            // Flush each draw before restoring its captured glo…
            for (BakedAlpha d : BAKED_ALPHA_QUEUE) {
                var mv=com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
                Vector3f[] savedLights=RailDrawQueue.currentShaderLights();
                var savedInterior=InteriorLighting.snapshot();
                mv.pushMatrix();
                mv.set(d.modelView());
                com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
                try(var scope=jp.ngt.ngtlib.renderer.LegacyRenderState.use(d.state())) {
                    if(d.lights()!=null) com.mojang.blaze3d.systems.RenderSystem.setShaderLights(d.lights()[0],d.lights()[1]);
                    InteriorLighting.restore(d.interior());
                    d.draw().accept(d.pose(),buffer);
                    buffer.endBatch();
                } finally {
                    InteriorLighting.restore(savedInterior);
                    if(savedLights!=null) com.mojang.blaze3d.systems.RenderSystem.setShaderLights(savedLights[0],savedLights[1]);
                    mv.popMatrix();
                    com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
                }
            }
            for (DeferredTranslucent d : TRANSLUCENT_QUEUE) {
                // ★積んだ時点と再生時でグローバルの ModelView が違うと、その差だけ位置がずれる。
                org.joml.Matrix4f mvNow = new org.joml.Matrix4f(
                    com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix());
                org.joml.Matrix4f corrected = new org.joml.Matrix4f(d.pose());
                if (!mvNow.equals(d.modelViewAtCapture(), 1.0e-6F)) {
                    corrected = mvNow.invert(new org.joml.Matrix4f())
                        .mul(d.modelViewAtCapture())
                        .mul(d.pose());
                }
                PoseStack ps = new PoseStack();
                ps.last().pose().set(corrected);
                ps.last().normal().set(d.normal());
                activeDefaultTexPaths = d.defaultTexPaths();
                com.portofino.realtrainmodunofficial.client.DeferredTranslucentRenderer
                    .setCurrentVehicle(d.entity());
                try {
                    replay(d.rec(), ps, buffer, d.packedLight(), d.packedOverlay(),
                        d.model(), d.graph(), RenderPass.TRANSPARENT.id, d.excluded());
                } finally {
                    com.portofino.realtrainmodunofficial.client.DeferredTranslucentRenderer
                        .setCurrentVehicle(null);
                    activeDefaultTexPaths = java.util.Set.of();
                }
            }
            buffer.endBatch();
        } catch (Throwable t) {
            RealTrainModUnofficial.LOGGER.warn("Deferred vehicle translucent draw failed", t);
        } finally {
            TRANSLUCENT_QUEUE.clear();
            BAKED_ALPHA_QUEUE.clear();
        }
    }

    /** bind の元パスがモデルの素テクスチャなら true (=上書き解除)。 */
    static boolean isDefaultTexturePath(String rawPath) {
        return rawPath != null && activeDefaultTexPaths.contains(rawPath);
    }


    private static Object newRenderer(Class<?> rc) throws ReflectiveOperationException {
        try {
            return rc.getConstructor(String[].class).newInstance(new Object[]{new String[]{"true"}});
        } catch (NoSuchMethodException e) {
            return rc.getDeclaredConstructor().newInstance();
        }
    }

    private static ModelObject buildModelObject(VehicleDefinition def) {
        ModelObject mo = new ModelObject(new TextureSet[0]);
        // 車体モデルグラフ (CustomAnimator の setFacesFromParts 等が面を…
        try {
            String modelFile = def.getModelFile();
            if (modelFile != null && !modelFile.isBlank()) {
                byte[] bytes = NGTFileLoader.findAsset("models/" + modelFile);
                if (bytes == null) {
                    bytes = NGTFileLoader.findAsset(modelFile);
                }
                if (bytes != null) {
                    mo.model = ModelLoader.parse(bytes, modelFile,def.getModelAccuracy(),4);
                }
            }
        } catch (Exception e) {
        }
        if (mo.model == null) {
            mo.model = new PolygonModel();
        }
        mo.setTextures(jp.ngt.ngtlib.renderer.model.LegacyTextureLayout.create(mo.model, def.getTextureOverrides()));
        return mo;
    }

    public static final class Scripted {
        private final VehiclePartsRenderer renderer;
        private final ScriptEngine engine;
        private final ModelObject modelObject;
        /** ログ用の車両 ID。どの車両のスクリプトが落ちたか判らないと追えない。 */
        private final String defId;
        /** 既にログした失敗メッセージ。失敗の種類ごとに 1 回出す (車両ごと 1 回ではない)。 */
        private final java.util.Set<String> loggedFailures = new java.util.HashSet<>();

        // --- スクリプト描画結果のキャッシュ -----------------------------…
        private volatile int[] overlayMatIds;

        /** 1 パスぶんの記録 (通常パス or 発光パス or マテリアル別オーバーレイ)。 */
        private static final class CachedPass {
            final GLRecorder rec;
            final int pass;
            final Set<String> excluded;
            // 非 null = マテリアル別 tessellator オーバーレイ (方向幕等)。
            final ResourceLocation overlayTexture;
            CachedPass(GLRecorder rec, int pass, Set<String> excluded) {
                this(rec, pass, excluded, null);
            }
            CachedPass(GLRecorder rec, int pass, Set<String> excluded, ResourceLocation overlayTexture) {
                this.rec = rec;
                this.pass = pass;
                this.excluded = excluded;
                this.overlayTexture = overlayTexture;
            }
        }

        /** このモデルの素テクスチャパス集合 (小文字正規化)。復帰bindの判定に使う。 */
        private final java.util.Set<String> defaultTexPaths = new java.util.HashSet<>();

        // ★以前あった「車両をまたいで使い回す記録の置き場」は撤去した。

        Scripted(VehiclePartsRenderer renderer, ScriptEngine engine, ModelObject modelObject, String defId) {
            this.defId = defId;
            this.renderer = renderer;
            this.engine = engine;
            this.modelObject = modelObject;
            if (modelObject != null && modelObject.textures != null) {
                for (TextureSet set : modelObject.textures) {
                    if (set == null || set.material == null
                            || !(set.material.texture instanceof jp.ngt.mccompat.ResourceLocation rl)) {
                        continue;
                    }
                    String p = rl.func_110623_a();
                    if (p != null) {
                        defaultTexPaths.add(p.replace('\\', '/').replaceFirst("^/+", "")
                            .toLowerCase(java.util.Locale.ROOT));
                    }
                }
            }
        }

        /** 毎フレーム: スクリプト render を記録して再生する。 */
        public boolean render(Object entity, float partialTick, PoseStack poseStack,
                              MultiBufferSource buffer, int packedLight, int packedOverlay,
                              MqoModelLoader.MqoModel bodyModel) {
            // ガラス (半透明) を遅延バッファへ回す判定用に、描画中の車両を記録する。
            com.portofino.realtrainmodunofficial.client.DeferredTranslucentRenderer.setCurrentVehicle(entity);
            activeDefaultTexPaths = this.defaultTexPaths;
            try {
                return renderDispatch(entity, partialTick, poseStack, buffer, packedLight, packedOverlay, bodyModel);
            } finally {
                com.portofino.realtrainmodunofficial.client.DeferredTranslucentRenderer.setCurrentVehicle(null);
                activeDefaultTexPaths = java.util.Set.of();
            }
        }

        private boolean renderDispatch(Object entity, float partialTick, PoseStack poseStack,
                              MultiBufferSource buffer, int packedLight, int packedOverlay,
                              MqoModelLoader.MqoModel bodyModel) {
            // ★スクリプトの実行回数は RTMU では制御しない (スクリプト任せ)。
            return LocalSpaceBatch.run(poseStack, buffer,
                    (localPose, localBuffer) -> renderReal(entity, partialTick, localPose,
                            localBuffer, packedLight, packedOverlay, bodyModel, null));
        }


        private static boolean recordersIdentical(GLRecorder a, GLRecorder b) {
            List<GLRecorder.Cmd> ca = a.getCommands();
            List<GLRecorder.Cmd> cb = b.getCommands();
            if (ca.size() != cb.size()) {
                return false;
            }
            for (int i = 0; i < ca.size(); i++) {
                GLRecorder.Cmd x = ca.get(i);
                GLRecorder.Cmd y = cb.get(i);
                if (x.op != y.op
                        || Float.floatToIntBits(x.a) != Float.floatToIntBits(y.a)
                        || Float.floatToIntBits(x.b) != Float.floatToIntBits(y.b)
                        || Float.floatToIntBits(x.c) != Float.floatToIntBits(y.c)
                        || Float.floatToIntBits(x.d) != Float.floatToIntBits(y.d)
                        || !java.util.Objects.equals(x.name, y.name)
                        || !java.util.Objects.equals(x.state, y.state)
                        || !payloadIdentical(x.payload, y.payload)) {
                    return false;
                }
            }
            return true;
        }

        private static boolean payloadIdentical(Object p, Object q) {
            if (p == q) {
                return true;
            }
            if (p == null || q == null) {
                return false;
            }
            if (p instanceof GLRecorder.TessDraw tp) {
                return q instanceof GLRecorder.TessDraw tq
                        && tp.mode == tq.mode && java.util.Arrays.equals(tp.verts, tq.verts)
                        && java.util.Arrays.equals(tp.normals,tq.normals) && tp.additive==tq.additive && tp.cullFront==tq.cullFront && tp.useCurrentColor==tq.useCurrentColor;
            }
            // Quaternionf / Set<String> / ResourceLocation / Bl…
            return p.equals(q);
        }

        /** スクリプト描画の実処理 (本家 RenderVehicleBase.doRender と同じ)。 */
        private boolean renderReal(Object entity, float partialTick, PoseStack poseStack,
                                   MultiBufferSource buffer, int packedLight, int packedOverlay,
                                   MqoModelLoader.MqoModel bodyModel, List<CachedPass> sink) {
            var window=net.minecraft.client.Minecraft.getInstance().getWindow();
            var modelView=new Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix()).mul(poseStack.last().pose());
            try(var selection=jp.ngt.ngtlib.renderer.LegacySelection.context(modelView,
                    com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix(),this.renderer.getPolygonModel(),
                    window==null?1:window.getWidth(),window==null?1:window.getHeight())) {
                return renderRealWithSelection(entity,partialTick,poseStack,buffer,packedLight,packedOverlay,bodyModel,sink);
            }
        }

        private boolean renderRealWithSelection(Object entity, float partialTick, PoseStack poseStack,
                                   MultiBufferSource buffer, int packedLight, int packedOverlay,
                                   MqoModelLoader.MqoModel bodyModel, List<CachedPass> sink) {
            // ActionParts 対話用: モデルローカル→カメラの行列を記録する (本家 PICK パスの…
            com.portofino.realtrainmodunofficial.client.ActionPartsPicker.record(entity,
                new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix())
                    .mul(poseStack.last().pose()),
                this.renderer);
            // 本家 RenderVehicleBase.doRender: 通常描画 (RenderPass.N…
            GLRecorder normal = record(entity, RenderPass.NORMAL.id, partialTick);
            // ★ isEmpty ではなく hasGeometry で判定する。
            if (normal == null || !normal.hasGeometry()) {
                return false;
            }
            PolygonModel graph = this.modelObject != null ? this.modelObject.model : null;
            // 本家 ResourceState.exclusionParts: スクリプトが描画から外したパーツ…
            java.util.Set<String> excluded = exclusionPartsOf(entity);
            // ★発光パス (RenderPass.LIGHT) を先に記録する。
            GLRecorder preLight0 = recordLight0IfNeeded(entity, partialTick, bodyModel);
            java.util.Set<String> coveredGroups = groupsDrawnBy(preLight0);
            MqoModelLoader.setLightCoveredGroups(coveredGroups);
            // ★焼き込み (VBO キャッシュ) はしない。本家 1.7.10 も車両は毎フレーム投げ直している。
            boolean pick = com.portofino.realtrainmodunofficial.client.ActionPartsPicker
                .shouldCapture(entity, this.renderer);
            if (pick) {
                com.portofino.realtrainmodunofficial.client.render.ActionPartsPickBuffer.begin(this.renderer);
            }
            try {
                replay(normal, poseStack, buffer, packedLight, packedOverlay, bodyModel, graph,
                        RenderPass.NORMAL.id, excluded);
            } finally {
                MqoModelLoader.setLightCoveredGroups(null);
            }
            if (pick) {
                // 描いた色を画面中央ピクセルから読み、当たっているパーツ ID を確定する。
                int pickedId = com.portofino.realtrainmodunofficial.client.render.ActionPartsPickBuffer.finish();
                com.portofino.realtrainmodunofficial.client.ActionPartsPicker.setHoveredId(entity, pickedId);
            }
            if (sink != null) {
                // excluded はエンティティ内部のライブ集合なので、キャッシュにはスナップショットを残す。
                sink.add(new CachedPass(normal, RenderPass.NORMAL.id, excluded == null ? null : Set.copyOf(excluded)));
            }
            // 軽量化「遠方車両のライト・方向幕を省略」: 車体 (上の NORMAL パス) は描いたので、 追…
            renderMaterialOverlays(entity, partialTick, poseStack, buffer, packedLight, packedOverlay,
                    bodyModel, sink, RenderPass.NORMAL.id);
            // ★発光パス (2-4) は半透明パス (1=窓) より先に描く。
            flushBatch(buffer);
            renderBodyLight(entity, partialTick, poseStack, buffer, packedLight, packedOverlay,
                    bodyModel, graph, sink, preLight0);
            flushBatch(buffer);
            // ★半透明パス (TRANSPARENT=1)。
            GLRecorder transparent = record(entity, RenderPass.TRANSPARENT.id, partialTick, true);
            if (transparent != null && transparent.hasGeometry()) {
                // ★ここでは描かず、レールを描き終えたあとへ回す (本家 Forge 描画パス 1 と同じ位置)。
                if (!enqueueTranslucent(transparent, poseStack, packedLight, packedOverlay, bodyModel, graph,
                        excluded, entity, this.defaultTexPaths)) {
                    // ワールド描画の外 (モデル選択画面のプレビュー等) は従来どおりその場で描く。
                    replay(transparent, poseStack, buffer, packedLight, packedOverlay, bodyModel, graph,
                            RenderPass.TRANSPARENT.id, excluded);
                }
                if (sink != null) {
                    sink.add(new CachedPass(transparent, RenderPass.TRANSPARENT.id,
                            excluded == null ? null : Set.copyOf(excluded)));
                }
            }
            return true;
        }

        /** 本家の即時描画と同じ順序にするため、パスの区切りでバッファを吐き出す。 */
        /** 車両描画の途中で BufferSource.endBatch を呼ぶと、 そのフレームでバッファに… */
        private static final boolean FLUSH_BETWEEN_PASSES = true;

        private static void flushBatch(MultiBufferSource buffer) {
            if (!FLUSH_BETWEEN_PASSES) {
                return;
            }
            if (buffer instanceof MultiBufferSource.BufferSource source) {
                source.endBatch();
            }
        }

        /** 発光パス (i==0 = RenderPass.LIGHT) を先行記録する。 */
        private GLRecorder recordLight0IfNeeded(Object entity, float partialTick,
                                                MqoModelLoader.MqoModel bodyModel) {
            if (bodyModel == null) {
                return null;
            }
            if (!(bodyModel.hasLightOption() || bodyModel.hasEmissiveBatches())) {
                return null;
            }
            if (!(entity instanceof EntityTrainBase train)) {
                return null;
            }
            int mode = train.getTrainStateData(TrainState.TrainStateType.State_Light.id);
            // 本家 RenderVehicleBase.renderBodyLight の i==0: doRe…
            if (mode != 0 && mode != 1) {
                return null;
            }
            GLRecorder rec = record(entity, RenderPass.LIGHT.id, partialTick);
            if (rec == null) {
                return null;
            }
            if (!rec.hasGeometry()) {
                return null;
            }
            return rec;
        }

        /** 記録の中で実際に描画されたグループ名 (正規化済み)。 */
        private static java.util.Set<String> groupsDrawnBy(GLRecorder rec) {
            if (rec == null) {
                return null;
            }
            java.util.Set<String> out = new java.util.HashSet<>();
            for (GLRecorder.Cmd c : rec.getCommands()) {
                if ((c.op == GLRecorder.Op.RENDER_PARTS || c.op == GLRecorder.Op.RENDER_GROUPS)
                        && c.payload instanceof java.util.Set<?> names) {
                    for (Object n : names) {
                        out.add(String.valueOf(n));
                    }
                }
            }
            return out.isEmpty() ? null : out;
        }

        private static java.util.Set<String> exclusionPartsOf(Object entity) {
            if (entity instanceof jp.ngt.rtm.entity.vehicle.EntityVehicleBase<?> vehicle) {
                jp.ngt.rtm.modelpack.state.ResourceState state = vehicle.getResourceState();
                if (state != null) {
                    return state.getExclusionParts();
                }
            }
            return null;
        }

        /** 本家 RenderVehicleBase.renderBodyLight の忠実移植。 */
        private void renderBodyLight(Object entity, float partialTick, PoseStack poseStack,
                                     MultiBufferSource buffer, int packedLight, int packedOverlay,
                                     MqoModelLoader.MqoModel bodyModel, PolygonModel graph, List<CachedPass> sink,
                                     GLRecorder preLight0) {
            if (!(entity instanceof EntityTrainBase train)) {
                return;
            }
            // ★本家 renderVehicleMain は `if (modelSet.modelObj.li…
            if (bodyModel == null || !(bodyModel.hasLightOption() || bodyModel.hasEmissiveBatches())) {
                return;
            }
            int dir = train.getTrainDirection();
            int mode = train.getTrainStateData(TrainState.TrainStateType.State_Light.id);
            boolean frontEmpty = train.getConnectedTrain(dir) == null;
            boolean backEmpty = train.getConnectedTrain(1 - dir) == null;
            // 本家 renderBodyLight の doRender。
            jp.ngt.rtm.modelpack.cfg.TrainConfig cfg = train.getConfig();
            boolean single = cfg != null && cfg.isSingleTrain && frontEmpty && backEmpty;

            for (int i = 0; i < 3; i++) {
                boolean doRender = switch (i) {
                    case 0 -> mode == 0 || mode == 1;
                    case 1 -> single ? (mode == 1 && dir == 0) || mode == 2
                                     : (mode == 1 && frontEmpty) || mode == 2;
                    default -> single ? (mode == 1 && dir == 1) || mode == 2
                                      : (mode == 1 && !frontEmpty && backEmpty) || mode == 2;
                };
                if (!doRender) {
                    continue;
                }
                int pass = RenderPass.LIGHT.id + i;
                // i==0 は NORMAL より前に記録済み。二重にスクリプトを走らせない。
                GLRecorder rec = (i == 0 && preLight0 != null) ? preLight0 : record(entity, pass, partialTick);
                // スクリプトが発光パスの途中で落ちても、そこまでに描いたライトは活かす。
                if (rec == null || !rec.hasGeometry()) {
                    continue;
                }
                // 発光パスも本体と同じ経路で描く。本家の pass0 → 発光 → 半透明 の順序は flushBa…
                replay(rec, poseStack, buffer, packedLight, packedOverlay, bodyModel, graph, pass);
                if (sink != null) {
                    sink.add(new CachedPass(rec, pass, null));
                }
                // ★本家 renderWithTexture と同じく、ライトパスでもマテリアル別に
                // スクリプトを呼ぶ (MatId ゲートがライトパスでも機能する)。
                renderMaterialOverlays(entity, partialTick, poseStack, buffer, packedLight, packedOverlay,
                        bodyModel, sink, pass);
            }
        }

        /** 完全に静止した車両か? = 速度0 かつ ドア/パンタが端点 (アニメ中でない)。 */
        private static boolean isFullyStatic(Object entity) {
            if (!(entity instanceof EntityTrainBase train)) {
                return false;
            }
            if (train.getSpeed() != 0.0F) {
                return false;
            }
            if (entity instanceof jp.ngt.rtm.entity.vehicle.EntityVehicleBase<?> v) {
                if (!atEndpoint(v.doorMoveL, jp.ngt.rtm.entity.vehicle.EntityVehicleBase.MAX_DOOR_MOVE)
                        || !atEndpoint(v.doorMoveR, jp.ngt.rtm.entity.vehicle.EntityVehicleBase.MAX_DOOR_MOVE)
                        || !atEndpoint(v.pantograph_F, jp.ngt.rtm.entity.vehicle.EntityVehicleBase.MAX_PANTOGRAPH_MOVE)
                        || !atEndpoint(v.pantograph_B, jp.ngt.rtm.entity.vehicle.EntityVehicleBase.MAX_PANTOGRAPH_MOVE)) {
                    return false;
                }
            }
            return true;
        }

        private static boolean atEndpoint(int value, int max) {
            return value <= 0 || value >= max;
        }


        /** 描画結果を左右する車両状態のシグネチャ。これが変わったら即座に描き直す。 */
        private long signature(Object entity) {
            EntityTrainBase train = (EntityTrainBase) entity;
            long h = 1125899906842597L;
            h = 31L * h + Float.floatToIntBits(train.wheelRotationR);
            h = 31L * h + Float.floatToIntBits(train.wheelRotationL);
            int dir = train.getTrainDirection();
            h = 31L * h + dir;
            h = 31L * h + train.getTrainStateData(TrainState.TrainStateType.State_Light.id);
            h = 31L * h + train.getTrainStateData(TrainState.TrainStateType.State_Destination.id);
            h = 31L * h + (train.getConnectedTrain(dir) == null ? 0 : 1);
            h = 31L * h + (train.getConnectedTrain(1 - dir) == null ? 0 : 1);
            if (entity instanceof jp.ngt.rtm.entity.vehicle.EntityVehicleBase<?> v) {
                h = 31L * h + v.doorMoveL;
                h = 31L * h + v.doorMoveR;
                h = 31L * h + v.pantograph_F;
                h = 31L * h + v.pantograph_B;
            }
            Set<String> ex = exclusionPartsOf(entity);
            h = 31L * h + (ex == null ? 0 : ex.hashCode());
            return h;
        }

        /** スクリプトの render(entity, pass, partialTick) を 1 パスぶん… */

        // -------------------------------------------------…

        /** 1 tick を何分割まで記録し直すか。 車両の描画は毎フレーム Nashorn の render… */
        private static final int RECORD_QUANTA_PER_TICK = 4;

        /** 1 スロット = ある (パス, マテリアル) の記録と、それを録った時刻。 */
        private static final class RecordSlot {
            GLRecorder rec;
            long quantum = Long.MIN_VALUE;
            boolean failed;
        }

        /** エンティティごとの記録。弱参照なので、車両が消えれば一緒に消える。 */
        private final java.util.WeakHashMap<Object, java.util.Map<Integer, RecordSlot>> recordCache =
            new java.util.WeakHashMap<>();

        /** いまの「記録時刻」。tick とフレーム内位置をまとめて量子化した値。 */
        private static long recordQuantum(Object entity, float partialTick) {
            long gameTime;
            if (entity instanceof net.minecraft.world.entity.Entity e && e.level() != null) {
                gameTime = e.level().getGameTime();
            } else {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.level == null) {
                    return Long.MIN_VALUE;
                }
                gameTime = mc.level.getGameTime();
            }
            float clamped = partialTick < 0.0F ? 0.0F : (partialTick > 1.0F ? 1.0F : partialTick);
            return gameTime * RECORD_QUANTA_PER_TICK + (long) (clamped * RECORD_QUANTA_PER_TICK);
        }

        /** 記録を取り直すか、前のものを使い回すかを決める。 */
        private GLRecorder recordCached(Object entity, int pass, int matId, float partialTick) {
            long quantum = recordQuantum(entity, partialTick);
            // エンティティが無い (モデル選択画面のプレビュー等) / 時刻が取れない場合は 使い回さない。
            if (entity == null || quantum == Long.MIN_VALUE) {
                return recordFresh(entity, pass, matId, partialTick, new GLRecorder());
            }
            java.util.Map<Integer, RecordSlot> perPass =
                this.recordCache.computeIfAbsent(entity, k -> new java.util.HashMap<>());
            int key = (pass << 16) | (matId & 0xFFFF);
            RecordSlot slot = perPass.computeIfAbsent(key, k -> new RecordSlot());
            if (slot.quantum == quantum) {
                // 同じ量子の中: 何度呼ばれても結果は同じなので録り直さない
                return slot.failed ? null : slot.rec;
            }
            if (slot.rec == null) {
                slot.rec = new GLRecorder();
            }
            GLRecorder result = recordFresh(entity, pass, matId, partialTick, slot.rec);
            slot.quantum = quantum;
            slot.failed = result == null;
            return result;
        }

        /** 実際にスクリプトを走らせて target へ録る。 */
        private GLRecorder recordFresh(Object entity, int pass, int matId, float partialTick,
                                       GLRecorder target) {
            long secStart = com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.sec();
            target.clear();
            target.state().baseline(jp.ngt.ngtlib.renderer.GL11Facade.GL_BLEND,pass!=0);
            target.state().alpha(pass==0?514:pass==1?513:516,pass<2?1.0F:0.1F);
            target.state().depthWrite(pass!=1);
            target.state().baseline(jp.ngt.ngtlib.renderer.GL11Facade.GL_LIGHTING,pass<2);
            target.state().baseline(jp.ngt.ngtlib.renderer.GL11Facade.GL_CULL_FACE,MqoModelLoader.shouldCullModelFaces(entity));
            GLRecorder.activate(target);
            try {
                this.renderer.currentMatId = matId;
                this.renderer.currentPass = pass;
                this.renderer.render(entity, pass, partialTick);
            } catch (Throwable t) {
                // 以前は車両ごとに 1 回しかログしなかったため、起動直後の別要因で 1 回出ると 以後の失敗が全て…
                String logKey = pass + "|" + String.valueOf(t);
                if (matId == 0 && this.loggedFailures.size() < 32 && this.loggedFailures.add(logKey)) {
                    RealTrainModUnofficial.LOGGER.warn("[RTMU] スクリプト描画に失敗: {} (pass={})",
                            this.defId, pass, t);
                }
                return null;
            } finally {
                this.renderer.currentMatId = 0;
                this.renderer.currentPass = 0;
                GLRecorder.deactivate();
                com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.secEnd(
                    com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.SEC_RECORD, secStart);
            }
            // スクリプトが落ちても記録は捨てない。 (発光パスの途中で落ちる車両が多く、記録ごと捨てるとライトが…
            // ★フォールバック廃止: 記録は常に活かす
            return target;
        }

        private GLRecorder record(Object entity, int pass, float partialTick) {
            return record(entity, pass, partialTick, false);
        }

        /** フレーム後半まで持ち越しても他の車両に壊されない (以前は使い回しバッファだったので専用の記録が要… */
        private GLRecorder record(Object entity, int pass, float partialTick, boolean fresh) {
            return recordCached(entity, pass, 0, partialTick);
        }

        /** マテリアル別 tessellator オーバーレイ (方向幕/速度計/ATC/モニタ/室内LED)。
         *  ★本家 renderWithTexture と同じく、ライトパス (2..4) でもマテリアル別に
         *  スクリプトを呼ぶ (500系等の MatId ゲートがライトパスでも機能する)。 */
        private void renderMaterialOverlays(Object entity, float partialTick, PoseStack poseStack,
                                            MultiBufferSource buffer, int packedLight, int packedOverlay,
                                            MqoModelLoader.MqoModel bodyModel, List<CachedPass> sink,
                                            int passOverride) {
            if (bodyModel == null) {
                return;
            }
            MqoModelLoader.ScriptModel sm = bodyModel.getScriptModel();
            if (sm == null || sm.textures == null || sm.textures.length <= 1) {
                return;
            }
            int matCount = sm.textures.length;
            int pass = passOverride;

            int[] ids = this.overlayMatIds;
            if (ids != null) {
                // 発見済み: オーバーレイを持つ matId だけ実行。
                for (int matId : ids) {
                    drawOneOverlay(entity, pass, partialTick, matId, bodyModel, sm, poseStack, buffer, packedLight, packedOverlay, sink);
                }
                return;
            }
            // 初回スキャン: 全マテリアルで render を回し、tess 描画を出す matId を発見・キ…
            java.util.List<Integer> found = new ArrayList<>();
            for (int matId = 1; matId < matCount; matId++) {
                if (drawOneOverlay(entity, pass, partialTick, matId, bodyModel, sm, poseStack, buffer, packedLight, packedOverlay, sink)) {
                    found.add(matId);
                }
            }
            int[] arr = new int[found.size()];
            for (int i = 0; i < found.size(); i++) {
                arr[i] = found.get(i);
            }
            this.overlayMatIds = arr;
        }

        /** 1 マテリアルぶんのオーバーレイを描く。tess 描画があれば true。 */
        private boolean drawOneOverlay(Object entity, int pass, float partialTick, int matId,
                                       MqoModelLoader.MqoModel bodyModel, MqoModelLoader.ScriptModel sm, PoseStack poseStack,
                                       MultiBufferSource buffer, int packedLight, int packedOverlay, List<CachedPass> sink) {
            GLRecorder rec = recordMat(entity, pass, partialTick, matId);
            if (rec == null || !rec.hasTess()) {
                return false;
            }
            // 本家 renderWithTexture: pass>=2 は subTextures (light 変体) を bind する
            ResourceLocation tex = pass >= RenderPass.LIGHT.id
                ? materialLightTexture(bodyModel, matId, pass) : materialTexture(sm, matId);
            if (tex == null) {
                tex = materialTexture(sm, matId);
            }
            replayTessOnly(rec, poseStack, buffer, packedLight, packedOverlay, tex);
            if (sink != null) {
                sink.add(new CachedPass(rec, pass, null, tex));
            }
            return true;
        }

        /** render を指定 matId で 1 パス記録する (tessellator オーバーレイ捕捉… */
        private GLRecorder recordMat(Object entity, int pass, float partialTick, int matId) {
            return recordCached(entity, pass, matId, partialTick);
        }

        /** マテリアル matId のテクスチャ (MqoModel が matId 順に解決済みで保持)。 */
        private static ResourceLocation materialTexture(MqoModelLoader.ScriptModel sm, int matId) {
            if (sm == null || sm.textures == null || matId < 0 || matId >= sm.textures.length) {
                return null;
            }
            MqoModelLoader.ScriptMaterialTexture smt = sm.textures[matId];
            if (smt == null || smt.material == null) {
                return null;
            }
            Object tex = smt.material.texture;
            if (tex instanceof ResourceLocation rl) {
                return rl;
            }
            // ScriptMaterial.texture は ScriptTexture でラップされている…
            if (tex instanceof MqoModelLoader.ScriptTexture st) {
                try {
                    return ResourceLocation.fromNamespaceAndPath(st.namespace, st.path);
                } catch (Exception e) {
                    return null;
                }
            }
            return null;
        }

        /** マテリアル matId のライト変体テクスチャ (本家 subTextures[pass-2] 相当)。 */
        private static ResourceLocation materialLightTexture(MqoModelLoader.MqoModel bodyModel, int matId, int pass) {
            return bodyModel == null ? null : bodyModel.lightTextureFor(matId, pass);
        }
    }

    static void replay(GLRecorder rec, PoseStack poseStack, MultiBufferSource buffer,
                       int packedLight, int packedOverlay, MqoModelLoader.MqoModel model,
                       PolygonModel bodyGraph) {
        replay(rec, poseStack, buffer, packedLight, packedOverlay, model, bodyGraph, RenderPass.NORMAL.id, null);
    }

    /** tessellator 描画 (DRAW_TESS) だけを再生する (本家式マテリアル別オーバー… */
    static void replayTessOnly(GLRecorder rec, PoseStack poseStack, MultiBufferSource buffer,
                               int packedLight, int packedOverlay, ResourceLocation defaultTex) {
        // 方向幕/速度計/ATC/モニタ/室内LED は発光する表示器 (JSON の "Light" マテ…
        long secStart = com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.sec();
        final int fullBright = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
        int light = fullBright;
        float colR=1,colG=1,colB=1,colA=1;
        ResourceLocation tex = defaultTex;
        int depth = 0;
        try(var matrices=new LegacyMatrixPlayback(poseStack,buffer)) {
        for (GLRecorder.Cmd cmd : rec.getCommands()) {
            try (var glState = jp.ngt.ngtlib.renderer.LegacyRenderState.use(cmd.state)) {
            switch (cmd.op) {
                case PUSH -> {
                    poseStack.pushPose();
                    depth++;
                }
                case POP -> {
                    if (depth > 0) {
                        poseStack.popPose();
                        depth--;
                    }
                }
                case TRANSLATE -> poseStack.translate(cmd.a, cmd.b, cmd.c);
                case ROTATE -> {
                    if (cmd.payload instanceof org.joml.Quaternionf quat) {
                        poseStack.mulPose(quat);
                    }
                }
                case SCALE -> poseStack.scale(cmd.a, cmd.b, cmd.c);
                case COLOR_MASK -> matrices.colorMask(cmd.a!=0,cmd.b!=0,cmd.c!=0,cmd.d!=0);
                case LOAD_MATRIX -> {if(cmd.payload instanceof float[] matrix)matrices.load(matrix);}
                case PROJECTION -> {if(cmd.payload instanceof float[] matrix)matrices.projection(matrix);}
                case DRAW_TEXT -> {
                    if(cmd.payload instanceof GLRecorder.TextDraw text) {
                        var font=net.minecraft.client.Minecraft.getInstance().font;
                        font.drawInBatch(text.text(),text.x(),text.y(),text.color(),text.shadow(),poseStack.last().pose(),buffer,net.minecraft.client.gui.Font.DisplayMode.NORMAL,0,net.minecraft.client.renderer.LightTexture.FULL_BRIGHT);
                    }
                }
                case MULT_MATRIX -> {
                    // glMultMatrix: 列優先 16 要素。JOML も列優先なのでそのまま流し込める。
                    if (cmd.payload instanceof float[] m && m.length >= 16) {
                        poseStack.mulPose(new Matrix4f().set(m));
                    }
                }
                case BRIGHTNESS -> light = cmd.a < 0 ? fullBright : (int) cmd.a;
                case BIND_TEXTURE -> {
                    if (cmd.payload instanceof GLRecorder.TexBind tb) {
                        tex = (tb.location() == null || isDefaultTexturePath(tb.rawPath())) ? defaultTex : tb.location();
                    } else {
                        tex = cmd.payload instanceof ResourceLocation rl ? rl : defaultTex;
                    }
                }
                case COLOR -> { colR=cmd.a; colG=cmd.b; colB=cmd.c; colA=cmd.d; }
                case DRAW_TESS -> {
                    if (cmd.payload instanceof GLRecorder.TessDraw draw) {
                        drawTess(draw, poseStack, draw.useCurrentColor ? LegacyColorBuffer.tint(buffer,colR,colG,colB,colA) : buffer, light, packedOverlay, tex);
                    }
                }
                default -> {
                    // RENDER_GROUPS/RENDER_PARTS/DRAW_MODEL_GROUP/COLOR…
                }
            }
            }
        }
        }
        while (depth > 0) {
            poseStack.popPose();
            depth--;
        }
        com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.secEnd(
            com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.SEC_REPLAY, secStart);
    }

    static void replay(GLRecorder rec, PoseStack poseStack, MultiBufferSource buffer,
                       int packedLight, int packedOverlay, MqoModelLoader.MqoModel model,
                       PolygonModel bodyGraph, int legacyPass) {
        replay(rec, poseStack, buffer, packedLight, packedOverlay, model, bodyGraph, legacyPass, null);
    }

    /** スクリプトが描いたグループを ***_light0/1/2.png に差し替えて重ねる (本家 M… */
    static void replay(GLRecorder rec, PoseStack poseStack, MultiBufferSource buffer,
                       int packedLight, int packedOverlay, MqoModelLoader.MqoModel model,
                       PolygonModel bodyGraph, int legacyPass, java.util.Set<String> excluded) {
        replay(rec, poseStack, buffer, packedLight, packedOverlay, model, bodyGraph, legacyPass, excluded, null);
    }

    /** テクスチャ (モデル定義の "default")。 */
    @SuppressWarnings("unchecked")
    static void replay(GLRecorder rec, PoseStack poseStack, MultiBufferSource buffer,
                               int packedLight, int packedOverlay, MqoModelLoader.MqoModel model,
                               PolygonModel bodyGraph, int legacyPass, java.util.Set<String> excluded,
                               ResourceLocation defaultTessTex) {
        long secStart = com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.sec();
        int light = packedLight;
        ResourceLocation overrideTex = null;
        // スクリプトの glColor4f (発光オーバーレイの強度等に使用)
        float colR = 1.0F, colG = 1.0F, colB = 1.0F, colA = 1.0F;
        int depth = 0;
        try(var matrices=new LegacyMatrixPlayback(poseStack,buffer)) {
        for (GLRecorder.Cmd cmd : rec.getCommands()) {
            try (var glState = jp.ngt.ngtlib.renderer.LegacyRenderState.use(cmd.state)) {
            switch (cmd.op) {
                case PUSH -> {
                    poseStack.pushPose();
                    depth++;
                }
                case POP -> {
                    if (depth > 0) {
                        poseStack.popPose();
                        depth--;
                    }
                }
                case TRANSLATE -> poseStack.translate(cmd.a, cmd.b, cmd.c);
                case ROTATE -> {
                    if (cmd.payload instanceof org.joml.Quaternionf quat) {
                        poseStack.mulPose(quat);
                    }
                }
                case SCALE -> poseStack.scale(cmd.a, cmd.b, cmd.c);
                case COLOR_MASK -> matrices.colorMask(cmd.a!=0,cmd.b!=0,cmd.c!=0,cmd.d!=0);
                case LOAD_MATRIX -> {if(cmd.payload instanceof float[] matrix)matrices.load(matrix);}
                case PROJECTION -> {if(cmd.payload instanceof float[] matrix)matrices.projection(matrix);}
                case DRAW_TEXT -> {
                    if(cmd.payload instanceof GLRecorder.TextDraw text) {
                        var font=net.minecraft.client.Minecraft.getInstance().font;
                        font.drawInBatch(text.text(),text.x(),text.y(),text.color(),text.shadow(),poseStack.last().pose(),buffer,net.minecraft.client.gui.Font.DisplayMode.NORMAL,0,net.minecraft.client.renderer.LightTexture.FULL_BRIGHT);
                    }
                }
                case MULT_MATRIX -> {
                    if(cmd.payload instanceof float[] matrix && matrix.length>=16) {
                        org.joml.Matrix4f transform=new org.joml.Matrix4f().set(matrix);
                        poseStack.mulPose(transform);
                    }
                }
                case RENDER_GUI_ITEM -> {
                    if(cmd.payload instanceof GLRecorder.GuiItemDraw item) jp.ngt.ngtlib.renderer.LegacyItemRenderer.replayGui(item,poseStack,buffer);
                }
                case LOAD_NAME -> {}
                case RENDER_ITEM -> {
                    if(cmd.payload instanceof GLRecorder.ItemDraw item) {
                        var minecraft=net.minecraft.client.Minecraft.getInstance();
                        minecraft.getItemRenderer().renderStatic(item.stack(),net.minecraft.world.item.ItemDisplayContext.FIXED,
                                light,packedOverlay,poseStack,buffer,minecraft.level,item.seed());
                    }
                }
                // 負値 = 元の環境光へ戻す (スクリプトの車内発光ブロック終端)
                case BRIGHTNESS -> light = cmd.a < 0 ? packedLight : (int) cmd.a;
                case COLOR -> {
                    colR = cmd.a;
                    colG = cmd.b;
                    colB = cmd.c;
                    colA = cmd.d;
                }
                case BIND_TEXTURE -> {
                    // 素テクスチャへの「復帰」bind (CustomLightParts の描画後処理等) は上書き解…
                    if (cmd.payload instanceof GLRecorder.TexBind tb) {
                        overrideTex = (tb.location() == null || isDefaultTexturePath(tb.rawPath())) ? null : tb.location();
                    } else {
                        overrideTex = cmd.payload instanceof ResourceLocation rl ? rl : null;
                    }
                }
                case RENDER_PARTS, RENDER_GROUPS -> {
                    if (cmd.payload instanceof Set<?> names) {
                        // 色ピッキング FBO 用: ActionParts を ID 色で専用バッファへ流す。
                        if (cmd.partsRef instanceof jp.ngt.rtm.render.ActionParts apRef) {
                            com.portofino.realtrainmodunofficial.client.render.ActionPartsPickBuffer.emitParts(
                                apRef, poseStack, packedLight, packedOverlay);
                        } else {
                            com.portofino.realtrainmodunofficial.client.render.ActionPartsPickBuffer.emitGroups(
                                (Set<String>) names, poseStack, packedLight, packedOverlay);
                        }
                        if (overrideTex != null && bodyGraph != null) {
                            // テクスチャ差し替え中 (発光/ヘッドライト等): モデルグラフから 同グループの面を差し替えテクス…
                            for (Object name : names) {
                                drawModelGroup(bodyGraph, String.valueOf(name), poseStack, buffer,
                                        light, packedOverlay, overrideTex, colR, colG, colB, colA, true,
                                        legacyPass);
                            }
                        } else if (model != null && legacyPass >= RenderPass.LIGHT.id) {
                            // 発光パス: Light マテリアルの面だけを ***_light0/1/2.png で描き直す (…
                            model.renderNamedGroupsEmissive(poseStack, LegacyColorBuffer.tint(buffer,colR,colG,colB,colA), light, packedOverlay,
                                    (Set<String>) names, legacyPass);
                        } else if (model != null) {
                            // 本家 RenderVehicleBase と同じく pass で不透明/半透明を切り替える。
                            boolean translucentPass = legacyPass == RenderPass.TRANSPARENT.id;
                            model.renderNamedGroups(poseStack, LegacyColorBuffer.tint(buffer,colR,colG,colB,colA), light, packedOverlay, translucentPass,
                                    (Set<String>) names, null, excluded);
                        }
                    }
                }
                case DRAW_TESS -> {
                    if (cmd.payload instanceof GLRecorder.TessDraw draw) {
                        // スクリプトが bind していなければモデル定義の default テクスチャで描く (架線スクリ…
                        drawTess(draw, poseStack, draw.useCurrentColor ? LegacyColorBuffer.tint(buffer,colR,colG,colB,colA) : buffer, light, packedOverlay,
                                overrideTex != null ? overrideTex : defaultTessTex);
                    }
                }
                case DRAW_MODEL_GROUP -> {
                    if (cmd.payload instanceof PolygonModel pm) {
                        // a = 本家 renderPart(smoothing, ...) の平滑指定
                        drawModelGroup(pm, cmd.name, poseStack, buffer, light, packedOverlay, overrideTex,
                                colR, colG, colB, colA, cmd.a != 0.0F, legacyPass);
                    }
                }
                case RENDER_BLOCK -> {
                    // NGTO Builder のミニチュアプレビュー: バニラ BlockState のゴーストを描く…
                    if (cmd.payload instanceof net.minecraft.world.level.block.state.BlockState st
                            && !st.isAir()) {
                        poseStack.pushPose();
                        poseStack.translate(cmd.a, cmd.b, cmd.c);
                        net.minecraft.client.Minecraft.getInstance().getBlockRenderer()
                            .renderSingleBlock(st, poseStack, buffer, light, packedOverlay);
                        poseStack.popPose();
                    }
                }
            }
            }
        }
        }
        while (depth > 0) {
            poseStack.popPose();
            depth--;
        }
        com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.secEnd(
            com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.SEC_REPLAY, secStart);
    }

    private static final int GL_TRIANGLES = 4;
    private static final int GL_TRIANGLE_FAN = 6;
    private static final int GL_QUADS = 7;

    /** 前照灯/回転灯のボリュームライト用の加算合成 RenderType。 */
    public static final RenderType ADDITIVE_TESS = RenderType.create(
        "rtmu_additive_light",
        com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY,
        com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
        256,
        false,
        true,
        RenderType.CompositeState.builder()
            .setShaderState(net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
            .setTextureState(net.minecraft.client.renderer.RenderStateShard.NO_TEXTURE)
            .setTransparencyState(net.minecraft.client.renderer.RenderStateShard.ADDITIVE_TRANSPARENCY)
            .setCullState(net.minecraft.client.renderer.RenderStateShard.NO_CULL)
            .setLightmapState(net.minecraft.client.renderer.RenderStateShard.LIGHTMAP)
            .setOverlayState(net.minecraft.client.renderer.RenderStateShard.OVERLAY)
            .setWriteMaskState(net.minecraft.client.renderer.RenderStateShard.COLOR_WRITE)
            .createCompositeState(false));

    /** 本家 ActionParts の輪郭線用 RenderType。 */
    public static final RenderType OUTLINE_TESS = RenderType.create(
        "rtmu_outline_light",
        com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY,
        com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
        256,
        false,
        true,
        RenderType.CompositeState.builder()
            .setShaderState(net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
            .setTextureState(net.minecraft.client.renderer.RenderStateShard.NO_TEXTURE)
            .setTransparencyState(net.minecraft.client.renderer.RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setCullState(net.minecraft.client.renderer.RenderStateShard.CULL)
            .setLightmapState(net.minecraft.client.renderer.RenderStateShard.LIGHTMAP)
            .setOverlayState(net.minecraft.client.renderer.RenderStateShard.OVERLAY)
            .setWriteMaskState(net.minecraft.client.renderer.RenderStateShard.COLOR_WRITE)
            .createCompositeState(false));

    private static final ThreadLocal<float[]> TESS_NORMALS = new ThreadLocal<>();

    private static void drawTess(GLRecorder.TessDraw draw, PoseStack poseStack, MultiBufferSource buffer,
                                 int light, int overlay, ResourceLocation texture) {
        long secStart = com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.sec();
        ResourceLocation tex = texture != null ? texture
                : ResourceLocation.withDefaultNamespace("textures/misc/white.png");
        // 本家はブレンド有効の即時描画 — 半透明テクスチャ (方向幕/LCD) を正しく合成する。
        if (draw.cullFront) {
            com.mojang.blaze3d.systems.RenderSystem.enableCull();
            org.lwjgl.opengl.GL11.glCullFace(org.lwjgl.opengl.GL11.GL_FRONT);
        }
        RenderType tessType = draw.cullFront ? OUTLINE_TESS : draw.additive ? ADDITIVE_TESS : RenderType.entityTranslucent(tex);
        boolean lineMode=draw.mode>=1 && draw.mode<=3;
        tessType = LegacyRenderTypes.select(tessType,tex,true,draw.cullFront,!draw.additive,
                lineMode ? com.mojang.blaze3d.vertex.VertexFormat.Mode.DEBUG_LINES : com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                770,draw.additive ? 1 : 771);
        if(draw.mode==0) tessType=LegacyRenderTypes.points(tex,draw.additive);
        VertexConsumer vc = buffer.getBuffer(tessType);
        int stride = 9;
        int count = draw.verts.length / stride;
        float[] previousNormals = TESS_NORMALS.get();
        TESS_NORMALS.set(draw.normals);
        try {
        switch (draw.mode) {
            case 0 -> {
                Vector3f normal=new Vector3f(0,0,1);
                for(int index=0;index<count;index++) for(int corner=0;corner<4;corner++)
                    emitVertex(vc,poseStack.last(),draw.verts,index,normal,light,overlay);
            }
            case 1,2,3 -> {
                int edges=draw.mode==1 ? count/2 : draw.mode==2 ? count : Math.max(0,count-1);
                Vector3f normal=new Vector3f(0,0,1);
                for(int edge=0;edge<edges;edge++) {
                    int first=draw.mode==1 ? edge*2 : edge;
                    int second=draw.mode==2 ? (edge+1)%count : first+1;
                    emitVertex(vc,poseStack.last(),draw.verts,first,normal,light,overlay);
                    emitVertex(vc,poseStack.last(),draw.verts,second,normal,light,overlay);
                }
            }
            case GL_QUADS -> {
                for (int q = 0; q + 4 <= count; q += 4) {
                    emitQuad(vc, poseStack, draw.verts, q, q + 1, q + 2, q + 3, light, overlay);
                }
            }
            case GL_TRIANGLES -> {
                for (int t = 0; t + 3 <= count; t += 3) {
                    emitQuad(vc, poseStack, draw.verts, t, t + 1, t + 2, t + 2, light, overlay);
                }
            }
            case 5 -> {
                for (int t=0;t+2<count;t++) {
                    int a=(t&1)==0 ? t : t+1, b=(t&1)==0 ? t+1 : t;
                    emitQuad(vc,poseStack,draw.verts,a,b,t+2,t+2,light,overlay);
                }
            }
            case 8 -> {
                for(int t=0;t+3<count;t+=2) emitQuad(vc,poseStack,draw.verts,t,t+1,t+3,t+2,light,overlay);
            }
            case 9, GL_TRIANGLE_FAN -> {
                for (int t = 1; t + 2 <= count; t++) {
                    emitQuad(vc, poseStack, draw.verts, 0, t, t + 1, t + 1, light, overlay);
                }
            }
            default -> {
                // Unsupported primitive modes do not produce triang…
            }
        }
        } finally {
            if(previousNormals==null) TESS_NORMALS.remove(); else TESS_NORMALS.set(previousNormals);
        }
        if (draw.cullFront) {
            // 前面カリングは flush 時の GL 状態で決まる。
            if (buffer instanceof MultiBufferSource.BufferSource bs) {
                bs.endBatch(OUTLINE_TESS);
            }
            org.lwjgl.opengl.GL11.glCullFace(org.lwjgl.opengl.GL11.GL_BACK);
        }
        com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.secEnd(
            com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.SEC_TESS, secStart);
    }

    private static void emitQuad(VertexConsumer vc, PoseStack poseStack, float[] v,
                                 int i0, int i1, int i2, int i3, int light, int overlay) {
        Vector3f normal = quadNormal(v, i0, i1, i2);
        PoseStack.Pose pose = poseStack.last();
        emitVertex(vc, pose, v, i0, normal, light, overlay);
        emitVertex(vc, pose, v, i1, normal, light, overlay);
        emitVertex(vc, pose, v, i2, normal, light, overlay);
        emitVertex(vc, pose, v, i3, normal, light, overlay);
    }

    private static Vector3f quadNormal(float[] v, int i0, int i1, int i2) {
        int a = i0 * 9, b = i1 * 9, c = i2 * 9;
        float ax = v[b] - v[a], ay = v[b + 1] - v[a + 1], az = v[b + 2] - v[a + 2];
        float bx = v[c] - v[a], by = v[c + 1] - v[a + 1], bz = v[c + 2] - v[a + 2];
        Vector3f n = new Vector3f(ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx);
        // ★大きさで捨てない (細かい文字の三角形が真上を向く)。
        com.portofino.realtrainmodunofficial.client.model.FaceNormals.normalize(n);
        return n;
    }

    private static void emitVertex(VertexConsumer vc, PoseStack.Pose pose, float[] v, int index,
                                   Vector3f normal, int light, int overlay) {
        int o = index * 9;
        Matrix4f mat = pose.pose();
        // バニラの addVertex(Matrix4f,..)/setNormal(Pose,..) は頂…
        VertexConsumer written = VertexWriter.addVertex(vc, mat, v[o], v[o + 1], v[o + 2])
                .setColor(v[o + 5], v[o + 6], v[o + 7], v[o + 8])
                .setUv(v[o + 3], v[o + 4])
                .setOverlay(overlay)
                .setLight(light);
        float[] normals = TESS_NORMALS.get();
        if(normals!=null && index*3+2<normals.length) {
            VertexWriter.setNormal(written,pose,normals[index*3],normals[index*3+1],normals[index*3+2]);
        } else VertexWriter.setNormal(written, pose, normal.x, normal.y, normal.z);
    }

    /** drawModelGroup 用の平坦化済み頂点キャッシュ (モデル → 小文字グループ名 → {… */
    private static final java.util.Map<PolygonModel, java.util.Map<String, float[]>> FLAT_GROUP_CACHE =
        new java.util.WeakHashMap<>();
    private static final float[] FLAT_EMPTY = new float[0];

    private static float[] flatGroupVerts(PolygonModel pm, String groupName, boolean smoothing) {
        java.util.Map<String, float[]> byName = FLAT_GROUP_CACHE.computeIfAbsent(pm, k -> new java.util.HashMap<>());
        // 平滑あり/なしで法線が変わるのでキーを分ける
        String key = (smoothing ? "s|" : "f|")
                + (groupName == null ? "" : groupName.toLowerCase(java.util.Locale.ROOT));
        GroupObject group=null;
        for(GroupObject candidate:pm.groupObjects) if(candidate.name.equalsIgnoreCase(groupName)) {group=candidate;break;}
        float[] flat=byName.get(key);
        if(!com.portofino.realtrainmodunofficial.client.model.LegacyModelGeometry.matches(group,smoothing,flat)) {
            flat=buildFlatGroup(group,smoothing);byName.put(key,flat);
        }
        return flat;
    }

    /** 旧 drawModelGroup の展開ロジックそのまま (四角形はそのまま/三角形は縮退クアッド… */
    private static float[] buildFlatGroup(GroupObject group, boolean smoothing) {
        return com.portofino.realtrainmodunofficial.client.model.LegacyModelGeometry.flatten(group, smoothing);
    }

    private static void drawModelGroup(PolygonModel pm, String groupName, PoseStack poseStack,
                                       MultiBufferSource buffer, int light, int overlay, ResourceLocation texture,
                                       float colR, float colG, float colB, float colA, boolean smoothing) {
        drawModelGroup(pm, groupName, poseStack, buffer, light, overlay, texture,
                colR, colG, colB, colA, smoothing, RenderPass.NORMAL.id);
    }

    private static void drawModelGroup(PolygonModel pm, String groupName, PoseStack poseStack,
                                       MultiBufferSource buffer, int light, int overlay, ResourceLocation texture,
                                       float colR, float colG, float colB, float colA, boolean smoothing,
                                       int legacyPass) {
        long secStart = com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.sec();
        // 調査用: /rtm hidegroup で隠した部品は描かない
        if (com.portofino.realtrainmodunofficial.client.DebugHiddenGroups.isHidden(groupName)) {
            com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.secEnd(
                com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.SEC_MODELGRP, secStart);
            return;
        }
        float[] flat = flatGroupVerts(pm, groupName, smoothing);
        if (flat.length == 0) {
            com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.secEnd(
                com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.SEC_MODELGRP, secStart);
            return;
        }
        ResourceLocation tex = texture != null ? texture
                : ResourceLocation.withDefaultNamespace("textures/misc/white.png");
        // ModelObject binds the original image for every pa…
        boolean normalPass = legacyPass == RenderPass.NORMAL.id;
        boolean cull = MqoModelLoader.shouldCullModelFaces(null);
        boolean blend = !normalPass;
        var mode=com.portofino.realtrainmodunofficial.client.model.LegacyIrisGeometry.useTriangles(com.portofino.realtrainmodunofficial.client.ShaderCompat.active(), smoothing) ? com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES : com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS;
        RenderType groupType = LegacyRenderTypes.select(
            cull ? RtmuRenderTypes.entityCutoutTriangles(tex) : RtmuRenderTypes.entityCutoutNoCullTriangles(tex),
            tex,blend,cull,true,mode);
        if (legacyPass < 2 && com.portofino.realtrainmodunofficial.client.DeferredTranslucentRenderer.currentVehicle() != null) {
            groupType = LegacyRenderTypes.vehicle(groupType,tex,blend,cull,true,
                mode,LegacyColorBuffer.alpha(buffer,(int)(colA*255))/255F);
        }
        VertexConsumer vc = LegacyShaderAttributes.wrap(buffer.getBuffer(groupType));
        PoseStack.Pose pose = poseStack.last();
        Matrix4f mat = pose.pose();
        org.joml.Matrix3f nm = pose.normal();
        // 色はバニラ setColor(float×4) の式 (int)(f*255.0F) を 1 回だ…
        int cr = (int) (colR * 255.0F);
        int cg = (int) (colG * 255.0F);
        int cb = (int) (colB * 255.0F);
        int ca = (int) (colA * 255.0F);
        // ★法線の行列変換は頂点ごと。 面ごと 1 回で済ませていたが、buildFlatGroup がスム…
        int[] indices=com.portofino.realtrainmodunofficial.client.model.LegacyTriangleSubmission.indices(flat);
        for(int submitted=0;submitted<(mode==com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS?flat.length/8:indices.length);submitted++) {
            int index=mode==com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS?submitted:indices[submitted];
            emitVertex(vc, mat, nm, flat, index * 8, cr, cg, cb, ca, overlay, light);
        }
    }

    /** flat 配列の 1 頂点を提出する (四角形/三角形どちらの経路からも同じものを出す)。 */
    private static void emitVertex(VertexConsumer vc, Matrix4f mat, org.joml.Matrix3f nm,
                                   float[] flat, int p, int cr, int cg, int cb, int ca,
                                   int overlay, int light) {
        float nx = flat[p + 5], ny = flat[p + 6], nz = flat[p + 7];
        float tnx = nm.m00() * nx + nm.m10() * ny + nm.m20() * nz;
        float tny = nm.m01() * nx + nm.m11() * ny + nm.m21() * nz;
        float tnz = nm.m02() * nx + nm.m12() * ny + nm.m22() * nz;
        VertexWriter.addVertex(vc, mat, flat[p], flat[p + 1], flat[p + 2])
                .setColor(cr, cg, cb, ca)
                .setUv(flat[p + 3], flat[p + 4])
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(tnx, tny, tnz);
    }

    private static Vector3f faceNormal(Face face, int[] idx) {
        Vertex v0 = face.vertices[idx[0]];
        Vertex v1 = face.vertices[idx[1]];
        Vertex v2 = face.vertices[idx[2]];
        float ax = v1.x - v0.x, ay = v1.y - v0.y, az = v1.z - v0.z;
        float bx = v2.x - v0.x, by = v2.y - v0.y, bz = v2.z - v0.z;
        Vector3f n = new Vector3f(ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx);
        // ★大きさで捨てない (細かい文字の三角形が真上を向く)。
        com.portofino.realtrainmodunofficial.client.model.FaceNormals.normalize(n);
        return n;
    }
}
