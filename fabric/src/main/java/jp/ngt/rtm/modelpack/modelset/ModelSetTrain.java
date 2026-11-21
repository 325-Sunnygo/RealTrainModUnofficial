package jp.ngt.rtm.modelpack.modelset;

import jp.ngt.rtm.modelpack.cfg.TrainConfig;

/**
 * 本家 KaizPatchX jp.ngt.rtm.modelpack.modelset.ModelSetTrain の移植。
 */
public class ModelSetTrain extends ModelSetVehicleBase<TrainConfig> {

    public ModelSetTrain() {
        super();
    }

    public ModelSetTrain(TrainConfig par1) {
        super(par1);
    }

    @Override
    public TrainConfig getDummyConfig() {
        return TrainConfig.getDummyConfig();
    }
}
