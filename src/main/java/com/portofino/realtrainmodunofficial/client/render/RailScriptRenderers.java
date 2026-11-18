package com.portofino.realtrainmodunofficial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.client.model.MqoModelLoader;
import com.portofino.realtrainmodunofficial.rail.RailDefinition;
import com.portofino.realtrainmodunofficial.rail.RailPackLoader;
import jp.ngt.ngtlib.io.ScriptUtil;
import jp.ngt.ngtlib.io.NGTFileLoader;
import jp.ngt.ngtlib.renderer.GLRecorder;
import jp.ngt.ngtlib.renderer.model.Material;
import jp.ngt.ngtlib.renderer.model.TextureSet;
import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import jp.ngt.rtm.rail.TileEntityLargeRailSwitchCore;
import jp.ngt.rtm.rail.util.RailMap;
import jp.ngt.rtm.render.ModelObject;
import jp.ngt.rtm.render.RailPartsRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

import javax.script.ScriptEngine;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 本家式レール描画システム (Phase 3 先行 / レール描画の作り直し)。 */
public final class RailScriptRenderers {

    /** スクリプトが GL11 として使うファサードのプリバインド (mozilla_compat の i… */
    private static final String PRELUDE =
            "var GL11 = Java.type('jp.ngt.ngtlib.renderer.GL11Facade');\n" +
            "var GL12 = GL11;\n";

    private static final Map<String, Scripted> CACHE = new ConcurrentHashMap<>();
    private static final Scripted INVALID = new Scripted(null, null);

    /** スクリプト無しレール用の素の RailPartsRenderer (shouldRenderObj… */
    private static final RailPartsRenderer PLAIN = new RailPartsRenderer();
    private static final Map<BlockPos, GLRecorder> PLAIN_CACHE = new HashMap<>();

    /** 重ねレール (サブレール) の記録キャッシュ。キーは (コア座標|サブレール定義ID)。 */
    private static final Map<String, GLRecorder> SUB_CACHE = new ConcurrentHashMap<>();

    public static void invalidateAll() {CACHE.clear();PLAIN_CACHE.clear();SUB_CACHE.clear();LIGHT_SIGNATURES.clear();RailMeshCache.clear();}
    private RailScriptRenderers() {
    }

    public static Scripted get(RailDefinition def) {
        if (def == null || def.getScriptPath() == null || def.getScriptPath().isBlank()) {
            return null;
        }
        Scripted s = CACHE.computeIfAbsent(def.getId(), id -> create(def));
        return s == INVALID ? null : s;
    }

    private static Scripted create(RailDefinition def) {
        try {
            String source = RailPackLoader.readScriptContent(def);
            if (source == null || source.isBlank()) {
                RealTrainModUnofficial.LOGGER.warn("Rail script not readable for {} ({})", def.getId(), def.getScriptPath());
                return INVALID;
            }
            // ★ //include の展開と FQN remap は全経路で共通 (PackScriptSou…
            ScriptEngine se = ScriptUtil.doScript(PRELUDE + com.portofino.realtrainmodunofficial.script.PackScriptSource.prepare(source, def.getScriptPath()), def.getScriptPath());
            Object rcName = se.get("renderClass");
            if (rcName == null) {
                RealTrainModUnofficial.LOGGER.warn("Rail script has no renderClass: {}", def.getId());
                return INVALID;
            }
            Class<?> rc = Class.forName(rcName.toString(), true, ScriptUtil.class.getClassLoader());
            Object instance = rc.getDeclaredConstructor().newInstance();
            if (!(instance instanceof RailPartsRenderer renderer)) {
                // 車両用等は対象外 (RailPartsRenderer のみ)
                return INVALID;
            }
            renderer.setScript(se);
            se.put("renderer", renderer);
            ModelObject modelObject = new ModelObject(new TextureSet[]{new TextureSet(new Material(null))});
            byte[] bytes = NGTFileLoader.findAsset("models/" + def.getModelFile());
            if (bytes == null) bytes = NGTFileLoader.findAsset(def.getModelFile());
            modelObject.model = bytes == null ? new jp.ngt.ngtlib.renderer.model.PolygonModel()
                    : jp.ngt.ngtlib.renderer.model.ModelLoader.parse(bytes, def.getModelFile());
            modelObject.setTextures(jp.ngt.ngtlib.renderer.model.LegacyTextureLayout.create(modelObject.model, def.getTextureOverrides()));
            // 本家 ModelObject: renderer.init(ModelSetBase, Model…
            jp.ngt.rtm.modelpack.cfg.RailConfig railCfg = new jp.ngt.rtm.modelpack.cfg.RailConfig();
            railCfg.railName = def.getDisplayName();
            railCfg.ballastWidth = Math.max(1, def.getBallastWidth());
            railCfg.model = new jp.ngt.rtm.modelpack.cfg.ModelConfig.ModelSource();
            railCfg.model.modelFile = def.getModelFile();
            railCfg.model.rendererPath = def.getScriptPath();
            railCfg.scale = def.getModelScale();
            var offset = def.getModelOffset();
            railCfg.offset = new float[]{(float) offset.x, (float) offset.y, (float) offset.z};
            railCfg.init();
            renderer.init(new jp.ngt.rtm.modelpack.modelset.ModelSetRail(railCfg), modelObject);
            return new Scripted(renderer, se);
        } catch (Throwable t) {
            RealTrainModUnofficial.LOGGER.warn("Failed to init rail script renderer for {}", def.getId(), t);
            return INVALID;
        }
    }

    /** レールごとの明るさ指紋。前回焼いた時から光が変わったかを判定する。 */
    private static final java.util.Map<BlockPos, Long> LIGHT_SIGNATURES = new java.util.concurrent.ConcurrentHashMap<>();

    /** このレールの明るさが前回から変わったか。 明るさは記録 (GLRecorder) と統合メッシュの… */
    /** 描画フレーム番号。明るさ判定を毎フレームやらないために使う。 */
    private static int frameCounter;
    /** 明るさを調べ直す間隔 (フレーム)。 */
    private static final int LIGHT_PROBE_INTERVAL = 10;

    /** そのレール 1 本ぶんの記録と指紋を捨てる。 線形が届いた/変わった時に呼ぶ。 */
    public static void invalidate(BlockPos pos) {
        PLAIN_CACHE.remove(pos);
        LIGHT_SIGNATURES.remove(pos);
        RailMeshCache.invalidate(pos);
    }

    /** フレーム頭で 1 回。 */
    public static void beginFrame() {
        frameCounter++;
    }

    private static boolean lightChanged(BlockPos pos, TileEntityLargeRailCore be, RailMap[] maps) {
        // ★毎フレームは調べない。 ここは 1 本につきレール上を 9 点サンプリングして LevelRen…
        int slot = Math.floorMod(pos.hashCode(), LIGHT_PROBE_INTERVAL);
        if (LIGHT_SIGNATURES.containsKey(pos)
                && Math.floorMod(frameCounter, LIGHT_PROBE_INTERVAL) != slot) {
            return false;
        }
        long signature = lightSignature(be, maps);
        Long previous = LIGHT_SIGNATURES.put(pos, signature);
        return previous == null || previous != signature;
    }

    /** レール上の等間隔サンプル点から作る指紋。明るさと線形の両方を混ぜる。 ★線形を入れるのが要点。 */
    private static long lightSignature(TileEntityLargeRailCore be, RailMap[] maps) {
        net.minecraft.world.level.Level level = be.getLevel();
        if (level == null || maps == null) {
            return 0L;
        }
        // 端と中間で 9 点。レール全体が一斉に暗い→明るいへ変わる読み込み直後を確実に捉えられる。
        final int probes = 8;
        long signature = 1L;
        for (RailMap map : maps) {
            if (map == null) {
                signature = signature * 31L + 7L;   // null も状態の一部
                continue;
            }
            // 長さ = サンプル数 (renderStaticParts の分割数) の元。
            signature = signature * 31L + Math.round(map.getLength() * 64.0D);
            for (int i = 0; i <= probes; i++) {
                double[] p = map.getRailPos(probes, i);
                double h = map.getRailHeight(probes, i);
                // 1/64 ブロックに量子化。誤差では変わらず、線形が変われば必ず変わる。
                signature = signature * 31L + Math.round(p[0] * 64.0D);
                signature = signature * 31L + Math.round(p[1] * 64.0D);
                signature = signature * 31L + Math.round(h * 64.0D);
                net.minecraft.core.BlockPos sp = net.minecraft.core.BlockPos.containing(p[1], h + 0.25D, p[0]);
                int light = net.minecraft.client.renderer.LevelRenderer.getLightColor(level, sp);
                if (light == 0) {
                    light = net.minecraft.client.renderer.LevelRenderer.getLightColor(level, sp.above());
                }
                signature = signature * 31L + light;
            }
        }
        return signature;
    }

    /** スクリプト無しレールの本家デフォルト描画 (作り直し後の標準パス)。 */
    public static boolean renderPlain(TileEntityLargeRailCore be, RailMap[] maps, PoseStack poseStack,
                                      MultiBufferSource buffer, int packedLight, int packedOverlay,
                                      MqoModelLoader.MqoModel model) {
        if (be instanceof TileEntityLargeRailSwitchCore) {
            return false;
        }
        BlockPos pos = be.getBlockPos();
        GLRecorder rec = PLAIN_CACHE.get(pos);
        boolean rebuilt = false;
        // 指紋は毎フレーム更新する (初回に記録し損ねると次フレームで無駄な作り直しが 1 回入る)
        boolean lightChanged = lightChanged(pos, be, maps);
        if (rec == null || be.shouldRerenderRail || lightChanged) {
            rec = new GLRecorder();
            GLRecorder.activate(rec);
            try {
                PLAIN.modelGroupNames = model.getOriginalGroupNames();
                PLAIN.renderStaticParts(be, 0.0D, 0.0D, 0.0D);
            } catch (Throwable t) {
                RealTrainModUnofficial.LOGGER.warn("Plain rail render failed at {}", pos, t);
            } finally {
                GLRecorder.deactivate();
            }
            PLAIN_CACHE.put(pos, rec);
            be.shouldRerenderRail = false;
            rebuilt = true;
        }
        if (RailMeshCache.draw(pos, rec, model, poseStack, packedLight, packedOverlay, rebuilt)) {
            return true;
        }
        replay(rec, poseStack, buffer, packedLight, packedOverlay, model);
        return true;
    }

    /** 重ねレール (サブレール) を 1 本描く。 モデル+スクリプトで描く。 */
    public static void renderSubRail(TileEntityLargeRailCore be, RailMap[] maps, float partialTick,
                                     PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay,
                                     RailDefinition subDef, MqoModelLoader.MqoModel subModel, int railIndex) {
        // ★分岐コアも対象にする。以前は TileEntityLargeRailSwitchCore を弾い…
        if (subDef == null || subModel == null) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        String key = pos.asLong() + "|" + subDef.getId();
        GLRecorder rec = SUB_CACHE.get(key);
        // メインの再記録フラグ shouldRerenderRail を共有で見る (ここでは false…
        if (rec == null || be.shouldRerenderRail) {
            rec = new GLRecorder();
            GLRecorder.activate(rec);
            try {
                Scripted sc = get(subDef);
                if (sc != null) {
                    sc.renderer.modelGroupNames = subModel.getOriginalGroupNames();
                    // ★本家 RenderLargeRail はサブレール i を currentRailIndex =…
                    sc.renderer.currentRailIndex = railIndex;
                    sc.renderer.renderRailStatic(be, 0.0D, 0.0D, 0.0D, partialTick, 0);
                    sc.renderer.renderRailDynamic(be, 0.0D, 0.0D, 0.0D, partialTick, 0);
                } else {
                    PLAIN.modelGroupNames = subModel.getOriginalGroupNames();
                    PLAIN.renderStaticParts(be, 0.0D, 0.0D, 0.0D);
                }
            } catch (Throwable t) {
                RealTrainModUnofficial.LOGGER.warn("Sub-rail render failed at {} ({})", pos, subDef.getId(), t);
            } finally {
                GLRecorder.deactivate();
            }
            SUB_CACHE.put(key, rec);
        }
        if (!rec.isEmpty()) {
            replay(rec, poseStack, buffer, packedLight, packedOverlay, subModel);
        }
    }

    public static final class Scripted {
        private final RailPartsRenderer renderer;
        private final ScriptEngine engine;
        private final Map<BlockPos, GLRecorder> staticCache = new HashMap<>();

        Scripted(RailPartsRenderer renderer, ScriptEngine engine) {
            this.renderer = renderer;
            this.engine = engine;
        }

        /** パックのレールスクリプト本体。グループの出し分け (shouldRenderObject) を聞く… */
        public RailPartsRenderer renderer() {
            return this.renderer;
        }

        /** スクリプト描画 (renderRailStatic → 内部で renderStaticParts… */
        public boolean render(TileEntityLargeRailCore be, RailMap[] maps, float partialTick, PoseStack poseStack,
                              MultiBufferSource buffer, int packedLight, int packedOverlay,
                              MqoModelLoader.MqoModel model) {
            boolean isSwitch = be instanceof TileEntityLargeRailSwitchCore;
            // ★トング可動パーツ持ちのモデル (RTM 公式レール等) もここで描く。
            BlockPos pos = be.getBlockPos();
            GLRecorder rec = this.staticCache.get(pos);
            // 明るさは記録に焼き込まれるので、光が変わったら記録ごと作り直す (lightChanged 参照)
            boolean lightChanged = lightChanged(pos, be, maps);
            boolean rebuilt = rec == null || be.shouldRerenderRail || lightChanged;
            if (rebuilt) {
                rec = new GLRecorder();
                GLRecorder.activate(rec);
                try {
                    this.renderer.modelGroupNames = model.getOriginalGroupNames();
                    if (isSwitch) {
                        // ★分岐でも静的描画は1 回だけ。 renderRailStatic は renderer.rend…
                        this.renderer.currentRailIndex = 0;
                        this.renderer.renderRailStatic(be, 0.0D, 0.0D, 0.0D, partialTick, 0);
                    } else {
                        // この定義のレール 1 本だけ描く。 RailCoreBlockEntityRenderer 側で各…
                        this.renderer.currentRailIndex = 0;
                        this.renderer.renderRailStatic(be, 0.0D, 0.0D, 0.0D, partialTick, 0);
                        // ★renderRailDynamic はここでは呼ばない。
                    }
                } catch (Throwable t) {
                    if (loggedFailures.add(pos)) RealTrainModUnofficial.LOGGER.warn("Rail script render failed at {}", pos, t);
                    this.staticCache.remove(pos);
                    return true;
                } finally {
                    GLRecorder.deactivate();
                    this.renderer.currentRailIndex = 0;
                    this.renderer.renderMapOverride = null;
                }
                this.staticCache.put(pos, rec);
                be.shouldRerenderRail = false;
            }

            // Reference RailPartsRenderer executes dynamic geom…
            GLRecorder dyn = recordDynamic(be, model, partialTick, pos);
            boolean staticDrew = rec.hasGeometry();
            boolean dynDrew = dyn != null && dyn.hasGeometry();
            if (staticDrew) {
                // 静的部分 (レール本体・枕木・バラスト) はレール 1 本 = 1 メッシュに焼いて GPU に置…
                if (!RailMeshCache.draw(pos, rec, model, poseStack, packedLight, packedOverlay, rebuilt)) {
                    replay(rec, poseStack, buffer, packedLight, packedOverlay, model);
                }
            }
            if (dynDrew) {
                replay(dyn, poseStack, buffer, packedLight, packedOverlay, model);
            }
            return true;
        }

        private final java.util.Set<BlockPos> loggedFailures = java.util.concurrent.ConcurrentHashMap.newKeySet();

        /** 本家 renderRailDynamic 相当。 */
        private GLRecorder recordDynamic(TileEntityLargeRailCore be,
                                         com.portofino.realtrainmodunofficial.client.model.MqoModelLoader.MqoModel model,
                                         float partialTick, net.minecraft.core.BlockPos pos) {
            // ★スクリプトが renderRailDynamic を持たないパックでは記録ごと作らない。
            if (!jp.ngt.ngtlib.io.ScriptUtil.hasFunction(this.renderer.getScript(), "renderRailDynamic")) {
                return null;
            }
            GLRecorder dyn = new GLRecorder();
            GLRecorder.activate(dyn);
            try {
                this.renderer.modelGroupNames = model.getOriginalGroupNames();
                this.renderer.renderRailDynamic(be, 0.0D, 0.0D, 0.0D, partialTick, 0);
            } catch (Throwable t) {
                if (loggedFailures.add(pos)) RealTrainModUnofficial.LOGGER.warn("Rail dynamic script failed at {}", pos, t);
                return null;
            } finally {
                GLRecorder.deactivate();
            }
            return dyn;
        }


    }

    /** RailMeshCache が「統合メッシュへの焼き込み」に使う再生 (捕獲用 MultiBuff… */
    static void replayForCapture(GLRecorder rec, PoseStack poseStack, MultiBufferSource buffer,
                                 int packedLight, int packedOverlay, MqoModelLoader.MqoModel model) {
        replay(rec, poseStack, buffer, packedLight, packedOverlay, model);
    }

    @SuppressWarnings("unchecked")
    private static void replay(GLRecorder rec, PoseStack poseStack, MultiBufferSource buffer,
                               int packedLight, int packedOverlay, MqoModelLoader.MqoModel model) {
        VehicleScriptRenderers.replay(rec,poseStack,buffer,packedLight,packedOverlay,model,null,0);
    }
}
