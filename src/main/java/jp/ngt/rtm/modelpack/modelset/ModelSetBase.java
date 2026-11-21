package jp.ngt.rtm.modelpack.modelset;

import jp.ngt.ngtlib.io.ScriptUtil;
import jp.ngt.rtm.modelpack.ModelPackManager;
import jp.ngt.rtm.modelpack.cfg.ModelConfig;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import javax.script.ScriptEngine;

/** 本家 jp.ngt.rtm.modelpack.modelset.ModelSetBase<T e… */
public abstract class ModelSetBase<T> {
    protected final T cfg;
    private final boolean isDummyModel;
    /** 本家 ModelSetBase.dataFormatter: defaultValues を Da… */
    public final jp.ngt.rtm.modelpack.state.DataFormatter dataFormatter;

    /** サーバースクリプト (Server_*.js) のエンジン。全インスタンスで共有。 */
    public ScriptEngine serverSE;
    /** スクリプト読み込みに失敗した理由 (診断用)。成功時は null。 */
    public String scriptLoadError;

    /** GUI スクリプト (Gui_*.js) のエンジン。クライアントのみ。 */
    public ScriptEngine guiSE;
    public ResourceLocation guiTexture;

    /** ダミー用 (本家 ModelSetBase() と同じ)。 */
    protected ModelSetBase() {
        this.cfg = this.getDummyConfig();
        this.isDummyModel = true;
        this.dataFormatter = new jp.ngt.rtm.modelpack.state.DataFormatter(
                this.cfg instanceof jp.ngt.rtm.modelpack.cfg.ResourceConfig rc ? rc : null);
    }

    protected ModelSetBase(T config) {
        this.cfg = config;
        this.isDummyModel = false;
        this.dataFormatter = new jp.ngt.rtm.modelpack.state.DataFormatter(
                config instanceof jp.ngt.rtm.modelpack.cfg.ResourceConfig rc ? rc : null);

        if (config instanceof ModelConfig mcfg) {
            if (mcfg.serverScriptPath != null && !mcfg.serverScriptPath.isBlank()) {
                try {
                    String script = ModelPackManager.INSTANCE.getScript(mcfg.serverScriptPath);
                    if (script != null) {
                        this.serverSE = ScriptUtil.doScript(
                                com.portofino.realtrainmodunofficial.script.PackScriptSource.SERVER_PRELUDE + script,
                                mcfg.serverScriptPath);
                    }
                } catch (Throwable t) {
                    this.scriptLoadError = "server:" + mcfg.serverScriptPath + ": " + t.getMessage();
                    com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.error(
                            "[RTMU] server script load failed ({} / {}): {}",
                            mcfg.getName(), mcfg.serverScriptPath, t.toString());
                }
            }

            if (FMLEnvironment.dist == Dist.CLIENT
                    && mcfg.guiScriptPath != null && !mcfg.guiScriptPath.isBlank()) {
                try {
                    String script = ModelPackManager.INSTANCE.getScript(mcfg.guiScriptPath);
                    if (script != null) {
                        this.guiSE = ScriptUtil.doScript(
                                com.portofino.realtrainmodunofficial.script.PackScriptSource.PRELUDE + script,
                                mcfg.guiScriptPath);
                    }
                } catch (Throwable t) {
                    this.scriptLoadError = "gui:" + mcfg.guiScriptPath + ": " + t.getMessage();
                    com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.error(
                            "[RTMU] gui script load failed ({} / {}): {}",
                            mcfg.getName(), mcfg.guiScriptPath, t.toString());
                }
                if (mcfg.guiTexture != null && !mcfg.guiTexture.isBlank()) {
                    this.guiTexture = ModelPackManager.INSTANCE.getResource(mcfg.guiTexture).toReal();
                }
            }
        }
    }

    public T getConfig() {
        return this.cfg;
    }

    public abstract T getDummyConfig();

    public boolean isDummy() {
        return this.isDummyModel;
    }

    /** 本家 ModelSetBase.getSoundResource。 */
    protected ResourceLocation getSoundResource(String path) {
        if (path != null && !path.isEmpty()) {
            String domain = "rtm";
            String file = path;
            if (path.contains(":")) {
                String[] sa = path.split(":", 2);
                domain = sa[0];
                file = sa[1];
            }
            return ModelPackManager.INSTANCE.getResource(domain, file).toReal();
        }
        return null;
    }
}
