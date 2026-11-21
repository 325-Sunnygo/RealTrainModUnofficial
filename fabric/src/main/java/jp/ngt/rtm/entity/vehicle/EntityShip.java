package jp.ngt.rtm.entity.vehicle;

import jp.ngt.rtm.entity.RTMEntities;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;

/**
 * 船。本家 {@code jp.ngt.rtm.entity.vehicle.EntityShip} の移植。
 * 水上でだけ操作でき、水に浸かると浮き上がる。旋回で傾く。
 */
public class EntityShip extends EntityVehicle {
    public EntityShip(EntityType<? extends EntityShip> type, Level level) {
        super(type, level);
    }

    public EntityShip(Level level) {
        this(RTMEntities.NGTO_SHIP.get(), level);
    }

    @Override
    protected boolean shouldUpdateMotion() {
        return this.isInWater();
    }

    @Override
    protected void updateMotion(LivingEntity entity, float moveStrafe, float moveForward) {
        super.updateMotion(entity, moveStrafe, moveForward);
        //本家: 旋回でロールする
        if (this.speed > 0.0D && this.isInWater()) {
            this.rotationRoll = -moveStrafe * (float) (this.speed / MAX_SPEED) * -5.0F;
        }
    }

    /** 本家 updateFallState: 水面下なら浮く、水面なら静止。 */
    @Override
    protected void updateFallState() {
        if (!this.isInWater() && !this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.05D, 0.0D));
            return;
        }
        AABB aabb = this.getBoundingBox();
        AABB lower = new AABB(aabb.minX, aabb.minY + 0.0625D, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ);
        if (this.containsWater(lower)) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, 0.05D, 0.0D));
        } else {
            this.setDeltaMovement(this.getDeltaMovement().x, 0.0D, this.getDeltaMovement().z);
        }
    }

    private boolean containsWater(AABB aabb) {
        int x0 = Mth.floor(aabb.minX);
        int x1 = Mth.ceil(aabb.maxX);
        int y0 = Mth.floor(aabb.minY);
        int y1 = Mth.ceil(aabb.maxY);
        int z0 = Mth.floor(aabb.minZ);
        int z1 = Mth.ceil(aabb.maxZ);
        for (int x = x0; x < x1; ++x) {
            for (int y = y0; y < y1; ++y) {
                for (int z = z0; z < z1; ++z) {
                    var fluid = this.level().getFluidState(new net.minecraft.core.BlockPos(x, y, z));
                    if (fluid.is(Fluids.WATER) || fluid.is(Fluids.FLOWING_WATER)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** 水上では傾きの平均化をしない (本家 updateRotation は共通だが、水上は揺れすぎる)。 */
    @Override
    protected void updateRotation() {
        if (this.isInWater()) {
            this.setXRot(this.getXRot() * 0.9F);
            this.rotationRoll *= 0.9F;
            return;
        }
        super.updateRotation();
    }
}
