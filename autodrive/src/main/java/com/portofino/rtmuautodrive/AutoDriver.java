package com.portofino.rtmuautodrive;

import jp.ngt.rtm.electric.SignalLevel;
import jp.ngt.rtm.entity.RTMEntities;
import jp.ngt.rtm.entity.npc.EntityMotorman;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.entity.train.util.EnumNotch;
import jp.ngt.rtm.entity.train.util.TrainState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * 1 編成ぶんの自動運転。{@link AutoDriveState} が 4 tick おきに呼ぶ。
 *
 * <p>速度の単位はブロック/tick (本家と同じ)。km/h に直すには 72 倍する。
 */
public final class AutoDriver {

    /** 最高速度 (ブロック/tick)。0.9 = 約 65km/h。本家 SignalLevel の「進行」上限に合わせてある。 */
    private static final float MAX_SPEED = 0.9F;
    /** 駅で停まっている時間 (tick)。20 tick = 1 秒。 */
    private static final int DWELL_TICKS = 200;
    /** ドアを閉めてから発車するまでの間 (tick)。3 秒。 */
    private static final int DEPART_DELAY_TICKS = 60;
    /** 終点でドアを開けてから編成が消えるまで (tick)。10 秒。 */
    private static final int TERMINAL_DESPAWN_TICKS = 200;
    /** 駅を探す範囲 (ブロック)。これより遠い駅は見ない。 */
    private static final double STATION_SEARCH = 256.0D;
    /** 停止位置の許容差 (ブロック)。これ以内に入ったら「着いた」とみなす。 */
    private static final double STOP_TOLERANCE = 1.5D;
    /** 発車後、同じ駅をもう一度停車対象にしないための距離 (ブロック)。 */
    private static final double LEAVE_DISTANCE = 24.0D;
    /**
     * 減速の目安 (ブロック/tick^2)。停止位置までの目標速度カーブを引くのに使う。
     * 0.003 ≒ 65km/h から約 135 ブロックで停止。
     */
    private static final double APPROACH_DECEL = 0.003D;
    /** 止まったと判定する速度。 */
    private static final float STOPPED_SPEED = 0.01F;

    private enum Phase {
        /** 走行中 */
        RUN,
        /** 駅に向けて減速中 */
        APPROACH,
        /** 停車してドアを開けている */
        DWELL,
        /** ドアを閉めた後、発車までの間 */
        DOOR_CLOSED,
        /** 発車直後 (停めたばかりの駅を通り過ぎるまで) */
        DEPART,
        /** 終点。ドアを開けたまま消えるのを待つ */
        TERMINAL
    }

    private Phase phase = Phase.RUN;
    private int dwellLeft;
    private BlockPos servedStation;
    /** 今止まっているのが終点か。 */
    private boolean atTerminal;
    /** 動かないときの切り分け用。1 編成につき数回だけ出す (一時的)。 */
    private int debugLeft = 20;

    /**
     * 自動運転を入れたときの準備。運転士を乗せてチャンクローダーを ON にする。
     * チャンクローダーは<b>編成全体</b>に入れる (本家はチケットを先頭車だけが張る)。
     */
    public static void prepare(ServerLevel level, EntityTrainBase train, int rollsign) {
        //方向幕 (本家 State_Destination)。編成全体に入る。
        train.setTrainStateData(TrainState.TrainStateType.State_Destination.id,
                (byte) Math.max(0, Math.min(127, rollsign)));
        //★レバーサを「前」に入れる。ここが中立 (Direction_Center) だと isControlCar() が false になり、
        //  updateSpeed が丸ごと飛ばされて setNotch を何度入れても動かない。
        train.setTrainStateData(TrainState.TrainStateType.State_Direction.id,
                TrainState.Direction_Front.data);
        ensureDriver(level, train);
        train.setTrainStateData(TrainState.TrainStateType.State_ChunkLoader.id, (byte) 1);
    }

    /** 自動運転を切ったときの後始末。運転士を降ろしてチャンクローダーを OFF にする。 */
    public static void release(ServerLevel level, EntityTrainBase train) {
        train.setTrainStateData(TrainState.TrainStateType.State_ChunkLoader.id, (byte) 0);
        train.setTrainStateData(TrainState.TrainStateType.State_Door.id, (byte) 0);
        train.setNotch(EnumNotch.brake_7.id);
        for (Entity passenger : train.getPassengers()) {
            if (passenger instanceof EntityMotorman motorman) {
                motorman.setExternallyDriven(false);
                motorman.stopRiding();
                motorman.discard();
            }
        }
    }

    /** 運転台が空いていたら運転士を乗せる (壊された/降りたときの再乗務にも使う)。 */
    private static void ensureDriver(ServerLevel level, EntityTrainBase train) {
        for (Entity passenger : train.getPassengers()) {
            if (passenger instanceof EntityMotorman motorman) {
                motorman.setExternallyDriven(true); //読み込み直しで落ちていても立て直す
                return;
            }
        }
        if (!train.isDriverSeatFree()) {
            return; //プレイヤーが運転席に居る間は乗せない
        }
        EntityMotorman motorman = RTMEntities.MOTORMAN.get().create(level);
        if (motorman == null) {
            return;
        }
        motorman.moveTo(train.getX(), train.getY(), train.getZ(), 0.0F, 0.0F);
        //★本家の運転 AI を止める。止めないと、信号を置いていない線区では
        //  EntityAIDrivingWithSignal が「現示 0 = 停止」と読んで毎回 brake_4 を入れ、
        //  こちらの力行ノッチを打ち消して<b>列車が一切動かない</b>。
        motorman.setExternallyDriven(true);
        if (level.addFreshEntity(motorman)) {
            train.boardDriverSeat(motorman, train.getTrainDirection());
        }
    }

    /** 4 tick に 1 回呼ばれる運転本体。 */
    public void drive(ServerLevel level, EntityTrainBase train,
                      java.util.List<AutoDriveState.RouteStop> stops, int dwellTicks, int rollsign) {
        ensureDriver(level, train);
        if (train.getTrainStateData(TrainState.TrainStateType.State_ChunkLoader.id) == 0) {
            train.setTrainStateData(TrainState.TrainStateType.State_ChunkLoader.id, (byte) 1);
        }
        //★方向幕は毎回入れ直す。発車時に 1 回だけだと、車両側の処理や
        //  再読み込みで戻ってしまったときに直らない。
        byte sign = (byte) Math.max(0, Math.min(127, rollsign));
        if (train.getTrainStateData(TrainState.TrainStateType.State_Destination.id) != sign) {
            train.setTrainStateData(TrainState.TrainStateType.State_Destination.id, sign);
        }

        float speed = Math.abs(train.getSpeed());
        double[] heading = heading(train);

        if (this.debugLeft > 0) {
            this.debugLeft--;
            org.slf4j.LoggerFactory.getLogger("RTMU-AutoDrive").info(
                "[自動運転診断] pos=({},{},{}) 制御車={} 向き={} ノッチ={} 速度={}({}km/h) 現示={} 段階={} 停車駅={}",
                (int) train.getX(), (int) train.getY(), (int) train.getZ(),
                train.isControlCar(),
                train.getTrainStateData(TrainState.TrainStateType.State_Direction.id),
                train.getNotch(), train.getSpeed(), String.format("%.1f", train.getSpeed() * 72.0F),
                train.getSignal(), this.phase, stops.size());
        }

        switch (this.phase) {
            case DWELL -> {
                train.setNotch(EnumNotch.brake_7.id);
                this.dwellLeft -= AutoDriveState.driveInterval();
                if (this.dwellLeft <= 0) {
                    train.setTrainStateData(TrainState.TrainStateType.State_Door.id, (byte) 0);
                    this.dwellLeft = DEPART_DELAY_TICKS;
                    this.phase = Phase.DOOR_CLOSED;
                }
            }
            case DOOR_CLOSED -> {
                //ドアを閉めてすぐ動き出さない (実車と同じで一拍おく)
                train.setNotch(EnumNotch.brake_7.id);
                this.dwellLeft -= AutoDriveState.driveInterval();
                if (this.dwellLeft <= 0) {
                    this.phase = Phase.DEPART;
                }
            }
            case TERMINAL -> {
                //終点。ドアを開けたまま待って、編成ごと消える
                train.setNotch(EnumNotch.brake_7.id);
                this.dwellLeft -= AutoDriveState.driveInterval();
                if (this.dwellLeft <= 0) {
                    despawnFormation(level, train);
                }
            }
            case DEPART -> {
                accelerateTo(train, targetSpeed(train), speed);
                if (this.servedStation == null
                        || distanceAlong(train, this.servedStation, heading) < -LEAVE_DISTANCE) {
                    this.servedStation = null;
                    this.phase = Phase.RUN;
                }
            }
            case APPROACH -> {
                BlockPos station = this.servedStation;
                if (station == null) {
                    this.phase = Phase.RUN;
                    break;
                }
                //★停止位置は<b>先頭車の先端</b>が駅ブロックに来る所。
                //  entity の座標は車体の中心なので、車体の半分だけ手前で止める。
                double d = distanceAlong(train, station, heading) - noseOffset(train);
                // ★「止まった＝着いた」にしてはいけない。
                //   ブレーキが効きすぎて手前で止まっただけのときに、そこでドアを開けて
                //   停車扱いしてしまう (駅の手前でいったん停まる原因はこれだった)。
                //   着いたと言えるのは<b>停止位置に入っている</b>ときだけ。
                if (d > STOP_TOLERANCE) {
                    if (speed <= STOPPED_SPEED) {
                        //手前で止まってしまった: 徐行で停止位置まで詰める
                        train.setNotch(EnumNotch.accelerate_1.id);
                    } else {
                        train.setNotch(approachNotch(speed, d).id);
                    }
                    break;
                }
                {
                    //停止位置に入った。非常ブレーキだと客が飛ぶので常用最大で締める
                    train.setNotch(EnumNotch.brake_5.id);
                    if (speed <= STOPPED_SPEED) {
                        //★停止位置がどれだけずれたかを 1 停車 1 行だけ残す (切り分け用)
                        double raw = distanceAlong(train, station, heading);
                        org.slf4j.LoggerFactory.getLogger("RTMU-AutoDrive").info(
                            "[自動運転診断] 停車 駅=({},{},{}) 車体中心→駅={} 先端補正={} 誤差={} "
                            + "編成={}両 先頭車={} モデル={}",
                            station.getX(), station.getY(), station.getZ(),
                            String.format("%.2f", raw),
                            String.format("%.2f", noseOffset(train)),
                            String.format("%.2f", raw - noseOffset(train)),
                            train.getFormation() == null ? 1 : train.getFormation().size(),
                            train.getFormation() != null && train.getFormation().isFrontCar(train),
                            train.getModelName());
                        org.slf4j.LoggerFactory.getLogger("RTMU-AutoDrive").info(
                            "[自動運転診断] 方向幕={} (指定={})",
                            train.getTrainStateData(TrainState.TrainStateType.State_Destination.id), rollsign);
                        //到着。駅ごとの設定 (両方/左/右) でドアを開けて待つ
                        train.setTrainStateData(TrainState.TrainStateType.State_Door.id,
                                doorValue(stops, station));
                        if (this.atTerminal) {
                            this.dwellLeft = TERMINAL_DESPAWN_TICKS;
                            this.phase = Phase.TERMINAL;
                        } else {
                            this.dwellLeft = dwellTicks;
                            this.phase = Phase.DWELL;
                        }
                    }
                }
            }
            default -> {
                BlockPos station = nextStation(stops, train, heading);
                if (station != null) {
                    double d = distanceAlong(train, station, heading) - noseOffset(train);
                    if (d > 0.0D && d <= brakingDistance(speed) + 4.0D) {
                        this.servedStation = station;
                        this.atTerminal = !stops.isEmpty()
                                && stops.get(stops.size() - 1).pos().equals(station);
                        //★終点なら停まる前に印を付ける。乗客 NPC はこれを見て
                        //  「降りるだけで乗ってこない」ようになる。
                        setTerminatingFlag(train, this.atTerminal);
                        this.phase = Phase.APPROACH;
                        break;
                    }
                }
                accelerateTo(train, targetSpeed(train), speed);
            }
        }
    }

    /**
     * 出せる速度。信号があればその現示の上限に従う (本家 SignalLevel と同じ表)。
     * 信号が無い線区では State_Signal が 0 のままで、本家の表では「停止」になってしまうので、
     * その場合だけ mod 側の最高速度を使う。
     */
    private static float targetSpeed(EntityTrainBase train) {
        int signal = train.getSignal();
        if (signal <= 0) {
            return MAX_SPEED; //信号が設置されていない線区
        }
        float limit = SignalLevel.getSignal(signal).speedUpperLimit;
        return Math.min(MAX_SPEED, limit);
    }

    /**
     * 目標速度へ寄せるノッチを選ぶ。
     *
     * <p>本家の運転士は「目標より速い→即 B4 / 遅い→即フルノッチ」なので、
     * 目標付近でノッチが跳ね回って<b>ガクガクした運転</b>になる。
     * ここでは目標との差でノッチを段階的に決め、目標のすぐ手前では惰行に入れて、
     * 実車のように滑らかに近付ける。
     */
    private static void accelerateTo(EntityTrainBase train, float target, float speed) {
        if (target <= 0.0F) {
            train.setNotch(EnumNotch.brake_3.id);
            return;
        }
        float gap = target - speed;
        int notch;
        if (gap > 0.30F) {
            notch = EnumNotch.accelerate_5.id;     //まだ遠い: 力行
        } else if (gap > 0.15F) {
            notch = EnumNotch.accelerate_4.id;
        } else if (gap > 0.07F) {
            notch = EnumNotch.accelerate_3.id;
        } else if (gap > 0.03F) {
            notch = EnumNotch.accelerate_2.id;     //目標が近い: 弱める
        } else if (gap > 0.005F) {
            notch = EnumNotch.accelerate_1.id;
        } else if (gap > -0.03F) {
            notch = EnumNotch.inertia.id;          //ほぼ目標: 惰行 (ここが広いほど滑らか)
        } else if (gap > -0.10F) {
            notch = EnumNotch.brake_1.id;          //少し出過ぎ: 軽く緩める
        } else {
            notch = EnumNotch.brake_3.id;
        }
        train.setNotch(notch);
    }

    /**
     * 減速中のノッチ。<b>「停止位置までの残り距離から出した目標速度」に速度を合わせる</b>方式。
     *
     * <p>EnumNotch の減速度をそのまま信じて 1 回で決めると、車両ごとに実際のブレーキ力
     * (JSON の deccelerations) が違うので、手前で止まったり行き過ぎたりする。
     * 目標速度カーブに毎回合わせに行けば、車両が何であっても停止位置に収まる。
     */
    private static EnumNotch approachNotch(float speed, double distance) {
        //残り距離で出せる速度 (この速度なら APPROACH_DECEL で停止位置に収まる)
        double margin = Math.max(distance - STOP_TOLERANCE, 0.0D);
        float target = (float) Math.sqrt(2.0D * APPROACH_DECEL * margin);
        float gap = speed - target;
        if (gap <= -0.05F && distance > 8.0D) {
            return EnumNotch.accelerate_1;   //カーブより遅い: 少し足して詰める
        }
        if (gap <= 0.0F) {
            return EnumNotch.inertia;        //カーブ通り: 惰行
        }
        if (gap < 0.05F) {
            return EnumNotch.brake_2;
        }
        if (gap < 0.12F) {
            return EnumNotch.brake_4;
        }
        if (gap < 0.25F) {
            return EnumNotch.brake_6;
        }
        return EnumNotch.brake_7;
    }

    /**
     * 減速を始める距離。<b>常用ブレーキ (B4) で止まれる距離</b>に少しの余裕を足す。
     *
     * <p>弱いブレーキ (B2) 基準で早くから当てると、ブレーキを緩めた惰性でズルズル進む
     * 「本物っぽくない」停まり方になる。実車は<b>ブレーキを当てたら緩めずに一定の減速度で
     * 停止位置まで詰める</b>ので、常用ブレーキで止まれる距離まで走ってから当てる。
     */
    private static double brakingDistance(float speed) {
        return (speed * speed) / (2.0D * APPROACH_DECEL) + STOP_TOLERANCE;
    }

    /**
     * 車体の中心から先端までの距離。
     * RTM の {@code trainDistance} は「車体中心から端まで」なのでそのまま使える。
     */
    private static double noseOffset(EntityTrainBase train) {
        try {
            float d = train.getConfig().trainDistance;
            return d > 0.0F ? d : 5.0D;
        } catch (Throwable ignored) {
            return 5.0D;
        }
    }

    /** 編成の全車両に終点フラグを立てる/下ろす。 */
    private static void setTerminatingFlag(EntityTrainBase train, boolean value) {
        jp.ngt.rtm.entity.train.util.Formation formation = train.getFormation();
        if (formation != null) {
            formation.getTrainStream().filter(java.util.Objects::nonNull)
                    .forEach(car -> car.setTerminating(value));
        }
        train.setTerminating(value);
    }

    /** 終点に着いた編成を消す。 */
    private static void despawnFormation(ServerLevel level, EntityTrainBase train) {
        java.util.List<EntityTrainBase> cars = new java.util.ArrayList<>();
        jp.ngt.rtm.entity.train.util.Formation formation = train.getFormation();
        if (formation != null) {
            formation.getTrainStream().filter(java.util.Objects::nonNull).forEach(cars::add);
        }
        if (!cars.contains(train)) {
            cars.add(train);
        }
        //運転士を先に降ろす (列車と一緒に消える)
        release(level, train);
        //先に集めてから消す (消しながら回すと編成の中身がずれる)
        for (EntityTrainBase car : cars) {
            if (!car.isRemoved()) {
                car.kill();
            }
        }
    }

    /**
     * その駅で開けるドア。RTM の State_Door は bit0=右 / bit1=左 なので、
     * 両側=3 / 左=2 / 右=1 を入れる。設定が無ければ両側。
     */
    private static byte doorValue(java.util.List<AutoDriveState.RouteStop> stops, BlockPos station) {
        for (AutoDriveState.RouteStop stop : stops) {
            if (stop.pos().equals(station)) {
                return switch (stop.door()) {
                    case 1 -> (byte) 2;
                    case 2 -> (byte) 1;
                    default -> (byte) 3;
                };
            }
        }
        return (byte) 3;
    }

    /**
     * 進行方向の単位ベクトル (x, z)。
     * RTM の yaw は +Z が 0 で時計回りなので前方は (-sin, cos)。後退中は反転する。
     */
    private static double[] heading(EntityTrainBase train) {
        double rad = Math.toRadians(train.getYRot());
        double fx = -Math.sin(rad);
        double fz = Math.cos(rad);
        if (train.getSpeed() < 0.0F) {
            fx = -fx;
            fz = -fz;
        }
        return new double[]{fx, fz};
    }

    /** 進行方向に沿った距離 (前方が正、後方が負)。 */
    private static double distanceAlong(EntityTrainBase train, BlockPos pos, double[] heading) {
        double dx = (pos.getX() + 0.5D) - train.getX();
        double dz = (pos.getZ() + 0.5D) - train.getZ();
        return dx * heading[0] + dz * heading[1];
    }

    /**
     * 進行方向の先にある、いちばん近い停車駅。無ければ null。
     * 候補は<b>スポナーで「停車」に設定された駅列車ブロックだけ</b>。通過駅はここに入らない。
     */
    private static BlockPos nextStation(java.util.List<AutoDriveState.RouteStop> stops,
                                        EntityTrainBase train, double[] heading) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (AutoDriveState.RouteStop stop : stops) {
            BlockPos pos = stop.pos();
            if (Math.abs(pos.getY() - train.getY()) > 8.0D) {
                continue; //高さが違う = 別の階層の路線
            }
            double along = distanceAlong(train, pos, heading);
            if (along <= 0.0D || along > STATION_SEARCH) {
                continue; //後方または遠すぎ
            }
            //線路から大きく外れた駅は対象外 (横方向のずれ)
            double dx = (pos.getX() + 0.5D) - train.getX();
            double dz = (pos.getZ() + 0.5D) - train.getZ();
            double lateral = Math.abs(dx * heading[1] - dz * heading[0]);
            if (lateral > 12.0D) {
                continue;
            }
            if (along < bestDistance) {
                bestDistance = along;
                best = pos;
            }
        }
        return best;
    }
}
