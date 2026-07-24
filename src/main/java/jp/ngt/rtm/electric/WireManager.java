package jp.ngt.rtm.electric;

import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 本家 ElectricalWiringManager/WireManager 相当 (簡略忠実版)。
 * ワイヤー (InstalledObject WIRE) の両端をエッジとして接続グラフを保持し、
 * 入力コネクタ/変換器からの信号を BFS で伝播する。
 */
public final class WireManager {
    private static final Map<Level, Map<BlockPos, Set<BlockPos>>> NETWORKS = new WeakHashMap<>();

    /** 本家 WireManager.INSTANCE。架線のジオメトリ (パンタが当たる高さ) を持つ。 */
    public static final WireManager INSTANCE = new WireManager();

    private WireManager() {
    }

    // ---- 架線ジオメトリ (本家 WireManager 相当) ----
    //
    // 信号伝播 (上の static 群) とは別物。こちらは「その座標の真上に架線があるか、
    // あるなら何 Y か」を答える。パンタグラフの上昇停止位置に使う。

    /** 直線の分割数 (本家 SPLIT)。 */
    private static final int SPLIT = 512;
    /** 高さ方向の許容差 (本家 Y_TANGE)。 */
    private static final double Y_TANGE = 2.0D;

    /** 架線 1 本分。XZ 平面の直線と、両端の Y。 */
    public static final class WireEntry {
        public final jp.ngt.ngtlib.math.ILine lineXZ;
        public final double minX, maxX, minY, maxY, minZ, maxZ;

        public WireEntry(jp.ngt.ngtlib.math.ILine lineXZ, double minY, double maxY) {
            this.lineXZ = lineXZ;
            this.minY = minY;
            this.maxY = maxY;
            double[] d1 = lineXZ.getPoint(SPLIT, 0);
            double[] d2 = lineXZ.getPoint(SPLIT, SPLIT);
            this.minX = Math.min(d1[1], d2[1]);
            this.maxX = Math.max(d1[1], d2[1]);
            this.minZ = Math.min(d1[0], d2[0]);
            this.maxZ = Math.max(d1[0], d2[0]);
        }

        /**
         * この架線の真下にいるか。車体の向き (yaw) で XZ の許容幅を変える
         * (斜めに走っていても架線を拾えるように)。
         */
        public boolean inRange(float yaw, double x, double y, double z) {
            float cos = Math.abs((float) Math.cos(Math.toRadians(yaw)));
            float sin = Math.abs((float) Math.sin(Math.toRadians(yaw)));
            return x >= this.minX - cos && x <= this.maxX + cos
                    && y >= this.minY - Y_TANGE && y <= this.maxY + Y_TANGE
                    && z >= this.minZ - sin && z <= this.maxZ + sin;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof WireEntry e
                    && this.minY == e.minY && this.maxY == e.maxY && this.lineXZ.equals(e.lineXZ);
        }

        @Override
        public int hashCode() {
            return this.lineXZ.hashCode();
        }
    }

    /** レベル → チャンク座標 (x<<32|z) → その チャンクに掛かる架線。 */
    private final Map<Level, Map<Long, java.util.List<WireEntry>>> loadedWires = new WeakHashMap<>();

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    /** 本家 clear: 全架線を忘れる。 */
    public synchronized void clear() {
        this.loadedWires.clear();
    }

    /** 本家 addWire: 両端座標から架線を登録する。 */
    public void addWire(Level level, BlockPos start, BlockPos end) {
        this.editWire(level, start, end, true);
    }

    /** 本家 removeWire。 */
    public void removeWire(Level level, BlockPos start, BlockPos end) {
        this.editWire(level, start, end, false);
    }

    private synchronized void editWire(Level level, BlockPos start, BlockPos end, boolean add) {
        if (level == null || start == null || end == null) {
            return;
        }
        double x1 = start.getX() + 0.5D;
        double y1 = start.getY() + 0.5D;
        double z1 = start.getZ() + 0.5D;
        double x2 = end.getX() + 0.5D;
        double y2 = end.getY() + 0.5D;
        double z2 = end.getZ() + 0.5D;
        //線は「低い方が始点」。getWireY が始点→終点の割合で高さを補間するため
        boolean startIsLower = y1 <= y2;
        double sx = startIsLower ? x1 : x2;
        double sz = startIsLower ? z1 : z2;
        double ex = startIsLower ? x2 : x1;
        double ez = startIsLower ? z2 : z1;
        WireEntry entry = new WireEntry(
                new jp.ngt.ngtlib.math.StraightLine(sz, sx, ez, ex),
                Math.min(y1, y2), Math.max(y1, y2));

        Map<Long, java.util.List<WireEntry>> byChunk =
                this.loadedWires.computeIfAbsent(level, l -> new HashMap<>());
        int cx1 = Mth.floor(Math.min(x1, x2)) >> 4;
        int cx2 = Mth.floor(Math.max(x1, x2)) >> 4;
        int cz1 = Mth.floor(Math.min(z1, z2)) >> 4;
        int cz2 = Mth.floor(Math.max(z1, z2)) >> 4;
        for (int i = cx1; i <= cx2; ++i) {
            for (int j = cz1; j <= cz2; ++j) {
                java.util.List<WireEntry> list =
                        byChunk.computeIfAbsent(chunkKey(i, j), k -> new java.util.ArrayList<>());
                if (add) {
                    if (!list.contains(entry)) {
                        list.add(entry);
                    }
                } else {
                    list.remove(entry);
                }
            }
        }
    }

    /**
     * 本家 getWireY: (x,y,z) の上にある架線の Y。無ければ y をそのまま返す。
     * 複数掛かっていれば一番低いものを採る (パンタは最初に当たる線で止まる)。
     */
    public synchronized double getWireY(Level level, float yaw, double x, double y, double z) {
        Map<Long, java.util.List<WireEntry>> byChunk = this.loadedWires.get(level);
        if (byChunk == null) {
            return y;
        }
        java.util.List<WireEntry> list = byChunk.get(chunkKey(Mth.floor(x) >> 4, Mth.floor(z) >> 4));
        if (list == null || list.isEmpty()) {
            return y;
        }
        double best = y;
        boolean found = false;
        for (WireEntry entry : list) {
            if (!entry.inRange(yaw, x, y, z)) {
                continue;
            }
            int index = entry.lineXZ.getNearlestPoint(SPLIT, x, z);
            double wy = entry.minY + (entry.maxY - entry.minY) * ((double) index / (double) SPLIT)
                    + jp.ngt.rtm.entity.train.EntityTrainBase.TRAIN_HEIGHT;
            if (!found || wy < best) {
                best = wy;
                found = true;
            }
        }
        return found ? best : y;
    }

    /** 本家と同じ 4 引数版 (レベル不明時は登録済み全レベルから探す)。 */
    public synchronized double getWireY(float yaw, double x, double y, double z) {
        for (Level level : this.loadedWires.keySet()) {
            double wy = this.getWireY(level, yaw, x, y, z);
            if (wy != y) {
                return wy;
            }
        }
        return y;
    }

    public static synchronized void register(Level level, BlockPos a, BlockPos b) {
        if (level == null || a == null || b == null) {
            return;
        }
        Map<BlockPos, Set<BlockPos>> adj = NETWORKS.computeIfAbsent(level, l -> new HashMap<>());
        adj.computeIfAbsent(a.immutable(), p -> new HashSet<>()).add(b.immutable());
        adj.computeIfAbsent(b.immutable(), p -> new HashSet<>()).add(a.immutable());
    }

    public static synchronized void unregister(Level level, BlockPos a, BlockPos b) {
        Map<BlockPos, Set<BlockPos>> adj = NETWORKS.get(level);
        if (adj == null || a == null || b == null) {
            return;
        }
        Set<BlockPos> sa = adj.get(a);
        if (sa != null) {
            sa.remove(b);
        }
        Set<BlockPos> sb = adj.get(b);
        if (sb != null) {
            sb.remove(a);
        }
    }

    /**
     * 信号伝播 (本家 propagateSignal)。origin から接続グラフ全体へ。
     * 変換器 (Increment/Decrement) を通過する信号はレベルが変換される。
     */
    public static void propagate(Level level, BlockPos origin, int signalLevel) {
        if (level == null || level.isClientSide) {
            return;
        }
        Map<BlockPos, Set<BlockPos>> adj;
        synchronized (WireManager.class) {
            adj = NETWORKS.get(level);
        }
        if (adj == null) {
            return;
        }
        Set<BlockPos> visited = new HashSet<>();
        Deque<Object[]> queue = new ArrayDeque<>();
        queue.add(new Object[]{origin.immutable(), signalLevel});
        visited.add(origin.immutable());
        int guard = 0;
        while (!queue.isEmpty() && guard++ < 4096) {
            Object[] entry = queue.poll();
            BlockPos pos = (BlockPos) entry[0];
            int lvl = (Integer) entry[1];

            int outLevel = applyAndTransform(level, pos, lvl, pos.equals(origin));

            Set<BlockPos> next = adj.get(pos);
            if (next == null) {
                continue;
            }
            for (BlockPos n : next) {
                if (visited.add(n)) {
                    queue.add(new Object[]{n, outLevel});
                }
            }
        }
    }

    /**
     * ノードへ信号を適用し、通過後のレベルを返す (変換器のみ変換)。
     */
    private static int applyAndTransform(Level level, BlockPos pos, int lvl, boolean isOrigin) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TileEntitySignalConverter converter) {
            if (!isOrigin) {
                converter.setElectricity(pos.getX(), pos.getY(), pos.getZ(), lvl);
            }
            return switch (converter.getConverterType()) {
                case Increment -> Mth.clamp(lvl + 1, 0, 15);
                case Decrement -> Mth.clamp(lvl - 1, 0, 15);
                default -> lvl;
            };
        }
        if (be instanceof InstalledObjectBlockEntity io && !isOrigin) {
            if (io.getCategory() == InstalledObjectCategory.CONNECTOR_INPUT
                    || io.getCategory() == InstalledObjectCategory.CONNECTOR_OUTPUT) {
                io.setElectricity(lvl);
            }
        }
        return lvl;
    }
}
