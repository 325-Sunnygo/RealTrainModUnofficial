package jp.tokyokinkou.tims;

import com.portofino.realtrainmodunofficial.entity.TrainEntity;

/** E233 ドア状態ブリッジ。RTMU の doorMove を読む。 */
public final class E233DoorRuntime {
    private E233DoorRuntime() {
    }

    public static void initialize() {
    }

    public static DoorState snapshot(Object entity, int series) {
        float left = 0.0F;
        float right = 0.0F;
        if (entity instanceof TrainEntity train) {
            left = Math.min(1.0F, Math.abs(train.doorMoveL) / 60.0F);
            right = Math.min(1.0F, Math.abs(train.doorMoveR) / 60.0F);
        } else if (entity instanceof jp.ngt.rtm.entity.vehicle.EntityVehicleBase vehicle) {
            left = Math.min(1.0F, Math.abs(vehicle.doorMoveL) / 60.0F);
            right = Math.min(1.0F, Math.abs(vehicle.doorMoveR) / 60.0F);
        }
        return new DoorState(left, right);
    }

    public static void sound(Object updater, int series) {
    }

    public static final class DoorState {
        public final boolean cut = false;
        public final float nL;
        public final float nR;
        public final float cL = 0.0F;
        public final float cR = 0.0F;
        public final boolean lampL;
        public final boolean lampR = false;
        public final boolean cutLampL = false;
        public final boolean cutLampR = false;
        public final boolean recoveryL = false;
        public final boolean recoveryR = false;
        public final boolean recoveryLampL = false;
        public final boolean recoveryLampR = false;

        DoorState(float left, float right) {
            this.nL = left;
            this.nR = right;
            this.lampL = left > 0.0F;
        }
    }
}
