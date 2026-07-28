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

    /** 設置してあるならtrue, 列車の上にあるならfalse */
    protected boolean isIndependent;
    private EntityVehicleBase<?> parent;
    private UUID unloadedParent;

    /** 最後に車体から位置を押してもらった時刻。親を引けなくても「生きている」証拠になる。 */
    private long lastFollowTick;

    public EntityVehiclePart(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.lastFollowTick = level.getGameTime();//spawn 直後を孤児扱いしないための猶予
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

    /** EntityVehicleBaseへ自身の登録を行う。(セーブデータからの読み込み時のみ) */
    public abstract void onLoadVehicle();

    @Override
    public boolean canBeCollidedWith() {
        return !this.isRemoved() && !this.isOrphan();
    }

    /**
     * 親を失ったパーツはクリック対象にしない。
     * 掃除役が回収するまでの数 tick でも、取り残された当たり判定が本物の座席の
     * 手前に居るとクリックを吸ってしまうため、両サイドで即座に無効化する。
     */
    @Override
    public boolean isPickable() {
        return !this.isRemoved() && !this.isOrphan();
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
            } else if (!this.level().isClientSide && this.isOrphan()) {
                // 親車両が居ないパーツは即座に消す。
                // 座れない当たり判定だけが残る」状態になる (ユーザー報告)。
                this.discard();
            }
        }
    }

    /**
     * 座席/床パーツはワールドに保存しない。
     * これらは車体の設定 (slotPos) から毎回作り直される派生データで、保存する意味が無い。
     */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    /**
     * 位置を更新 (両サイド: クライアントは補間済みの車体位置に追従)。
     * 回転は設定しない — 乗員の視点が車両の向きに固定されないように
     * (視点は完全に自由、本家の座席と同じ感覚)。
     */
    public void updatePartPos(EntityVehicleBase<?> vehicle) {
        this.lastFollowTick = this.level().getGameTime();
        Vec3 v3 = this.getPartVec();
        v3 = v3.rotateAroundZ(-vehicle.rotationRoll);
        v3 = v3.rotateAroundX(vehicle.getXRot());
        v3 = v3.rotateAroundY(vehicle.getYRot());
        this.setPos(vehicle.getX() + v3.getX(), vehicle.getY() + v3.getY(), vehicle.getZ() + v3.getZ());
    }

    /** 乗員の視点回転には一切干渉しない */
    @Override
    public void onPassengerTurned(net.minecraft.world.entity.Entity rider) {
    }

    /**
     * 本家 setPositionAndRotation2 相当。ただしトラッカー座標をそのまま使わない。
     * クライアントの車体は「最後に受けたサーバー位置へ数 tick かけて寄せる」補間なので、
     * サーバーの座席座標をそのまま入れると車体より数 tick 先に飛んでガタつく。
     * ★これは追従の 3 本目の経路でもある。
     */
    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        EntityVehicleBase<?> vehicle = this.getVehicle();
        if (vehicle != null) {
            this.updatePartPos(vehicle);
        }
        // ★親が引けないときはトラッカー座標を使わない。
        // 純粋な計算結果で、ネットワーク越しの座標は必ず数 tick 古い。
    }

    /**
     * ワールドに追加された時点で車体の一覧へ登録する。
     * tick 内で登録していると、座席が何らかの理由で tick しないとき車体側からも
     * 押し出せず、取り残されたまま復帰できない。
     */
    public void onAddedToLevel() {
        // NeoForge 拡張の onAddedToLevel はバニラに無いので super 呼び出しは不要
        this.registerToVehicle();
    }

    /** 車体の追従リストへ自分を登録する (座席のみ。両サイド)。 */
    protected void registerToVehicle() {
        EntityVehicleBase<?> vehicle = this.getVehicle();
        if (vehicle != null && this instanceof EntityFloor floor) {
            vehicle.setFloor(floor);
        }
    }

    public void setVehicle(EntityVehicleBase<?> vehicle) {
        this.entityData.set(DATA_VEHICLE, vehicle.getId());
        this.parent = vehicle;
    }

    /**
     * 親車体。居なくなっていたら必ず null を返す。
     * ★以前はここで引き直しに失敗しても parent に削除済みの車体が
     * 残ったままで、それをそのまま返していた。
     */
    public EntityVehicleBase<?> getVehicle() {
        if (this.parent == null || this.parent.isRemoved()) {
            this.parent = null;
            Entity entity = this.level().getEntity(this.entityData.get(DATA_VEHICLE));
            if (entity instanceof EntityVehicleBase<?> vehicle && !vehicle.isRemoved()) {
                this.parent = vehicle;
            }
        }
        return this.parent;
    }

    /** 親車体のエンティティ ID (車体が既に消えていても引ける)。 */
    public int getVehicleId() {
        return this.entityData.get(DATA_VEHICLE);
    }

    /**
     * 親を失っていて、もうワールドに居てはいけない状態か。
     * 掃除役 (SeatCleanup) と当たり判定の有効/無効の両方がこれを見る。
     */
    public boolean isOrphan() {
        if (this.isIndependent) {
            return false;//設置型パーツは単独で存在してよい
        }
        if (this.unloadedParent != null) {
            return false;//セーブから復帰中。親を引き直すまで待つ
        }
        if (this.getVehicle() != null) {
            return false;
        }
        // ★自分では親を引けなくても、車体が位置を押してくれているなら生きている。
        // 車体側の一覧に載っていれば追従は成立していて、当たり判定も正しい場所にある。
        return this.level().getGameTime() - this.lastFollowTick > 40L;
    }

    /** EntityTrainとの相対位置を保存 */
    public void setPartPos(float x, float y, float z) {
        this.entityData.set(DATA_VEC_X, x);
        this.entityData.set(DATA_VEC_Y, y);
        this.entityData.set(DATA_VEC_Z, z);
    }

    /** EntityTrainとの相対位置を取得 */
    public Vec3 getPartVec() {
        return new Vec3(this.entityData.get(DATA_VEC_X), this.entityData.get(DATA_VEC_Y), this.entityData.get(DATA_VEC_Z));
    }

    /** 本家 needsUpdatePos: 次 tick に位置を親車両へ追従させるか。 */
    public boolean needsUpdatePos = true;

}
