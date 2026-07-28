package jp.ngt.rtm.rail.util;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlocks;
import com.portofino.realtrainmodunofficial.block.BallastBlock;
import com.portofino.realtrainmodunofficial.block.MarkerBlock;
import com.portofino.realtrainmodunofficial.block.RailCollisionBlock;
import com.portofino.realtrainmodunofficial.blockentity.RailCollisionBlockEntity;
import jp.ngt.ngtlib.io.NGTLog;
import jp.ngt.ngtlib.math.BezierCurve;
import jp.ngt.ngtlib.math.NGTMath;
import jp.ngt.ngtlib.math.Vec3;
import jp.ngt.rtm.rail.BlockLargeRailBase;
import jp.ngt.rtm.rail.TileEntityLargeRailBase;
import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import jp.ngt.rtm.rail.TileEntityMarker;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 本家 jp.ngt.rtm.rail.util.RailMap (KaizPatchX) の忠実移植。
 * 道床(レールベース)ブロックの生成/破壊を担う抽象基底。
 */
public abstract class RailMap {
    protected final List<int[]> rails = new ArrayList<>();

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof RailMap) {
            RailMap rm = (RailMap) obj;
            return getStartRP().equals(rm.getStartRP());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return getStartRP() != null ? getStartRP().hashCode() : 0;
    }

    public abstract RailPosition getStartRP();

    public abstract RailPosition getEndRP();

    public abstract double getLength();

    public abstract int getNearlestPoint(int paramInt, double paramDouble1, double paramDouble2);

    public abstract double[] getRailPos(int paramInt1, int paramInt2);

    public abstract double getRailHeight(int paramInt1, int paramInt2);

    public abstract float getRailYaw(int paramInt1, int paramInt2);

    @Deprecated
    public final float getRailRotation(int split, int index) {
        return getRailYaw(split, index);
    }

    public abstract float getRailPitch(int paramInt1, int paramInt2);

    public abstract float getRailRoll(int paramInt1, int paramInt2);

    @Deprecated
    public final float getCant(int split, int index) {
        return getRailRoll(split, index);
    }

    /**
     * RailMapの端同士が繋げられるかどうか(=連続した曲線になるか)
     * 同一RailMapの場合はtrue
     * @param railMap null可
     */
    public boolean canConnect(RailMap railMap) {
        if (railMap == null) {
            return false;
        }
        if (equals(railMap)) {
            return true;
        }
        if (railMap instanceof RailMapTurntable) {
            return railMap.canConnect(this);
        }
        // 端点間距離で接続判定 (本家は 1e-5 完全一致。分岐の計算誤差ぶんだけ緩和)
        return minEndpointGap(railMap) <= CONNECT_TOLERANCE;
    }

    /** 端点同士の最短距離。 */
    public double minEndpointGap(RailMap railMap) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                double[] p0 = getRailPos(10, i * 10);
                double[] p1 = railMap.getRailPos(10, j * 10);
                double dz = p0[0] - p1[0];
                double dx = p0[1] - p1[1];
                double d = Math.sqrt(dz * dz + dx * dx);
                if (d < best) {
                    best = d;
                }
            }
        }
        return best;
    }

    /** 接続とみなす端点間距離。緩めると近接する別レールへ列車が飛ぶ。 */
    private static final double CONNECT_TOLERANCE = 0.001D;

    /**
     * 道床ブロックのリストを作成
     * レールの生成時と破壊時に呼ばれる
     */
    protected void createRailList(RailProperty prop) {
        int width = prop.getBallastWidth() >> 1;//本家: prop.getModelSet().getConfig().ballastWidth

        this.rails.clear();
        int split = (int) (this.getLength() * 4.0D);
        double halfPi = Math.PI * 0.5D;
        for (int j = 0; j < split; j++) {
            double[] point = this.getRailPos(split, j);
            double x = point[1];
            double z = point[0];
            double slope = Math.toRadians(this.getRailYaw(split, j));
            double height = this.getRailHeight(split, j);
            // 本家 RailMap.createRailList と同じく「レール面のあるブロック」に置く。
            // 以前は -1 して1ブロック下に埋めていたが、当たり判定の高さは
            // BlockLargeRailBase.getRailShape → TileEntityLargeRailBase.getBlockHeights
            // = (レール面Y − ブロックY)
            // で決まるため、ブロックを1つ下げるとその分だけ箱が丸ごと1ブロック高くなる。
            // ★ (int) キャストは使わない。0 方向への切り捨てなので、負の Y で 1 つ上を指す。
            int y = Mth.floor(height);
            int x0 = Mth.floor(x);
            int z0 = Mth.floor(z);

            double sinSlopePlus = Math.sin(slope + halfPi);
            double cosSlopePlus = Math.cos(slope + halfPi);
            double sinSlopeMinus = Math.sin(slope - halfPi);
            double cosSlopeMinus = Math.cos(slope - halfPi);

            for (int i = 1; i <= width; i++) {
                int x1 = Mth.floor(x + sinSlopePlus * i);
                int z1 = Mth.floor(z + cosSlopePlus * i);
                int x2 = Mth.floor(x + sinSlopeMinus * i);
                int z2 = Mth.floor(z + cosSlopeMinus * i);
                this.addRailBlock(x1, y, z1);
                this.addRailBlock(x2, y, z2);
            }
            this.addRailBlock(x0, y, z0);
        }
    }

    protected void addRailBlock(int x, int y, int z) {
        for (int i = 0; i < this.rails.size(); i++) {
            int[] ia = this.rails.get(i);
            if (ia[0] == x && ia[2] == z) {
                if (ia[1] <= y) {
                    return;
                } else {
                    this.rails.remove(i);
                    --i;
                }
            }
        }
        int[] pos = new int[]{x, y, z};
        if (!Arrays.equals(pos, this.getStartRP().getNeighborPos()) && !Arrays.equals(pos, this.getEndRP().getNeighborPos())) {
            this.rails.add(new int[]{x, y, z});//始点と終点に接する位置にはブロック生成しないように
        }
    }

    /** スクリプト互換: WorldCompat (entity.field_70170_p) を渡す SRB3 等の呼び出しを受ける。 */
    public void setRail(Object world, Object block, int x0, int y0, int z0, RailProperty prop) {
        Level level = jp.ngt.ngtlib.block.BlockUtil.toLevel(world);
        if (level != null && block instanceof Block b) {
            this.setRail(level, b, x0, y0, z0, prop);
        }
    }

    /** スクリプト互換 canPlaceRail (WorldCompat 版) */
    public boolean canPlaceRail(Object world, boolean isCreative, RailProperty prop) {
        Level level = jp.ngt.ngtlib.block.BlockUtil.toLevel(world);
        return level != null && this.canPlaceRail(level, isCreative, prop);
    }

    /** スクリプト互換 breakRail (WorldCompat 版) */
    public void breakRail(Object world, RailProperty prop, TileEntityLargeRailCore core) {
        Level level = jp.ngt.ngtlib.block.BlockUtil.toLevel(world);
        if (level != null) {
            this.breakRail(level, prop, core);
        }
    }

    /**
     * スクリプト互換オーバーロード。
     * スクリプトの world は entity.field_70170_p =
     * jp.ngt.mccompat.WorldCompat であり実 Level ではない。
     */
    public void setRail(Object world, Block block, int x0, int y0, int z0, RailProperty prop) {
        Level level = jp.ngt.ngtlib.block.BlockUtil.toLevel(world);
        if (level != null) {
            this.setRail(level, block, x0, y0, z0, prop);
        }
    }

    /** ブロックの設置 */
    public void setRail(Level world, Block block, int x0, int y0, int z0, RailProperty prop) {
        this.createRailList(prop);
        BlockEntity tile = world.getBlockEntity(new BlockPos(x0, y0, z0));
        if (tile instanceof TileEntityMarker && ((TileEntityMarker) tile).getState(MarkerState.LINE2)) {
            this.setBaseBlock(world, x0, y0, z0);
        }
        this.rails.forEach(rail -> {
            int x = rail[0];
            int y = rail[1];
            int z = rail[2];
            BlockPos pos = new BlockPos(x, y, z);
            Block block2 = world.getBlockState(pos).getBlock();
            if (!(block2 instanceof BlockLargeRailBase) || block2 == block)//異なる種類のレールを上書きしない
            {
                world.setBlock(pos, block.defaultBlockState(), 2);
                BlockEntity be = world.getBlockEntity(pos);
                if (be instanceof TileEntityLargeRailBase railBase) {
                    railBase.setStartPoint(x0, y0, z0);
                    railBase.setChanged();
                } else {
                    jp.ngt.ngtlib.io.NGTLog.debug("[RailMap] setRail: no rail BE at %d,%d,%d (block=%s)", x, y, z, String.valueOf(be));
                }
            }
        });
        this.rails.clear();
    }

    private void setBaseBlock(Level world, int x0, int y0, int z0) {
        int split = (int) (this.getLength() * 4.0D);
        RailPosition rp = this.getStartRP();
        int minWidth = Mth.floor(rp.constLimitWN + 0.5F);
        int maxWidth = Mth.floor(rp.constLimitWP + 0.5F);
        int minHeight = Mth.floor(rp.constLimitHN);
        int maxHeight = Mth.floor(rp.constLimitHP);
        BlockState[][] blocks = new BlockState[maxHeight - minHeight + 1][maxWidth - minWidth + 1];
        for (int k = 0; k < split - 1; k++) {
            double[] point = this.getRailPos(split, k);
            double x = point[1];
            double z = point[0];
            double y = this.getRailHeight(split, k);
            float yaw = Mth.wrapDegrees(this.getRailYaw(split, k));
            for (int i = 0; i < blocks.length; i++) {
                int h = minHeight + i;
                for (int j = 0; j < (blocks[i]).length; j++) {
                    int w = minWidth + j;
                    Vec3 vec = new Vec3(w, h, 0.0D);
                    vec = vec.rotateAroundY(yaw);
                    BlockPos pos = new BlockPos(Mth.floor(x + vec.getX()), Mth.floor(y + vec.getY()), Mth.floor(z + vec.getZ()));
                    BlockState state = world.getBlockState(pos);
                    Block block = state.getBlock();
                    if (k == 0) {
                        if (!(block instanceof jp.ngt.rtm.rail.BlockMarker) && !(block instanceof MarkerBlock) && !(block instanceof BlockLargeRailBase)) {
                            blocks[i][j] = state;
                        }
                    } else if (blocks[i][j] != null) {
                        if (!(block instanceof BlockLargeRailBase)) {
                            world.setBlock(pos, blocks[i][j], 3);
                        }
                    }
                }
            }
        }
    }

    /** ブロックの破壊 */
    public void breakRail(Level world, RailProperty prop, TileEntityLargeRailCore core) {
        this.createRailList(prop);
        List<BlockPos> posList = new ArrayList<>();
        this.rails.forEach(anInt -> {
            // ベッド行の算出オフセットがビルド間で変わっても連鎖するよう ±1 行も確認する
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos pos = new BlockPos(anInt[0], anInt[1] + dy, anInt[2]);
                BlockEntity rail = world.getBlockEntity(pos);
                if (rail instanceof TileEntityLargeRailBase) {
                    if (rail == core) {
                        continue;
                    }

                    // 重なっている他レールを破壊しないように
                    // coreが既に破壊さている場合は続行
                    TileEntityLargeRailCore core2 = ((TileEntityLargeRailBase) rail).getRailCore();
                    if (core2 == null || core2 == core) {
                        posList.add(pos);
                    }
                }
            }
        });
        posList.forEach(pos -> {
            world.removeBlockEntity(pos);
            world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        });
        BlockPos corePos = core.getBlockPos();
        world.removeBlockEntity(corePos);
        world.setBlock(corePos, Blocks.AIR.defaultBlockState(), 3);

        this.rails.clear();
    }

    public boolean canPlaceRail(Level world, boolean isCreative, RailProperty prop) {
        this.createRailList(prop);
        boolean flag = true;
        for (int[] rail : this.rails) {
            // 本家 canPlaceRail と同じく、レールブロックを置く位置そのもので障害物を見る。
            // (以前はレールブロックを1つ下に埋めていたので +1 してレール面を見ていた。
            BlockPos pos = new BlockPos(rail[0], rail[1], rail[2]);
            BlockState state = world.getBlockState(pos);
            Block block = state.getBlock();
            boolean b0 = state.isAir()
                    || block instanceof jp.ngt.rtm.rail.BlockMarker || block instanceof MarkerBlock
                    || (block instanceof BlockLargeRailBase && !((BlockLargeRailBase) block).isCore());
            if (!isCreative && !b0) {
                NGTLog.sendChatMessageToAll("message.rail.obstacle", ":" + rail[0] + "," + rail[1] + "," + rail[2]);
                return false;
            }
            flag = b0 && flag;
        }
        return true;
    }

    public List<int[]> getRailBlockList(RailProperty prop) {
        this.createRailList(prop);
        return new ArrayList<>(this.rails);
    }

    public void showRailProp() {
        NGTLog.sendChatMessageToAll(String.format("SP X%5.1f Z%5.1f", (this.getStartRP()).posX, (this.getStartRP()).posZ));
        NGTLog.sendChatMessageToAll(String.format("SA L%5.1f Y%5.1f", (this.getStartRP()).anchorLengthHorizontal, (this.getStartRP()).anchorYaw));
        NGTLog.sendChatMessageToAll(String.format("EP X%5.1f Z%5.1f", (this.getEndRP()).posX, (this.getEndRP()).posZ));
        NGTLog.sendChatMessageToAll(String.format("EA L%5.1f Y%5.1f", (this.getEndRP()).anchorLengthHorizontal, (this.getEndRP()).anchorYaw));
    }

    // ==================================================================================
    // 以下は Remaster 旧システム (RailCollisionBlock 方式) 向けの暫定互換 API。
    // Phase 1 flip 完了時に削除する。

    /** @deprecated Remaster 独自。 */
    @Deprecated
    public static boolean suppressRailRemoval = false;

    /** @deprecated Remaster 独自 API。本家に存在しない。 */
    @Deprecated
    public boolean isStraightTrack() {
        return false;
    }

    /** @deprecated Remaster 独自 API。本家に存在しない。 */
    @Deprecated
    public double getHorizontalPathLength() {
        return this.getLength();
    }

    /** @deprecated Remaster 独自 API。本家に存在しない。 */
    @Deprecated
    public static int curveSplitForLength(double length) {
        return BezierCurve.splitForLength(length);
    }

    /** @deprecated 旧 RailCollisionBlock 方式。 */
    @Deprecated
    protected void createRailListLegacy(RailProperties prop) {
        this.rails.clear();
        double halfW = Math.max(prop.ballastWidth / 2.0D, 0.6D);
        int split = (int) (this.getLength() * 4.0D);
        if (split < 2) split = 2;
        int n = split + 1;

        double[] sx = new double[n];
        double[] sz = new double[n];
        int[] sy = new int[n];
        double[] soff = new double[n];
        double minx = Double.MAX_VALUE, maxx = -Double.MAX_VALUE;
        double minz = Double.MAX_VALUE, maxz = -Double.MAX_VALUE;
        for (int j = 0; j < n; ++j) {
            double[] point = this.getRailPos(split, j);
            sx[j] = point[1];
            sz[j] = point[0];
            double h = this.getRailHeight(split, j);
            sy[j] = (int) Math.floor(h + 1.0e-4);
            soff[j] = h - sy[j];
            if (sx[j] < minx) minx = sx[j];
            if (sx[j] > maxx) maxx = sx[j];
            if (sz[j] < minz) minz = sz[j];
            if (sz[j] > maxz) maxz = sz[j];
        }

        int bx0 = NGTMath.floor(minx - halfW - 1.0D);
        int bx1 = NGTMath.floor(maxx + halfW + 1.0D);
        int bz0 = NGTMath.floor(minz - halfW - 1.0D);
        int bz1 = NGTMath.floor(maxz + halfW + 1.0D);
        double thrSq = halfW * halfW;

        for (int X = bx0; X <= bx1; ++X) {
            for (int Z = bz0; Z <= bz1; ++Z) {
                double cx = X + 0.5D;
                double cz = Z + 0.5D;
                double best = Double.MAX_VALUE;
                int bestJ = 0;
                for (int j = 0; j < n; ++j) {
                    double dx = cx - sx[j];
                    double dz = cz - sz[j];
                    double d = dx * dx + dz * dz;
                    if (d < best) {
                        best = d;
                        bestJ = j;
                    }
                }
                if (best > thrSq) continue;
                if (bestJ == 0 || bestJ == n - 1) {
                    int inner = bestJ == 0 ? Math.min(1, n - 1) : Math.max(0, n - 2);
                    double tinx = sx[inner] - sx[bestJ];
                    double tinz = sz[inner] - sz[bestJ];
                    double vx = cx - sx[bestJ];
                    double vz = cz - sz[bestJ];
                    if (vx * tinx + vz * tinz < -1.0e-3) {
                        continue;
                    }
                }
                for (int j = 0; j < n; ++j) {
                    double dx = cx - sx[j];
                    double dz = cz - sz[j];
                    if (dx * dx + dz * dz > thrSq) continue;
                    int off16 = (int) Math.round(soff[j] * 16.0D);
                    if (off16 < 0) off16 = 0;
                    if (off16 > 15) off16 = 15;
                    this.addRailBlockLegacy(X, sy[j], Z, off16);
                }
            }
        }
    }

    @Deprecated
    private void addRailBlockLegacy(int x, int y, int z, int surfaceOffset16) {
        for (int[] ia : this.rails) {
            if (ia[0] == x && ia[1] == y && ia[2] == z) {
                if (ia.length >= 4 && surfaceOffset16 > ia[3]) {
                    ia[3] = surfaceOffset16;
                }
                return;
            }
        }
        this.rails.add(new int[]{x, y, z, surfaceOffset16});
    }

    /** @deprecated 旧 RailCollisionBlock 方式。 */
    @Deprecated
    public void setRail(Level level, Block ballastBlock, int x0, int y0, int z0, RailProperties prop) {
        if (level == null || prop == null) {
            this.rails.clear();
            return;
        }
        this.createRailListLegacy(prop);
        Block collisionBlock = RealTrainModUnofficialBlocks.RAIL_COLLISION.get();
        BlockPos corePos = new BlockPos(x0, y0, z0);
        for (int[] rail : this.rails) {
            BlockPos pos = new BlockPos(rail[0], rail[1], rail[2]);
            BlockState existingState = level.getBlockState(pos);
            Block existing = existingState.getBlock();
            boolean replaceable = existing == Blocks.AIR
                    || existing == Blocks.CAVE_AIR || existing == Blocks.VOID_AIR
                    || existing instanceof RailCollisionBlock
                    || existing instanceof BallastBlock
                    || existing instanceof MarkerBlock
                    || existingState.canBeReplaced();
            if (replaceable) {
                level.setBlock(pos, collisionBlock.defaultBlockState(), Block.UPDATE_ALL);
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof RailCollisionBlockEntity rbe) {
                    rbe.setCorePos(corePos);
                    float surfaceY = rail.length >= 4 ? rail[3] / 16.0f : 0.0f;
                    rbe.setSurfaceY(surfaceY);
                    level.sendBlockUpdated(pos, rbe.getBlockState(), rbe.getBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        this.rails.clear();
    }

    /** @deprecated 旧 RailCollisionBlock 方式。 */
    @Deprecated
    public boolean canPlaceRail(Level level, boolean isCreative, RailProperties prop) {
        double len = this.getLength();
        int samples = Math.max(3, (int) Math.ceil(len) + 1);
        int split = curveSplitForLength(this.getHorizontalPathLength());
        BlockPos startNeighbor = this.getStartRP().getNeighborBlockPos();
        BlockPos endNeighbor = this.getEndRP().getNeighborBlockPos();
        boolean allClear = true;
        for (int i = 0; i < samples; i++) {
            int j = samples <= 1 ? 0 : (int) Math.round((double) split * i / (samples - 1));
            if (j > split) j = split;
            double[] point = this.getRailPos(split, j);
            int x = NGTMath.floor(point[1]);
            int z = NGTMath.floor(point[0]);
            int y = NGTMath.floor(this.getRailHeight(split, j));//(int) は負の Y で 1 つ上を指すため不可
            BlockPos pos = new BlockPos(x, y, z);
            if (pos.equals(startNeighbor) || pos.equals(endNeighbor)) {
                continue;
            }
            Block block = level.getBlockState(pos).getBlock();
            boolean passable = block == Blocks.AIR
                    || block == Blocks.CAVE_AIR
                    || block == Blocks.VOID_AIR
                    || block instanceof MarkerBlock
                    || block instanceof BallastBlock
                    || block instanceof RailCollisionBlock;
            if (!passable) {
                allClear = false;
                if (!isCreative) return false;
            }
        }
        return isCreative || allClear;
    }

    /** @deprecated 旧 RailCollisionBlock 方式。 */
    @Deprecated
    public List<int[]> getRailBlockList(RailProperties prop, boolean regenerate) {
        if (this.rails.isEmpty() || regenerate) {
            this.createRailListLegacy(prop);
        }
        return new ArrayList<>(this.rails);
    }

    /** @deprecated 旧 RailCollisionBlock 方式。 */
    @Deprecated
    public void removeRailBlocks(Level level) {
        double len = this.getLength();
        int split = curveSplitForLength(this.getHorizontalPathLength());
        int samples = Math.max(3, split + 1);
        for (int i = 0; i < samples; i++) {
            int j = samples <= 1 ? 0 : (int) Math.round((double) split * i / (samples - 1));
            if (j > split) j = split;
            double[] point = this.getRailPos(split, j);
            int x = NGTMath.floor(point[1]);
            int z = NGTMath.floor(point[0]);
            int y = NGTMath.floor(this.getRailHeight(split, j));//(int) は負の Y で 1 つ上を指すため不可
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = -1; dy <= 0; dy++) {
                        BlockPos pos = new BlockPos(x + dx, y + dy, z + dz);
                        Block block = level.getBlockState(pos).getBlock();
                        if (block instanceof BallastBlock || block instanceof RailCollisionBlock) {
                            level.removeBlock(pos, false);
                        }
                    }
                }
            }
        }
    }
}
