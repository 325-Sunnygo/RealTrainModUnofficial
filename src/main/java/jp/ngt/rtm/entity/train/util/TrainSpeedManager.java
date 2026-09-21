package jp.ngt.rtm.entity.train.util;

import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.modelpack.cfg.TrainConfig;

/**
 * 本家 jp.ngt.rtm.entity.train.util.TrainSpeedManager の忠実移植。
 * useVariableAcceleration/Deceleration のときはサーバースクリプトの
 * getAcceleration/getDeceleration(train, prevSpeed) を呼ぶ (KaizPatchX と同一)。
 */
public final class TrainSpeedManager {
    private static final float[] BRAKE = {-0.0005F, -0.001F, -0.0015F, -0.002F, -0.0025F, -0.003F, -0.0035F, -0.01F};

    private TrainSpeedManager() {
    }

    public static float getAcceleration(EntityTrainBase train, int notch, float prevSpeed, TrainConfig cfg) {
        if (notch == 0) {
            return 0.0F;
        } else if (notch > 0) {
            --notch;
            if (prevSpeed >= cfg.maxSpeed[Math.min(cfg.maxSpeed.length - 1, notch)]) {
                return 0.0F;
            } else {
                if (cfg.useVariableAcceleration) {
                    Object obj = jp.ngt.ngtlib.io.ScriptUtil.doScriptIgnoreError(
                            train.getServerScriptEngine(),
                            "getAcceleration",
                            train,
                            prevSpeed);
                    return obj != null ? Float.parseFloat(obj.toString()) : 0.0F;
                }
                return cfg.accelerateions[Math.min(cfg.accelerateions.length - 1, notch)];
            }
        } else {
            float deceleration;
            if (cfg.useVariableDeceleration) {
                Object obj = jp.ngt.ngtlib.io.ScriptUtil.doScriptIgnoreError(
                        train.getServerScriptEngine(),
                        "getDeceleration",
                        train,
                        prevSpeed);
                deceleration = obj != null ? Float.parseFloat(obj.toString()) : 0.0F;
            } else {
                deceleration = cfg.deccelerations[Math.min(cfg.deccelerations.length - 1, -notch)];
            }
            if (prevSpeed + deceleration < 0.0F) {
                return -prevSpeed;
            }
            return deceleration;
        }
    }

    public static float getMaxSpeed(TrainConfig cfg) {
        return cfg.maxSpeed[cfg.maxSpeed.length - 1];
    }
}
