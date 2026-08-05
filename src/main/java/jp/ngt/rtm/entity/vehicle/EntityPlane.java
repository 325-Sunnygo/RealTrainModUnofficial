package jp.ngt.rtm.entity.vehicle;

import jp.ngt.rtm.entity.RTMEntities;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 飛行機。本家 {@code jp.ngt.rtm.entity.vehicle.EntityPlane} の移植。
 *
 * <p>空中でも操作でき、速度に応じてピッチで昇降する。
 * ★本家は上昇/下降を専用キー (setUpDown) で行うが、RTMU にそのキーが無いので
 * <b>運転者の視線ピッチ</b>で機首を操作する (速度が出ているときだけ)。
 */
public class EntityPlane extends EntityVehicle {
    public EntityPlane(EntityType<? extends EntityPlane> type, Level level) {
        super(type, level);
    }

    public EntityPlane(Level level) {
        this(RTMEntities.NGTO_PLANE.get(), level);
    }

    @Override
    protected boolean shouldUpdateMotion() {
        return true;   //本家: 空中でも操作できる
    }

    @Override
    protected void updateMotion(LivingEntity entity, float moveStrafe, float moveForward) {
        this.speed += moveForward * ACCELERATION;
        float f0 = -moveStrafe * YAW_COEFFICIENT;
        f0 *= (float) (this.speed / MAX_SPEED);
        f0 = Mth.clamp(f0, -MAX_YAW, MAX_YAW);
        this.setYRot(this.getYRot() + f0);

        this.speed = Mth.clamp((float) this.speed, 0.0F, MAX_SPEED);

        //★視線ピッチで機首を動かす (本家 setUpDown の代替)
        if (this.speed > 0.0D) {
            float target = entity.getXRot();
            float step = PITCH_COEFFICIENT * (float) (this.speed / MAX_SPEED);
            float dif = Mth.clamp(Mth.wrapDegrees(target - this.getXRot()), -step, step);
            this.setXRot(this.getXRot() + dif);
            if (this.onGround() && this.getXRot() > -1.0F) {
                this.setXRot(0.0F);   //本家: 地上では機首上げのみ
            }
        }

        Vec3 vec = this.getMotionVec();
        double d0 = 0.05D * (1.0D - (this.speed / MAX_SPEED));
        this.setDeltaMovement(vec.x, vec.y - d0, vec.z);

        if (moveForward == 0.0F) {
            this.speed *= FRICTION;
        }
        if (Math.abs(this.speed) < 0.001D) {
            this.speed = 0.0D;
            this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
        }

        //本家: 旋回でロール
        if (this.speed > 0.0D && !this.onGround()) {
            this.rotationRoll = -moveStrafe * (float) (this.speed / MAX_SPEED) * -ROLL_COEFFICIENT;
        } else {
            this.rotationRoll *= 0.75F;
        }
        if (Math.abs(this.rotationRoll) < 0.01F) {
            this.rotationRoll = 0.0F;
        }
    }

    /** 本家 getMotionVec: ピッチ方向にも進む (機首の向きへ)。 */
    @Override
    protected Vec3 getMotionVec() {
        if ((this.onGround() && this.getXRot() < 0.0F) || this.isInWater()) {
            return super.getMotionVec();
        }
        float yawRad = (float) Math.toRadians(this.getYRot());
        float pitchRad = (float) Math.toRadians(this.getXRot());
        double xz = Mth.cos(pitchRad) * this.speed;
        return new Vec3(-Mth.sin(yawRad) * xz, -Mth.sin(pitchRad) * this.speed, Mth.cos(yawRad) * xz);
    }

    /** 本家 updateFallState: 速度ゼロのときだけ落ちる。 */
    @Override
    protected void updateFallState() {
        if (this.speed == 0.0D) {
            super.updateFallState();
        }
    }

    /** 本家 updateRotation: 機首は少しずつ水平へ戻る。 */
    @Override
    protected void updateRotation() {
        this.setXRot(this.getXRot() * 0.99F);
        if (Math.abs(this.getXRot()) < 0.01F) {
            this.setXRot(0.0F);
        }
    }
}
