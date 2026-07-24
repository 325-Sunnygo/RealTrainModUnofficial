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

/**
 * 本家式の車両スクリプト描画 (Phase 3 車両編)。
 * パックの rendererPath スクリプトを Nashorn (本家と同一エンジン) で実行し、
 * renderClass (jp.ngt.rtm.render.VehiclePartsRenderer 等) をリフレクション生成、
 * 毎フレーム render(entity, pass, partialTick) を GLRecorder に記録して PoseStack に再生する。
 *
 * 1.7.10 スクリプトが直接参照する Minecraft クラス (ResourceLocation/TextureUtil 等) は
 * FQN リマップで jp.ngt.mccompat.* に差し替える。//include <path> ディレクティブも解決する。
 */
public final class VehicleScriptRenderers {

    private static final String PRELUDE = com.portofino.realtrainmodunofficial.script.PackScriptSource.PRELUDE;

    private static final Map<String, Scripted> CACHE = new ConcurrentHashMap<>();
    private static final Scripted INVALID = new Scripted(null, null, null);

    private VehicleScriptRenderers() {
    }

    public static Scripted get(VehicleDefinition def) {
        if (def == null || !def.hasScript()) {
            return null;
        }
        Scripted s = CACHE.computeIfAbsent(def.getId(), id -> create(def));
        return s == INVALID ? null : s;
    }

    private static Scripted create(VehicleDefinition def) {
        try {
            String source = VehiclePackLoader.readScriptContent(def);
            if (source == null || source.isBlank()) {
                RealTrainModUnofficial.LOGGER.warn("Vehicle script not readable for {} ({})", def.getId(), def.getScriptPath());
                return INVALID;
            }
            source = com.portofino.realtrainmodunofficial.script.PackScriptSource.prepare(source);

            ScriptEngine se = ScriptUtil.doScript(PRELUDE + source);
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
            se.put("renderer", renderer);

            //無音終了 (ネイティブ/メモリ由来) の切り分け用。1 車両につき 1 回だけ出る。
            RealTrainModUnofficial.LOGGER.info("[RTMU] vehicle script init: {} ({})", def.getId(), def.getScriptPath());
            installScriptTrace(se, def.getId());
            ModelObject modelObject = buildModelObject(def);
            RealTrainModUnofficial.LOGGER.info("[RTMU] vehicle model graph built: {} groups={}", def.getId(),
                    modelObject != null && modelObject.model != null ? modelObject.model.groupObjects.size() : -1);
            jp.ngt.rtm.modelpack.modelset.ModelSetCompat modelSet =
                    new jp.ngt.rtm.modelpack.modelset.ModelSetCompat(
                            jp.ngt.rtm.modelpack.cfg.TrainConfigAdapter.get(def.getId()));
            //init 内の一部機能 (モニタ等) が失敗しても登録済み Parts で描画を続行する
            renderer.init(modelSet, modelObject);
            RealTrainModUnofficial.LOGGER.info("[RTMU] vehicle script init done: {}", def.getId());

            return new Scripted(renderer, se, modelObject);
        } catch (Throwable t) {
            RealTrainModUnofficial.LOGGER.warn("Failed to init vehicle script renderer for {}", def.getId(), t);
            return INVALID;
        }
    }

    /** 起動から数十回だけ出す診断カウンタ (フレーム毎に溢れさせない)。 */
    private static int scriptTraceBudget = 60;

    /**
     * 描画中の車両モデルの素テクスチャパス集合 (小文字正規化)。
     * BIND_TEXTURE の再生で「素テクスチャへ戻す bind = 上書き解除」を判定する。
     * 描画は単一スレッドなので素の static で足りる。
     */
    private static java.util.Set<String> activeDefaultTexPaths = java.util.Set.of();

    /** bind の元パスがモデルの素テクスチャなら true (=上書き解除)。 */
    static boolean isDefaultTexturePath(String rawPath) {
        return rawPath != null && activeDefaultTexPaths.contains(rawPath);
    }

    /** スクリプトから呼ぶ診断ログ。無音終了時に「最後にどのライブラリまで入ったか」を残す。 */
    public static void scriptTrace(String msg) {
        if (scriptTraceBudget > 0) {
            scriptTraceBudget--;
            RealTrainModUnofficial.LOGGER.info("[RTMU] script trace: {}", msg);
        }
    }

    /**
     * パックライブラリ (hi03 系) の描画エントリを包んで進行位置をログに出す。
     * Java 例外を伴わない無音終了 (ネイティブ/メモリ) では、最後に出たトレースが死んだ場所になる。
     */
    private static void installScriptTrace(ScriptEngine se, String id) {
        try {
            se.put("__RTMU_TRACE__", (java.util.function.Consumer<String>) VehicleScriptRenderers::scriptTrace);
            se.eval(
                "(function(){\n"
                + "  var tag = '" + id.replace("'", "") + "';\n"
                + "  var wrap = function(name, obj, method) {\n"
                + "    try {\n"
                + "      if (!obj || typeof obj[method] !== 'function') return;\n"
                + "      var f = obj[method];\n"
                + "      obj[method] = function() {\n"
                + "        __RTMU_TRACE__.accept(tag + ' > ' + name + '.' + method + ' enter');\n"
                + "        var r = f.apply(this, arguments);\n"
                + "        __RTMU_TRACE__.accept(tag + ' > ' + name + '.' + method + ' exit');\n"
                + "        return r;\n"
                + "      };\n"
                + "    } catch (e) {}\n"
                + "  };\n"
                + "  try { if (typeof ATS_P_Ps_State !== 'undefined') wrap('ATS', ATS_P_Ps_State.prototype, 'onUpdate'); } catch(e){}\n"
                + "  try { if (typeof CustomMonitor_ATSDisplay !== 'undefined') wrap('ATSDisplay', CustomMonitor_ATSDisplay.prototype, 'render'); } catch(e){}\n"
                + "  try { if (typeof CustomAnimator !== 'undefined') wrap('Animator', CustomAnimator.prototype, 'render'); } catch(e){}\n"
                + "  try { if (typeof DoorRenderer !== 'undefined') wrap('Door', DoorRenderer.prototype, 'render'); } catch(e){}\n"
                + "  try { if (typeof CustomLightParts !== 'undefined') wrap('Light', CustomLightParts.prototype, 'render'); } catch(e){}\n"
                + "  try { if (typeof renderRollsign === 'function') { var rs = renderRollsign; renderRollsign = function(){ __RTMU_TRACE__.accept(tag + ' > renderRollsign enter'); var r = rs.apply(this, arguments); __RTMU_TRACE__.accept(tag + ' > renderRollsign exit'); return r; }; } } catch(e){}\n"
                + "  try { if (typeof renderDriversCab === 'function') { var dc = renderDriversCab; renderDriversCab = function(){ __RTMU_TRACE__.accept(tag + ' > renderDriversCab enter'); var r = dc.apply(this, arguments); __RTMU_TRACE__.accept(tag + ' > renderDriversCab exit'); return r; }; } } catch(e){}\n"
                + "  try { if (typeof renderPantograph === 'function') { var pg = renderPantograph; renderPantograph = function(){ __RTMU_TRACE__.accept(tag + ' > renderPantograph enter'); var r = pg.apply(this, arguments); __RTMU_TRACE__.accept(tag + ' > renderPantograph exit'); return r; }; } } catch(e){}\n"
                + "  try { if (typeof renderBogie === 'function') { var bg = renderBogie; renderBogie = function(){ __RTMU_TRACE__.accept(tag + ' > renderBogie enter'); var r = bg.apply(this, arguments); __RTMU_TRACE__.accept(tag + ' > renderBogie exit'); return r; }; } } catch(e){}\n"
                + "})();\n");
        } catch (Throwable ignored) {
            //診断が入らなくても描画には影響させない
        }
    }

    private static Object newRenderer(Class<?> rc) throws ReflectiveOperationException {
        try {
            return rc.getConstructor(String[].class).newInstance(new Object[]{new String[]{"true"}});
        } catch (NoSuchMethodException e) {
            return rc.getDeclaredConstructor().newInstance();
        }
    }

    private static ModelObject buildModelObject(VehicleDefinition def) {
        List<TextureSet> sets = new ArrayList<>();
        Map<String, String> texMap = def.getTextureOverrides();
        if (texMap != null) {
            for (String path : texMap.values()) {
                //ローダ内部メタ ("|ptmeta=alphablend" 等) を除去 — スクリプトは
                //このパスから "_headLight.png" 等の発光テクスチャ名を合成する
                int meta = path.indexOf("|ptmeta=");
                String clean = meta >= 0 ? path.substring(0, meta) : path;
                sets.add(new TextureSet(new Material(new jp.ngt.mccompat.ResourceLocation("minecraft", clean))));
            }
        }
        if (sets.isEmpty()) {
            sets.add(new TextureSet(new Material(null)));
        }
        ModelObject mo = new ModelObject(sets.toArray(new TextureSet[0]));
        //車体モデルグラフ (CustomAnimator の setFacesFromParts 等が面を参照)
        try {
            String modelFile = def.getModelFile();
            if (modelFile != null && !modelFile.isBlank()) {
                byte[] bytes = NGTFileLoader.findAsset("models/" + modelFile);
                if (bytes == null) {
                    bytes = NGTFileLoader.findAsset(modelFile);
                }
                if (bytes != null) {
                    mo.model = ModelLoader.parse(bytes, modelFile);
                }
            }
        } catch (Exception e) {
        }
        if (mo.model == null) {
            mo.model = new PolygonModel();
        }
        return mo;
    }

    public static final class Scripted {
        private final VehiclePartsRenderer renderer;
        private final ScriptEngine engine;
        private final ModelObject modelObject;
        private boolean warnedRenderFail;

        //--- スクリプト描画結果のキャッシュ ---------------------------------------------
        //本家式マテリアル別 tessellator オーバーレイ (方向幕/速度計/ATC/モニタ/室内LED)。
        //スクリプトは render() 内で「var MatId = renderer.currentMatId; if(MatId==5){ tessで方向幕描画 }」
        //のように、描画中のマテリアル番号に応じてオーバーレイを描く。本家は render() をマテリアル
        //ごとに呼ぶが、RTMU は currentMatId=0 で 1 回しか呼んでいなかったため方向幕等が永久に
        //描かれなかった。ここで「オーバーレイ (tess 描画) を持つ matId」を初回に 1 度だけ発見して
        //キャッシュし、以降はその matId だけ追加で render() する (毎フレーム全マテリアル実行を避ける)。
        //空配列 = オーバーレイ無し。null = 未スキャン。車種定義ごとに 1 度だけ確定する。
        private volatile int[] overlayMatIds;

        /** 1 パスぶんの記録 (通常パス or 発光パス or マテリアル別オーバーレイ)。 */
        private static final class CachedPass {
            final GLRecorder rec;
            final int pass;
            final Set<String> excluded;
            //非 null = マテリアル別 tessellator オーバーレイ (方向幕等)。この場合は tess 描画のみを
            //このテクスチャで再生する (本体グループは通常パスで既に描かれているため描かない)。
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

        Scripted(VehiclePartsRenderer renderer, ScriptEngine engine, ModelObject modelObject) {
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

        /**
         * 毎フレーム: スクリプト render() を記録して再生する。
         *
         * @return true = 描画を担当した
         */
        public boolean render(Object entity, float partialTick, PoseStack poseStack,
                              MultiBufferSource buffer, int packedLight, int packedOverlay,
                              MqoModelLoader.MqoModel bodyModel) {
            //ガラス (半透明) を遅延バッファへ回す判定用に、描画中の車両を記録する。
            //replay 経路では renderNamedGroups に entity が渡らず null になるため、
            //この記録が無いとガラスが遅延されず、色付きガラス越しのレールに色が付かない。
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
            //★スクリプトの実行回数は RTMU では制御しない (スクリプト任せ)。
            //
            //以前はここで「完全静止した車両は描画結果を GLRecorder ごとキャッシュし、
            //一定フレーム間隔でだけ描き直す」間引きをしていた。しかしパックのアニメーションは
            //スクリプト自身が進めるものが多く (SR1 のパンタ等)、その進行が RTMU の再記録間隔に
            //乗ってしまい「0.5 秒に 1 コマ」のカクつきになっていた。isFullyStatic は Java 側の
            //pantograph_F/doorMove しか見ないため、スクリプトが独自に動かすパーツは
            //「静止」と誤判定される。
            //
            //間引きの有無を RTMU が判断する方法は原理的に無い (スクリプトが何を動かしているか
            //RTMU からは分からない) ので、毎フレーム素直に実行する。
            com.portofino.realtrainmodunofficial.perf.RtmuProfiler.addVehicle(false);
            return renderReal(entity, partialTick, poseStack, buffer, packedLight, packedOverlay,
                    bodyModel, null);
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
                        && tp.mode == tq.mode && java.util.Arrays.equals(tp.verts, tq.verts);
            }
            //Quaternionf / Set<String> / ResourceLocation / BlockState は equals で比較。
            //PolygonModel は equals 非オーバーライド = 同一インスタンスのみ一致だが、
            //モデルオブジェクトは使い回されるのでそれで正しい。
            return p.equals(q);
        }

        /**
         * スクリプト描画の実処理 (本家 RenderVehicleBase.doRender と同じ)。
         * sink が非 null のとき、再生した各パスの記録を追加してキャッシュに残す。
         */
        /** 初回描画だけ段階ログを出す (無音終了の切り分け用)。 */
        private int firstRenderTrace;

        private void trace(String step) {
            if (firstRenderTrace < 8) {
                firstRenderTrace++;
                RealTrainModUnofficial.LOGGER.info("[RTMU] render step: {}", step);
            }
        }

        private boolean renderReal(Object entity, float partialTick, PoseStack poseStack,
                                   MultiBufferSource buffer, int packedLight, int packedOverlay,
                                   MqoModelLoader.MqoModel bodyModel, List<CachedPass> sink) {
            trace("normal-record");
            //本家 RenderVehicleBase.doRender: 通常描画 (RenderPass.NORMAL) → 発光描画 (renderBodyLight)
            GLRecorder normal = record(entity, RenderPass.NORMAL.id, partialTick);
            trace("normal-recorded");
            //★ isEmpty ではなく hasGeometry で判定する。スクリプトが何も描かずに落ちると
            //  glPushMatrix だけが残り isEmpty()==false になるため、「描画済み」と誤判定して
            //  素のモデル描画がスキップされ、車体が透明になる (223 系で発生)。
            if (normal == null || !normal.hasGeometry()) {
                return false;
            }
            PolygonModel graph = this.modelObject != null ? this.modelObject.model : null;
            //本家 ResourceState.exclusionParts: スクリプトが描画から外したパーツ (開いたドア等)。
            //スクリプトの render() 内で add/removeExclusionParts が呼ばれた後に読む。
            java.util.Set<String> excluded = exclusionPartsOf(entity);
            trace("normal-replay");
            replay(normal, poseStack, buffer, packedLight, packedOverlay, bodyModel, graph,
                    RenderPass.NORMAL.id, excluded);
            trace("normal-replayed");
            if (sink != null) {
                //excluded はエンティティ内部のライブ集合なので、キャッシュにはスナップショットを残す。
                sink.add(new CachedPass(normal, RenderPass.NORMAL.id, excluded == null ? null : Set.copyOf(excluded)));
            }
            //軽量化「遠方車両のライト・方向幕を省略」: 車体 (上の NORMAL パス) は描いたので、
            //追加の Nashorn 実行になる発光パス・オーバーレイ・半透明パスをこの車両については省く。
            //既定 OFF。ON でも近距離の車両は通常どおり全部描く。
            //★マテリアル別 tessellator オーバーレイ (方向幕/速度計/ATC/モニタ/室内LED)。
            //  スクリプトは currentMatId に応じてオーバーレイを描くため、pass0 の本体描画とは別に
            //  「オーバーレイを持つ matId」で render() を呼び直して tess 描画のみを拾う。
            trace("overlays");
            renderMaterialOverlays(entity, partialTick, poseStack, buffer, packedLight, packedOverlay,
                    bodyModel, sink);
            //★発光パス (2-4) は半透明パス (1=窓) より先に描く。
            //  バッファ描画では同フレーム内の描画順 = RenderType の挿入順で、窓 (entityTranslucent)
            //  が先に深度を書くと、後から描いた室内灯/尾灯が窓越しの視線で深度テストに落ち、
            //  「外から見るとライトが消えている・乗ると点いている」珍現象になる。
            //  灯り→窓の順なら、窓ガラスの色が灯りの上にブレンドされる (本家の見た目と同じ)。
            trace("bodyLight");
            renderBodyLight(entity, partialTick, poseStack, buffer, packedLight, packedOverlay,
                    bodyModel, graph, sink);
            //★半透明パス (TRANSPARENT=1)。本家 RenderVehicleBase は毎フレーム pass0(不透明)+
            //  pass1(半透明) を回すが、RTMU は従来 pass1 を飛ばしていた。そのため:
            //  (a) 座席回転・ドア開閉・ドアライトのアニメ時計 (スクリプト内で
            //      「if(pass==1){ setDouble(GetSystemTime()) }」と pass==1 でのみ進む) が永久に止まり、
            //  (b) 色付き半透明の窓 (RTM スクリプトは body_a_* 等の半透明部を pass1 で描く) が
            //      描画されず色が出なかった (KQ パックの窓)。
            //  ここで pass 1 を実行し replay する。下記 replay は legacyPass==TRANSPARENT のとき
            //  renderNamedGroups を translucent=true で呼び、window テクスチャ+ブレンドで描く。
            //  pass0 は opaque テクスチャ (窓の部分αは落ちる) なので二重描画にはならない。
            trace("transparent");
            GLRecorder transparent = record(entity, RenderPass.TRANSPARENT.id, partialTick);
            if (transparent != null && transparent.hasGeometry()) {
                replay(transparent, poseStack, buffer, packedLight, packedOverlay, bodyModel, graph,
                        RenderPass.TRANSPARENT.id, excluded);
                if (sink != null) {
                    sink.add(new CachedPass(transparent, RenderPass.TRANSPARENT.id,
                            excluded == null ? null : Set.copyOf(excluded)));
                }
            }
            return true;
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

        /**
         * 本家 {@code RenderVehicleBase.renderBodyLight} の忠実移植。
         * <pre>
         *   dir         = 進行方向 (0:前 / 1:後)
         *   mode        = ライト状態 (0:消灯 / 1:前照灯 / 2:前照灯+尾灯)
         *   isFrontEmpty = 進行方向側に連結相手がいない (= 先頭車)
         *   isBackEmpty  = 逆側に連結相手がいない       (= 最後尾車)
         *
         *   i=0 (LIGHT       / _light0) : mode == 0 || mode == 1
         *   i=1 (LIGHT_FRONT / _light1) : (mode == 1 && isFrontEmpty)                  || mode == 2
         *   i=2 (LIGHT_BACK  / _light2) : (mode == 1 && !isFrontEmpty && isBackEmpty)  || mode == 2
         * </pre>
         * 先頭車だけが前照灯、最後尾車だけが尾灯、中間車はどちらも点かない。
         */
        private void renderBodyLight(Object entity, float partialTick, PoseStack poseStack,
                                     MultiBufferSource buffer, int packedLight, int packedOverlay,
                                     MqoModelLoader.MqoModel bodyModel, PolygonModel graph, List<CachedPass> sink) {
            if (!(entity instanceof EntityTrainBase train)) {
                return;
            }
            //発光 (Light) マテリアル or 旧式ライトグループ (head_light/room_light 等の別ジオメトリ) の
            //どちらも無いパックは LIGHT パス自体が無意味。旧式 (223 系など) は発光テクスチャを持たず、
            //スクリプトが LIGHT パスで別ジオメトリを描くので hasScriptLightGroups でも回す
            //(でないと前照灯/室内灯が一切出ない)。素テクスチャの不透明描画は
            //MqoModelLoader.renderLightGroupOpaque が担当 (ライト名グループ限定でガラスは塞がない)。
            if (bodyModel == null || (!bodyModel.hasEmissiveBatches() && !bodyModel.hasScriptLightGroups())) {
                return;
            }
            int dir = train.getTrainDirection();
            int mode = train.getTrainStateData(TrainState.TrainStateType.State_Light.id);
            boolean frontEmpty = train.getConnectedTrain(dir) == null;
            boolean backEmpty = train.getConnectedTrain(1 - dir) == null;
            //旧式ライト (発光テクスチャ無し) は、スクリプトが pass2 (i==0) で head/tail/room を mode 別に
            //全部描く。sub-pass を mode でゲートすると尾灯(mode2)で i==0 が飛び「点けると消える」に
            //なるため、i==0 を常に回してスクリプトに委ねる。i==1,2 はこの種のスクリプトは描かない。
            //発光テクスチャ持ち (新式) は従来どおり本家 sub-pass ロジック。
            boolean scriptLights = !bodyModel.hasEmissiveBatches() && bodyModel.hasScriptLightGroups();

            for (int i = 0; i < 3; i++) {
                boolean doRender = scriptLights
                    ? (i == 0)
                    : switch (i) {
                        case 0 -> mode == 0 || mode == 1;
                        case 1 -> (mode == 1 && frontEmpty) || mode == 2;
                        default -> (mode == 1 && !frontEmpty && backEmpty) || mode == 2;
                    };
                if (!doRender) {
                    continue;
                }
                int pass = RenderPass.LIGHT.id + i;
                GLRecorder rec = record(entity, pass, partialTick);
                //スクリプトが発光パスの途中で落ちても、そこまでに描いたライトは活かす。
                if (rec == null || !rec.hasGeometry()) {
                    continue;
                }
                replay(rec, poseStack, buffer, packedLight, packedOverlay, bodyModel, graph, pass);
                if (sink != null) {
                    sink.add(new CachedPass(rec, pass, null));
                }
            }
        }

        /**
         * 完全に静止した車両か? = 速度0 かつ ドア/パンタが端点 (アニメ中でない)。
         * ここが true の間だけ描画結果をキャッシュする。動作/アニメ中は毎フレーム描いて
         * partialTick 補間の滑らかさを保つ。
         */
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


        /**
         * 描画結果を左右する車両状態のシグネチャ。これが変わったら即座に描き直す。
         * (時間依存アニメなど、ここに含まれない変化は CACHE_REFRESH_FRAMES で救済する)
         */
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

        /** スクリプトの render(entity, pass, partialTick) を 1 パスぶん記録する。 */
        private GLRecorder record(Object entity, int pass, float partialTick) {
            long secStart = com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.sec();
            GLRecorder rec = new GLRecorder();
            GLRecorder.activate(rec);
            try {
                this.renderer.currentMatId = 0;
                this.renderer.currentPass = pass;
                this.renderer.render(entity, pass, partialTick);
            } catch (Throwable t) {
                if (!this.warnedRenderFail) {
                    this.warnedRenderFail = true;
                    RealTrainModUnofficial.LOGGER.warn("Vehicle script render failed", t);
                }
                return null;
            } finally {
                this.renderer.currentPass = 0;
                GLRecorder.deactivate();
                com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.secEnd(
                    com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.SEC_RECORD, secStart);
            }
            //スクリプトが落ちても記録は捨てない。途中まで描いていればそれは活かす
            //(発光パスの途中で落ちる車両が多く、記録ごと捨てるとライトが消える)。
            //「何も描かずに落ちた」かどうかは呼び出し側が rec.hasGeometry() で判断する。
            this.renderer.consumeScriptFailure();
            return rec;
        }

        /**
         * マテリアル別 tessellator オーバーレイ (方向幕/速度計/ATC/モニタ/室内LED) を描く。
         * スクリプトは render() 内で currentMatId を見てオーバーレイを描くため、pass0 の本体描画とは
         * 別に「オーバーレイ (tess) を持つ matId」で render() を呼び直し、tess 描画だけをそのマテリアルの
         * テクスチャで再生する。初回に全マテリアルをスキャンして該当 matId を発見・キャッシュし、以降は
         * その matId だけ実行する (毎フレーム全マテリアル実行を避ける = perf)。
         */
        private void renderMaterialOverlays(Object entity, float partialTick, PoseStack poseStack,
                                            MultiBufferSource buffer, int packedLight, int packedOverlay,
                                            MqoModelLoader.MqoModel bodyModel, List<CachedPass> sink) {
            if (bodyModel == null) {
                return;
            }
            MqoModelLoader.ScriptModel sm = bodyModel.getScriptModel();
            if (sm == null || sm.textures == null || sm.textures.length <= 1) {
                return;
            }
            int matCount = sm.textures.length;
            int pass = RenderPass.NORMAL.id;

            int[] ids = this.overlayMatIds;
            if (ids != null) {
                //発見済み: オーバーレイを持つ matId だけ実行。
                for (int matId : ids) {
                    drawOneOverlay(entity, pass, partialTick, matId, sm, poseStack, buffer, packedLight, packedOverlay, sink);
                }
                return;
            }
            //初回スキャン: 全マテリアルで render() を回し、tess 描画を出す matId を発見・キャッシュ。
            java.util.List<Integer> found = new ArrayList<>();
            for (int matId = 1; matId < matCount; matId++) {
                if (drawOneOverlay(entity, pass, partialTick, matId, sm, poseStack, buffer, packedLight, packedOverlay, sink)) {
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
                                       MqoModelLoader.ScriptModel sm, PoseStack poseStack, MultiBufferSource buffer,
                                       int packedLight, int packedOverlay, List<CachedPass> sink) {
            GLRecorder rec = recordMat(entity, pass, partialTick, matId);
            if (rec == null || !rec.hasTess()) {
                return false;
            }
            ResourceLocation tex = materialTexture(sm, matId);
            replayTessOnly(rec, poseStack, buffer, packedLight, packedOverlay, tex);
            if (sink != null) {
                sink.add(new CachedPass(rec, pass, null, tex));
            }
            return true;
        }

        /** render() を指定 matId で 1 パス記録する (tessellator オーバーレイ捕捉用)。 */
        private GLRecorder recordMat(Object entity, int pass, float partialTick, int matId) {
            long secStart = com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.sec();
            GLRecorder rec = new GLRecorder();
            GLRecorder.activate(rec);
            try {
                this.renderer.currentMatId = matId;
                this.renderer.currentPass = pass;
                this.renderer.render(entity, pass, partialTick);
            } catch (Throwable t) {
                return null;
            } finally {
                this.renderer.currentMatId = 0;
                this.renderer.currentPass = 0;
                GLRecorder.deactivate();
                com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.secEnd(
                    com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.SEC_RECORD, secStart);
            }
            this.renderer.consumeScriptFailure();
            return rec;
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
            //ScriptMaterial.texture は ScriptTexture でラップされている (namespace/path を保持)。
            //ここを ResourceLocation と誤判定して null を返していたため、方向幕がテクスチャ無し=空白だった。
            if (tex instanceof MqoModelLoader.ScriptTexture st) {
                try {
                    return ResourceLocation.fromNamespaceAndPath(st.namespace, st.path);
                } catch (Exception e) {
                    return null;
                }
            }
            return null;
        }
    }

    static void replay(GLRecorder rec, PoseStack poseStack, MultiBufferSource buffer,
                       int packedLight, int packedOverlay, MqoModelLoader.MqoModel model,
                       PolygonModel bodyGraph) {
        replay(rec, poseStack, buffer, packedLight, packedOverlay, model, bodyGraph, RenderPass.NORMAL.id, null);
    }

    /**
     * tessellator 描画 (DRAW_TESS) だけを再生する (本家式マテリアル別オーバーレイ = 方向幕等)。
     * 本体グループ (RENDER_GROUPS/RENDER_PARTS/DRAW_MODEL_GROUP) は通常パスで既に描かれているので描かない。
     * defaultTex = そのマテリアルのテクスチャ (スクリプトが BIND_TEXTURE で上書きしたらそれを優先)。
     */
    static void replayTessOnly(GLRecorder rec, PoseStack poseStack, MultiBufferSource buffer,
                               int packedLight, int packedOverlay, ResourceLocation defaultTex) {
        //方向幕/速度計/ATC/モニタ/室内LED は発光する表示器 (JSON の "Light" マテリアル) なので、
        //既定で FULL_BRIGHT にして昼夜問わず読めるようにする。スクリプトが明示 setBrightness した
        //場合はそれを優先 (負値リセットは表示器なので FULL_BRIGHT に戻す)。
        long secStart = com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.sec();
        final int fullBright = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
        int light = fullBright;
        ResourceLocation tex = defaultTex;
        int depth = 0;
        for (GLRecorder.Cmd cmd : rec.getCommands()) {
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
                case BRIGHTNESS -> light = cmd.a < 0 ? fullBright : (int) cmd.a;
                case BIND_TEXTURE -> {
                    if (cmd.payload instanceof GLRecorder.TexBind tb) {
                        tex = (tb.location() == null || isDefaultTexturePath(tb.rawPath())) ? defaultTex : tb.location();
                    } else {
                        tex = cmd.payload instanceof ResourceLocation rl ? rl : defaultTex;
                    }
                }
                case DRAW_TESS -> {
                    if (cmd.payload instanceof GLRecorder.TessDraw draw) {
                        drawTess(draw, poseStack, buffer, light, packedOverlay, tex);
                    }
                }
                default -> {
                    //RENDER_GROUPS/RENDER_PARTS/DRAW_MODEL_GROUP/COLOR は本体描画なので無視
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

    /**
     * @param legacyPass 本家 RenderPass の id。2 以上 (LIGHT/LIGHT_FRONT/LIGHT_BACK) のとき、
     *                   スクリプトが描いたグループを ***_light0/1/2.png に差し替えて重ねる
     *                   (本家 ModelObject.renderWithTexture と同じ)。
     * @param excluded   本家 ResourceState.exclusionParts。スクリプトが「今は描かない」と指定した
     *                   パーツ (ドアが開いた側の扉など)。null なら除外なし。
     */
    static void replay(GLRecorder rec, PoseStack poseStack, MultiBufferSource buffer,
                       int packedLight, int packedOverlay, MqoModelLoader.MqoModel model,
                       PolygonModel bodyGraph, int legacyPass, java.util.Set<String> excluded) {
        replay(rec, poseStack, buffer, packedLight, packedOverlay, model, bodyGraph, legacyPass, excluded, null);
    }

    /**
     * @param defaultTessTex スクリプトが {@code bindTexture} を呼ばずに tessellator で描く場合に使う
     *                       テクスチャ (モデル定義の "default")。<b>架線スクリプトはこれに依存する</b>:
     *                       本家は描画前にモデルの default テクスチャを bind してからスクリプトを
     *                       呼ぶため、RenderSimpleCatenary.js は自分では bind せず UV だけ指定する。
     *                       null のままだと白テクスチャへ落ちて、架線が単色の板になる。
     */
    @SuppressWarnings("unchecked")
    static void replay(GLRecorder rec, PoseStack poseStack, MultiBufferSource buffer,
                               int packedLight, int packedOverlay, MqoModelLoader.MqoModel model,
                               PolygonModel bodyGraph, int legacyPass, java.util.Set<String> excluded,
                               ResourceLocation defaultTessTex) {
        long secStart = com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.sec();
        int light = packedLight;
        ResourceLocation overrideTex = null;
        //スクリプトの glColor4f (発光オーバーレイの強度等に使用)
        float colR = 1.0F, colG = 1.0F, colB = 1.0F, colA = 1.0F;
        int depth = 0;
        for (GLRecorder.Cmd cmd : rec.getCommands()) {
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
                //負値 = 元の環境光へ戻す (スクリプトの車内発光ブロック終端)
                case BRIGHTNESS -> light = cmd.a < 0 ? packedLight : (int) cmd.a;
                case COLOR -> {
                    colR = cmd.a;
                    colG = cmd.b;
                    colB = cmd.c;
                    colA = cmd.d;
                }
                case BIND_TEXTURE -> {
                    //素テクスチャへの「復帰」bind (CustomLightParts の描画後処理等) は上書き解除。
                    //解除しないと以降の全パーツがそのテクスチャ+生グラフ描画になり、
                    //パンタ上げ等でライト描画が走った瞬間にテクスチャが壊れる。
                    if (cmd.payload instanceof GLRecorder.TexBind tb) {
                        overrideTex = (tb.location() == null || isDefaultTexturePath(tb.rawPath())) ? null : tb.location();
                    } else {
                        overrideTex = cmd.payload instanceof ResourceLocation rl ? rl : null;
                    }
                }
                case RENDER_PARTS, RENDER_GROUPS -> {
                    if (cmd.payload instanceof Set<?> names) {
                        if (overrideTex != null && bodyGraph != null) {
                            //テクスチャ差し替え中 (発光/ヘッドライト等): モデルグラフから
                            //同グループの面を差し替えテクスチャで描画 (UV は MQO のまま)
                            for (Object name : names) {
                                drawModelGroup(bodyGraph, String.valueOf(name), poseStack, buffer,
                                        light, packedOverlay, overrideTex, colR, colG, colB, colA);
                            }
                        } else if (model != null && legacyPass >= RenderPass.LIGHT.id) {
                            //発光パス: Light マテリアルの面だけを ***_light0/1/2.png で描き直す
                            //(本家 ModelObject.renderWithTexture と同じ。グループ名は見ない)
                            model.renderNamedGroupsEmissive(poseStack, buffer, light, packedOverlay,
                                    (Set<String>) names, legacyPass);
                        } else if (model != null) {
                            //本家 RenderVehicleBase と同じく pass で不透明/半透明を切り替える。
                            //pass0(NORMAL)=不透明テクスチャ、pass1(TRANSPARENT)=window テクスチャ+ブレンド。
                            //これで色付き半透明の窓 (body_a_* を pass1 で描く RTM スクリプト) が
                            //テクスチャ本来の色で半透明描画される (KQ パックの窓が色付かない問題)。
                            //excluded はスクリプトが除外指定したパーツ (開いたドア等) を落とす。
                            boolean translucentPass = legacyPass == RenderPass.TRANSPARENT.id;
                            model.renderNamedGroups(poseStack, buffer, light, packedOverlay, translucentPass,
                                    (Set<String>) names, null, excluded);
                        }
                    }
                }
                case DRAW_TESS -> {
                    if (cmd.payload instanceof GLRecorder.TessDraw draw) {
                        //スクリプトが bind していなければモデル定義の default テクスチャで描く
                        //(架線スクリプトは bind せず UV だけ指定するため、ここが無いと白板になる)。
                        drawTess(draw, poseStack, buffer, light, packedOverlay,
                                overrideTex != null ? overrideTex : defaultTessTex);
                    }
                }
                case DRAW_MODEL_GROUP -> {
                    if (cmd.payload instanceof PolygonModel pm) {
                        drawModelGroup(pm, cmd.name, poseStack, buffer, light, packedOverlay, overrideTex,
                                colR, colG, colB, colA);
                    }
                }
                case RENDER_BLOCK -> {
                    //NGTO Builder のミニチュアプレビュー: バニラ BlockState のゴーストを描く
                    //(a/b/c = ミニチュア内の相対座標)。テクスチャはブロックアトラス側が持つ。
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

    private static void drawTess(GLRecorder.TessDraw draw, PoseStack poseStack, MultiBufferSource buffer,
                                 int light, int overlay, ResourceLocation texture) {
        long secStart = com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.sec();
        ResourceLocation tex = texture != null ? texture
                : ResourceLocation.withDefaultNamespace("textures/misc/white.png");
        //本家はブレンド有効の即時描画 — 半透明テクスチャ (方向幕/LCD) を正しく合成する
        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucent(tex));
        int stride = 9;
        int count = draw.verts.length / stride;
        switch (draw.mode) {
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
            case GL_TRIANGLE_FAN -> {
                for (int t = 1; t + 2 <= count; t++) {
                    emitQuad(vc, poseStack, draw.verts, 0, t, t + 1, t + 1, light, overlay);
                }
            }
            default -> {
                //LINES 等は未対応
            }
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
        if (n.lengthSquared() < 1.0e-8F) {
            n.set(0.0F, 1.0F, 0.0F);
        } else {
            n.normalize();
        }
        return n;
    }

    private static void emitVertex(VertexConsumer vc, PoseStack.Pose pose, float[] v, int index,
                                   Vector3f normal, int light, int overlay) {
        int o = index * 9;
        Matrix4f mat = pose.pose();
        //バニラの addVertex(Matrix4f,..)/setNormal(Pose,..) は頂点ごとに new Vector3f() を
        //確保する。スクリプト車両は 1 フレームに数万頂点を流すため GC 負荷が大きい。
        //変換式は同一のまま確保だけを避ける (見た目は不変)。
        VertexConsumer written = VertexWriter.addVertex(vc, mat, v[o], v[o + 1], v[o + 2])
                .setColor(v[o + 5], v[o + 6], v[o + 7], v[o + 8])
                .setUv(v[o + 3], v[o + 4])
                .setOverlay(overlay)
                .setLight(light);
        VertexWriter.setNormal(written, pose, normal.x, normal.y, normal.z);
    }

    /**
     * drawModelGroup 用の平坦化済み頂点キャッシュ (モデル → 小文字グループ名 → {x,y,z,u,v,nx,ny,nz}×N)。
     * <p>
     * 旧実装は毎フレーム「グループ線形探索 + face ごとの int[]/Vector3f 確保 + 法線計算 + UV 範囲チェック」を
     * やり直していて、これが列車描画 5ms/両 の主犯だった (F8 実測 mGrp=4.4ms/f。E259 はスクリプトが
     * 車体モジュールを DRAW_MODEL_GROUP で毎フレーム多数回描く)。頂点は Vertex(final)/uvs ともロード後
     * 不変なので、展開結果を一度だけ焼いて以降は同じ値を流す (提出内容はビット単位で同一 = 見た目不変)。
     * <p>
     * WeakHashMap (PolygonModel は equals 非オーバーライド = identity): リソース再読込で古いモデルごと
     * 自然に GC される。描画スレッド専用なので同期なし。
     */
    private static final java.util.Map<PolygonModel, java.util.Map<String, float[]>> FLAT_GROUP_CACHE =
        new java.util.WeakHashMap<>();
    private static final float[] FLAT_EMPTY = new float[0];

    private static float[] flatGroupVerts(PolygonModel pm, String groupName) {
        java.util.Map<String, float[]> byName = FLAT_GROUP_CACHE.computeIfAbsent(pm, k -> new java.util.HashMap<>());
        String key = groupName == null ? "" : groupName.toLowerCase(java.util.Locale.ROOT);
        float[] flat = byName.get(key);
        if (flat == null) {
            GroupObject group = null;
            for (GroupObject g : pm.groupObjects) {
                if (g.name.equalsIgnoreCase(groupName)) {
                    group = g;
                    break;
                }
            }
            flat = buildFlatGroup(group);
            byName.put(key, flat);
        }
        return flat;
    }

    /** 旧 drawModelGroup の展開ロジックそのまま (四角形はそのまま/三角形は縮退クアッド/5角以上は扇状分割)。 */
    private static float[] buildFlatGroup(GroupObject group) {
        if (group == null || group.faces.isEmpty()) {
            return FLAT_EMPTY;
        }
        int quadVerts = 0;
        for (Face face : group.faces) {
            int n = face.vertices.length;
            if (n >= 3) {
                quadVerts += (n == 4 ? 1 : n - 2) * 4;
            }
        }
        float[] flat = new float[quadVerts * 8];
        int w = 0;
        for (Face face : group.faces) {
            int n = face.vertices.length;
            if (n < 3) {
                continue;
            }
            for (int t = 0; t < (n == 4 ? 1 : n - 2); t++) {
                int[] idx = n == 4 ? new int[]{0, 1, 2, 3} : new int[]{0, t + 1, t + 2, t + 2};
                Vector3f normal = faceNormal(face, idx);
                for (int k = 0; k < 4; k++) {
                    Vertex vert = face.vertices[idx[k]];
                    float u = 0.0F, vv = 0.0F;
                    if (face.uvs != null && idx[k] * 2 + 1 < face.uvs.length) {
                        u = face.uvs[idx[k] * 2];
                        vv = face.uvs[idx[k] * 2 + 1];
                    }
                    flat[w++] = vert.x;
                    flat[w++] = vert.y;
                    flat[w++] = vert.z;
                    flat[w++] = u;
                    flat[w++] = vv;
                    flat[w++] = normal.x;
                    flat[w++] = normal.y;
                    flat[w++] = normal.z;
                }
            }
        }
        return flat;
    }

    private static void drawModelGroup(PolygonModel pm, String groupName, PoseStack poseStack,
                                       MultiBufferSource buffer, int light, int overlay, ResourceLocation texture,
                                       float colR, float colG, float colB, float colA) {
        long secStart = com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.sec();
        float[] flat = flatGroupVerts(pm, groupName);
        if (flat.length == 0) {
            com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.secEnd(
                com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.SEC_MODELGRP, secStart);
            return;
        }
        ResourceLocation tex = texture != null ? texture
                : ResourceLocation.withDefaultNamespace("textures/misc/white.png");
        //発光テクスチャ (***_light*.png) は黒地=非発光なので加算合成+フルブライトで重ねる
        //(通常ブレンドだと黒地が不透明に描かれて方向幕等が黒く潰れる)
        boolean lightOverlay = NGTFileLoader.isLightOverlayTexture(tex);
        if (lightOverlay) {
            light = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
        }
        //本家はブレンド有効の即時描画 (モニタ/発光パーツ)
        VertexConsumer vc = buffer.getBuffer(lightOverlay ? RenderType.eyes(tex) : RenderType.entityTranslucent(tex));
        PoseStack.Pose pose = poseStack.last();
        Matrix4f mat = pose.pose();
        org.joml.Matrix3f nm = pose.normal();
        //色はバニラ setColor(float×4) の式 (int)(f*255.0F) を 1 回だけ前計算 (結果は同一)。
        int cr = (int) (colR * 255.0F);
        int cg = (int) (colG * 255.0F);
        int cb = (int) (colB * 255.0F);
        int ca = (int) (colA * 255.0F);
        //クアッド内の 4 頂点は同一法線 (buildFlatGroup が同じ値を 4 回書く) なので、
        //法線の行列変換は面ごとに 1 回だけ行う (値は VertexWriter.setNormal と同一)。
        for (int o = 0; o < flat.length; o += 32) {
            float nx = flat[o + 5], ny = flat[o + 6], nz = flat[o + 7];
            float tnx = nm.m00() * nx + nm.m10() * ny + nm.m20() * nz;
            float tny = nm.m01() * nx + nm.m11() * ny + nm.m21() * nz;
            float tnz = nm.m02() * nx + nm.m12() * ny + nm.m22() * nz;
            for (int k = 0; k < 4; k++) {
                int p = o + k * 8;
                VertexWriter.addVertex(vc, mat, flat[p], flat[p + 1], flat[p + 2])
                        .setColor(cr, cg, cb, ca)
                        .setUv(flat[p + 3], flat[p + 4])
                        .setOverlay(overlay)
                        .setLight(light)
                        .setNormal(tnx, tny, tnz);
            }
        }
        com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.secEnd(
            com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.SEC_MODELGRP, secStart);
    }

    private static Vector3f faceNormal(Face face, int[] idx) {
        Vertex v0 = face.vertices[idx[0]];
        Vertex v1 = face.vertices[idx[1]];
        Vertex v2 = face.vertices[idx[2]];
        float ax = v1.x - v0.x, ay = v1.y - v0.y, az = v1.z - v0.z;
        float bx = v2.x - v0.x, by = v2.y - v0.y, bz = v2.z - v0.z;
        Vector3f n = new Vector3f(ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx);
        if (n.lengthSquared() < 1.0e-8F) {
            n.set(0.0F, 1.0F, 0.0F);
        } else {
            n.normalize();
        }
        return n;
    }
}
