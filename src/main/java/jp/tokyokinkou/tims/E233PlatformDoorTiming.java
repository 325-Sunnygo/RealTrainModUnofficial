package jp.tokyokinkou.tims;

import com.portofino.realtrainmodunofficial.entity.TrainEntity;

/** ドア開閉タイミングのブリッジ。 */
public final class E233PlatformDoorTiming {
    private E233PlatformDoorTiming() {
    }

    public static float nativeMove(Object entity, boolean left) {
        if (entity instanceof TrainEntity train) {
            return Math.min(1.0F, Math.abs(left ? train.doorMoveL : train.doorMoveR) / 60.0F);
        }
        if (entity instanceof jp.ngt.rtm.entity.vehicle.EntityVehicleBase vehicle) {
            return Math.min(1.0F, Math.abs(left ? vehicle.doorMoveL : vehicle.doorMoveR) / 60.0F);
        }
        return 0.0F;
    }

    public static void openingSound(Object updater, String sound) {
    }
}
