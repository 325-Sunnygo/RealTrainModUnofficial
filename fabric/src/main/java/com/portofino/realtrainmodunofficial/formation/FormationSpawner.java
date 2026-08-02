package com.portofino.realtrainmodunofficial.formation;

import jp.ngt.rtm.entity.train.EntityBogie;
import jp.ngt.rtm.entity.train.EntityTrain;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.rail.TileEntityLargeRailBase;
import jp.ngt.rtm.rail.util.RailMap;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * 編成アイテムを線路へ右クリックしたときに、編成をまとめて設置する。
 *
 * <p>1 両ずつバラバラに置くのではなく、<b>レールに沿って弧長で間隔を取ってから</b>置き、
 * 隣どうしを連結する。だから同じ場所に重なって出たり、間が空いたりしない。
 *
 * <p>間隔は車両の {@code trainConfig.trainDistance} (車体中心から端までの長さ)。
 * 車 i と車 i+1 の中心間距離 = {@code trainDistance(i) + trainDistance(i+1)}。
 * これは本家の連結時の距離と同じで、連結器のぶんの隙間は入れない。
 */
public final class FormationSpawner {

    /** レール上を歩くときの 1 レールあたりの分割数。細かいほど弧長が正確になる。 */
    private static final int WALK_SPLIT = 512;
    /** 端を越えて次のレールを探すときに、どれだけ先を見るか (ブロック)。 */
    private static final double RAIL_LOOKAHEAD = 0.6D;
    /** 1 編成の最大両数 (暴走よけ)。 */
    private static final int MAX_CARS = 30;

    private FormationSpawner() {
    }

    /** 設置に失敗した理由。呼び出し側がそのままプレイヤーへ出す。 */
    public enum Result {
        OK,
        /** クリックした所にレールが無い。 */
        NO_RAIL,
        /** 編成が空。 */
        EMPTY,
        /** 編成のぶんだけレールが続いていない。 */
        NOT_ENOUGH_RAIL,
        /** 置こうとした所に既に車両が居る。 */
        OCCUPIED
    }

    /** 1 両ぶんの設置先。 */
    private record Placement(double x, double y, double z, float yaw, float pitch) {
    }

    /**
     * 編成を設置する。サーバー側でのみ呼ぶこと。
     *
     * @param clicked   右クリックしたブロック (レール)
     * @param playerYaw 設置者の向き。編成の向き (どちらが先頭か) を決める
     */
    public static Result spawn(Level level, BlockPos clicked, float playerYaw, List<String> vehicleIds) {
        if (vehicleIds == null || vehicleIds.isEmpty()) {
            return Result.EMPTY;
        }
        RailMap map = TileEntityLargeRailBase.getRailMapFromCoordinates(
                level, null, clicked.getX(), clicked.getY(), clicked.getZ());
        if (map == null) {
            return Result.NO_RAIL;
        }

        List<String> ids = vehicleIds.size() > MAX_CARS ? vehicleIds.subList(0, MAX_CARS) : vehicleIds;

        // 先頭車の位置と向きを決める (本家 ItemTrain と同じ決め方)。
        int index = map.getNearlestPoint(WALK_SPLIT, clicked.getX() + 0.5D, clicked.getZ() + 0.5D);
        float railYaw = Mth.wrapDegrees(map.getRailYaw(WALK_SPLIT, index));
        float yaw = EntityBogie.fixBogieYaw(Mth.wrapDegrees(-playerYaw), railYaw);

        // 添字が増える向きが「前」か「後ろ」か。編成は先頭車の後ろへ伸ばす。
        int backStep = indexStepTowardRear(map, index, yaw);

        // まず全両ぶんの位置を出す。1 両でも置けなければ何も置かない
        // (途中まで置いて中途半端な編成にしない)。
        List<Placement> placements = new ArrayList<>(ids.size());
        Walker walker = new Walker(level, map, index, backStep);
        double previousHalf = 0.0D;
        for (int i = 0; i < ids.size(); i++) {
            double half = halfLength(ids.get(i));
            if (i > 0) {
                // 1 つ前の車体の後端 + 今の車体の前端 = 中心間距離
                if (!walker.advance(previousHalf + half)) {
                    return Result.NOT_ENOUGH_RAIL;
                }
            }
            previousHalf = half;
            placements.add(walker.placement(yaw));
        }

        // 既に車両が居る所には置かない
        for (Placement p : placements) {
            if (isOccupied(level, p)) {
                return Result.OCCUPIED;
            }
        }

        List<EntityTrainBase> spawned = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            Placement p = placements.get(i);
            EntityTrain train = new EntityTrain(jp.ngt.rtm.entity.RTMEntities.TRAIN.get(), level);
            train.moveTo(p.x(), p.y(), p.z(), p.yaw(), p.pitch());
            train.setModelName(ids.get(i));
            train.spawnTrain(level);
            train.onModelChanged();
            spawned.add(train);
        }

        // 隣どうしを連結する。台車は「相手に近い方」で選ぶので、
        // 台車 0/1 のどちらが前かという車両ごとの約束に左右されない。
        for (int i = 0; i + 1 < spawned.size(); i++) {
            connect(spawned.get(i), spawned.get(i + 1));
        }
        return Result.OK;
    }

    /** 車体中心から端までの長さ。取れなければ 1 両 ≒ 10m の半分で凌ぐ。 */
    private static double halfLength(String vehicleId) {
        try {
            float d = jp.ngt.rtm.modelpack.cfg.TrainConfigAdapter.get(vehicleId).trainDistance;
            if (d > 0.0F) {
                return d;
            }
        } catch (Throwable ignored) {
            //パック未読込など。既定で置く
        }
        return 5.0D;
    }

    /** 添字をどちら向きに進めると編成の後方 (yaw の逆向き) になるか。 */
    private static int indexStepTowardRear(RailMap map, int index, float yaw) {
        int a = Math.max(0, Math.min(WALK_SPLIT - 1, index));
        double[] p0 = map.getRailPos(WALK_SPLIT, a);
        double[] p1 = map.getRailPos(WALK_SPLIT, a + 1);
        // 添字が増える向きのベクトル
        double dx = p1[1] - p0[1];
        double dz = p1[0] - p0[0];
        // 車体前方のベクトル (RTM の yaw は Z+ を 0 として時計回り)
        double fx = -Math.sin(Math.toRadians(yaw));
        double fz = Math.cos(Math.toRadians(yaw));
        return (dx * fx + dz * fz) >= 0.0D ? -1 : 1;
    }

    /**
     * レール上を弧長で歩く。端まで来たら、その先にあるレールへ乗り移る
     * (台車の乗り移りと同じで、少し先の座標でレールを引き直す)。
     */
    private static final class Walker {
        private final Level level;
        private RailMap map;
        private int index;
        private int step;

        Walker(Level level, RailMap map, int index, int step) {
            this.level = level;
            this.map = map;
            this.index = Math.max(0, Math.min(WALK_SPLIT, index));
            this.step = step;
        }

        /** 弧長で distance ぶん進む。進みきれなければ false。 */
        boolean advance(double distance) {
            double remaining = distance;
            int guard = WALK_SPLIT * 64;   //無限ループよけ
            while (remaining > 1.0E-4D && guard-- > 0) {
                int next = this.index + this.step;
                if (next < 0 || next > WALK_SPLIT) {
                    if (!this.hop()) {
                        return false;
                    }
                    continue;
                }
                double[] a = this.map.getRailPos(WALK_SPLIT, this.index);
                double[] b = this.map.getRailPos(WALK_SPLIT, next);
                double ay = this.map.getRailHeight(WALK_SPLIT, this.index);
                double by = this.map.getRailHeight(WALK_SPLIT, next);
                double dx = b[1] - a[1];
                double dy = by - ay;
                double dz = b[0] - a[0];
                double seg = Math.sqrt(dx * dx + dy * dy + dz * dz);
                this.index = next;
                remaining -= seg;
            }
            return remaining <= 1.0E-4D;
        }

        /** 端まで来た。その先のレールへ乗り移る。 */
        private boolean hop() {
            double[] p = this.map.getRailPos(WALK_SPLIT, this.index);
            double y = this.map.getRailHeight(WALK_SPLIT, this.index);
            // 進行方向へ少しはみ出した所で次のレールを引く
            int inner = this.index == 0 ? 1 : WALK_SPLIT - 1;
            double[] q = this.map.getRailPos(WALK_SPLIT, inner);
            double dx = p[1] - q[1];
            double dz = p[0] - q[0];
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 1.0E-6D) {
                return false;
            }
            double nx = p[1] + dx / len * RAIL_LOOKAHEAD;
            double nz = p[0] + dz / len * RAIL_LOOKAHEAD;

            RailMap next = TileEntityLargeRailBase.getRailMapFromCoordinates(
                    this.level, null, nx, y + 0.5D, nz);
            if (next == null || next == this.map) {
                return false;
            }
            int ni = next.getNearlestPoint(WALK_SPLIT, nx, nz);
            // 乗り移った先でも「元のレールから遠ざかる向き」へ進む
            this.step = stepAwayFrom(next, ni, p[1], p[0]);
            this.map = next;
            this.index = Math.max(0, Math.min(WALK_SPLIT, ni));
            return true;
        }

        /** (fromX, fromZ) から遠ざかる添字方向。 */
        private static int stepAwayFrom(RailMap map, int index, double fromX, double fromZ) {
            int a = Math.max(0, Math.min(WALK_SPLIT - 1, index));
            double[] p0 = map.getRailPos(WALK_SPLIT, a);
            double[] p1 = map.getRailPos(WALK_SPLIT, a + 1);
            double d0 = sq(p0[1] - fromX) + sq(p0[0] - fromZ);
            double d1 = sq(p1[1] - fromX) + sq(p1[0] - fromZ);
            return d1 >= d0 ? 1 : -1;
        }

        private static double sq(double v) {
            return v * v;
        }

        Placement placement(float yaw) {
            double[] p = this.map.getRailPos(WALK_SPLIT, this.index);
            double y = this.map.getRailHeight(WALK_SPLIT, this.index) + EntityTrainBase.TRAIN_HEIGHT;
            float railYaw = Mth.wrapDegrees(this.map.getRailYaw(WALK_SPLIT, this.index));
            // その地点のレール向きに合わせつつ、編成の向き (yaw) と同じ側を向かせる
            float carYaw = EntityBogie.fixBogieYaw(yaw, railYaw);
            float pitch = EntityBogie.fixBogiePitch(
                    this.map.getRailPitch(WALK_SPLIT, this.index), railYaw, carYaw);
            return new Placement(p[1], y, p[0], carYaw, pitch);
        }
    }

    /** そこに既に車両が居るか (本家 ItemTrain と同じく車体長で見る)。 */
    private static boolean isOccupied(Level level, Placement p) {
        double r = 3.0D;
        var box = new net.minecraft.world.phys.AABB(
                p.x() - r, p.y() - 4.0D, p.z() - r, p.x() + r, p.y() + 4.0D, p.z() + r);
        for (EntityTrainBase other : level.getEntitiesOfClass(EntityTrainBase.class, box)) {
            if (other.isAlive() && !other.isRemoved()) {
                return true;
            }
        }
        return false;
    }

    /** 2 両を連結する。互いに一番近い台車どうしをつなぐ。 */
    private static void connect(EntityTrainBase front, EntityTrainBase rear) {
        EntityBogie fb = nearestBogie(front, rear);
        EntityBogie rb = nearestBogie(rear, front);
        if (fb != null && rb != null) {
            front.connectTrain(fb, rb);
        }
    }

    /** train の台車のうち、target に近い方。 */
    private static EntityBogie nearestBogie(EntityTrainBase train, EntityTrainBase target) {
        EntityBogie best = null;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < 2; i++) {
            EntityBogie bogie = train.getBogie(i);
            if (bogie == null) {
                continue;
            }
            double d = bogie.distanceToSqr(target.getX(), target.getY(), target.getZ());
            if (d < bestDist) {
                bestDist = d;
                best = bogie;
            }
        }
        return best;
    }
}
