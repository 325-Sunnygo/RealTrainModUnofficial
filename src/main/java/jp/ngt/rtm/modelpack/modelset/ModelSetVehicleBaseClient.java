package jp.ngt.rtm.modelpack.modelset;

import jp.ngt.ngtlib.io.ScriptUtil;
import jp.ngt.rtm.modelpack.ModelPackManager;
import jp.ngt.rtm.modelpack.cfg.VehicleBaseConfig;
import jp.ngt.rtm.render.ModelObject;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.script.ScriptEngine;

/** 本家 jp.ngt.rtm.modelpack.modelset.ModelSetVehicleB… */
@OnlyIn(Dist.CLIENT)
public abstract class ModelSetVehicleBaseClient<T extends VehicleBaseConfig> extends ModelSetVehicleBase<T> {
    public final ModelObject vehicleModel;
    public final ResourceLocation buttonTexture;
    public final ResourceLocation rollsignTexture;

    public final ResourceLocation sound_Stop;
    public final ResourceLocation sound_S_A;
    public final ResourceLocation sound_Acceleration;
    public final ResourceLocation sound_Deceleration;
    public final ResourceLocation sound_D_S;
    public final ResourceLocation sound_Horn;
    public final ResourceLocation sound_ATSChime;
    public final ResourceLocation sound_ATSBell;

    /** 走行音スクリプト (soundScriptPath) のエンジン。 */
    public ScriptEngine se;

    protected ModelSetVehicleBaseClient() {
        super();
        this.vehicleModel = null;
        this.buttonTexture = null;
        this.rollsignTexture = null;
        this.sound_Stop = null;
        this.sound_S_A = null;
        this.sound_Acceleration = null;
        this.sound_Deceleration = null;
        this.sound_D_S = null;
        this.sound_Horn = null;
        this.sound_ATSChime = this.getSoundResource("rtm:train.ats");
        this.sound_ATSBell = this.getSoundResource("rtm:train.ats_bell");
    }

    @SuppressWarnings("unchecked")
    protected ModelSetVehicleBaseClient(VehicleBaseConfig config) {
        super((T) config);
        this.vehicleModel = null;
        this.buttonTexture = config.buttonTexture == null ? null
                : ModelPackManager.INSTANCE.getResource(config.buttonTexture).toReal();
        this.rollsignTexture = config.rollsignTexture == null ? null
                : ModelPackManager.INSTANCE.getResource(config.rollsignTexture).toReal();

        this.sound_Stop = this.getSoundResource(config.sound_Stop);
        this.sound_S_A = this.getSoundResource(config.sound_S_A);
        this.sound_Acceleration = this.getSoundResource(config.sound_Acceleration);
        this.sound_Deceleration = this.getSoundResource(config.sound_Deceleration);
        this.sound_D_S = this.getSoundResource(config.sound_D_S);
        this.sound_Horn = this.getSoundResource(config.sound_Horn);

        this.sound_ATSChime = this.getSoundResource(config.sound_ATSChime == null
                ? "rtm:train.ats" : config.sound_ATSChime);
        this.sound_ATSBell = this.getSoundResource(config.sound_ATSBell == null
                ? "rtm:train.ats_bell" : config.sound_ATSBell);

        if (config.soundScriptPath != null && !config.soundScriptPath.isBlank()) {
            try {
                String script = ModelPackManager.INSTANCE.getScript(config.soundScriptPath);
                if (script != null) {
                    this.se = ScriptUtil.doScript(
                            com.portofino.realtrainmodunofficial.script.PackScriptSource.PRELUDE + script,
                            config.soundScriptPath);
                }
            } catch (Throwable t) {
                this.scriptLoadError = "sound:" + config.soundScriptPath + ": " + t.getMessage();
                com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.error(
                        "[RTMU] sound script load failed ({} / {}): {}",
                        config.getName(), config.soundScriptPath, t.toString());
            }
        }
    }
}
