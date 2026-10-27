package jp.ngt.rtm.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 移動装置が動かすブロック 1 個ぶんの当たり判定。
 * 本家 {@code jp.ngt.rtm.entity.EntityMMBoundingBox} の移植。
 *
 * <p>ブロックは動かせないので、動いている間だけ「見えない箱」を置いて、
 * その上に乗っている物を一緒に運ぶ。
 */
public class EntityMMBoundingBox extends Entity {

    /** このブロックの当たり判定 (ブロック中心からの相対)。 */
    private AABB shape = new AABB(-0.5D, 0.0D, -0.5D, 0.5D, 1.0D, 0.5D);
    /** 上が空いているか (乗れるか)。本家 p3。 */
    private boolean topFree = true;
    /** 判定用に一時的に使う箱。 */
    private AABB work = new AABB(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);

    public EntityMMBoundingBox(EntityType<? extends EntityMMBoundingBox> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public EntityMMBoundingBox(Level level) {
        this(RTMEntities.MM_BOUNDING_BOX.get(), level);
    }

    public void setShape(AABB shape, boolean topFree) {
        this.shape = shape;
        this.topFree = topFree;
        this.setBoundingBox(shape.move(this.getX(), this.getY(), this.getZ()));
    }

    public AABB getShape() {
        return this.shape;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public void setPos(double x, double y, double z) {
        super.setPos(x, y, z);
        if (this.shape != null) {
            this.setBoundingBox(this.shape.move(x, y, z));
        }
    }

    /** 本家 moveMM: 先に乗っている物へ移動を伝えてから自分が動く。 */
    public void moveMM(double dx, double dy, double dz) {
        this.applyCollisionToEntities(dx, dy, dz);
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();
    }

    private void applyCollisionToEntities(double dx, double dy, double dz) {
        //本家: 上に乗っている物を拾うため、判定箱を少し上へ広げる
        double d0 = 0.41999998688697815D + (dy < 0.0D ? -dy : 0.0D);
        this.work = this.shape.move(this.getX(), this.getY() + d0, this.getZ());

        List<Entity> list = this.level().getEntities(this, this.work);
        for (Entity entity : list) {
            if (!(entity instanceof EntityMMBoundingBox)) {
                this.moveEntity(entity, dx, dy, dz);
            }
        }
    }

    private void moveEntity(Entity entity, double dx, double dy, double dz) {
        AABB entityBB = entity.getBoundingBox();
        AABB myBB = this.getBoundingBox();
        boolean flag = false;

        if (!this.inY(entity, dy)) {
            return;
        }
        if (this.onY(entity, dy)) {
            //上に乗っている
            if (this.topFree && this.inXAndZ(entity)) {
                double y1 = myBB.maxY - (entityBB.minY + dy);
                if (y1 != 0.0D) {
                    dy += y1;
                }
                entity.fallDistance = 0.0F;
                entity.setDeltaMovement(entity.getDeltaMovement().x, 0.0D, entity.getDeltaMovement().z);
                entity.setOnGround(true);   //false だと XZ 移動がぬるっとする
                flag = true;
            }
        } else if (this.inXOrZ(entity)) {
            //横から当たっている
            dy = 0.0D;
            dx = 0.0D;
            dz = 0.0D;
            flag = true;
        }

        if (!flag) {
            return;
        }
        double newY = entityBB.minY + dy;
        if (this.level().isClientSide()) {
            entity.setPos(entity.getX() + dx, newY, entity.getZ() + dz);
        } else if (!(entity instanceof Player)) {
            entity.setPos(entity.getX() + dx, newY, entity.getZ() + dz);
            entity.hurtMarked = true;
        }
    }

    private boolean inY(Entity entity, double moveY) {
        AABB e = entity.getBoundingBox();
        AABB m = this.getBoundingBox();
        if (moveY > 0.0D) {
            return e.minY <= m.maxY && e.maxY > m.minY;
        }
        return e.minY <= m.maxY - moveY && e.maxY > m.minY;
    }

    private boolean onY(Entity entity, double moveY) {
        AABB e = entity.getBoundingBox();
        AABB m = this.getBoundingBox();
        double d0 = 0.21D;
        if (moveY > 0.0D) {
            return e.minY >= m.maxY - moveY - d0 && e.minY <= m.maxY;
        }
        return e.minY >= m.maxY - d0 && e.minY <= m.maxY - moveY;
    }

    private boolean inXAndZ(Entity entity) {
        return entity.getX() >= this.work.minX && entity.getX() < this.work.maxX
            && entity.getZ() >= this.work.minZ && entity.getZ() < this.work.maxZ;
    }

    private boolean inXOrZ(Entity entity) {
        return (entity.getX() >= this.work.minX && entity.getX() < this.work.maxX)
            || (entity.getZ() >= this.work.minZ && entity.getZ() < this.work.maxZ);
    }

    @Override
    public Vec3 getDeltaMovement() {
        return Vec3.ZERO;
    }

    @Override
    public boolean shouldBeSaved() {
        //動いている間だけの一時的な物。ワールドには残さない
        return false;
    }
}
