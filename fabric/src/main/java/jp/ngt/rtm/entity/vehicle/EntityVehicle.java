package jp.ngt.rtm.entity.vehicle;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 運転できる乗り物 (車/船/飛行機) の基底。本家 {@code jp.ngt.rtm.entity.vehicle.EntityVehicle} の移植。
 *
 * <p>物理は本家の式そのまま (加速/最大速度/摩擦/滑り/ブロック高さからのピッチ・ロール)。
 * 設定値はパックが無いので本家 {@code VehicleConfig} の<b>既定値</b>で固定:
 * 摩擦 0.9 / 加速 0.0125 / 最大速度 0.8 / 最大ヨー 15 / ヨー係数 4.5。
 *
 * <p>★同期は本家 (毎 tick サーバー計算 + パケット) ではなく、
 * バニラのボートと同じ<b>運転者クライアント主導</b>にしてある
 * ({@code getControllingPassenger} が運転者を返すと、位置はバニラが同期する)。
 */
public abstract class EntityVehicle extends Entity {
    private static final EntityDataAccessor<CompoundTag> NGTO =
        SynchedEntityData.defineId(EntityVehicle.class, EntityDataSerializers.COMPOUND_TAG);

    //本家 VehicleConfig の既定値
    protected static final float FRICTION = 0.9F;
    protected static final float ACCELERATION = 0.0125F;
    protected static final float MAX_SPEED = 0.8F;
    protected static final float MAX_YAW = 15.0F;
    protected static final float YAW_COEFFICIENT = 4.5F;
    protected static final float PITCH_COEFFICIENT = 2.5F;
    protected static final float ROLL_COEFFICIENT = 15.0F;

    protected double speed;
    public float rotationRoll;
    public float prevRotationRoll;
    private float prevPitchDif;
    private float prevRollDif;
    @Nullable
    private VehicleNGTO vngto;

    //補間 (非運転クライアント用)
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYRot;
    private double lerpXRot;

    public EntityVehicle(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(NGTO, new CompoundTag());
    }

    // ───── NGTO モデル ─────

    public void setNGTO(VehicleNGTO obj) {
        this.vngto = obj;
        this.entityData.set(NGTO, obj == null ? new CompoundTag() : obj.writeToNBT());
    }

    @Nullable
    public VehicleNGTO getNGTO() {
        if (this.vngto == null) {
            CompoundTag nbt = this.entityData.get(NGTO);
            if (!nbt.isEmpty()) {
                this.vngto = VehicleNGTO.readFromNBT(nbt, false);
            }
        }
        return this.vngto;
    }

    // ───── 本体 ─────

    @Override
    public void tick() {
        super.tick();
        this.prevRotationRoll = this.rotationRoll;

        if (this.isControlledByLocalInstance()) {
            this.lerpSteps = 0;
            //運転者の入力で動かす (本家 updateMovement)
            if (this.shouldUpdateMotion() && this.getFirstPassenger() instanceof LivingEntity living) {
                this.updateMotion(living, living.xxa, living.zza);
            } else {
                this.applyPhysicalEffect();
            }
            this.updateFallState();
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.updateRotation();
        } else if (this.lerpSteps > 0) {
            this.lerpPositionAndRotationStep(this.lerpSteps, this.lerpX, this.lerpY, this.lerpZ,
                this.lerpYRot, this.lerpXRot);
            --this.lerpSteps;
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = yRot;
        this.lerpXRot = xRot;
        this.lerpSteps = 10;
    }

    /** プレーヤーの操作を反映するか (本家 shouldUpdateMotion: 車は接地時のみ)。 */
    protected boolean shouldUpdateMotion() {
        return this.onGround();
    }

    /** 本家 updateMotion: 加速・旋回・滑り。 */
    protected void updateMotion(LivingEntity entity, float moveStrafe, float moveForward) {
        this.speed += moveForward * ACCELERATION;
        float f0 = -moveStrafe * YAW_COEFFICIENT;
        f0 *= (float) (this.speed / MAX_SPEED);   //changeYawOnStopping=false 既定
        f0 = Mth.clamp(f0, -MAX_YAW, MAX_YAW);
        this.setYRot(this.getYRot() + f0);

        this.speed = Mth.clamp((float) this.speed, -MAX_SPEED, MAX_SPEED);

        Vec3 vec = this.getMotionVec();
        this.setDeltaMovement(vec.x, this.getDeltaMovement().y, vec.z);
        if (moveForward == 0.0F) {
            this.speed *= FRICTION;
        }
        if (Math.abs(this.speed) < 0.001D) {
            this.speed = 0.0D;
            this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
        }
    }

    /** 本家 getMotionVec: 速度が上がるほどヨーの追従が遅れる = ドリフト。 */
    protected Vec3 getMotionVec() {
        float f0 = (float) (1.0D - (this.speed / MAX_SPEED));
        float f1 = this.yRotO + (Mth.wrapDegrees(this.getYRot() - this.yRotO) * f0);
        float yaw2 = (this.onGround() || this.isInWater()) ? f1 : this.getYRot();
        float rad = (float) Math.toRadians(yaw2);
        return new Vec3(-Mth.sin(rad) * this.speed, 0.0D, Mth.cos(rad) * this.speed);
    }

    /** 本家 applyPhysicalEffect: 誰も乗っていないときの減速。 */
    protected void applyPhysicalEffect() {
        if (this.onGround()) {
            this.speed *= FRICTION;
            Vec3 m = this.getDeltaMovement();
            this.setDeltaMovement(m.x * 0.9D, Math.min(m.y, 0.0D), m.z * 0.9D);
        } else {
            this.speed *= 0.9999D;
        }
        if (Math.abs(this.speed) < 0.001D) {
            this.speed = 0.0D;
        }
    }

    /** 本家 updateFallState: 接地していなければ落ちる。 */
    protected void updateFallState() {
        if (this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().x, 0.0D, this.getDeltaMovement().z);
        } else {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.05D, 0.0D));
        }
    }

    /** 本家 updateRotation: 前後左右のブロック高さからピッチとロールを出す。 */
    protected void updateRotation() {
        float prevPitch = this.getXRot();
        float prevRoll = this.rotationRoll;
        float pitch = prevPitch;
        float roll = prevRoll;

        if (this.onGround() && (this.getDeltaMovement().x != 0.0D || this.getDeltaMovement().z != 0.0D)) {
            double hFront = this.getBlockHeight(this.getYRot());
            double hBack = this.getBlockHeight(this.getYRot() + 180.0F);
            double hLeft = this.getBlockHeight(this.getYRot() + 90.0F);
            double hRight = this.getBlockHeight(this.getYRot() - 90.0F);
            pitch = (float) Math.toDegrees(Math.atan2(hFront - hBack, this.getBbWidth()));
            roll = (float) Math.toDegrees(Math.atan2(hLeft - hRight, this.getBbWidth()));
        } else {
            pitch *= 0.75F;
            roll *= 0.75F;
        }

        if (Math.abs(pitch) < 0.01F) pitch = 0.0F;
        if (Math.abs(roll) < 0.01F) roll = 0.0F;

        //本家: 変化量を前回と平均して滑らかにする
        float pitchDif = pitch - prevPitch;
        pitch = prevPitch + (pitchDif + this.prevPitchDif) * 0.5F;
        this.prevPitchDif = pitch - prevPitch;
        float rollDif = roll - prevRoll;
        roll = prevRoll + (rollDif + this.prevRollDif) * 0.5F;
        this.prevRollDif = roll - prevRoll;

        this.setXRot(pitch);
        this.rotationRoll = roll;
    }

    /** 本家 getBlockHeight: その方角のブロック上面の高さ。 */
    protected double getBlockHeight(float yaw) {
        float rad = (float) Math.toRadians(yaw);
        double r = this.getBbWidth() * 0.5D;
        int blockX = Mth.floor(this.getX() - Mth.sin(rad) * r);
        int blockZ = Mth.floor(this.getZ() + Mth.cos(rad) * r);
        int blockY = Mth.floor(this.getY()) + 1;
        BlockPos pos = new BlockPos(blockX, blockY, blockZ);
        for (; blockY > this.level().getMinBuildHeight(); --blockY) {
            VoxelShape shape = this.level().getBlockState(pos).getCollisionShape(this.level(), pos);
            if (!shape.isEmpty()) {
                return shape.bounds().maxY + blockY;
            }
            pos = pos.below();
        }
        return this.getY();
    }

    // ───── 乗る/降りる/壊す ─────

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (this.getFirstPassenger() instanceof Player && this.getFirstPassenger() != player) {
            return InteractionResult.PASS;
        }
        if (!this.level().isClientSide()) {
            player.startRiding(this);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide());
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return this.getFirstPassenger() instanceof Player player ? player : null;
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        VehicleNGTO obj = this.getNGTO();
        Vec3 seat = obj == null ? new Vec3(0.0D, this.getBbHeight() * 0.75D, 0.0D)
            : new Vec3(obj.riderPosX * obj.scale, obj.riderPosY * obj.scale, obj.riderPosZ * obj.scale);
        float rad = (float) Math.toRadians(this.getYRot());
        double x = seat.x * Mth.cos(rad) + seat.z * Mth.sin(rad);
        double z = seat.z * Mth.cos(rad) - seat.x * Mth.sin(rad);
        callback.accept(passenger, this.getX() + x, this.getY() + seat.y, this.getZ() + z);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!this.level().isClientSide() && source.getEntity() instanceof Player player) {
            this.discard();
            //★生成した乗り物はブロックから作った物なのでアイテムは落とさない
            //  (本家は itemVehicle を落とすが、RTMU に船/飛行機のアイテムは無い)
            return true;
        }
        return false;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** 飛行機の上昇/下降キー (本家 setUpDown)。 */
    public void setUpDown(int par1) {
    }

    public double getVehicleSpeed() {
        return this.speed;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        if (nbt.contains("VehicleNGTO")) {
            VehicleNGTO obj = VehicleNGTO.readFromNBT(nbt.getCompound("VehicleNGTO"), false);
            if (obj != null) {
                this.setNGTO(obj);
            }
        }
        this.rotationRoll = nbt.getFloat("Roll");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        VehicleNGTO obj = this.getNGTO();
        if (obj != null) {
            nbt.put("VehicleNGTO", obj.writeToNBT());
        }
        nbt.putFloat("Roll", this.rotationRoll);
    }

    /** 判定箱は NGTO の大きさに合わせる。 */
    @Override
    protected AABB makeBoundingBox() {
        return super.makeBoundingBox();
    }
}
