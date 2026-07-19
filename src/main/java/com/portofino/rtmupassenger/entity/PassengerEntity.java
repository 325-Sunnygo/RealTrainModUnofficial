package com.portofino.rtmupassenger.entity;

import com.portofino.rtmupassenger.station.PassengerPopulation;
import com.portofino.rtmupassenger.station.StopTargetBlock;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * 乗客 NPC。停車中でドアの開いた列車にドア前から直接着席し、目的の駅で降りる。
 *
 * <p>状態遷移: WAITING(停止位置目標で待つ) → BOARDING(ドアまで歩く→着席) →
 * RIDING(乗車) → LEAVING(降車後、駅へ立ち去り) → discard。
 *
 * <p><b>降車判定は乗客自身が乗車中に行う</b> (駅ブロックの tick に頼らない)。
 * 駅ブロックのチャンクがアンロードされていても、列車 (と一緒にロードされている乗客) が
 * 目的駅の座標に近づき停車してドアが開けば、扉が開いてから 1 秒後に降りる。
 * これが「駅のチャンクがロードされていないと降りない」問題の根治。
 *
 * <p><b>列車が壊されたら乗客も消える</b>。総数はワールド全体で
 * {@link PassengerPopulation} により厳密に管理される (チャンク外も数える)。
 */
public class PassengerEntity extends PathfinderMob {

    public enum State {
        WAITING, BOARDING, ENTERING, RIDING, ALIGHTING, LEAVING
    }

    /** 車内歩行の 1 tick の歩幅 (ゆっくり)。 */
    private static final double WALK_STEP = 0.11D;
    /** 車内で歩く/立つ床の追加下げ量 (立ち乗りの床より NPC はこのぶん低く歩く)。高すぎたので下げる。 */
    private static final double NPC_FLOOR_LOWER = 0.4D;

    /** 「目的駅に着いた」とみなす、列車の車体軸から目的駅ブロックまでの距離の2乗 (10m)。 */
    private static final double ALIGHT_DIST_SQ = 100.0D;

    private State state = State.WAITING;
    @Nullable
    private BlockPos homeStation;
    @Nullable
    private BlockPos destinationPos;
    /** 待機・乗車するドア (停止位置目標)。 */
    @Nullable
    private BlockPos waitSpot;
    /** 乗車を試みている列車 (サーバー側のみ、保存しない)。 */
    @Nullable
    private EntityTrainBase targetTrain;
    /** 直前 tick に乗っていた列車 (破壊検出用)。 */
    @Nullable
    private EntityTrainBase lastRiddenTrain;
    /** 降車予定 tick (0=未予約)。扉が開いてから 1 秒待って降りる。 */
    private int alightAtTick;
    /** 乗車試行の期限。過ぎたら WAITING へ戻る。 */
    private int boardDeadline;
    /** 立ち去り開始 tick。 */
    private int leaveStart;
    /** 次に navigation を出し直す tick。 */
    private int nextRepath;
    /** 車内歩行の経由点番号 (0=扉正面/内側, 1=扉通過, 2=席)。 */
    private int walkStage;
    /** 目指す/着席中の座席オフセット (車体ローカル)。 */
    @Nullable
    private float[] ridingSeat;
    /** 乗車で使う<b>実際のドア</b>の車体ローカル位置 (モデルのドアグループ重心)。null なら停止位置目標基準。 */
    @Nullable
    private float[] boardDoorLocal;
    /** 降車で使う実際のドアの車体ローカル位置。null なら停止位置目標基準。 */
    @Nullable
    private float[] alightDoorLocal;
    /** 降車の出口ドア (目的駅の停止位置目標)。null なら座席側から出る。 */
    @Nullable
    private BlockPos exitDoor;
    /** 車内歩行 (ENTERING/ALIGHTING) の開始 tick (詰まり保険)。 */
    private int interiorStart;

    public PassengerEntity(EntityType<? extends PassengerEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                //殴られても弾き飛ばされない (整列を崩さない)。押されたぶんは tickWaiting が整列位置へ戻す。
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        //移動は tick で navigation を直接制御する。ここは待機中の仕草だけ。
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    //---- 駅 BE から呼ばれる操作 ----

    /** スポーン時の初期化 (所属駅・目的駅・待機ドア)。 */
    public void initPassenger(BlockPos home, @Nullable BlockPos dest, @Nullable BlockPos waitSpot) {
        this.homeStation = home.immutable();
        this.restrictTo(home, 24);
        this.destinationPos = dest == null ? null : dest.immutable();
        this.waitSpot = waitSpot == null ? null : waitSpot.immutable();
        this.state = State.WAITING;
    }

    public State getState() {
        return this.state;
    }

    @Nullable
    public BlockPos getWaitSpot() {
        return this.waitSpot;
    }

    @Nullable
    public BlockPos getDestinationPos() {
        return this.destinationPos;
    }

    /** 停車中の列車へ乗車開始 (駅 BE が停車＋ドア開を検知して各行列の先頭に対して呼ぶ)。 */
    public void beginBoarding(EntityTrainBase train) {
        if (this.state != State.WAITING) {
            return;
        }
        this.state = State.BOARDING;
        this.targetTrain = train;
        this.boardDeadline = this.tickCount + 20 * 15;
        //★停止位置目標に最も近い<b>実際のドア</b>へ吸い付く。特急などドア配置が違っても、
        //  停止位置目標がドアと合っていなくても、本物のドアから乗る。ドアが無い車種は null。
        this.boardDoorLocal = this.waitSpot == null ? null
                : TrainDoorLocator.nearestDoorLocal(train,
                        this.waitSpot.getX() + 0.5D, this.waitSpot.getZ() + 0.5D);
    }

    /**
     * 乗降のドア形状を車体の実位置から求める。
     * {@code local} (ドアの車体ローカル位置) があればそれを、無ければ停止位置目標 {@code spot} を
     * ドア位置とする。返り値 {doorX, doorZ, inX, inZ, inLen}: ドアのワールド XZ と、そこから車内へ
     * 向かう単位ベクトル (車体軸への垂線) と軸までの距離。
     */
    private double[] doorGeom(EntityTrainBase train, @Nullable float[] local, @Nullable BlockPos spot) {
        double doorX;
        double doorZ;
        if (local != null) {
            Vec3 w = train.localToWorldVec(local[0], local[1], local[2]);
            doorX = w.x;
            doorZ = w.z;
        } else if (spot != null) {
            doorX = spot.getX() + 0.5D;
            doorZ = spot.getZ() + 0.5D;
        } else {
            doorX = this.getX();
            doorZ = this.getZ();
        }
        double[] foot = closestAxisPoint(train, doorX, doorZ);
        double inX = foot[0] - doorX;
        double inZ = foot[1] - doorZ;
        double inLen = Math.sqrt(inX * inX + inZ * inZ);
        if (inLen > 1.0E-4D) {
            inX /= inLen;
            inZ /= inLen;
        } else {
            inX = 0.0D;
            inZ = 1.0D;
        }
        return new double[]{doorX, doorZ, inX, inZ, inLen};
    }

    /**
     * 点 (px,pz) から列車の車体軸 (台車0-1を結ぶ線分) への最近点 {cx, cz}。
     * ドア侵入/退出の「線路と垂直な向き」を車体の実位置から正確に出すために使う。
     */
    public static double[] closestAxisPoint(EntityTrainBase t, double px, double pz) {
        jp.ngt.rtm.entity.train.EntityBogie b0 = t.getBogie(0);
        jp.ngt.rtm.entity.train.EntityBogie b1 = t.getBogie(1);
        if (b0 == null || b1 == null) {
            return new double[]{t.getX(), t.getZ()};
        }
        double ax = b0.getX();
        double az = b0.getZ();
        double abx = b1.getX() - ax;
        double abz = b1.getZ() - az;
        double len2 = abx * abx + abz * abz;
        double param = len2 < 1.0E-6D ? 0.0D : ((px - ax) * abx + (pz - az) * abz) / len2;
        param = Math.max(0.0D, Math.min(1.0D, param));
        return new double[]{ax + abx * param, az + abz * param};
    }

    /** 点 (px,pz) から列車の車体軸 (台車0-1を結ぶ線分) への水平距離の2乗。駅 BE も使う。 */
    public static double distToTrainBodySq(EntityTrainBase t, double px, double pz) {
        jp.ngt.rtm.entity.train.EntityBogie b0 = t.getBogie(0);
        jp.ngt.rtm.entity.train.EntityBogie b1 = t.getBogie(1);
        if (b0 == null || b1 == null) {
            double dx = t.getX() - px;
            double dz = t.getZ() - pz;
            return dx * dx + dz * dz;
        }
        double ax = b0.getX();
        double az = b0.getZ();
        double abx = b1.getX() - ax;
        double abz = b1.getZ() - az;
        double len2 = abx * abx + abz * abz;
        double param = len2 < 1.0E-6D ? 0.0D : ((px - ax) * abx + (pz - az) * abz) / len2;
        param = Math.max(0.0D, Math.min(1.0D, param));
        double cx = ax + abx * param;
        double cz = az + abz * param;
        double dx = px - cx;
        double dz = pz - cz;
        return dx * dx + dz * dz;
    }

    //---- tick ----

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        //乗車中: 列車を追跡し、降車判定は自分で行う (駅ブロックの tick に頼らない)。
        if (this.getVehicle() instanceof EntityTrainBase train) {
            this.lastRiddenTrain = train;
            this.state = State.RIDING;
            this.tickRiding(train);
            return;
        }
        if (this.lastRiddenTrain != null) {
            if (this.lastRiddenTrain.isRemoved() && this.state == State.RIDING) {
                //降車を経ずに列車が消えた = 破壊。乗客も消滅 (要件)。
                this.discard();
                return;
            }
            this.lastRiddenTrain = null;
        }
        //車内歩行中に列車が壊れたら: ENTERING(車内)=消滅、ALIGHTING(出口付近)=立ち去りへ。
        if ((this.state == State.ENTERING || this.state == State.ALIGHTING)
                && (this.targetTrain == null || this.targetTrain.isRemoved())) {
            this.setInteriorMode(false);
            if (this.state == State.ENTERING) {
                this.discard();
                return;
            }
            this.toLeaving();
        }

        switch (this.state) {
            case WAITING -> this.tickWaiting();
            case BOARDING -> this.tickBoarding();
            case ENTERING -> this.tickEntering();
            case ALIGHTING -> this.tickAlighting();
            case RIDING -> this.toLeaving(); //外部要因で降ろされた保険
            case LEAVING -> this.tickLeaving();
        }
    }

    /**
     * 乗車中の降車判定: 目的駅の近くで停車しドアが開いたら、<b>扉が開いてから 1 秒待って</b>降りる。
     * 駅ブロックのチャンク状態に依存しないので、チャンク外の駅でも確実に降りられる。
     */
    private void tickRiding(EntityTrainBase train) {
        if (this.destinationPos == null) {
            return;
        }
        boolean stopped = Math.abs(train.getSpeed()) < 0.05F;
        boolean doorOpen = train.getTrainStateData(4) != 0; //State_Door (0=閉)
        if (this.alightAtTick == 0) {
            boolean atDest = distToTrainBodySq(train,
                    this.destinationPos.getX() + 0.5D, this.destinationPos.getZ() + 0.5D) <= ALIGHT_DIST_SQ;
            if (atDest && stopped && doorOpen) {
                this.alightAtTick = this.tickCount + 20; //扉が開いてから 1 秒待つ
            }
        } else if (!stopped || !doorOpen) {
            this.alightAtTick = 0; //発車/ドア閉で降りそこねた → 次に停車したら再判定
        } else if (this.tickCount >= this.alightAtTick) {
            //1 秒経過 → 席から立って車内を歩いて降りる (ワープしない)。
            this.beginAlightingWalk(train);
        }
    }

    /** 降車開始: 席から立ち、車内を歩いて扉→車外へ出る (ALIGHTING)。ワープしない。 */
    private void beginAlightingWalk(EntityTrainBase train) {
        this.alightAtTick = 0;
        if (this.ridingSeat == null) {
            this.ridingSeat = train.getSeatOffset(this); //リロードで消えていても復元
        }
        this.exitDoor = this.findExitDoor(train);
        //降り口の停止位置目標に最も近い<b>実際のドア</b>へ吸い付いて出る。無ければ停止位置目標基準。
        this.alightDoorLocal = this.exitDoor == null ? null
                : TrainDoorLocator.nearestDoorLocal(train,
                        this.exitDoor.getX() + 0.5D, this.exitDoor.getZ() + 0.5D);
        this.stopRiding();
        //席のワールド位置へ置く (今そこに座っているので見た目の飛びは無い)。
        if (this.ridingSeat != null) {
            Vec3 sw = train.getSeatWorldPos(this.ridingSeat);
            this.setPos(sw.x, (train.getInteriorFloorY() - NPC_FLOOR_LOWER), sw.z);
        }
        this.setDeltaMovement(Vec3.ZERO);
        this.fallDistance = 0.0F;
        this.targetTrain = train;
        this.walkStage = 0;
        this.interiorStart = this.tickCount;
        this.setInteriorMode(true);
        this.getNavigation().stop();
        this.state = State.ALIGHTING;
    }

    /** 目的駅周辺の停止位置目標のうち、列車の車体に最も近いもの (降り口)。チャンク未ロードなら null。 */
    @Nullable
    private BlockPos findExitDoor(EntityTrainBase train) {
        if (this.destinationPos == null || !this.level().hasChunkAt(this.destinationPos)) {
            return null;
        }
        BlockPos best = null;
        double bestSq = Double.MAX_VALUE;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                for (int dy = -3; dy <= 3; dy++) {
                    mp.set(this.destinationPos.getX() + dx, this.destinationPos.getY() + dy,
                            this.destinationPos.getZ() + dz);
                    if (this.level().getBlockState(mp).getBlock() instanceof StopTargetBlock) {
                        double d = distToTrainBodySq(train, mp.getX() + 0.5D, mp.getZ() + 0.5D);
                        if (d < bestSq) {
                            bestSq = d;
                            best = mp.immutable();
                        }
                    }
                }
            }
        }
        return best;
    }

    /** 待機: 停止位置目標の後ろへ一直線に整列 (前に人がいれば後ろに並ぶ)。殴られ/押されても必ず戻る。 */
    private void tickWaiting() {
        if (this.waitSpot == null) {
            return;
        }
        int qi = this.queueIndex();
        double[] dir = this.queueDir();
        //qi+1: 先頭も停止位置目標の 1 ブロック後ろに立たせ、目標の真上に溜まって重ならないようにする
        //(乗車時は tickBoarding が目標=ドアまで歩く)。以降 1 ブロックずつ後ろへ伸ばす。
        double slotX = this.waitSpot.getX() + 0.5D + dir[0] * (qi + 1);
        double slotZ = this.waitSpot.getZ() + 0.5D + dir[1] * (qi + 1);
        double dx = slotX - this.getX();
        double dz = slotZ - this.getZ();
        double distSq = dx * dx + dz * dz;
        //整列位置から外れている (殴られた・押された・湧いた直後) → 歩いて戻る。
        //★必ず<b>ナビゲーション (経路探索)</b> で戻す。setPos で直接動かすと当たり判定を無視して
        //  壁の中へ押し込み、そこから出られず「壁抜け・ドアに行けない・乗れない」の原因になる。
        if (distSq > 0.8D) {
            //残った横方向の勢い (ノックバック等) を止めてから経路を出す。
            Vec3 v = this.getDeltaMovement();
            this.setDeltaMovement(v.x * 0.5D, v.y, v.z * 0.5D);
            if (this.tickCount >= this.nextRepath || this.getNavigation().isDone()) {
                this.getNavigation().moveTo(slotX, this.waitSpot.getY(), slotZ, 0.9D);
                this.nextRepath = this.tickCount + 10;
            }
            return;
        }
        //整列位置に到着 → その場で止まり、停止位置目標 (ドア) を向く。
        this.getNavigation().stop();
        Vec3 v = this.getDeltaMovement();
        this.setDeltaMovement(0.0D, v.y, 0.0D);
        this.getLookControl().setLookAt(this.waitSpot.getX() + 0.5D, this.getEyeY(), this.waitSpot.getZ() + 0.5D);
    }

    /** 乗車: ドア (停止位置目標) まで歩き、着いたら演出なしでそのまま着席する。 */
    private void tickBoarding() {
        EntityTrainBase train = this.targetTrain;
        if (train == null || train.isRemoved()
                || Math.abs(train.getSpeed()) > 0.05F
                || train.getTrainStateData(4) == 0
                || this.tickCount > this.boardDeadline) {
            //乗り損ね/ドアが閉まった: 待機に戻る
            this.targetTrain = null;
            this.state = State.WAITING;
            return;
        }
        //実際のドア (吸着先) の位置とホーム側への向きを車体の実位置から出す。
        double[] g = this.doorGeom(train, this.boardDoorLocal, this.waitSpot);
        double doorX = g[0];
        double doorZ = g[1];
        //ホーム側 (車内の逆) へ 1.6 ブロック出た「ドア前のホーム上」を目標に歩く。端ぎりぎりでなく
        //ホーム側なので経路探索が届きやすい。
        double approachX = doorX - g[2] * 1.6D;
        double approachZ = doorZ - g[3] * 1.6D;
        double doorY = this.waitSpot != null ? this.waitSpot.getY() : this.getY();
        double dx = approachX - this.getX();
        double dz = approachZ - this.getZ();
        double dsq = dx * dx + dz * dz;
        boolean reached = dsq <= 1.6D;
        //★ドア前はプラットフォーム端に近く、地上の経路探索が寄り切れず手前で止まりがち
        //  (moveTo が経路を返さない=isDone)。行き詰まって、かつドア前がそこそこ近ければ、そこから
        //  先は ENTERING の noPhysics 歩行で確実に寄せる。これで「ドアまで行かない・乗らない」を防ぐ。
        long elapsed = this.tickCount - (this.boardDeadline - 20 * 15);
        boolean navStuckNearDoor = this.getNavigation().isDone() && elapsed > 12 && dsq <= 49.0D;
        if (!reached && !navStuckNearDoor) {
            //まだ経路で寄れる余地がある → ドア前まで歩く (一定間隔で必ず再探索)。
            if (this.tickCount >= this.nextRepath || this.getNavigation().isDone()) {
                this.getNavigation().moveTo(approachX, doorY, approachZ, 1.0D);
                this.nextRepath = this.tickCount + 8;
            }
            this.getLookControl().setLookAt(doorX, this.getEyeY(), doorZ);
            return;
        }
        //ドア前に到着 (または経路が端で行き詰まった) → 空席を確保して ENTERING で確実に扉から入る。
        this.getNavigation().stop();
        float[] seat = findFreeSeat(train);
        if (seat != null) {
            this.ridingSeat = seat;
            this.walkStage = 0;
            this.interiorStart = this.tickCount;
            this.setInteriorMode(true);
            this.state = State.ENTERING;
            return;
        }
        //満席: 待機に戻す
        this.targetTrain = null;
        this.state = State.WAITING;
    }

    /**
     * 乗車の車内歩行: ① 停止位置目標 (=ドア) の真上へ整列 → ② そこから線路側へ真っ直ぐ車内へ →
     * ③ 席へ、と自分の足で歩き、席の 1 ブロック以内で着席する。
     *
     * <p><b>侵入の基準は「停止位置目標の実座標」</b>にする (以前は車体からの相対位置で算出していたため、
     * 列車が 1 ブロックほどずれて停まると扉の脇=壁を抜けて入ってしまっていた)。停止位置目標は
     * ユーザーが扉の位置に合わせて置くマーカーなので、そこを基準に<b>ホームと垂直に真っ直ぐ</b>
     * 入れば、多少停止位置がずれても常に扉から入る。中に入ってからは席まで歩く (車内は従来どおり)。
     */
    private void tickEntering() {
        EntityTrainBase train = this.targetTrain;
        if (train == null || train.isRemoved() || this.ridingSeat == null || this.waitSpot == null) {
            this.setInteriorMode(false);
            this.targetTrain = null;
            this.state = State.WAITING;
            return;
        }
        boolean departed = Math.abs(train.getSpeed()) > 0.05F || train.getTrainStateData(4) == 0;
        boolean timeout = this.tickCount - this.interiorStart > 20 * 14;
        double floorY = (train.getInteriorFloorY() - NPC_FLOOR_LOWER);
        //★実際のドア (吸着先) の位置と、そこから車内へ向かう垂線を車体の実位置から出す。
        //  boardDoorLocal があればモデルの本物のドア、無ければ停止位置目標を基準にする。
        double[] g = this.doorGeom(train, this.boardDoorLocal, this.waitSpot);
        double doorX = g[0];
        double doorZ = g[1];
        double inX = g[2];
        double inZ = g[3];
        double inLen = g[4];

        if (this.walkStage == 0) {
            //① 実際のドアの真正面 (ホーム側すぐ) へ整列してから入る。
            double frontX = doorX - inX * 0.6D;
            double frontZ = doorZ - inZ * 0.6D;
            if (this.walkToward(frontX, this.getY(), frontZ, false, 0.3D) || departed || timeout) {
                this.walkStage = 1;
            }
            return;
        }
        if (this.walkStage == 1) {
            //② ドアから車内へ、軸を少し越える所まで真っ直ぐ入る (必ず扉を通り、壁を斜めに抜けない)。
            double into = inLen + 0.4D;
            double tx = doorX + inX * into;
            double tz = doorZ + inZ * into;
            if (this.walkToward(tx, floorY, tz, true, 0.35D) || departed || timeout) {
                this.walkStage = 2;
            }
            return;
        }
        //③ 席へ歩き、1 ブロック以内で着席。
        Vec3 seatW = train.getSeatWorldPos(this.ridingSeat);
        if (this.walkToward(seatW.x, floorY, seatW.z, true, 1.0D) || departed || timeout) {
            float[] seat = this.ridingSeat;
            if (train.isSeatOccupied(seat)) {
                seat = findFreeSeat(train);
            }
            this.setInteriorMode(false);
            if (seat != null && train.mountEntityToSeat(this, seat)) {
                this.ridingSeat = seat;
                this.state = State.RIDING;
                this.targetTrain = null;
                this.walkStage = 0;
            } else {
                this.discard(); //満席で座れなかった (稀)
            }
        }
    }

    /**
     * 降車の車内歩行: ① 扉の内側へ → ② 扉から車外(ホーム)へ、と自分の足で歩いて出る。車外に
     * 出たら立ち去りへ。
     */
    private void tickAlighting() {
        EntityTrainBase train = this.targetTrain;
        if (train == null || train.isRemoved()) {
            this.setInteriorMode(false);
            this.toLeaving();
            return;
        }
        boolean timeout = this.tickCount - this.interiorStart > 20 * 14;
        double floorY = (train.getInteriorFloorY() - NPC_FLOOR_LOWER);

        if (this.alightDoorLocal != null || this.exitDoor != null) {
            //実際のドア (吸着先。無ければ降り口マーカー) を基準に、そこからホームへ真っ直ぐ出る。
            double[] g = this.doorGeom(train, this.alightDoorLocal, this.exitDoor);
            double doorX = g[0];
            double doorZ = g[1];
            double outX = -g[2];  //車内方向の逆 = ホーム方向
            double outZ = -g[3];
            double outY = this.exitDoor != null ? this.exitDoor.getY() : floorY;
            if (this.walkStage == 0) {
                //① 車内から実際のドアの真上まで歩く。
                if (this.walkToward(doorX, floorY, doorZ, true, 0.32D) || timeout) {
                    this.walkStage = 1;
                }
                return;
            }
            //② ドアからホームへ 1.6 ブロック出て、立ち去りへ。
            double tx = doorX + outX * 1.6D;
            double tz = doorZ + outZ * 1.6D;
            if (this.walkToward(tx, outY, tz, false, 0.4D) || timeout) {
                this.setInteriorMode(false);
                this.targetTrain = null;
                this.exitDoor = null;
                this.alightDoorLocal = null;
                this.walkStage = 0;
                this.toLeaving();
            }
            return;
        }

        //降り口マーカーが無い (目的駅チャンク未ロード等): 車体から横へ出す従来のフォールバック。
        double halfWidth = EntityTrainBase.TRAIN_WIDTH / 2.0D;
        double[] door = this.doorLocal(train, null);
        double sign = door[0];
        double doorLen = door[1];
        if (this.walkStage == 0) {
            Vec3 w = train.localToWorldVec(sign * (halfWidth - 0.6D), 0.0D, doorLen);
            if (this.walkToward(w.x, floorY, w.z, true, 0.3D) || timeout) {
                this.walkStage = 1;
            }
            return;
        }
        Vec3 w = train.localToWorldVec(sign * (halfWidth + 1.2D), 0.0D, doorLen);
        if (this.walkToward(w.x, floorY, w.z, false, 0.4D) || timeout) {
            this.setInteriorMode(false);
            this.targetTrain = null;
            this.exitDoor = null;
            this.walkStage = 0;
            this.toLeaving();
        }
    }

    /**
     * 車内歩行の 1 tick: (tx,ty,tz) へ WALK_STEP ずつ近づく。{@code insideCar} なら Y を床へスナップ、
     * そうでなければ徐々に寄せる。進行方向へ体を向ける。水平距離が {@code reach} 以内で true。
     */
    private boolean walkToward(double tx, double ty, double tz, boolean insideCar, double reach) {
        double dx = tx - this.getX();
        double dz = tz - this.getZ();
        double h = Math.sqrt(dx * dx + dz * dz);
        double ny = insideCar ? ty : (this.getY() + (ty - this.getY()) * 0.25D);
        if (h > 1.0E-4D) {
            double mv = Math.min(WALK_STEP, h);
            this.setPos(this.getX() + dx / h * mv, ny, this.getZ() + dz / h * mv);
            float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
            this.setYRot(yaw);
            this.yBodyRot = yaw;
            this.setYHeadRot(yaw);
        } else {
            this.setPos(this.getX(), ny, this.getZ());
        }
        this.setDeltaMovement(Vec3.ZERO);
        this.fallDistance = 0.0F;
        return h <= reach;
    }

    /**
     * 扉の車体ローカル {widthSign(±1), length}。{@code ref} (停止位置目標) を車体ローカルへ射影して
     * 長さと左右どちら側かを取る。列車の実位置・向きから算出するので停止位置が多少ずれても追従する。
     */
    private double[] doorLocal(EntityTrainBase train, @Nullable BlockPos ref) {
        if (ref == null) {
            double len = this.ridingSeat != null ? this.ridingSeat[2] : 0.0D;
            return new double[]{1.0D, len};
        }
        Vec3 loc = train.worldToLocalVec(ref.getX() + 0.5D, train.getY(), ref.getZ() + 0.5D);
        return new double[]{loc.x >= 0.0D ? 1.0D : -1.0D, loc.z};
    }

    /** 車内歩行モード: 重力と物理(押し出し・衝突)を切る。押されて経路が乱れる=壁抜けを防ぐ。 */
    private void setInteriorMode(boolean on) {
        this.setNoGravity(on);
        this.noPhysics = on;
    }

    /** 降車後: 目的駅ブロックへ歩き、着いたら消える。 */
    private void tickLeaving() {
        BlockPos exit = this.destinationPos;
        if (exit != null) {
            double dx = (exit.getX() + 0.5D) - this.getX();
            double dz = (exit.getZ() + 0.5D) - this.getZ();
            if (dx * dx + dz * dz <= 2.0D) {
                this.discard();
                return;
            }
            if (this.tickCount >= this.nextRepath && this.getNavigation().isDone()) {
                this.getNavigation().moveTo(exit.getX() + 0.5D, exit.getY(), exit.getZ() + 0.5D, 1.0D);
                this.nextRepath = this.tickCount + 12;
            }
        }
        if (this.tickCount - this.leaveStart > 20 * 25) {
            this.discard(); //保険
        }
    }

    private void toLeaving() {
        this.getNavigation().stop();
        this.state = State.LEAVING;
        this.leaveStart = this.tickCount;
        if (this.destinationPos != null) {
            this.restrictTo(this.destinationPos, 24);
        }
    }

    /** 待ち行列でのこの乗客の順番 (0=先頭)。同じ停止位置目標に並ぶ、先に湧いた乗客の数。 */
    private int queueIndex() {
        if (this.waitSpot == null) {
            return 0;
        }
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(this.waitSpot).inflate(24.0D, 6.0D, 24.0D);
        int idx = 0;
        for (PassengerEntity o : this.level().getEntitiesOfClass(PassengerEntity.class, box)) {
            if (o == this || o.waitSpot == null || !o.waitSpot.equals(this.waitSpot)) {
                continue;
            }
            if ((o.state == State.WAITING || o.state == State.BOARDING) && o.getId() < this.getId()) {
                idx++;
            }
        }
        return idx;
    }

    /**
     * 並ぶ方向: 停止位置目標から見て<b>駅ブロック側 (ホーム奥)</b>へ一直線。駅ブロックはホーム上
     * (線路と反対側) に置かれるので、この向き＝線路から離れる向き＝ドアの後ろへ縦並び、になる。
     * <p><b>列車の位置ではなく駅ブロックで向きを決める</b>のが要点。列車が通過するたびに向きが
     * 反転して列が乱れるのを防ぎ、常に同じ向きへ綺麗に整列する。1 つの最寄り軸へ量子化する。
     */
    private double[] queueDir() {
        if (this.waitSpot == null || this.homeStation == null) {
            return new double[]{0.0D, 1.0D};
        }
        double dx = this.homeStation.getX() - this.waitSpot.getX();
        double dz = this.homeStation.getZ() - this.waitSpot.getZ();
        if (Math.abs(dx) < 1.0E-4D && Math.abs(dz) < 1.0E-4D) {
            return new double[]{0.0D, 1.0D};
        }
        if (Math.abs(dx) >= Math.abs(dz)) {
            return new double[]{dx >= 0 ? 1.0D : -1.0D, 0.0D};
        }
        return new double[]{0.0D, dz >= 0 ? 1.0D : -1.0D};
    }

    /** 列車の空席オフセットを 1 つ返す。無ければ null。 */
    @Nullable
    private static float[] findFreeSeat(EntityTrainBase train) {
        for (float[] slot : seatsOf(train)) {
            if (slot != null && slot.length >= 3 && !train.isSeatOccupied(slot)) {
                return slot;
            }
        }
        return null;
    }

    /**
     * 座席オフセット群: (1) config の slotPos、(2) 無ければ既定 (車体中心付近に 8 席)。
     * 座席未定義の車両でも乗れるようにするため。
     */
    private static float[][] seatsOf(EntityTrainBase train) {
        try {
            float[][] slots = train.getConfig().getSlotPos();
            if (slots != null && slots.length > 0) {
                return slots;
            }
        } catch (Throwable ignored) {
        }
        float[][] fallback = new float[8][];
        for (int i = 0; i < 8; i++) {
            fallback[i] = new float[]{i % 2 == 0 ? 0.4F : -0.4F, 0.0F, (i / 2 - 1.5F) * 1.2F};
        }
        return fallback;
    }

    //---- 永続化 / 総数管理 ----

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("PsgState", this.state.name());
        if (this.destinationPos != null) {
            tag.put("PsgDest", NbtUtils.writeBlockPos(this.destinationPos));
        }
        if (this.homeStation != null) {
            tag.put("PsgHome", NbtUtils.writeBlockPos(this.homeStation));
        }
        if (this.waitSpot != null) {
            tag.put("PsgWait", NbtUtils.writeBlockPos(this.waitSpot));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        try {
            this.state = State.valueOf(tag.getString("PsgState"));
        } catch (IllegalArgumentException e) {
            this.state = State.WAITING;
        }
        //ターゲット列車を保存しない遷移状態は安全側へ戻す。物理も確実に復元。
        if (this.state == State.BOARDING || this.state == State.ENTERING) {
            this.state = State.WAITING;
        } else if (this.state == State.ALIGHTING) {
            this.state = State.LEAVING;
            this.leaveStart = this.tickCount;
        }
        this.setInteriorMode(false);
        NbtUtils.readBlockPos(tag, "PsgDest").ifPresent(pos -> this.destinationPos = pos);
        NbtUtils.readBlockPos(tag, "PsgHome").ifPresent(pos -> {
            this.homeStation = pos;
            this.restrictTo(pos, 24);
        });
        NbtUtils.readBlockPos(tag, "PsgWait").ifPresent(pos -> this.waitSpot = pos);
    }

    /**
     * 本当に消えた時 (討伐・discard) だけ総数から引く。チャンクアンロード
     * ({@code shouldDestroy()=false}) では引かない — チャンク外でも「存在している」数として
     * 維持することで、ワールド全体の上限を厳密に守る。
     */
    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!this.level().isClientSide && reason.shouldDestroy() && this.level() instanceof ServerLevel sl) {
            PassengerPopulation.get(sl).remove(this.getUUID());
        }
        super.remove(reason);
    }

    //---- 振る舞いの細部 ----

    /** 自然デスポーンしない (駅で待ち続ける)。 */
    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    /** 他の乗客に押されない (待機列でも乗車中でも)。ドアでの押し合い・列崩れを防ぐ。 */
    @Override
    public boolean isPushable() {
        return false;
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData groupData) {
        return super.finalizeSpawn(level, difficulty, spawnType, groupData);
    }
}
