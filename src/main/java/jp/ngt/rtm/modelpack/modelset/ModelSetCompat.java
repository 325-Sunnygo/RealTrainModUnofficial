package jp.ngt.rtm.modelpack.modelset;

import jp.ngt.rtm.modelpack.cfg.TrainConfig;

/** RTMU の既存コードが参照する互換名。 実体は本家 ModelSetTrain と同じで、構築時… */
public class ModelSetCompat extends ModelSetTrain {
    public ModelSetCompat() {
        super();
    }

    public ModelSetCompat(TrainConfig config) {
        super(config);
    }
}
