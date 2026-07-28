package jp.kaiz.atsassistmod.controller.trainprotection;

import jp.ngt.rtm.entity.train.EntityTrainBase;

/**
 * 本家 jp.kaiz.atsassistmod.controller.trainprotection.TrainProtection の移植 (保安装置の基底)。
 * getNotch が負値を返したら車上装置がブレーキを強制する。
 */
public class TrainProtection {
    protected EntityTrainBase train;

    public void onTick(EntityTrainBase train, double distance) throws Exception {
        this.train = train;
    }

    /** @param speedH 現在速度 (km/h) */
    public int getNotch(float speedH) {
        return 1;
    }

    public TrainProtectionType getType() {
        return TrainProtectionType.NONE;
    }

    /** HUD 表示用の制限速度 (km/h)。MAX_VALUE = 表示なし。 */
    public int getDisplaySpeed() {
        return Integer.MAX_VALUE;
    }
}
