package jp.ngt.rtm.entity.train.parts;

import jp.ngt.ngtlib.math.Vec3;
import jp.ngt.rtm.entity.vehicle.EntityVehicleBase;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * 本家 jp.ngt.rtm.entity.train.parts.EntityVehiclePart (KaizPatchX) の忠実移植。
 * 車両に追従するパーツ (フロア/座席等) の基底。
 */
public abstract class EntityVehiclePart extends Entity {
    private static final EntityDataAccessor<Integer> DATA_VEHICLE =
            SynchedEntityData.defineId(EntityVehiclePart.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_VEC_X =
            SynchedEntityData.defineId(EntityVehiclePart.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_VEC_Y =
            SynchedEntityData.defineId(EntityVehiclePart.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_VEC_Z =
            SynchedEntityData.defineId(EntityVehiclePart.class, EntityDataSerializers.FLOAT);

    /**
     * 設置してあるならtrue, 列車の上にあるならfalse
     */
    protected boolean isIndependent;
    private EntityVehicleBase<?> parent;
    private UUID unloadedParent;

    public EntityVehiclePart(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_VEHICLE, 0);
        builder.define(DATA_VEC_X, 0.0F);
        builder.define(DATA_VEC_Y, 0.0F);
        builder.define(DATA_VEC_Z, 0.0F);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        nbt.putBoolean("Independent", this.isIndependent);
        Vec3 v3 = this.getPartVec();
        nbt.putFloat("vecX", (float) v3.getX());
        nbt.putFloat("vecY", (float) v3.getY());
        nbt.putFloat("vecZ", (float) v3.getZ());
        if (this.getVehicle() != null) {
            UUID uuid = this.getVehicle().getUUID();
            nbt.putLong("trainUUID_Most", uuid.getMostSignificantBits());
            nbt.putLong("trainUUID_Least", uuid.getLeastSignificantBits());
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        this.isIndependent = nbt.getBoolean("Independent");
        this.setPartPos(nbt.getFloat("vecX"), nbt.getFloat("vecY"), nbt.getFloat("vecZ"));
        if (nbt.contains("trainUUID_Most") && nbt.contains("trainUUID_Least")) {
            long l0 = nbt.getLong("trainUUID_Most");
            long l1 = nbt.getLong("trainUUID_Least");
            if (l0 != 0L || l1 != 0L) {
                this.unloadedParent = new UUID(l0, l1);
            }
        }
    }

    private boolean loadTrainFromUUID(UUID uuid) {
        if (this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            Entity entity = serverLevel.getEntity(uuid);
            if (entity instanceof EntityVehicleBase<?> vehicle) {
                this.setVehicle(vehicle);
                this.onLoadVehicle();
                return true;
            }
        }
        return false;
    }

    /**
     * EntityVehicleBaseへ自身の登録を行う。(セーブデータからの読み込み時のみ)
     */
    public abstract void onLoadVehicle();

    @Override
    public boolean canBeCollidedWith() {
        return !this.isRemoved();
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public void tick() {
        this.baseTick();

        if (!this.isIndependent) {
            if (this.unloadedParent != null && !this.level().isClientSide) {
                if (this.loadTrainFromUUID(this.unloadedParent)) {
                    this.unloadedParent = null;
                }
            }

            EntityVehicleBase<?> vehicle = this.getVehicle();
            if (vehicle != null) {
                this.updatePartPos(vehicle);
            }
        }
    }

    /**
     * 位置を更新 (両サイド: クライアントは補間済みの車体位置に追従)。
     * 回転は設定しない — 乗員の視点が車両の向きに固定されないように
     * (視点は完全に自由、本家の座席と同じ感覚)。
     */
    public void updatePartPos(EntityVehicleBase<?> vehicle) {
        Vec3 v3 = this.getPartVec();
        v3 = v3.rotateAroundZ(-vehicle.rotationRoll);
        v3 = v3.rotateAroundX(vehicle.getXRot());
        v3 = v3.rotateAroundY(vehicle.getYRot());
        this.setPos(vehicle.getX() + v3.getX(), vehicle.getY() + v3.getY(), vehicle.getZ() + v3.getZ());
    }

    /**
     * 乗員の視点回転には一切干渉しない
     */
    @Override
    public void onPassengerTurned(net.minecraft.world.entity.Entity rider) {
    }

    /**
     * 本家 setPositionAndRotation2: 車体追従中はトラッカー同期を無視
     */
    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        EntityVehicleBase<?> vehicle = this.getVehicle();
        if (vehicle == null) {
            this.setPos(x, y, z);
            this.setRot(yaw, pitch);
        }
    }

    public void setVehicle(EntityVehicleBase<?> vehicle) {
        this.entityData.set(DATA_VEHICLE, vehicle.getId());
        this.parent = vehicle;
    }

    public EntityVehicleBase<?> getVehicle() {
        if (this.parent == null || this.parent.isRemoved()) {
            Entity entity = this.level().getEntity(this.entityData.get(DATA_VEHICLE));
            if (entity instanceof EntityVehicleBase<?> vehicle) {
                this.parent = vehicle;
            }
        }
        return this.parent;
    }

    /**
     * EntityTrainとの相対位置を保存
     */
    public void setPartPos(float x, float y, float z) {
        this.entityData.set(DATA_VEC_X, x);
        this.entityData.set(DATA_VEC_Y, y);
        this.entityData.set(DATA_VEC_Z, z);
    }

    /**
     * EntityTrainとの相対位置を取得
     */
    public Vec3 getPartVec() {
        return new Vec3(this.entityData.get(DATA_VEC_X), this.entityData.get(DATA_VEC_Y), this.entityData.get(DATA_VEC_Z));
    }

    /** 本家 needsUpdatePos: 次 tick に位置を親車両へ追従させるか。 */
    public boolean needsUpdatePos = true;

}
