package com.portofino.realtrainmodunofficial.entity;

import com.portofino.realtrainmodunofficial.blockentity.LargeRailCoreBlockEntity;
import com.portofino.realtrainmodunofficial.blockentity.RailCollisionBlockEntity;
import jp.ngt.rtm.rail.util.RailMap;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * 1台車のレール追従状態。分割数は整数だが位置インデックスは小数で保持し、
 * 補間によってなめらかな座標・角度を計算する。
 *
 * <p>RTM yaw 規約: {@code getRailYaw()} は RTM 角度（東=+90°）を返す。
 * Minecraft yaw（東=-90°）に変換するには符号を反転すること。
 *
 * <p>{@code direction} は {@code index} の増加方向が列車前進方向かを示す。
 * +1 = index 増加が前進、-1 = index 減少が前進。
 */
public final class BogieTracker {

    public static final int SPLIT_PER_METER = 32;

    private static final double MAX_ENDPOINT_DIST_SQ = 0.49;  // 0.7m^2
    private static final float MAX_ENDPOINT_YAW_DIFF = 35.0F;
    private static final int MAX_WALK_GUARDS = 24;

    // --- 台車状態（公開: TrainEntity の連結コードが直接アクセスする） ---
    public LargeRailCoreBlockEntity core;
    public RailMap map;
    public int split;
    /** 小数インデックス [0, split]。補間に使用。 */
    public double index;
    /** +1: index増加が列車前進方向、-1: index減少が列車前進方向。 */
    public int direction = 1;
    public double worldX, worldY, worldZ;
    /** Minecraft yaw 規約（東=-90°）。 */
    public float yaw;
    public float pitch;
    public float roll;

    /** レール上に有効な位置を持つか。 */
    public boolean isValid() {
        return map != null && split > 0;
    }

    /**
     * 前台車を {@code meters} メートル前進させる。
     * レール区間の端に達したら隣接区間へ乗り換える。
     */
    public void advance(double meters, Level level) {
        if (!isValid()) return;
        walkInPlace(meters, level);
        sampleState();
    }

    /**
     * {@code source} 台車の位置から {@code offset} メートルだけレールを遡って自身を配置する。
     * 後台車の配置に使用（offset は負値）。
     */
    public void walkFrom(BogieTracker source, double offset, Level level) {
        if (!source.isValid()) return;
        this.core = source.core;
        this.map = source.map;
        this.split = source.split;
        this.index = source.index;
        this.direction = source.direction;
        walkInPlace(offset, level);
        sampleState();
        alignYawTo(source.yaw);
    }

    /**
     * 指定ワールド座標の近傍レールを探して位置を初期化する。
     *
     * @return スナップに成功すれば true
     */
    public boolean initAt(double x, double y, double z, float initYaw, Level level) {
        LargeRailCoreBlockEntity c = findCore(level, x, y, z);
        if (c == null) c = this.core;
        if (c == null) return false;
        this.core = c;
        RailMap m = bestMap(c, x, z);
        if (m == null) m = this.map;
        if (m == null) return false;
        this.map = m;
        this.split = splitForMap(m);
        this.index = Mth.clamp(m.getNearlestPoint(this.split, x, z), 0, this.split);
        // 初期方向を initYaw から推定
        float rawYaw = -m.getRailYaw(this.split, (int) this.index);
        float diff = Mth.wrapDegrees(initYaw - rawYaw);
        this.direction = (Math.abs(diff) <= 90.0F) ? 1 : -1;
        sampleState();
        return true;
    }

    /** 全フィールドをリセット（レールから降りたとき用）。 */
    public void reset() {
        core = null;
        map = null;
        split = 0;
        index = 0.0;
        direction = 1;
        worldX = 0;
        worldY = 0;
        worldZ = 0;
        yaw = 0;
        pitch = 0;
        roll = 0;
    }

    // -------------------------------------------------------------------------
    // 内部ロジック
    // -------------------------------------------------------------------------

    /**
     * {@code meters} メートル分だけ現在の map/index を前進させる。
     * meters > 0 で前進（direction 方向）、< 0 で後退。
     */
    private void walkInPlace(double meters, Level level) {
        int dir = meters >= 0 ? this.direction : -this.direction;
        double remaining = Math.abs(meters);

        for (int guard = 0; guard < MAX_WALK_GUARDS && remaining > 1.0e-7; guard++) {
            if (map == null || split <= 0) break;
            double len = map.getLength();
            if (len <= 0.0) break;
            double step = len / split;   // meters per index unit

            double indexToBoundary = dir > 0 ? (split - index) : index;
            double metersToBoundary = indexToBoundary * step;

            if (remaining <= metersToBoundary + 1.0e-9) {
                index = Mth.clamp(index + dir * (remaining / step), 0.0, (double) split);
                remaining = 0.0;
                break;
            }

            // 端点に到達
            remaining -= metersToBoundary;
            int endpointIdx = dir > 0 ? split : 0;
            index = endpointIdx;

            // 端点での接線 yaw を求める
            float epTangent = -map.getRailYaw(split, endpointIdx);
            float outgoingYaw = dir > 0 ? epTangent : Mth.wrapDegrees(epTangent + 180.0F);

            RailTransition next = findConnectedRail(level, map, split, endpointIdx, outgoingYaw);
            if (next == null) break;   // 接続なし → 端点で停止

            this.core = next.core;
            this.map = next.map;
            this.split = next.split;
            this.index = next.startIndex;
            // 新区間での前進方向を更新
            dir = next.entryDir;
            this.direction = dir;
        }
    }

    /** 小数 index を補間して worldX/Y/Z と yaw/pitch/roll を更新する。 */
    private void sampleState() {
        if (map == null || split <= 0) return;
        double clamped = Mth.clamp(index, 0.0, (double) split);
        int low = Math.max(0, (int) Math.floor(clamped));
        int high = Math.min(split, low + 1);
        double t = clamped - low;

        double[] posLow = map.getRailPos(split, low);
        double[] posHigh = (high <= split) ? map.getRailPos(split, high) : posLow;
        double yLow = map.getRailHeight(split, low);
        double yHigh = (high <= split) ? map.getRailHeight(split, high) : yLow;

        this.worldX = lerp(t, posLow[1], posHigh[1]);
        this.worldZ = lerp(t, posLow[0], posHigh[0]);
        this.worldY = lerp(t, yLow, yHigh);

        float rawYawLow = -map.getRailYaw(split, low);
        float rawYaw;
        if (t > 1.0e-6 && high < split) {
            rawYaw = lerpYaw((float) t, rawYawLow, -map.getRailYaw(split, high));
        } else {
            rawYaw = rawYawLow;
        }

        this.yaw = (direction > 0) ? rawYaw : Mth.wrapDegrees(rawYaw + 180.0F);
        this.pitch = snapPitch(map.getRailPitch(split, low), rawYaw, this.yaw);
        this.roll = map.getCant(split, low);
    }

    /**
     * 後台車用: source の yaw を参照して自身の yaw 方向を決定し直す。
     * 後台車の yaw は「列車前進方向の逆」になる。
     */
    private void alignYawTo(float referenceYaw) {
        if (map == null || split <= 0) return;
        int idx = Math.max(0, Math.min(split, (int) Math.round(this.index)));
        float rawYaw = -map.getRailYaw(split, idx);
        // 後台車: referenceYaw（前台車の yaw）の反対方向を向く
        float oppositeRef = Mth.wrapDegrees(referenceYaw + 180.0F);
        this.yaw = snapYaw(oppositeRef, rawYaw);
        this.pitch = snapPitch(map.getRailPitch(split, idx), rawYaw, this.yaw);
    }

    // -------------------------------------------------------------------------
    // 隣接レール探索
    // -------------------------------------------------------------------------

    private record RailTransition(LargeRailCoreBlockEntity core, RailMap map, int split,
                                   double startIndex, int entryDir) {}

    private static RailTransition findConnectedRail(Level level, RailMap currentMap,
                                                     int currentSplit, int endpointIdx,
                                                     float outgoingYaw) {
        double[] epPos = currentMap.getRailPos(currentSplit, endpointIdx);
        double epX = epPos[1];
        double epZ = epPos[0];
        double epY = currentMap.getRailHeight(currentSplit, endpointIdx);

        int px = (int) Math.floor(epX);
        int py = (int) Math.floor(epY);
        int pz = (int) Math.floor(epZ);

        RailTransition best = null;
        double bestScore = Double.MAX_VALUE;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos bp = new BlockPos(px + dx, py + dy, pz + dz);
                    LargeRailCoreBlockEntity cand = findCoreDirect(level, bp);
                    if (cand == null) continue;
                    for (RailMap m : cand.getActiveRailMaps()) {
                        if (m == null || m == currentMap) continue;
                        int sp = splitForMap(m);
                        for (int ep : new int[]{0, sp}) {
                            double[] candPos = m.getRailPos(sp, ep);
                            double ddx = candPos[1] - epX;
                            double ddz = candPos[0] - epZ;
                            double distSq = ddx * ddx + ddz * ddz;
                            if (distSq > MAX_ENDPOINT_DIST_SQ) continue;

                            float railTangent = -m.getRailYaw(sp, ep);
                            int entryDir = (ep == 0) ? 1 : -1;
                            float incomingYaw = (ep == 0)
                                    ? railTangent
                                    : Mth.wrapDegrees(railTangent + 180.0F);
                            float yawDiff = Math.abs(Mth.wrapDegrees(outgoingYaw - incomingYaw));
                            if (yawDiff > MAX_ENDPOINT_YAW_DIFF) continue;

                            if (distSq < bestScore) {
                                bestScore = distSq;
                                best = new RailTransition(cand, m, sp, (double) ep, entryDir);
                            }
                        }
                    }
                }
            }
        }
        return best;
    }

    // -------------------------------------------------------------------------
    // 静的ユーティリティ（TrainEntity の連結コードからも使用）
    // -------------------------------------------------------------------------

    /** マップの分割数を計算する（RTM 本家と同一ロジック）。 */
    public static int splitForMap(RailMap map) {
        if (map == null) return 0;
        return Math.max(2, (int) (map.getHorizontalPathLength() * SPLIT_PER_METER));
    }

    /**
     * ブロック位置の BE を直接取得する（RailCollision 経由含む）。
     */
    public static LargeRailCoreBlockEntity findCoreDirect(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof LargeRailCoreBlockEntity core && core.isLoaded()) {
            return core;
        }
        if (level.getBlockEntity(pos) instanceof RailCollisionBlockEntity col) {
            BlockPos cp = col.getCorePos();
            if (cp != null
                    && level.getBlockEntity(cp) instanceof LargeRailCoreBlockEntity c
                    && c.isLoaded()) {
                return c;
            }
        }
        return null;
    }

    /**
     * ワールド座標近傍のレールコアを探す。
     * まず真上~下の列を検索し、見つからなければ XZ ±1 ブロックも検索する。
     */
    public static LargeRailCoreBlockEntity findCore(Level level, double x, double y, double z) {
        int px = (int) Math.floor(x);
        int py = (int) Math.floor(y);
        int pz = (int) Math.floor(z);
        // 中心列を先に検索
        for (int dy = 2; dy >= -2; dy--) {
            LargeRailCoreBlockEntity c = findCoreDirect(level, new BlockPos(px, py + dy, pz));
            if (c != null) return c;
        }
        // 隣接 XZ も検索
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                for (int dy = 2; dy >= -2; dy--) {
                    LargeRailCoreBlockEntity c = findCoreDirect(level, new BlockPos(px + dx, py + dy, pz + dz));
                    if (c != null) return c;
                }
            }
        }
        return null;
    }

    /** コアが持つ全マップの中で (x, z) に最も近いものを返す。 */
    public static RailMap bestMap(LargeRailCoreBlockEntity core, double x, double z) {
        if (core == null) return null;
        RailMap best = null;
        double bestDist = Double.MAX_VALUE;
        for (RailMap m : core.getActiveRailMaps()) {
            if (m == null) continue;
            int sp = splitForMap(m);
            int nearest = Mth.clamp(m.getNearlestPoint(sp, x, z), 0, sp);
            double[] p = m.getRailPos(sp, nearest);
            double dx = p[1] - x, dz = p[0] - z;
            double dist = dx * dx + dz * dz;
            if (dist < bestDist) {
                bestDist = dist;
                best = m;
            }
        }
        return best;
    }

    /**
     * 移動方向 {@code forwardYaw} に近い方向の {@code railYaw}（またはその逆）を返す。
     */
    public static float snapYaw(float forwardYaw, float railYaw) {
        float diff = Mth.wrapDegrees(forwardYaw - railYaw);
        if (diff > 90.0F) return Mth.wrapDegrees(railYaw + 180.0F);
        if (diff < -90.0F) return Mth.wrapDegrees(railYaw - 180.0F);
        return railYaw;
    }

    /** レール勾配を台車の向きに合わせて符号を修正する。 */
    public static float snapPitch(float railPitch, float railYaw, float bogieYaw) {
        float diff = Math.abs(Mth.wrapDegrees(bogieYaw - railYaw));
        return diff > 45.0F ? -railPitch : railPitch;
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static float lerpYaw(float t, float a, float b) {
        float diff = Mth.wrapDegrees(b - a);
        return Mth.wrapDegrees(a + t * diff);
    }
}
