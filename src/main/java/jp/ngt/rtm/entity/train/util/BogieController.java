package jp.ngt.rtm.entity.train.util;

import jp.ngt.ngtlib.math.NGTMath;
import jp.ngt.ngtlib.math.Vec3;
import jp.ngt.rtm.entity.RTMEntities;
import jp.ngt.rtm.entity.train.EntityBogie;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/** 本家 jp.ngt.rtm.entity.train.util.BogieController (KaizPatchX) の忠実移植。 */
public class BogieController {
    private final EntityBogie[] bogies = new EntityBogie[2];

    public void createBogie(Level world, EntityTrainBase train) {
        this.bogies[0] = new EntityBogie(RTMEntities.BOGIE.get(), world, (byte) 0);
        this.bogies[1] = new EntityBogie(RTMEntities.BOGIE.get(), world, (byte) 1);
    }

    public void spawnBogies(Level world, EntityTrainBase train) {
        if (!world.isClientSide) {
            for (int i = 0; i < 2; ++i) {
                EntityBogie bogie = this.getBogie(i);

                if (world.addFreshEntity(bogie)) {
                    bogie.setFront(true);
                    bogie.setTrain(train);
                    train.setBogie(i, bogie);
                } else {
                    jp.ngt.ngtlib.io.NGTLog.debug("[RTM] Can't spawn bogie " + i);
                    return;
                }
            }
        }
    }

    public void setupBogiePos(EntityTrainBase train) {
        float ro0 = Mth.wrapDegrees(train.getYRot());
        float[][] bPosArray = train.getConfig().getBogiePos();

        float bPos = bPosArray[0][2];
        this.getBogie(0).moveTo(
                train.getX() + NGTMath.sin(ro0) * bPos, train.getY(), train.getZ() + NGTMath.cos(ro0) * bPos,
                ro0, 0.0F);

        bPos = bPosArray[1][2];
        this.getBogie(1).moveTo(
                train.getX() + NGTMath.sin(ro0) * bPos, train.getY(), train.getZ() + NGTMath.cos(ro0) * bPos,
                Mth.wrapDegrees(ro0 + 180.0F), 0.0F);
    }

    public EntityBogie getBogie(int bogieId) {
        return this.bogies[bogieId];
    }

    private EntityBogie getBogie(boolean isFront) {
        return this.getBogie(0).isFront() == isFront ? this.getBogie(0) : this.getBogie(1);
    }

    public void setBogie(int bogieId, EntityBogie bogie) {
        this.bogies[bogieId] = bogie;
    }

    public void updateBogies() {
        this.getBogie(0).updateBogie();
        this.getBogie(1).updateBogie();
    }

    public void setDead() {
        EntityBogie bogieF = this.getBogie(0);
        if (bogieF != null) {
            bogieF.discard();
        }
        EntityBogie bogieB = this.getBogie(1);
        if (bogieB != null) {
            bogieB.discard();
        }
    }

    public void moveTrainWithBogie(EntityTrainBase train, EntityTrainBase prevTrain, float speed, boolean forceMove) {
        if (speed == 0.0F && !forceMove) {
            this.updateBogiePos(train, 0, UpdateFlag.NONE);
            this.updateBogiePos(train, 1, UpdateFlag.NONE);
            return;
        }
        EntityBogie frontBogie = getBogie(true);
        EntityBogie backBogie = getBogie(false);
        float[][] bogiePos = train.getConfig().getBogiePos();
        float lengthF = bogiePos[0][2];
        float lengthB = bogiePos[1][2];
        float trainLength = Math.abs(lengthF - lengthB);
        boolean flag;
        if (prevTrain == null) {
            flag = frontBogie.updateBogiePos(speed, 0.0F, null);
        } else {
            float[][] bogiePos2 = prevTrain.getConfig().getBogiePos();
            double disTrain = train.getDefaultDistanceToConnectedTrain(prevTrain);
            double lenBF = Math.abs(bogiePos2[1 - prevTrain.getTrainDirection()][2]);
            double lenBB = Math.abs(bogiePos[train.getTrainDirection()][2]);
            float disBogie = (float) (disTrain - lenBF - lenBB);
            EntityBogie prevBogie = prevTrain.getBogie(1 - prevTrain.getTrainDirection());
            flag = frontBogie.updateBogiePos(speed, disBogie, prevBogie);
        }
        if (flag && backBogie.updateBogiePos(speed, trainLength, frontBogie)) {
            this.updateTrainPos(train, lengthF, lengthB);
        }
    }

    /** 台車2つを元に車体位置を更新 */
    private void updateTrainPos(EntityTrainBase train, float lf, float lb) {
        // 車体長分の先頭側台車の位置
        double d0 = Math.abs(lf) / (Math.abs(lf - lb));
        double[] fp = this.getBogie(0).getPosBuf();
        double[] bp = this.getBogie(1).getPosBuf();

        double x = fp[0] + (bp[0] - fp[0]) * d0;
        double y = (fp[1] + bp[1]) * 0.5D;
        double z = fp[2] + (bp[2] - fp[2]) * d0;

        double x0 = fp[0] - bp[0];
        double y0 = fp[1] - bp[1];
        double z0 = fp[2] - bp[2];
        float yaw = Mth.wrapDegrees((float) NGTMath.toDegrees(Math.atan2(x0, z0)));
        float pitch = Mth.wrapDegrees((float) NGTMath.toDegrees(Math.atan2(y0, Math.sqrt(x0 * x0 + z0 * z0))));
        float roll = (this.getBogie(0).rotationRoll + -(this.getBogie(1).rotationRoll)) * 0.5F;

        // カント分車体をずらす
        Vec3 vec = new Vec3(0.0D, EntityTrainBase.TRAIN_HEIGHT, 0.0D);
        vec = vec.rotateAroundZ(-roll);
        vec = vec.rotateAroundY(yaw);
        x += vec.getX();
        y += vec.getY() - EntityTrainBase.TRAIN_HEIGHT;
        z += vec.getZ();

        // ★車体を上下に揺らす仕掛けは持たない。
        // 以前 RTMU 独自の「車体サスペンション」(継ぎ目や勾配で沈んで揺り戻す上下動) を
        // 入れていたが、坂で列車が沈んでは戻るのを繰り返して見えるため撤去した。
        // 車体 Y は台車中点の理想値をそのまま使い、レールにぴったり乗せる。

        train.setPositionAndRotationDirect(x, y, z, yaw, pitch);
        train.updateRoll(roll);

        this.updateBogiePos(train, 0, UpdateFlag.ALL);
        this.updateBogiePos(train, 1, UpdateFlag.ALL);
    }

    /**
     * 車体位置を元に台車位置を更新
     * 台車位置を再計算することで、車体とのずれを解消する
     */
    public void updateBogiePos(EntityTrainBase train, int bogieIndex, UpdateFlag flag) {
        float[][] pos = train.getConfig().getBogiePos();
        Vec3 v31 = new Vec3(pos[bogieIndex][0], pos[bogieIndex][1], pos[bogieIndex][2]);
        v31 = v31.rotateAroundX(train.getXRot());
        v31 = v31.rotateAroundY(train.getYRot());
        this.getBogie(bogieIndex).moveBogie(train, train.getX() + v31.getX(), train.getY() + v31.getY(), train.getZ() + v31.getZ(), flag);
    }

    public enum UpdateFlag {
        /** 全Rotationを更新 */
        ALL,
        /** Yawのみ更新、転車台で使用 */
        YAW,
        /** Rotation更新なし */
        NONE
    }
}
