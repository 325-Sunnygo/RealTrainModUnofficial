package jp.kaiz.atsassistmod.controller.trainprotection;

/** 本家 ATSPsController の移植 (本家も基底のみのスケルトン)。 */
public class ATSPsController extends TrainProtection {

    @Override
    public TrainProtectionType getType() {
        return TrainProtectionType.ATSPs;
    }
}
