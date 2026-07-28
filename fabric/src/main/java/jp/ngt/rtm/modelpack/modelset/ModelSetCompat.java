package jp.ngt.rtm.modelpack.modelset;

import jp.ngt.rtm.modelpack.cfg.TrainConfig;

/**
 * 本家 ModelSetVehicleBase のスクリプト互換最小移植 (getConfig のみ)。
 * TODO(Phase 4): ModelSetTrain(Client) の本実装に置換。
 */
public class ModelSetCompat {
    private final TrainConfig config;

    public ModelSetCompat(TrainConfig config) {
        this.config = config;
    }

    public TrainConfig getConfig() {
        return this.config;
    }

    public boolean isDummy() {
        return false;
    }
}
