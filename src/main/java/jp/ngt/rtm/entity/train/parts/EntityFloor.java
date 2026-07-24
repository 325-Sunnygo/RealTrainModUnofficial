package jp.ngt.rtm.entity.train.parts;

import jp.ngt.rtm.entity.vehicle.EntityVehicleBase;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * 本家 jp.ngt.rtm.entity.train.parts.EntityFloor (KaizPatchX) の忠実移植。
 * config の slotPos ごとにスポーンし、右クリックで着席する。
 */
public class EntityFloor extends EntityVehiclePart {
    private static final EntityDataAccessor<Byte> DATA_SEAT_TYPE =
            SynchedEntityData.defineId(EntityFloor.class, EntityDataSerializers.BYTE);

    public EntityFloor(EntityType<?> type, Level level) {
        super(type, level);
    }

    public EntityFloor(EntityType<?> type, Level level, EntityVehicleBase<?> vehicle, float[] pos, byte seatType) {
        this(type, level);
        this.setVehicle(vehicle);
        this.setPartPos(pos[0], pos[1], pos[2]);
        this.updatePartPos(vehicle);
        this.setSeatType(seatType);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SEAT_TYPE, (byte) 0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        this.setSeatType(nbt.getByte("seatType"));
        super.readAdditionalSaveData(nbt);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        nbt.putByte("seatType", this.getSeatType());
        super.addAdditionalSaveData(nbt);
    }

    @Override
    public void onLoadVehicle() {
        //setupFloors が新しいフロアを常にスポーンし直すため、旧セーブのフロアは破棄する
        if (!this.level().isClientSide) {
            this.discard();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.getVehicle() == null || this.getVehicle().isRemoved()) {
            if (!this.level().isClientSide) {
                this.discard();
            }
            return true;
        }
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        //車体側の一覧へ自分を登録しておく (車体が移動後に位置を押し出すため)。
        //クライアントでは床はトラッカー経由で個別に湧くので setupFloors を通らず、
        //ここで登録しないと車体からは追従させられない。
        EntityVehicleBase<?> parent = this.getVehicle();
        if (parent != null) {
            parent.setFloor(this);
        }
        if (!this.level().isClientSide) {
            //親車両が消えたら追従して消える
            if (this.tickCount > 100 && this.getVehicle() == null) {
                this.discard();
            }
            //スニークで降車 (バニラ経路が効かない場合の保険)
            for (Entity rider : new java.util.ArrayList<>(this.getPassengers())) {
                if (rider instanceof Player player && player.isShiftKeyDown()) {
                    player.stopRiding();
                }
            }
        }
    }

    /**
     * 当たり判定ボックスの持ち上げ量。slotPos (着座オフセット) は座面高さで台車と
     * 同じ高さになるため、そのままだと台車をクリックしても座席が先に当たってしまう。
     * 判定だけ着座した体 (胴体) の高さへ持ち上げる — 着座位置 (getPartVec) は不変。
     */
    private static final double HITBOX_LIFT = 0.55D;

    @Override
    public void updatePartPos(EntityVehicleBase<?> vehicle) {
        super.updatePartPos(vehicle);
        this.setPos(this.getX(), this.getY() + HITBOX_LIFT, this.getZ());
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (this.getSeatType() == 0 || player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        //バール所持中は着席しない。台車を狙ったクリックが手前の座席に吸われて
        //連結モードに入れなくなるため、最寄りの台車へ転送する (本家の連結操作)。
        if (player.getMainHandItem().getItem() instanceof com.portofino.realtrainmodunofficial.item.CrowbarItem) {
            EntityVehicleBase<?> parentVehicle = this.getVehicle();
            if (parentVehicle instanceof jp.ngt.rtm.entity.train.EntityTrainBase train) {
                jp.ngt.rtm.entity.train.EntityBogie nearest = null;
                double best = 16.0D; //4 ブロック以内
                for (int i = 0; i < 2; i++) {
                    jp.ngt.rtm.entity.train.EntityBogie bogie = train.getBogie(i);
                    if (bogie != null) {
                        double d = bogie.distanceToSqr(this.getX(), this.getY(), this.getZ());
                        if (d < best) {
                            best = d;
                            nearest = bogie;
                        }
                    }
                }
                if (nearest != null) {
                    return nearest.interact(player, hand);
                }
            }
            return InteractionResult.PASS;
        }
        //作り直し: EntityFloor に乗せる旧方式は視点固定バグの温床だったため、
        //列車本体へ座席オフセット付きで直接乗せる (運転席と同じ経路 = 視点フリー)。
        EntityVehicleBase<?> vehicle = this.getVehicle();
        if (vehicle instanceof jp.ngt.rtm.entity.train.EntityTrainBase train) {
            jp.ngt.ngtlib.math.Vec3 pv = this.getPartVec();
            if (train.mountToSeat(player, new float[]{(float) pv.getX(), (float) pv.getY(), (float) pv.getZ()})) {
                return InteractionResult.CONSUME;
            }
            return InteractionResult.PASS;
        }
        //列車以外の親 (保険): 従来方式
        if (this.getPassengers().isEmpty()) {
            player.startRiding(this);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected void positionRider(Entity rider, Entity.MoveFunction move) {
        if (this.hasPassenger(rider)) {
            //本家: posY + height + 0.25 (1.21 は着座姿勢分を引く)
            move.accept(rider, this.getX(), this.getY() + this.getBbHeight() + 0.25D - 0.45D, this.getZ());
        }
    }

    @Override
    public net.minecraft.world.phys.Vec3 getDismountLocationForPassenger(net.minecraft.world.entity.LivingEntity rider) {
        //車体の外 (左右) に降ろす
        EntityVehicleBase<?> vehicle = this.getVehicle();
        float yaw = vehicle != null ? vehicle.getYRot() : this.getYRot();
        double side = 2.0D;
        double rad = Math.toRadians(yaw);
        double ox = Math.cos(rad) * side;
        double oz = -Math.sin(rad) * side;
        return new net.minecraft.world.phys.Vec3(this.getX() + ox, this.getY() + 1.0D, this.getZ() + oz);
    }

    public void setSeatType(byte par1) {
        this.entityData.set(DATA_SEAT_TYPE, par1);
    }

    public byte getSeatType() {
        return this.entityData.get(DATA_SEAT_TYPE);
    }
}
