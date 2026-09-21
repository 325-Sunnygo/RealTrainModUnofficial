package jp.ngt.rtm.modelpack.modelset;

import net.minecraft.resources.ResourceLocation;

import javax.script.ScriptEngine;

/**
 * 本家 {@code jp.ngt.rtm.modelpack.modelset.ModelSetBase<T extends ModelConfig>} の移植。
 *
 * <p>本家はコンストラクタで {@code serverScriptPath}/{@code guiScriptPath} から
 * {@link #serverSE}/{@link #guiSE} を読み込むが、RTMU は {@code getModelSet()} が
 * 呼ばれるたびに ModelSet を生成するため、ここでは<b>読み込まない</b>
 * (毎tickで Nashorn エンジンを作ると重すぎる)。エンジンは各描画/サーバー経路が
 * 実体 (VehicleScriptRenderers / CarServerScripts 等) で保持している。
 *
 * <p>したがって {@code getModelSet().serverSE} は null になり得る。
 * スクリプトが engine を直接必要とする場面は無い (エンジンは呼び出し側が渡す) ため実害は無い。
 */
public abstract class ModelSetBase<T> {
    protected final T cfg;
    private final boolean isDummyModel;

    public ScriptEngine serverSE;
    public ScriptEngine guiSE;
    public ResourceLocation guiTexture;

    /** ダミー用 (本家 ModelSetBase() と同じ)。 */
    protected ModelSetBase() {
        this.cfg = this.getDummyConfig();
        this.isDummyModel = true;
    }

    protected ModelSetBase(T config) {
        this.cfg = config;
        this.isDummyModel = false;
    }

    public T getConfig() {
        return this.cfg;
    }

    public abstract T getDummyConfig();

    public boolean isDummy() {
        return this.isDummyModel;
    }
}
