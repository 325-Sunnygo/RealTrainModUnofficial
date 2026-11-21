package jp.ngt.rtm.modelpack.modelset;

import jp.ngt.rtm.modelpack.cfg.TrainConfig;

/** 本家 jp.ngt.rtm.modelpack.modelset.ModelSetTrain (K… */
public class ModelSetTrain extends ModelSetVehicleBase<TrainConfig> {
    public ModelSetTrain() {
        super();
    }

    public ModelSetTrain(TrainConfig config) {
        super(config);
    }

    @Override
    public TrainConfig getDummyConfig() {
        return TrainConfig.getDummyConfig();
    }
}
