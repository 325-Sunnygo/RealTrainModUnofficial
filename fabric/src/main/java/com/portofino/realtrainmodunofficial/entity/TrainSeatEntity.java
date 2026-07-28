package com.portofino.realtrainmodunofficial.entity;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

public final class TrainSeatEntity extends Entity {
    private static final float HITBOX_WIDTH = 0.9F;
    private static final float HITBOX_HEIGHT = 0.25F;
    private static final float RIDER_YAW_FOLLOW_STRENGTH = 0.35F;
    private static final float RIDER_MAX_YAW_DELTA = 2.0F;
    private static final EntityDataAccessor<Integer> TRAIN_ENTITY_ID =
        SynchedEntityData.defineId(TrainSeatEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SEAT_INDEX =
        SynchedEntityData.defineId(TrainSeatEntity.class, EntityDataSerializers.INT);

    private TrainEntity cachedTrain;
    private float deltaYaw;

    public TrainSeatEntity(EntityType<? extends TrainSeatEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.noCulling = true;
        this.setNoGravity(true);
    }

    public void attachToTrain(TrainEntity train, int seatIndex) {
        if (train == null) {
            return;
        }
        this.cachedTrain = train;
        this.entityData.set(TRAIN_ENTITY_ID, train.getId());
        this.entityData.set(SEAT_INDEX, seatIndex);
        this.refreshDimensions();
        refreshFromTrain();
    }

    public int getSeatIndex() {
        return this.entityData.get(SEAT_INDEX);
    }

    public boolean belongsToTrain(int trainEntityId) {
        return this.entityData.get(TRAIN_ENTITY_ID) == trainEntityId;
    }

    public TrainEntity getTrain() {
        return resolveTrain();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(TRAIN_ENTITY_ID, -1);
        builder.define(SEAT_INDEX, 0);
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        this.entityData.set(TRAIN_ENTITY_ID, tag.getInt("TrainEntityId"));
        this.entityData.set(SEAT_INDEX, tag.getInt("SeatIndex"));
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        tag.putInt("TrainEntityId", this.entityData.get(TRAIN_ENTITY_ID));
        tag.putInt("SeatIndex", this.entityData.get(SEAT_INDEX));
    }

    @Override
    public void tick() {
        super.tick();
        TrainEntity train = resolveTrain();
        if (train == null || !train.isAlive() || train.isRemoved()) {
            ejectPassengers();
            discard();
            return;
        }
        if (level().isClientSide() && !shouldRefreshClientTransform()) {
            return;
        }
        refreshFromTrain();
    }

    private boolean shouldRefreshClientTransform() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && (mc.player.getVehicle() == this || mc.player.getVehicle() == resolveTrain())) {
            return true;
        }
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        double distanceSq = cameraPos.distanceToSqr(getX(), getY(), getZ());
        if (distanceSq > 100.0D * 100.0D) {
            return (tickCount & 7) == 0;
        }
        if (distanceSq > 48.0D * 48.0D) {
            return (tickCount & 3) == 0;
        }
        return true;
    }

    private void refreshFromTrain() {
        TrainEntity train = resolveTrain();
        if (train == null) {
            return;
        }
        Vec3 target = train.getSeatWorldPosition(getSeatIndex());
        float targetYaw = train.getSeatWorldYaw(getSeatIndex());
        this.deltaYaw = Mth.wrapDegrees(targetYaw - this.getYRot());
        this.setPos(target.x, target.y, target.z);
        this.setYRot(targetYaw);
        this.setXRot(0.0F);
        this.xo = target.x;
        this.yo = target.y;
        this.zo = target.z;
        this.yRotO = targetYaw;
        this.xRotO = 0.0F;
        this.setYHeadRot(this.getYRot());
        this.setYBodyRot(this.getYRot());
    }

    @Override
    protected void positionRider(@NotNull Entity passenger, Entity.@NotNull MoveFunction callback) {
        super.positionRider(passenger, callback);
        if (passenger instanceof Player player) {
            float appliedYaw = Mth.clamp(this.deltaYaw * RIDER_YAW_FOLLOW_STRENGTH, -RIDER_MAX_YAW_DELTA, RIDER_MAX_YAW_DELTA);
            float nextYaw = player.getYRot() + appliedYaw;
            player.setYRot(nextYaw);
            player.yRotO += appliedYaw;
            player.setYHeadRot(nextYaw);
            player.setYBodyRot(nextYaw);
            player.yHeadRotO += appliedYaw;
            player.yBodyRotO += appliedYaw;
        }
    }

    private TrainEntity resolveTrain() {
        if (cachedTrain != null && cachedTrain.isAlive() && cachedTrain.getId() == this.entityData.get(TRAIN_ENTITY_ID)) {
            return cachedTrain;
        }
        Entity entity = level().getEntity(this.entityData.get(TRAIN_ENTITY_ID));
        if (entity instanceof TrainEntity train && train.isAlive()) {
            cachedTrain = train;
            return train;
        }
        return null;
    }

    @Override
    public @NotNull InteractionResult interact(@NotNull Player player, @NotNull InteractionHand hand) {
        if (isHoldingTrainPlacementItem(player)) {
            return InteractionResult.PASS;
        }
        TrainEntity train = resolveTrain();
        if (train == null) {
            return InteractionResult.PASS;
        }
        boolean holdingCrowbar = player.getMainHandItem().is(RealTrainModUnofficialItems.CROWBAR_ITEM.get())
            || player.getOffhandItem().is(RealTrainModUnofficialItems.CROWBAR_ITEM.get());
        if (holdingCrowbar) {
            return train.interact(player, hand);
        }
        return train.rideSeat(player, getSeatIndex());
    }

    private static boolean isHoldingTrainPlacementItem(Player player) {
        return player.getMainHandItem().is(RealTrainModUnofficialItems.TRAIN_ITEM.get())
            || player.getOffhandItem().is(RealTrainModUnofficialItems.TRAIN_ITEM.get())
            || player.getMainHandItem().is(RealTrainModUnofficialItems.TRAIN_VEHICLE_ITEM.get())
            || player.getOffhandItem().is(RealTrainModUnofficialItems.TRAIN_VEHICLE_ITEM.get());
    }

    @Override
    protected boolean canAddPassenger(@NotNull Entity passenger) {
        return getPassengers().isEmpty();
    }

    @Override
    protected void removePassenger(@NotNull Entity passenger) {
        super.removePassenger(passenger);
        if (!level().isClientSide() && passenger instanceof Player player) {
            TrainEntity train = resolveTrain();
            if (train != null) {
                train.clearSeatAssignment(player.getUUID());
            }
        }
    }

    @Override
    public boolean canBeCollidedWith() {
        return !isRemoved();
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public @NotNull EntityDimensions getDimensions(@NotNull Pose pose) {
        return EntityDimensions.scalable(HITBOX_WIDTH, HITBOX_HEIGHT);
    }

    @Override
    protected AABB makeBoundingBox() {
        double half = HITBOX_WIDTH * 0.5D;
        return new AABB(
            getX() - half,
            getY(),
            getZ() - half,
            getX() + half,
            getY() + HITBOX_HEIGHT,
            getZ() + half
        );
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        TrainEntity train = resolveTrain();
        if (train == null || !train.isAlive()) {
            if (!level().isClientSide()) {
                discard();
            }
            return true;
        }
        return train.hurt(source, amount);
    }

    @Override
    public @NotNull Packet<ClientGamePacketListener> getAddEntityPacket(@NotNull ServerEntity entity) {
        return new ClientboundAddEntityPacket(this, entity);
    }
}
