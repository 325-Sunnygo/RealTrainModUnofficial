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
 * TODO(Phase 3 続き): 車両系ヘルパー (getMCTime/getData/setData/renderLightEffect 等) の拡充。
 */
public class PartsRenderer {
    public static java.util.Calendar CALENDAR = java.util.Calendar.getInstance();

    protected ScriptEngine script;
    protected ModelObject modelObject = new ModelObject(null);
    protected Object modelSet;
    protected final java.util.List<Parts> partsList = new java.util.ArrayList<>();
    protected final java.util.Map<Integer, Object> dataMap = new java.util.HashMap<>();

    /**
     * 本家: マテリアルごとの描画パスで現在のマテリアル ID (スクリプトが直接参照)
     */
    public int currentMatId;
    public int currentPass;

    /**
     * 直近の render() でスクリプトが例外を投げたか。
     * <p>
     * ★これが無いと「モデルが透明になる」。本家 (と RTMU) はスクリプトの例外を
     * {@code doScriptIgnoreError} で握りつぶすが、握りつぶした結果、途中まで記録された
     * GL コマンド (glPushMatrix だけ等) が残る。呼び出し側はそれを見て「スクリプトが
     * 描画を担当した」と判断し、素のモデル描画をスキップしてしまう。
     * <p>
     * 実例: RTM 標準の Render223.js は 1 行目で {@code entity.getVehicleState(...)} を呼ぶが、
     * RTMU の車両エンティティにそのメソッドが無く即 TypeError。結果 223 系の車体だけが
     * 丸ごと消えていた (当たり判定は残るので「透明な列車」に見える)。
     * <p>
     * スクリプトが落ちたらこのフラグを立て、呼び出し側は「スクリプト描画は失敗」として
     * 素のモデル描画にフォールバックする。アニメーションは効かないが車体は必ず見える。
     */
    private boolean scriptFailed;

    public PartsRenderer(String... par1) {
    }

    /**
     * スクリプトを実行し、例外が出たら {@link #scriptFailed} を立てる。
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
            if (!this.warnedScriptFail) {
                this.warnedScriptFail = true;
                com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.warn(
                    "Render script failed (falling back to plain model rendering): {}",
                    String.valueOf(t.getCause() != null ? t.getCause() : t));
            }
        }
    }

    private boolean warnedScriptFail;

    /**
     * 直近の render() が失敗したかを読み取ってフラグを下ろす。
     *
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
            this.modelObject = mo;
        }
        if (this.script != null) {
            ScriptUtil.doScriptIgnoreError(this.script, "init", modelSet, this.modelObject);
        }
        this.partsList.forEach(parts -> parts.init(this));
    }

    public ModelObject getModelObject() {
        return this.modelObject;
    }

    public Parts registerParts(Parts par1) {
        this.partsList.add(par1);
        return par1;
    }

    /**
     * 本家: 指定された座標を中心として回転 (GL 記録)
     */
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

    /**
     * 本家 sigmoid(float)
     */
    public float sigmoid(float par1) {
        if (par1 == 1.0F || par1 == 0.0F) {
            return par1;
        }
        float f0 = (par1 - 0.5F) * 5.0F;
        float f1 = (float) ((double) f0 / Math.sqrt(1.0D + (double) f0 * (double) f0));
        return (f1 + 1.0F) * 0.5F;
    }

    /**
     * 本家: modelSet.getConfig().getName() — スクリプトが車種判定に使う
     */
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
     * 本家 PartsRenderer.getColor: 対象の {@code ResourceState.color} (プレイヤーが設定する色)。
     * <pre>
     * return entity instanceof IResourceSelector
     *      ? ((IResourceSelector) entity).getResourceState().color : 0;
     * </pre>
     *
     * <p><b>未実装だったため電線スクリプトが動いていなかった。</b> RenderBasicWire.js は
     * {@code tessellator.setColorOpaque_I(renderer.getColor(tileEntity))} を呼ぶので、
     * このメソッドが無いと TypeError でスクリプトごと落ち、呼び出し側が「何も描かなかった」と
     * 判断して RTMU 独自のハードコード色 (ほぼ黒) のフォールバックに切り替わる。
     * 結果、既定のワイヤーが真っ黒な電線になっていた。
     *
     * <p>色を持たない対象では本家 {@code ResourceState.color} の既定
     * {@code 16777215} (0xFFFFFF = 白) を返す。本家が 0 を返すのは
     * {@code IResourceSelector} ですらない場合だけで、電線の TileEntity は該当しない。
     */
    public int getColor(Object entity) {
        if (entity instanceof com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity io) {
            return io.getWireColor();
        }
        return 16777215;
    }

    public int getMCTime() {
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
        return (int) ((System.currentTimeMillis() / 1000L) % 86400L);
    }

    public long getSystemTimeMillis() {
        return System.currentTimeMillis();
    }

    public int getSystemHour() {
        return CALENDAR.get(java.util.Calendar.HOUR_OF_DAY);
    }

    public int getSystemMinute() {
        return CALENDAR.get(java.util.Calendar.MINUTE);
    }

    public int getSystemSecond() {
        return CALENDAR.get(java.util.Calendar.SECOND);
    }

    public int getSystemMillisecond() {
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

    /**
     * 本家: 前照灯のボリュームライト描画。TODO: 未移植 (安全に無視)。
     */
    public void renderLightEffect(Object normal, double[] pos, float rL, float rS, float length, int color, int type, boolean reverse) {
    }

    /**
     * Parts.render() から呼ばれる (GLRecorder への記録)。
     */
    public void recordRenderParts(String objName) {
        GLRecorder r = GLRecorder.active();
        if (r != null) {
            r.renderParts(objName);
        }
    }

    /**
     * Parts.render() から呼ばれる — 正規化済み名前 Set を 1 コマンドで記録。
     */
    public void recordRenderPartsSet(java.util.Set<String> normalizedNames) {
        GLRecorder r = GLRecorder.active();
        if (r != null) {
            r.renderGroups(normalizedNames);
        }
    }

    public void bindTexture(Object texture) {
        //GLRecorder に BIND_TEXTURE として記録 (null でデフォルト復帰)
        jp.ngt.ngtlib.util.NGTUtilClient.bindTexture(texture);
    }

    /**
     * 本家: world.getLightBrightnessForSkyBlocks 相当のパック輝度。
     */
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
     * (E257 等: 室内灯で {@code setFullBright(); … renderer.enableLighting();})。
     * enableLighting = 環境光へ復帰 (brightness 負値 = 再生側が packedLight に戻す)。
     */
    public void disableLighting() {
        //フルブライト化はスクリプトが直後に setBrightness/setLightmapMaxBrightness で行う
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

    public void debug(String msg) {
        jp.ngt.ngtlib.io.NGTLog.debug(msg);
    }
}
