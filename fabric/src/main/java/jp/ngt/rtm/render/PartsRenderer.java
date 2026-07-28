package jp.ngt.rtm.render;

import jp.ngt.ngtlib.io.ScriptUtil;
import jp.ngt.ngtlib.math.NGTMath;
import jp.ngt.ngtlib.renderer.GLRecorder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.script.ScriptEngine;

/**
 * 本家 jp.ngt.rtm.render.PartsRenderer の段階的移植 (レールスクリプトが使う面から)。
 * GL 呼び出し・Parts 描画は GLRecorder に記録され、BER 側で PoseStack に再生される。
 */
public class PartsRenderer {
    public static java.util.Calendar CALENDAR = java.util.Calendar.getInstance();

    // スクリプトが時刻系API (getTick/getSystemTime/getMCTime等) を読んだ = 描画結果が時間依存の合図。
    // VehicleScriptRenderers はこれを見て、その車両を「静止でも毎フレーム再実行」に切り替える。
    private static boolean timeAccessed;

    /** 時刻系APIが呼ばれたら立てる。 */
    protected static void markTimeAccessed() {
        timeAccessed = true;
    }

    /** 1回ぶんの描画前に呼んで検知フラグを倒す。 */
    public static void clearTimeAccessed() {
        timeAccessed = false;
    }

    /** 直前の描画中に時刻系APIが読まれたか。 */
    public static boolean wasTimeAccessed() {
        return timeAccessed;
    }

    /** 本家 PACKAGE_NAME: スクリプトの renderClass 解決に使う既定パッケージ。 */
    public static final String PACKAGE_NAME = "jp.ngt.rtm.render";
    /** 本家 rendererMap: renderClass 名 → クラス。 */
    public static java.util.Map<String, Class<?>> rendererMap = new java.util.HashMap<>();
    /** 本家 BRIGHTNESS_RATE: 輝度値の正規化係数。 */
    protected static final double BRIGHTNESS_RATE = 1.0D / 256.0D;
    protected static final boolean DEBUG = false;
    /** 本家 DIV_NUM: ライトエフェクトの円周分割数。 */
    protected static final byte DIV_NUM = 32;
    /** 本家 ANGLE: DIV_NUM 分割 1 つ分の角度 (ラジアン)。 */
    protected static final float ANGLE = (float) (Math.PI * 2.0D / (double) DIV_NUM);

    protected ScriptEngine script;
    /**
     * 本家のフィールド名は modelObj (KaizPatchX PartsRenderer.java:39)。
     * スクリプトは getter ではなくリフレクションでフィールド名を直接読む:
     */
    protected ModelObject modelObj = new ModelObject(null);
    protected Object modelSet;
    protected final java.util.List<Parts> partsList = new java.util.ArrayList<>();
    protected final java.util.Map<Integer, Object> dataMap = new java.util.HashMap<>();

    /** 本家 targetsList: クリック可能パーツ (ActionParts) の一覧。 */
    protected final java.util.List<Parts> targetsList = new java.util.ArrayList<>();
    /** 本家 hittedEntity: 今カーソルが当たっている車両。 */
    public Object hittedEntity;
    /** 本家 hittedParts: 車両ごとの当たっているパーツ。 */
    protected final java.util.Map<Object, Parts> hittedParts = new java.util.HashMap<>();

    /** 本家: マテリアルごとの描画パスで現在のマテリアル ID (スクリプトが直接参照) */
    public int currentMatId;
    public int currentPass;

    /**
     * 直近の render でスクリプトが例外を投げたか。
     * ★これが無いと「モデルが透明になる」。
     */
    private boolean scriptFailed;

    public PartsRenderer(String... par1) {
    }

    /**
     * スクリプトを実行し、例外が出たら #scriptFailed を立てる。
     * 例外自体は本家どおり握りつぶす (1 フレームの失敗でクラッシュさせない)。
     */
    protected void execRenderScript(Object entity, int pass, float partialTick) {
        if (this.script == null) {
            return;
        }
        try {
            jp.ngt.ngtlib.io.ScriptUtil.doScriptFunction(this.script, "render", entity, pass, partialTick);
        } catch (Throwable t) {
            this.scriptFailed = true;
            // 失敗の種類ごとに 1 回出す。以前は「このレンダラで 1 回だけ」だったため、
            // 起動直後に別の理由で 1 回出ると以後の失敗が全て無音になり、
            // ClassCastException で描画スクリプトが丸ごと落ちていても気付けなかった。
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            String key = pass + "|" + cause;
            if (this.loggedScriptFailures.size() < 32 && this.loggedScriptFailures.add(key)) {
                com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.warn(
                    "[RTMU] 描画スクリプトが落ちました (素モデル描画へフォールバック) pass={} script={}: {}",
                    pass, this.scriptName == null ? "?" : this.scriptName, String.valueOf(cause));
            }
        }
    }

    /** 既にログした失敗 (pass, 例外) の組。 */
    private final java.util.Set<String> loggedScriptFailures = new java.util.HashSet<>();

    /** ログ用のスクリプト名。どのパックが落ちたか判らないと追えない。 */
    public String scriptName;

    /**
     * 直近の render が失敗したかを読み取ってフラグを下ろす。
     * @return true = スクリプトが落ちた (呼び出し側は素のモデル描画へフォールバックすること)
     */
    public boolean consumeScriptFailure() {
        boolean failed = this.scriptFailed;
        this.scriptFailed = false;
        return failed;
    }

    public void setScript(ScriptEngine se) {
        this.script = se;
    }

    public ScriptEngine getScript() {
        return this.script;
    }

    public void init(Object modelSet, Object modelObject) {
        this.modelSet = modelSet;
        if (modelObject instanceof ModelObject mo) {
            this.modelObj = mo;
        }
        if (this.script != null) {
            ScriptUtil.doScriptIgnoreError(this.script, "init", modelSet, this.modelObj);
        }
        this.partsList.forEach(parts -> parts.init(this));
    }

    public ModelObject getModelObject() {
        return this.modelObj;
    }

    /**
     * 本家のフィールド名は modelObj。Nashorn はプロパティ参照を getter へ
     * 解決するので、この名前で renderer.modelObj と書ける。
     */
    public ModelObject getModelObj() {
        return this.modelObj;
    }

    public Parts registerParts(Parts par1) {
        this.partsList.add(par1);
        // 本家はクリック可能パーツ (ActionParts) を targetsList にも積み、id を 1 始まりで振る
        if (par1 != null && par1.isActionParts()) {
            par1.id = this.targetsList.size() + 1;
            this.targetsList.add(par1);
        }
        return par1;
    }

    /**
     * 本家シグネチャ getRendererWithScript(ResourceLocation par1, String... args)
     * (KaizPatchX PartsRenderer.java:440)。
     * // test.zip!.../__targets__/kaizpatch/scripts/hi03_lib/lib_RTMApiCompatClient.compat.js:38
     * Packages.jp.ngt.rtm.render.PartsRenderer.getRendererWithScript.apply(..., [resource, ...args]);
     */
    public static PartsRenderer getRendererWithScript(Object resource, String... args)
            throws ReflectiveOperationException {
        if (resource instanceof ScriptEngine se) {
            return getRendererWithScript(se, args);
        }
        String script = jp.ngt.rtm.modelpack.ModelPackManager.INSTANCE.getScript(resource);
        if (script == null || script.isBlank()) {
            return null;
        }
        ScriptEngine se = jp.ngt.ngtlib.io.ScriptUtil.doScript(
                com.portofino.realtrainmodunofficial.script.PackScriptSource.PRELUDE
                        + com.portofino.realtrainmodunofficial.script.PackScriptSource.prepare(script, String.valueOf(resource)));
        return getRendererWithScript(se, args);
    }

    /**
     * 本家 getRendererWithScript: スクリプトの renderClass で指定された
     * PartsRenderer サブクラスを生成し、スクリプトを結びつける。
     */
    public static PartsRenderer getRendererWithScript(ScriptEngine se, String... args)
            throws ReflectiveOperationException {
        String className = se == null ? null : String.valueOf(se.get("renderClass"));
        Class<?> clazz = className == null || className.isEmpty() || "null".equals(className)
                ? PartsRenderer.class
                : rendererMap.computeIfAbsent(className, n -> {
                    try {
                        return Class.forName(n.contains(".") ? n : PACKAGE_NAME + "." + n);
                    } catch (ClassNotFoundException e) {
                        return PartsRenderer.class;
                    }
                });
        PartsRenderer renderer = (PartsRenderer) clazz
                .getConstructor(String[].class).newInstance((Object) args);
        renderer.setScript(se);
        if (se != null) {
            se.put("renderer", renderer);
        }
        return renderer;
    }

    /** 本家: 指定された座標を中心として回転 (GL 記録) */
    public void rotate(float angle, char axis, float x, float y, float z) {
        GLRecorder r = GLRecorder.active();
        if (r == null) {
            return;
        }
        r.translate(x, y, z);
        switch (axis) {
            case 'X' -> r.rotate(angle, 1.0F, 0.0F, 0.0F);
            case 'Y' -> r.rotate(angle, 0.0F, 1.0F, 0.0F);
            case 'Z' -> r.rotate(angle, 0.0F, 0.0F, 1.0F);
            default -> {
            }
        }
        r.translate(-x, -y, -z);
    }

    /** 本家 sigmoid(float) */
    public float sigmoid(float par1) {
        if (par1 == 1.0F || par1 == 0.0F) {
            return par1;
        }
        float f0 = (par1 - 0.5F) * 5.0F;
        float f1 = (float) ((double) f0 / Math.sqrt(1.0D + (double) f0 * (double) f0));
        return (f1 + 1.0F) * 0.5F;
    }

    /** 本家: modelSet.getConfig.getName — スクリプトが車種判定に使う */
    /**
     * 本家 RailPartsRendererBase 限定の API だが、パックによっては車両/設置物の
     * レンダラーからも呼ばれる。落とさないよう空実装を用意する
     * (レール用は RailPartsRenderer が override する)。
     */
    public void renderStaticParts(Object tile, double x, double y, double z) {
    }

    public String getModelName() {
        if (this.modelSet instanceof jp.ngt.rtm.modelpack.modelset.ModelSetCompat msc) {
            return msc.getConfig().getName();
        }
        return "";
    }

    /**
     * 本家 PartsRenderer.getColor: 対象の ResourceState.color (プレイヤーが設定する色)。
     * return entity instanceof IResourceSelector
     * ? ((IResourceSelector) entity).getResourceState.color : 0;
     */
    public int getColor(Object entity) {
        if (entity instanceof com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity io) {
            return io.getWireColor();
        }
        return 16777215;
    }

    public int getMCTime() {
        markTimeAccessed();
        Level level = Minecraft.getInstance().level;
        return level == null ? 0 : (int) (level.getDayTime() % 24000L);
    }

    public int getMCHour() {
        return ((this.getMCTime() / 1000) + 6) % 24;
    }

    public int getMCMinute() {
        return (int) ((float) (this.getMCTime() % 1000) * 0.06F);
    }

    public int getSystemTime() {
        markTimeAccessed();
        return (int) ((System.currentTimeMillis() / 1000L) % 86400L);
    }

    public long getSystemTimeMillis() {
        markTimeAccessed();
        return System.currentTimeMillis();
    }

    public int getSystemHour() {
        markTimeAccessed();
        return CALENDAR.get(java.util.Calendar.HOUR_OF_DAY);
    }

    public int getSystemMinute() {
        markTimeAccessed();
        return CALENDAR.get(java.util.Calendar.MINUTE);
    }

    public int getSystemSecond() {
        markTimeAccessed();
        return CALENDAR.get(java.util.Calendar.SECOND);
    }

    public int getSystemMillisecond() {
        markTimeAccessed();
        CALENDAR.setTimeInMillis(System.currentTimeMillis());
        return CALENDAR.get(java.util.Calendar.MILLISECOND);
    }

    public Object getData(int id) {
        Object v = this.dataMap.get(id);
        return v != null ? v : 0;
    }

    public void setData(int id, Object value) {
        this.dataMap.put(id, value);
    }

    /** 本家: 前照灯のボリュームライト描画。TODO: 未移植 (安全に無視)。 */
    public void renderLightEffect(Object normal, double[] pos, float rL, float rS, float length, int color, int type, boolean reverse) {
    }

    /** Parts.render から呼ばれる (GLRecorder への記録)。 */
    public void recordRenderParts(String objName) {
        GLRecorder r = GLRecorder.active();
        if (r != null) {
            r.renderParts(objName);
        }
    }

    /** Parts.render から呼ばれる — 正規化済み名前 Set を 1 コマンドで記録。 */
    public void recordRenderPartsSet(java.util.Set<String> normalizedNames) {
        GLRecorder r = GLRecorder.active();
        if (r != null) {
            r.renderGroups(normalizedNames);
        }
    }

    public void bindTexture(Object texture) {
        // GLRecorder に BIND_TEXTURE として記録 (null でデフォルト復帰)
        jp.ngt.ngtlib.util.NGTUtilClient.bindTexture(texture);
    }

    /** 本家: world.getLightBrightnessForSkyBlocks 相当のパック輝度。 */
    public int getBrightness(Object world, double x, double y, double z) {
        if (world instanceof Level level) {
            BlockPos pos = new BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));
            return net.minecraft.client.renderer.LevelRenderer.getLightColor(level, pos);
        }
        return 0xF000F0;
    }

    public void setBrightness(int packedLight) {
        GLRecorder r = GLRecorder.active();
        if (r != null) {
            r.brightness(packedLight);
        }
    }

    /**
     * 本家 GLHelper.disableLighting/enableLighting をレンダラー経由で呼ぶスクリプト互換
     * 等: 室内灯で setFullBright; … renderer.enableLighting;)。
     * enableLighting = 環境光へ復帰 (brightness 負値 = 再生側が packedLight に戻す)。
     */
    public void disableLighting() {
        // フルブライト化はスクリプトが直後に setBrightness/setLightmapMaxBrightness で行う
    }

    public void enableLighting() {
        GLRecorder r = GLRecorder.active();
        if (r != null) {
            r.brightness(-1);
        }
    }

    public Level getWorld(Object tile) {
        if (tile instanceof BlockEntity be) {
            return be.getLevel();
        }
        return Minecraft.getInstance().level;
    }

    public int getX(Object tile) {
        return tile instanceof BlockEntity be ? be.getBlockPos().getX() : 0;
    }

    public int getY(Object tile) {
        return tile instanceof BlockEntity be ? be.getBlockPos().getY() : 0;
    }

    public int getZ(Object tile) {
        return tile instanceof BlockEntity be ? be.getBlockPos().getZ() : 0;
    }

    public double sigmoid(double x, double c) {
        return NGTMath.sigmoid(x, c);
    }

    /**
     * 本家 PartsRenderer.spawnParticle: 粒子名 (文字列) で粒子を出す。
     * SL パックの蒸気/煙演出が renderer.spawnParticle(entity, "explode", ...) で呼ぶ。
     */
    public void spawnParticle(Object entity, String name, double posX, double posY, double posZ,
                              double speedX, double speedY, double speedZ) {
        Level level = getWorld(entity);
        if (level != null && level.isClientSide) {
            level.addParticle(jp.ngt.mccompat.EnumParticleTypes.particleByLegacyName(name),
                    posX, posY, posZ, speedX, speedY, speedZ);
        }
    }

    public void debug(String msg) {
        jp.ngt.ngtlib.io.NGTLog.debug(msg);
    }

    /** 本家preRender: 既定は何もしない。 */
    public void preRender(Object t, boolean smoothing, boolean culling, float par3) {
    }

    /** 本家postRender: 既定は何もしない。 */
    public void postRender(Object t, boolean smoothing, boolean culling, float par3) {
    }

    /** 本家render: サブクラスが実装。 */
    public void render(Object t, int pass, float partialTick) {
        this.currentPass = pass;
        this.execRenderScript(t, pass, partialTick);
    }

    /** 本家getViewerVec: 対象座標→視点のベクトル。 */
    public static jp.ngt.ngtlib.math.Vec3 getViewerVec(double x, double y, double z) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.world.entity.Entity viewer = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
        if (viewer == null) {
            return new jp.ngt.ngtlib.math.Vec3(0.0D, 1.0D, 0.0D);
        }
        return new jp.ngt.ngtlib.math.Vec3(
            viewer.getX() - x, viewer.getEyeY() - y, viewer.getZ() - z);
    }

    /** 本家validPath: アセットが存在するか。 */
    public boolean validPath(String path) {
        return path != null && !path.isBlank()
            && jp.ngt.ngtlib.io.NGTFileLoader.findAsset(path) != null;
    }

    /** 本家renderLightEffectS: ボリュームライトは未対応(安全に無視)。 */
    public static void renderLightEffectS(Object normal, double x, double y, double z,
                                          float rL, float rS, float length, int color, int type, boolean reverse) {
    }

    /** 本家TileEntityPartsRenderer.getMetadata互換。 */
    public int getMetadata(Object tile) {
        if (tile instanceof BlockEntity be && be.getLevel() != null) {
            return jp.ngt.ngtlib.block.BlockUtil.getMetadata(be.getLevel(),
                be.getBlockPos().getX(), be.getBlockPos().getY(), be.getBlockPos().getZ());
        }
        return 0;
    }


    /**
     * 本家 rotateAndRender: 指定座標を中心に 3 軸回転してからパーツを描く。
     * 角度はラジアン (本家は内部で度へ変換して glRotatef している)。
     */
    public void rotateAndRender(Parts parts, float x, float y, float z,
                                float rotationX, float rotationY, float rotationZ) {
        GLRecorder r = GLRecorder.active();
        if (r == null || parts == null) {
            return;
        }
        r.push();
        r.translate(x, y, z);
        r.rotate((float) Math.toDegrees(rotationZ), 0.0F, 0.0F, 1.0F);
        r.rotate((float) Math.toDegrees(rotationY), 0.0F, 1.0F, 0.0F);
        r.rotate((float) Math.toDegrees(rotationX), 1.0F, 0.0F, 0.0F);
        r.translate(-x, -y, -z);
        parts.render(this);
        r.pop();
    }

}
