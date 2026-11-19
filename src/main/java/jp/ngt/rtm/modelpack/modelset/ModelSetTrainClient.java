package jp.ngt.rtm.modelpack.modelset;

import jp.ngt.rtm.modelpack.cfg.TrainConfig;

/** 本家 jp.ngt.rtm.modelpack.modelset.ModelSetTrainCli… */
public class ModelSetTrainClient extends ModelSetVehicleBaseClient<TrainConfig> {
    public final String sound_brakeRelease_s;
    public final String sound_brakeRelease_w;

    public ModelSetTrainClient() {
        super();
        this.sound_brakeRelease_s = null;
        this.sound_brakeRelease_w = null;
    }

    public ModelSetTrainClient(TrainConfig config) {
        super(config);
        // 本家は ResourceLocation を "domain:path" 文字列にして保持する。
        this.sound_brakeRelease_s = config.sound_BrakeRelease == null ? null
                : jp.ngt.rtm.modelpack.ModelPackManager.INSTANCE.getResource(config.sound_BrakeRelease).toString();
        this.sound_brakeRelease_w = config.sound_BrakeRelease2 == null ? null
                : jp.ngt.rtm.modelpack.ModelPackManager.INSTANCE.getResource(config.sound_BrakeRelease2).toString();
    }

    @Override
    public TrainConfig getDummyConfig() {
        return TrainConfig.getDummyConfig();
    }
}
