package jp.kaiz.atsassistmod.controller.trainprotection;

/** 本家 StationPremisesController の移植 (構内 25km/h 照査)。 */
public class StationPremisesController extends TrainProtection {

    @Override
    public int getNotch(float speedH) {
        if (speedH > 25) {
            return -8;
        } else {
            return 1;
        }
    }

    @Override
    public TrainProtectionType getType() {
        return TrainProtectionType.STATION_PREMISES;
    }

    @Override
    public int getDisplaySpeed() {
        return 25;
    }
}
