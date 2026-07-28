package com.portofino.realtrainmodunofficial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.client.model.MqoModelLoader;
import com.portofino.realtrainmodunofficial.rail.RailDefinition;
import com.portofino.realtrainmodunofficial.rail.RailPackLoader;
import jp.ngt.ngtlib.io.ScriptUtil;
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

/**
 * 本家式レール描画システム (Phase 3 先行 / レール描画の作り直し)。
 * 本家アーキテクチャの忠実再現:
 */
public final class RailScriptRenderers {

    /** スクリプトが GL11 として使うファサードのプリバインド (mozilla_compat の importPackage より優先される)。 */
    private static final String PRELUDE =
            "var GL11 = Java.type('jp.ngt.ngtlib.renderer.GL11Facade');\n" +
            "var GL12 = GL11;\n";

    private static final Map<String, Scripted> CACHE = new ConcurrentHashMap<>();
    private static final Scripted INVALID = new Scripted(null, null);

    /** スクリプト無しレール用の素の RailPartsRenderer (shouldRenderObject 常に true)。 */
    private static final RailPartsRenderer PLAIN = new RailPartsRenderer();
    private static final Map<BlockPos, GLRecorder> PLAIN_CACHE = new HashMap<>();

    /**
     * 重ねレール (サブレール) の記録キャッシュ。キーは (コア座標|サブレール定義ID)。
     * メインレールは pos キーのメッシュ/記録キャッシュを使うため、サブレールは別キーで持ち、
     * メッシュ焼き込み (pos キー) には乗せず直接 replay する。
     */
    private static final Map<String, GLRecorder> SUB_CACHE = new ConcurrentHashMap<>();

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
            // ★ //include の展開と FQN remap は全経路で共通 (PackScriptSource.prepare)。
            // 以前この経路だけ prepare を通しておらず、include を使うレール/架線/設置物パックが
            // 「スクリプトはあるのに何も描かない」状態になっていた。
            ScriptEngine se = ScriptUtil.doScript(PRELUDE + com.portofino.realtrainmodunofficial.script.PackScriptSource.prepare(source, def.getScriptPath()));
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
            renderer.init(null, modelObject);
            return new Scripted(renderer, se);
        } catch (Throwable t) {
            RealTrainModUnofficial.LOGGER.warn("Failed to init rail script renderer for {}", def.getId(), t);
            return INVALID;
        }
    }

    /** レールごとの明るさ指紋。前回焼いた時から光が変わったかを判定する。 */
    private static final java.util.Map<BlockPos, Long> LIGHT_SIGNATURES = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * このレールの明るさが前回から変わったか。
     * 明るさは記録 (GLRecorder) と統合メッシュの両方に焼き込まれるので、光が変わったら
     * 記録ごと作り直さないと元に戻らない。
     */
    /** 描画フレーム番号。明るさ判定を毎フレームやらないために使う。 */
    private static int frameCounter;
    /** 明るさを調べ直す間隔 (フレーム)。 */
    private static final int LIGHT_PROBE_INTERVAL = 10;

    /** フレーム頭で 1 回。 */
    public static void beginFrame() {
        frameCounter++;
    }

    private static boolean lightChanged(BlockPos pos, TileEntityLargeRailCore be, RailMap[] maps) {
        // ★毎フレームは調べない。
        // ここは 1 本につきレール上を 9 点サンプリングして LevelRenderer.getLightColor を引く。
        int slot = Math.floorMod(pos.hashCode(), LIGHT_PROBE_INTERVAL);
        if (LIGHT_SIGNATURES.containsKey(pos)
                && Math.floorMod(frameCounter, LIGHT_PROBE_INTERVAL) != slot) {
            return false;
        }
        long signature = lightSignature(be, maps);
        Long previous = LIGHT_SIGNATURES.put(pos, signature);
        return previous == null || previous != signature;
    }

    /** レール上の等間隔サンプル点の明るさから作る指紋。 */
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
                continue;
            }
            for (int i = 0; i <= probes; i++) {
                double[] p = map.getRailPos(probes, i);
                double h = map.getRailHeight(probes, i);
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

    /**
     * スクリプト無しレールの本家デフォルト描画 (作り直し後の標準パス)。
     * @return true = 描画を担当した
     */
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

    /**
     * 重ねレール (サブレール) を 1 本描く。
     * モデル+スクリプトで描く。
     */
    public static void renderSubRail(TileEntityLargeRailCore be, RailMap[] maps, float partialTick,
                                     PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay,
                                     RailDefinition subDef, MqoModelLoader.MqoModel subModel) {
        if (be instanceof TileEntityLargeRailSwitchCore || subDef == null || subModel == null) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        String key = pos.asLong() + "|" + subDef.getId();
        GLRecorder rec = SUB_CACHE.get(key);
        // メインの再記録フラグ shouldRerenderRail を共有で見る (ここでは false に戻さない。
        // 戻すのはメイン描画側。サブが先に描かれてもこの後メインが再記録できるようにするため)。
        if (rec == null || be.shouldRerenderRail) {
            rec = new GLRecorder();
            GLRecorder.activate(rec);
            try {
                Scripted sc = get(subDef);
                if (sc != null) {
                    sc.renderer.modelGroupNames = subModel.getOriginalGroupNames();
                    sc.renderer.currentRailIndex = 0;
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

        /** パックのレールスクリプト本体。グループの出し分け (shouldRenderObject) を聞くのに使う。 */
        public RailPartsRenderer renderer() {
            return this.renderer;
        }

        /**
         * スクリプト描画 (renderRailStatic → 内部で renderStaticParts) を記録・再生する。
         * @return true = このレンダラが描画を担当した
         */
        public boolean render(TileEntityLargeRailCore be, RailMap[] maps, float partialTick, PoseStack poseStack,
                              MultiBufferSource buffer, int packedLight, int packedOverlay,
                              MqoModelLoader.MqoModel model) {
            boolean isSwitch = be instanceof TileEntityLargeRailSwitchCore;
            // ★トング可動パーツ持ちのモデル (RTM 公式レール等) もここで描く。
            // 以前は「renderRailDynamic 未移植」として RTMU 独自パイプラインへ逃がしていたが、
            // その独自パイプラインは道床/枕木/レールを RTMU が名前で勝手に振り分けており
            // (RailGroup.BASE + renderSwitchPoint)、分岐で道床や枕木が抜ける原因になっていた。
            BlockPos pos = be.getBlockPos();
            // スクリプトが分岐を描かないパック (AR 等は isSwitchRail で早期 return):
            // 記録が空だったコアは旧パイプラインに任せる (端トリミング付き)
            if (isSwitch && this.scriptSkippedSwitches.contains(pos) && !be.shouldRerenderRail) {
                return false;
            }
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
                        // ★分岐でも静的描画は1 回だけ。
                        // renderRailStatic は renderer.renderStaticParts(...) を 1 回呼ぶだけで、
                        // RailMap ごとに回したりはしない。
                        this.renderer.currentRailIndex = 0;
                        this.renderer.renderRailStatic(be, 0.0D, 0.0D, 0.0D, partialTick, 0);
                    } else {
                        // この定義のレール 1 本だけ描く。
                        // RailCoreBlockEntityRenderer 側で各サブ定義のモデル+スクリプトを使い別途描く
                        // (#renderSubRail)。
                        this.renderer.currentRailIndex = 0;
                        this.renderer.renderRailStatic(be, 0.0D, 0.0D, 0.0D, partialTick, 0);
                        // ★renderRailDynamic はここでは呼ばない。
                        // 本家 RailPartsRenderer.renderRail は
                        // renderRailStatic(...)   → ディスプレイリストへ焼く
                        // renderRailDynamic(...)  → リストの外で毎フレーム実行 (キャッシュしない)
                        // の順で、可動部を焼き込みに含めない。
                    }
                } catch (Throwable t) {
                    RealTrainModUnofficial.LOGGER.warn("Rail script render failed at {}", pos, t);
                } finally {
                    GLRecorder.deactivate();
                    this.renderer.currentRailIndex = 0;
                    this.renderer.renderMapOverride = null;
                }
                this.staticCache.put(pos, rec);
                be.shouldRerenderRail = false;
            }

            // 分岐: レール本体は本家どおり renderRailDynamic (トング可動) が描く。
            // movement (転てつ状態) が変わらない限り記録を再利用する。
            // ★可動部は全レール共通で焼き込みの外。
            GLRecorder dyn = null;
            if (isSwitch) {
                long dynKey = computeSwitchDynKey((TileEntityLargeRailSwitchCore) be);
                DynEntry cached = this.dynamicCache.get(pos);
                if (cached != null && cached.key == dynKey) {
                    dyn = cached.rec;
                } else {
                    dyn = recordDynamic(be, model, partialTick, pos);
                    this.dynamicCache.put(pos, new DynEntry(dynKey, dyn));
                }
            } else {
                dyn = recordDynamic(be, model, partialTick, pos);
            }

            boolean staticDrew = !rec.isEmpty();
            boolean dynDrew = dyn != null && !dyn.isEmpty();
            if (isSwitch && !staticDrew && !dynDrew) {
                // スクリプトが分岐で何も描かなかった → 旧パイプラインで描かせる
                this.scriptSkippedSwitches.add(pos);
                return false;
            }
            this.scriptSkippedSwitches.remove(pos);
            if (staticDrew) {
                // 静的部分 (レール本体・枕木・バラスト) はレール 1 本 = 1 メッシュに焼いて
                // GPU に置きっぱなしにする (本家のディスプレイリスト相当)。
                // 転てつのトング (dyn) は動くので焼かず、従来どおり毎フレーム描く。
                if (!RailMeshCache.draw(pos, rec, model, poseStack, packedLight, packedOverlay, rebuilt)) {
                    replay(rec, poseStack, buffer, packedLight, packedOverlay, model);
                }
            }
            if (dynDrew) {
                // ★可動部 (トング) も焼き込みへ。
                // 本家 renderRail は renderRailDynamic を焼き込みの外で毎フレーム実行する。
                final GLRecorder dynRec = dyn;
                int dynBakeKey = 31 * dynRec.contentKey() + packedLight;
                boolean dynBaked = ObjectMeshCache.draw(be, poseStack, dynBakeKey,
                        buf -> replayForCapture(dynRec, new PoseStack(), buf,
                                packedLight, packedOverlay, model));
                if (!dynBaked) {
                    replay(dyn, poseStack, buffer, packedLight, packedOverlay, model);
                }
            }
            return true;
        }

        /** スクリプトが描画を拒否した分岐コア (毎フレームのスクリプト再実行を避けるキャッシュ)。 */
        private final java.util.Set<BlockPos> scriptSkippedSwitches =
                java.util.concurrent.ConcurrentHashMap.newKeySet();

        /** 分岐の動的描画 (トング) 記録キャッシュ。転てつ movement が変わったら再記録。 */
        private final java.util.Map<BlockPos, DynEntry> dynamicCache = new java.util.concurrent.ConcurrentHashMap<>();

        private record DynEntry(long key, GLRecorder rec) {
        }

        /** 本家 renderRailDynamic 相当。焼き込みには載せず、その場で 1 フレーム分だけ記録する。 */
        private GLRecorder recordDynamic(TileEntityLargeRailCore be,
                                         com.portofino.realtrainmodunofficial.client.model.MqoModelLoader.MqoModel model,
                                         float partialTick, net.minecraft.core.BlockPos pos) {
            // ★スクリプトが renderRailDynamic を持たないパックでは記録ごと作らない。
            // 可動部を定義しないレールは多く、そこで毎フレーム GLRecorder を確保しても
            // 中身は必ず空になる。本家も「関数があるパックだけ毎フレーム実行」に等しい。
            if (!jp.ngt.ngtlib.io.ScriptUtil.hasFunction(this.renderer.getScript(), "renderRailDynamic")) {
                return null;
            }
            GLRecorder dyn = new GLRecorder();
            GLRecorder.activate(dyn);
            try {
                this.renderer.modelGroupNames = model.getOriginalGroupNames();
                this.renderer.renderRailDynamic(be, 0.0D, 0.0D, 0.0D, partialTick, 0);
            } catch (Throwable t) {
                RealTrainModUnofficial.LOGGER.warn("Rail dynamic script failed at {}", pos, t);
            } finally {
                GLRecorder.deactivate();
            }
            return dyn;
        }

        private static long computeSwitchDynKey(TileEntityLargeRailSwitchCore be) {
            long key = 1L;
            jp.ngt.rtm.rail.util.SwitchType st = be.getSwitch();
            if (st != null) {
                jp.ngt.rtm.rail.util.Point[] points = st.getPoints();
                if (points != null) {
                    for (jp.ngt.rtm.rail.util.Point p : points) {
                        key = key * 31L + (p == null ? 0 : Float.floatToIntBits(p.getMovement()));
                    }
                }
            }
            return key;
        }

    }

    /** RailMeshCache が「統合メッシュへの焼き込み」に使う再生 (捕獲用 MultiBufferSource に流す)。 */
    static void replayForCapture(GLRecorder rec, PoseStack poseStack, MultiBufferSource buffer,
                                 int packedLight, int packedOverlay, MqoModelLoader.MqoModel model) {
        replay(rec, poseStack, buffer, packedLight, packedOverlay, model);
    }

    @SuppressWarnings("unchecked")
    private static void replay(GLRecorder rec, PoseStack poseStack, MultiBufferSource buffer,
                               int packedLight, int packedOverlay, MqoModelLoader.MqoModel model) {
        int light = packedLight;
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
                    // Quaternion は記録時に確定済み (payload)
                    if (cmd.payload instanceof org.joml.Quaternionf quat) {
                        poseStack.mulPose(quat);
                    }
                }
                case SCALE -> poseStack.scale(cmd.a, cmd.b, cmd.c);
                case BRIGHTNESS -> light = (int) cmd.a;
                case COLOR -> {
                    // カラーオーバーレイは現状のレールスクリプトでは未使用
                }
                case RENDER_PARTS -> {
                    // 正規化 Set は記録時に確定済み (payload) — 同一インスタンスで
                    // renderNamedGroups の IdentityHashMap キャッシュにヒットさせる
                    if (cmd.payload instanceof Set<?> names) {
                        model.renderNamedGroups(poseStack, buffer, light, packedOverlay, false, (Set<String>) names, null);
                    }
                }
                case RENDER_GROUPS -> {
                    if (cmd.payload instanceof Set<?> names) {
                        model.renderNamedGroups(poseStack, buffer, light, packedOverlay, false, (Set<String>) names, null);
                    }
                }
            }
        }
        // スクリプトの push/pop 不整合を補正
        while (depth > 0) {
            poseStack.popPose();
            depth--;
        }
    }
}
