package jp.ngt.rtm.modelpack.modelset;

import jp.ngt.rtm.modelpack.cfg.TrainConfig;

/**
 * 本家 ModelSetVehicleBase のスクリプト互換移植。
 * 本家の継承関係に合わせて {@link ModelSetBase} を継承する (getConfig/isDummy/serverSE/guiSE)。
 */
public class ModelSetCompat extends ModelSetBase<TrainConfig> {

    public ModelSetCompat(TrainConfig config) {
        super(config);
    }

    @Override
    public TrainConfig getDummyConfig() {
        return null;
    }
}
