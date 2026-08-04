package com.portofino.rtmuautodrive;

import jp.ngt.rtm.rail.TileEntityLargeRailBase;
import jp.ngt.rtm.rail.util.RailMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * レールを実際に辿って、スポナーの先にある駅列車ブロックを順番に拾う。
 *
 * <p>★<b>線路が繋がっていない駅は出さない</b> (ユーザー指定)。
 * 直線距離ではなくレールの上を歩いて探すので、線路が切れていたり 1 ブロックでもずれて
 * 繋がっていなければ、その先の駅は候補に出ない。乗り移りの判定は本家の台車と同じ
 * (少し先の座標でレールを引き直す) やり方にしてある。
 */
public final class RailRoute {

    /** レールの分割数。本家/編成設置と同じ。 */
    private static final int SPLIT = 512;
    /** 次のレールを探すときに進行方向へはみ出す距離。 */
    private static final double LOOKAHEAD = 0.6D;
    /** 何ブロック先まで辿るか。 */
    private static final double MAX_DISTANCE = 2048.0D;
    /** 駅列車ブロックを線路から何ブロックまで拾うか (設置条件と同じ)。 */
    private static final double STATION_RADIUS = 5.0D;

    private RailRoute() {
    }

    /** 経路上の駅 1 つ分。 */
    public record Stop(BlockPos pos, String name, double distance) {
    }

    /**
     * スポナーの向きへレールを辿り、見つかった駅列車ブロックを近い順に返す。
     *
     * @param railStart スポナーの近くにあるレール
     * @param yaw       発車する向き (スポナーを置いた人の向き)
     */
    public static List<Stop> scan(ServerLevel level, BlockPos railStart, float yaw) {
        List<Stop> stops = new ArrayList<>();
        RailMap map = TileEntityLargeRailBase.getRailMapFromCoordinates(
                level, null, railStart.getX(), railStart.getY(), railStart.getZ());
        if (map == null) {
            return stops;
        }
        int index = map.getNearlestPoint(SPLIT, railStart.getX() + 0.5D, railStart.getZ() + 0.5D);
        int step = stepTowardYaw(map, index, yaw);

        Set<BlockPos> seen = new LinkedHashSet<>();
        var stations = StationStopRegistry.get(level).all();
        double travelled = 0.0D;
        int guard = SPLIT * 512;

        while (travelled < MAX_DISTANCE && guard-- > 0) {
            double[] here = map.getRailPos(SPLIT, index);
            double y = map.getRailHeight(SPLIT, index);
            //この地点の近くにある駅を拾う
            for (var entry : stations.entrySet()) {
                BlockPos pos = entry.getKey();
                if (seen.contains(pos)) {
                    continue;
                }
                double dx = (pos.getX() + 0.5D) - here[1];
                double dy = pos.getY() - y;
                double dz = (pos.getZ() + 0.5D) - here[0];
                if (dx * dx + dz * dz <= STATION_RADIUS * STATION_RADIUS && Math.abs(dy) <= STATION_RADIUS) {
                    seen.add(pos);
                    String name = entry.getValue() == null || entry.getValue().isBlank()
                            ? pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
                            : entry.getValue();
                    stops.add(new Stop(pos, name, travelled));
                }
            }
            //1 区間進む
            int next = index + step;
            if (next < 0 || next > SPLIT) {
                RailMap hopped = hop(level, map, index, step);
                if (hopped == null) {
                    break; //線路がここで途切れている = この先の駅は候補にしない
                }
                double[] p = map.getRailPos(SPLIT, index);
                int ni = hopped.getNearlestPoint(SPLIT, p[1], p[0]);
                step = stepAwayFrom(hopped, ni, p[1], p[0]);
                map = hopped;
                index = Math.max(0, Math.min(SPLIT, ni));
                continue;
            }
            double[] a = map.getRailPos(SPLIT, index);
            double[] b = map.getRailPos(SPLIT, next);
            double ay = map.getRailHeight(SPLIT, index);
            double by = map.getRailHeight(SPLIT, next);
            double dx = b[1] - a[1];
            double dy = by - ay;
            double dz = b[0] - a[0];
            travelled += Math.sqrt(dx * dx + dy * dy + dz * dz);
            index = next;
        }
        return stops;
    }

    /** yaw の向きへ進む添字方向。 */
    private static int stepTowardYaw(RailMap map, int index, float yaw) {
        int a = Math.max(0, Math.min(SPLIT - 1, index));
        double[] p0 = map.getRailPos(SPLIT, a);
        double[] p1 = map.getRailPos(SPLIT, a + 1);
        double dx = p1[1] - p0[1];
        double dz = p1[0] - p0[0];
        double fx = -Math.sin(Math.toRadians(yaw));
        double fz = Math.cos(Math.toRadians(yaw));
        return (dx * fx + dz * fz) >= 0.0D ? 1 : -1;
    }

    /** 端まで来たので次のレールへ乗り移る。繋がっていなければ null。 */
    private static RailMap hop(Level level, RailMap map, int index, int step) {
        double[] p = map.getRailPos(SPLIT, index);
        double y = map.getRailHeight(SPLIT, index);
        int inner = index == 0 ? 1 : SPLIT - 1;
        double[] q = map.getRailPos(SPLIT, inner);
        double dx = p[1] - q[1];
        double dz = p[0] - q[0];
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-6D) {
            return null;
        }
        double nx = p[1] + dx / len * LOOKAHEAD;
        double nz = p[0] + dz / len * LOOKAHEAD;
        RailMap next = TileEntityLargeRailBase.getRailMapFromCoordinates(level, null, nx, y + 0.5D, nz);
        return (next == null || next == map) ? null : next;
    }

    /** (fromX, fromZ) から遠ざかる添字方向。 */
    private static int stepAwayFrom(RailMap map, int index, double fromX, double fromZ) {
        int a = Math.max(0, Math.min(SPLIT - 1, index));
        double[] p0 = map.getRailPos(SPLIT, a);
        double[] p1 = map.getRailPos(SPLIT, a + 1);
        double d0 = sq(p0[1] - fromX) + sq(p0[0] - fromZ);
        double d1 = sq(p1[1] - fromX) + sq(p1[0] - fromZ);
        return d1 >= d0 ? 1 : -1;
    }

    private static double sq(double v) {
        return v * v;
    }
}
