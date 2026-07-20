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
 *
 * 本家アーキテクチャの忠実再現:
 * - スクリプト付き: renderRailStatic(tile,x,y,z,pt,pass) をスクリプトが実行。
 *   デフォルト配置はスクリプトが renderer.renderStaticParts(...) を呼んだ時のみ
 *   (shouldRenderObject を位置ごとに通す = 端トリミング/枕木循環)。
 * - スクリプト無し: renderStaticParts 相当のデフォルト配置のみ。
 * - GL 呼び出しは GLRecorder に記録し BE ごとにキャッシュ (本家 DisplayList 相当)。
 * - 分岐コアのみ既存パイプラインにフォールバック (トング/ポイント描画は未移植)。
 */
public final class RailScriptRenderers {

    /**
     * スクリプトが GL11 として使うファサードのプリバインド (mozilla_compat の importPackage より優先される)。
     */
    private static final String PRELUDE =
            "var GL11 = Java.type('jp.ngt.ngtlib.renderer.GL11Facade');\n" +
            "var GL12 = GL11;\n";

    private static final Map<String, Scripted> CACHE = new ConcurrentHashMap<>();
    private static final Scripted INVALID = new Scripted(null, null);

    /**
     * スクリプト無しレール用の素の RailPartsRenderer (shouldRenderObject 常に true)。
     */
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
            ScriptEngine se = ScriptUtil.doScript(PRELUDE + source);
            Object rcName = se.get("renderClass");
            if (rcName == null) {
                RealTrainModUnofficial.LOGGER.warn("Rail script has no renderClass: {}", def.getId());
                return INVALID;
            }
            Class<?> rc = Class.forName(rcName.toString(), true, ScriptUtil.class.getClassLoader());
            Object instance = rc.getDeclaredConstructor().newInstance();
            if (!(instance instanceof RailPartsRenderer renderer)) {
                //車両用等は対象外 (RailPartsRenderer のみ)
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

    /**
     * スクリプト無しレールの本家デフォルト描画 (作り直し後の標準パス)。
     *
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
        if (rec == null || be.shouldRerenderRail) {
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
     * 重ねレール (サブレール) を 1 本描く。メインと同じ RailMap 上に、<b>サブレール定義自身の
     * モデル+スクリプト</b>で描く。
     * <p>
     * メインは pos キーのメッシュキャッシュ (RailMeshCache) を使うため、同じ pos に複数モデルを
     * 焼くとキャッシュが取り合いになる。そこでサブレールは {@link #SUB_CACHE} に (pos|定義ID) で
     * 記録をキャッシュし、焼かず直接 replay する。分岐コアはトング可動と競合するので未対応。
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
        //メインの再記録フラグ shouldRerenderRail を共有で見る (ここでは false に戻さない。
        //戻すのはメイン描画側。サブが先に描かれてもこの後メインが再記録できるようにするため)。
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

        /**
         * スクリプト描画 (renderRailStatic → 内部で renderStaticParts) を記録・再生する。
         *
         * @return true = このレンダラが描画を担当した
         */
        public boolean render(TileEntityLargeRailCore be, RailMap[] maps, float partialTick, PoseStack poseStack,
                              MultiBufferSource buffer, int packedLight, int packedOverlay,
                              MqoModelLoader.MqoModel model) {
            boolean isSwitch = be instanceof TileEntityLargeRailSwitchCore;
            if (isSwitch && modelHasTongParts(model)) {
                //トング可動パーツ持ちのモデル (RTM 公式レール等) は既存パイプラインへ
                //(Point 単位の転てつアニメ; renderRailDynamic 未移植)
                return false;
            }
            BlockPos pos = be.getBlockPos();
            //スクリプトが分岐を描かないパック (AR 等は isSwitchRail で早期 return):
            //記録が空だったコアは旧パイプラインに任せる (端トリミング付き)
            if (isSwitch && this.scriptSkippedSwitches.contains(pos) && !be.shouldRerenderRail) {
                return false;
            }
            GLRecorder rec = this.staticCache.get(pos);
            boolean rebuilt = rec == null || be.shouldRerenderRail;
            if (rebuilt) {
                rec = new GLRecorder();
                GLRecorder.activate(rec);
                try {
                    this.renderer.modelGroupNames = model.getOriginalGroupNames();
                    if (isSwitch) {
                        //分岐: 全 RailMap をスクリプト静的描画 (本家 createRailPos は
                        //getAllRailMaps を全部回す)。これでスクリプトの shouldRenderObject
                        //(端トリミング/ピース循環) が分岐でも効き、AR 系レールで
                        //端のピースが飛び出さない。
                        RailMap[] allMaps = be.getAllRailMaps();
                        if (allMaps != null) {
                            for (int i = 0; i < allMaps.length; i++) {
                                if (allMaps[i] == null) {
                                    continue;
                                }
                                this.renderer.currentRailIndex = i;
                                this.renderer.renderMapOverride = allMaps[i];
                                this.renderer.renderRailStatic(be, 0.0D, 0.0D, 0.0D, partialTick, 0);
                            }
                        }
                    } else {
                        //この定義のレール 1 本だけ描く。重ねレール (サブレール) は
                        //RailCoreBlockEntityRenderer 側で各サブ定義のモデル+スクリプトを使い別途描く
                        //({@link #renderSubRail})。以前はここで 1+subRails.size() 回ループしていたが、
                        //モデルは差し替わらないため<b>本レールを重複描画</b>していただけで、サブレールの
                        //モデルは一切出ていなかった (ユーザー報告: 重ねても見た目が変わらない)。
                        this.renderer.currentRailIndex = 0;
                        this.renderer.renderRailStatic(be, 0.0D, 0.0D, 0.0D, partialTick, 0);
                        //本家 RailPartsRendererBase.renderRail は static の直後に dynamic も呼ぶ
                        //(Baru's Roof 等、renderRailDynamic に全部書くパック対策)。
                        this.renderer.renderRailDynamic(be, 0.0D, 0.0D, 0.0D, partialTick, 0);
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

            //分岐: レール本体は本家どおり renderRailDynamic (トング可動) が描く。
            //movement (転てつ状態) が変わらない限り記録を再利用する。
            GLRecorder dyn = null;
            if (isSwitch) {
                long dynKey = computeSwitchDynKey((TileEntityLargeRailSwitchCore) be);
                DynEntry cached = this.dynamicCache.get(pos);
                if (cached != null && cached.key == dynKey) {
                    dyn = cached.rec;
                } else {
                    dyn = new GLRecorder();
                    GLRecorder.activate(dyn);
                    try {
                        this.renderer.modelGroupNames = model.getOriginalGroupNames();
                        this.renderer.renderRailDynamic(be, 0.0D, 0.0D, 0.0D, partialTick, 0);
                    } catch (Throwable t) {
                        RealTrainModUnofficial.LOGGER.warn("Rail dynamic script failed at {}", pos, t);
                    } finally {
                        GLRecorder.deactivate();
                    }
                    this.dynamicCache.put(pos, new DynEntry(dynKey, dyn));
                }
            }

            boolean staticDrew = !rec.isEmpty();
            boolean dynDrew = dyn != null && !dyn.isEmpty();
            if (isSwitch && !staticDrew && !dynDrew) {
                //スクリプトが分岐で何も描かなかった → 旧パイプラインで描かせる
                this.scriptSkippedSwitches.add(pos);
                return false;
            }
            this.scriptSkippedSwitches.remove(pos);
            if (staticDrew) {
                //静的部分 (レール本体・枕木・バラスト) はレール 1 本 = 1 メッシュに焼いて
                //GPU に置きっぱなしにする (本家のディスプレイリスト相当)。
                //転てつのトング (dyn) は動くので焼かず、従来どおり毎フレーム描く。
                if (!RailMeshCache.draw(pos, rec, model, poseStack, packedLight, packedOverlay, rebuilt)) {
                    replay(rec, poseStack, buffer, packedLight, packedOverlay, model);
                }
            }
            if (dynDrew) {
                replay(dyn, poseStack, buffer, packedLight, packedOverlay, model);
            }
            return true;
        }

        /**
         * スクリプトが描画を拒否した分岐コア (毎フレームのスクリプト再実行を避けるキャッシュ)。
         */
        private final java.util.Set<BlockPos> scriptSkippedSwitches =
                java.util.concurrent.ConcurrentHashMap.newKeySet();

        /**
         * 分岐の動的描画 (トング) 記録キャッシュ。転てつ movement が変わったら再記録。
         */
        private final java.util.Map<BlockPos, DynEntry> dynamicCache = new java.util.concurrent.ConcurrentHashMap<>();

        private record DynEntry(long key, GLRecorder rec) {
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

        /**
         * トング可動パーツ (L0/L1/R0/R1 + railL/railR) を持つモデルか。
         * 持つ場合は転てつアニメのため既存パイプラインで描く必要がある。
         */
        private static boolean modelHasTongParts(MqoModelLoader.MqoModel model) {
            java.util.Set<String> groups = model.getAllNormalizedGroupNames();
            if (groups == null) {
                return false;
            }
            boolean hasTong = false;
            boolean hasRail = false;
            for (String g : groups) {
                String n = g.toLowerCase(java.util.Locale.ROOT);
                if (n.equals("l0") || n.equals("l1") || n.equals("r0") || n.equals("r1")) {
                    hasTong = true;
                }
                if (n.equals("raill") || n.equals("railr")) {
                    hasRail = true;
                }
            }
            return hasTong && hasRail;
        }
    }

    /**
     * RailMeshCache が「統合メッシュへの焼き込み」に使う再生 (捕獲用 MultiBufferSource に流す)。
     */
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
                    //Quaternion は記録時に確定済み (payload)
                    if (cmd.payload instanceof org.joml.Quaternionf quat) {
                        poseStack.mulPose(quat);
                    }
                }
                case SCALE -> poseStack.scale(cmd.a, cmd.b, cmd.c);
                case BRIGHTNESS -> light = (int) cmd.a;
                case COLOR -> {
                    //カラーオーバーレイは現状のレールスクリプトでは未使用
                }
                case RENDER_PARTS -> {
                    //正規化 Set は記録時に確定済み (payload) — 同一インスタンスで
                    //renderNamedGroups の IdentityHashMap キャッシュにヒットさせる
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
        //スクリプトの push/pop 不整合を補正
        while (depth > 0) {
            poseStack.popPose();
            depth--;
        }
    }
}
