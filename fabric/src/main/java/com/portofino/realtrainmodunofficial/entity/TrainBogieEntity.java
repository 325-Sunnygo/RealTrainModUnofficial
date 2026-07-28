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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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

public final class TrainBogieEntity extends Entity {
    private static final float HITBOX_WIDTH = 1.8F;
    private static final float HITBOX_HEIGHT = 1.05F;
    private static final EntityDataAccessor<Integer> TRAIN_ENTITY_ID =
        SynchedEntityData.defineId(TrainBogieEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BOGIE_INDEX =
        SynchedEntityData.defineId(TrainBogieEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ACTIVATED =
        SynchedEntityData.defineId(TrainBogieEntity.class, EntityDataSerializers.BOOLEAN);

    private TrainEntity cachedTrain;

    public TrainBogieEntity(EntityType<? extends TrainBogieEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = false;
        this.noCulling = true;
        this.setNoGravity(true);
    }

    public void attachToTrain(TrainEntity train, int bogieIndex) {
        if (train == null) {
            return;
        }
        this.cachedTrain = train;
        this.entityData.set(TRAIN_ENTITY_ID, train.getId());
        this.entityData.set(BOGIE_INDEX, bogieIndex);
        this.refreshDimensions();
        refreshFromTrain();
    }

    public int getBogieIndex() {
        return this.entityData.get(BOGIE_INDEX);
    }

    public boolean belongsToTrain(int trainEntityId) {
        return this.entityData.get(TRAIN_ENTITY_ID) == trainEntityId;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(TRAIN_ENTITY_ID, -1);
        builder.define(BOGIE_INDEX, 0);
        builder.define(ACTIVATED, false);
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        this.entityData.set(TRAIN_ENTITY_ID, tag.getInt("TrainEntityId"));
        this.entityData.set(BOGIE_INDEX, tag.getInt("BogieIndex"));
        this.entityData.set(ACTIVATED, tag.getBoolean("Activated"));
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        tag.putInt("TrainEntityId", this.entityData.get(TRAIN_ENTITY_ID));
        tag.putInt("BogieIndex", this.entityData.get(BOGIE_INDEX));
        tag.putBoolean("Activated", isActivated());
    }

    public boolean isActivated() {
        return this.entityData.get(ACTIVATED);
    }

    public void setActivated(boolean value) {
        this.entityData.set(ACTIVATED, value);
    }

    @Override
    public void tick() {
        super.tick();
        TrainEntity train = resolveTrain();
        // クライアント側で親エンティティがまだ届いていないだけのケースで
        // 台車を discard すると、その後親列車が同期しても台車が永遠に消えたままになる。
        // discard はサーバー側でのみ行い、クライアントは親が来るまで待機する。
        if (train == null || !train.isAlive() || train.isRemoved()) {
            if (!level().isClientSide()) {
                discard();
            }
            return;
        }
        if (level().isClientSide() && !shouldRefreshClientTransform()) {
            return;
        }
        refreshFromTrain();
        if (!level().isClientSide()) {
            handleServerBogieContacts(train);
        }
    }

    private void handleServerBogieContacts(TrainEntity train) {
        double dis = Math.max(getBbWidth() * 4.0D, 6.0D);
        AABB searchBox = new AABB(getX() - dis, getY() - 3.0D, getZ() - dis,
                                  getX() + dis, getY() + 3.0D, getZ() + dis);
        for (Entity entity : level().getEntities(this, searchBox)) {
            if (!(entity instanceof TrainBogieEntity other) || other.isRemoved()) continue;
            TrainEntity otherTrain = other.getTrain();
            if (otherTrain == null || otherTrain == train || !otherTrain.isAlive()) continue;
            // 同編成・直結の車両はブレーキ・再連結をスキップ
            if (train.isConnectedTo(otherTrain)) continue;
            TrainEntity thisHead = train.getFormationHead();
            TrainEntity otherHead = otherTrain.getFormationHead();
            if (thisHead != null && thisHead == otherHead) continue;
            boolean bogieTouching = getBoundingBox().inflate(0.35D).intersects(other.getBoundingBox().inflate(0.35D))
                    || position().distanceToSqr(other.position()) <= 4.0D;
            boolean couplingApproach = train.isCouplingApproachCloseEnough(otherTrain, getBogieIndex(), other.getBogieIndex());
            if (!bogieTouching && !couplingApproach) continue;
            if (!train.canResolveBogieContactWith(otherTrain, getBogieIndex(), other.getBogieIndex())) {
                if (bogieTouching) {
                    train.handleBogieContactWithoutCoupling(getBogieIndex(), otherTrain, other.getBogieIndex());
                }
                return;
            }
            // 連結器が3m以内 or 台車が接触 → 連結試行、失敗時はブレーキ
            if (!train.tryCoupleFromBogieContact(getBogieIndex(), otherTrain, other.getBogieIndex())) {
                train.handleBogieContactWithoutCoupling(getBogieIndex(), otherTrain, other.getBogieIndex());
            }
            return;
        }
    }

    private boolean shouldRefreshClientTransform() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getVehicle() == this) {
            return true;
        }
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        double distanceSq = cameraPos.distanceToSqr(getX(), getY(), getZ());
        if (distanceSq > 100.0D * 100.0D) {
            return (tickCount & 3) == 0;
        }
        if (distanceSq > 48.0D * 48.0D) {
            return (tickCount & 1) == 0;
        }
        return true;
    }

    private void refreshFromTrain() {
        TrainEntity train = resolveTrain();
        if (train == null) {
            return;
        }
        Vec3 target = train.getBogieEntityWorldPosition(getBogieIndex());
        if (level().isClientSide()) {
            // クライアント側: xo を train.xo/yRotO ベースで明示設定して
            // train body の lerp(pt, train.xo, train.getX()) と同期させる。
            // tick順序の不定性により baseTick が設定する xo がずれると高速時に
            // 台車が前後にずれて見えるため、毎tick明示的に揃える。
            Vec3 prevTarget = train.getBogieEntityWorldPositionPrev(getBogieIndex());
            this.xo = prevTarget.x;
            this.yo = prevTarget.y;
            this.zo = prevTarget.z;
        }
        this.setPos(target.x, target.y, target.z);
        if (!level().isClientSide()) {
            // サーバー側: railアンカーから毎tick yaw/pitch を計算して setRot。
            // この値が ClientboundMoveEntityPacket.Rot 等でクライアントへ同期される。
            float targetYaw = train.getBogieWorldYaw(getBogieIndex());
            float targetPitch = train.getBogiePitch(getBogieIndex());
            this.setYRot(targetYaw);
            this.setXRot(targetPitch);
            this.yRotO = targetYaw;
            this.xRotO = targetPitch;
            this.setYHeadRot(targetYaw);
            this.setYBodyRot(targetYaw);
        }
        // クライアント側では rotation はサーバーからの lerpTo() で setYRot/setXRot され、
        // yRotO/xRotO は lerpTo() 内で前tick値として保持される。ここでは触らない。
    }

    public TrainEntity getTrain() {
        return resolveTrain();
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
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        // 位置は refreshFromTrain() が毎tick設定するため server lerp パケットの位置は無視。
        // 回転はサーバーが railアンカーから計算した値を反映する。
        // yRotO/xRotO は現時点の回転値を退避して描画補間で使う (= 前tickのrotation)。
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
        this.setYRot(yRot);
        this.setXRot(xRot);
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
        return train.interactWithBogie(player, getBogieIndex(), hand, holdingCrowbar);
    }

    private static boolean isHoldingTrainPlacementItem(Player player) {
        return player.getMainHandItem().is(RealTrainModUnofficialItems.TRAIN_ITEM.get())
            || player.getOffhandItem().is(RealTrainModUnofficialItems.TRAIN_ITEM.get())
            || player.getMainHandItem().is(RealTrainModUnofficialItems.TRAIN_VEHICLE_ITEM.get())
            || player.getOffhandItem().is(RealTrainModUnofficialItems.TRAIN_VEHICLE_ITEM.get());
    }

    @Override
    public boolean canBeCollidedWith() {
        return !isRemoved();
    }

    @Override
    public boolean canCollideWith(@NotNull Entity entity) {
        return !isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
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
            getY() - (HITBOX_HEIGHT * 0.5D),
            getZ() - half,
            getX() + half,
            getY() + (HITBOX_HEIGHT * 0.5D),
            getZ() + half
        );
    }

    @Override
    public @NotNull AABB getBoundingBoxForCulling() {
        TrainEntity train = resolveTrain();
        if (train != null) {
            return train.getBoundingBox().inflate(8.0D, 6.0D, 8.0D);
        }
        return getBoundingBox().inflate(6.0D, 4.0D, 6.0D);
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
