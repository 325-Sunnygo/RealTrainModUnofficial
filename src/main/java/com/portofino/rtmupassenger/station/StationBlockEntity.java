package com.portofino.rtmupassenger.station;

import com.portofino.rtmupassenger.PassengerMod;
import com.portofino.rtmupassenger.entity.PassengerEntity;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 駅ブロックの BlockEntity。
 * 自分を StationRegistry に登録 (乗客の行き先候補)。
 */
public class StationBlockEntity extends BlockEntity {

    /** 列車・ホームの検知半径。 */
    public static final int RADIUS = 8;
    /**
     * 待機乗客を補充する間隔 (tick)。
     *
     * <p>設定した人数を切ったらすぐ埋め直したいので短くしてある (旧: 120)。
     * 1 回の補充で湧かせるのは足りない人数まで (最大 {@link #SPAWN_BURST} 人)。
     */
    private static final int SPAWN_INTERVAL = 40;
    /** 1 回の補充で湧かせる上限。まとめて出すと目の前にポンと並ぶので少しずつ。 */
    private static final int SPAWN_BURST = 2;
    /**
     * 「列車のドアがこの停止位置目標に付いている」とみなす距離の2乗 (車体軸からの水平距離)。
     * 停止位置目標は車体側面 (軸から約2m) に置くので 4m もあれば十分。
     */
    private static final double SERVE_DIST_SQ = 16.0D;

    /** 範囲内の停止位置目標。乗客の待機位置・乗降ドアに使う。定期再スキャン。 */
    private final java.util.List<BlockPos> stopTargets = new java.util.ArrayList<>();
    private long nextStopScan;

    public StationBlockEntity(BlockPos pos, BlockState state) {
        super(PassengerMod.STATION_BE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, StationBlockEntity be) {
        if (!(level instanceof ServerLevel sl)) {
            return;
        }
        long time = level.getGameTime();
        // 駅を登録し続ける (初回ロード・保険)。
        if (time % 200L == 0L) {
            StationRegistry.get(sl).add(pos);
        }
        if (time >= be.nextStopScan) {
            be.scanStopTargets(sl);
            be.nextStopScan = time + 100L;
        }
        if (time % 10L == 0L) {
            be.tickTrains(sl);
        }
        if (time % SPAWN_INTERVAL == 0L) {
            be.trySpawn(sl);
        }
    }

    /** ホーム範囲内の停止位置目標ブロックを探して記録する。 */
    private void scanStopTargets(ServerLevel level) {
        this.stopTargets.clear();
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                for (int dy = -3; dy <= 3; dy++) {
                    mp.set(this.worldPosition.getX() + dx, this.worldPosition.getY() + dy, this.worldPosition.getZ() + dz);
                    if (level.getBlockState(mp).getBlock() instanceof StopTargetBlock) {
                        this.stopTargets.add(mp.immutable());
                    }
                }
            }
        }
    }

    /** ホーム範囲 (待機乗客の探索用)。 */
    private AABB platformArea() {
        return new AABB(this.worldPosition).inflate(RADIUS, 4.0D, RADIUS);
    }

    /** 停車＋ドア開の列車を検知し、降車と乗車 (各停止位置目標の待ち行列の先頭から) を行う。 */
    private void tickTrains(ServerLevel level) {
        AABB trainArea = new AABB(this.worldPosition).inflate(RADIUS + 16.0D, 5.0D, RADIUS + 16.0D);
        List<EntityTrainBase> trains = level.getEntitiesOfClass(EntityTrainBase.class, trainArea);
        if (trains.isEmpty()) {
            return;
        }
        EntityTrainBase boardable = null;
        for (EntityTrainBase train : trains) {
            boolean stopped = Math.abs(train.getSpeed()) < 0.05F;
            boolean doorOpen = train.getTrainStateData(4) != 0; //State_Door (0=閉)
            // この駅の停止位置目標にドアが付いている列車だけを扱う。
            // 隣の駅に停車中の列車を自分の駅と誤認して乗せないため。
            // ★終点の列車には乗せない (降車は PassengerEntity 側でそのまま行われる)。
            //   乗せると、このあと消える列車に乗り込むことになる。
            if (stopped && doorOpen && !train.isTerminating() && this.trainServesThisStation(train)) {
                if (boardable == null) {
                    boardable = train;
                }
            }
        }
        if (boardable == null) {
            return;
        }
        // 乗車は停止位置目標ごとに 1 人ずつ: 既に乗り込み中 (BOARDING) の人が居る
        // 停止位置目標は、その人が着席するまで次を出さない (ドアでの押し合いを防ぐ)。
        AABB scan = this.platformArea().inflate(6.0D, 3.0D, 6.0D);
        java.util.Set<BlockPos> boarding = new java.util.HashSet<>();
        for (PassengerEntity b : level.getEntitiesOfClass(PassengerEntity.class, scan,
                q -> (q.getState() == PassengerEntity.State.BOARDING
                        || q.getState() == PassengerEntity.State.ENTERING) && q.getWaitSpot() != null)) {
            boarding.add(b.getWaitSpot());
        }
        // 各停止位置目標の待ち行列の先頭 (id 最小=先に湧いた)。
        Map<BlockPos, PassengerEntity> fronts = new HashMap<>();
        for (PassengerEntity p : level.getEntitiesOfClass(PassengerEntity.class, scan,
                q -> q.getState() == PassengerEntity.State.WAITING && q.getWaitSpot() != null)) {
            PassengerEntity cur = fronts.get(p.getWaitSpot());
            if (cur == null || p.getId() < cur.getId()) {
                fronts.put(p.getWaitSpot(), p);
            }
        }
        for (Map.Entry<BlockPos, PassengerEntity> e : fronts.entrySet()) {
            if (boarding.contains(e.getKey())) {
                continue; //その停止位置目標は今 1 人乗り込み中 → 次は着席後
            }
            e.getValue().beginBoarding(boardable);
        }
    }

    /** 停止位置目標のどれかに列車のドアが付いているか (この駅に実際に停車しているか)。 */
    private boolean trainServesThisStation(EntityTrainBase train) {
        for (BlockPos sp : this.stopTargets) {
            if (PassengerEntity.distToTrainBodySq(train, sp.getX() + 0.5D, sp.getZ() + 0.5D) <= SERVE_DIST_SQ) {
                return true;
            }
        }
        return false;
    }

    /**
     * 待機乗客を「出る人数」まで補充する。
     *
     * <p><b>設定した人数は必ず埋める。</b> 待っている人が減ったら (列車に乗った・
     * 殴られて消えた等) その差分をここで湧かせ直す。
     *
     * <p>★以前は需要 (タグ × 時刻) の抽選でここを弾いていた。タグ無しの駅だと
     * 1 回あたり約 10% しか通らず、6 秒間隔なので 1 人湧くのに平均 1 分・
     * 16 人埋まるのに 15 分かかっていた。これが「人数を上げても増えない」の正体。
     * 需要は<b>湧く速さ</b> (1 回に何人出すか) にだけ効かせる。
     */
    private void trySpawn(ServerLevel level) {
        if (this.stopTargets.isEmpty()) {
            return; //停止位置目標が無ければ湧かせない
        }
        // ★出る人数は駅ブロックごとの設定 (右クリック GUI)。0 なら湧かせない = 行き先専用駅。
        StationRegistry registry = StationRegistry.get(level);
        int capacity = registry.capacity(this.worldPosition);
        if (capacity <= 0) {
            return;
        }
        // ★人数の上限は駅ブロックの GUI だけで決める。
        // 以前はここで RTMU 設定「乗客の最大数」(ワールド共通) が上から蓋をしていて、
        // 駅ごとに増やしても近くの他の駅の乗客に押し出されて増えなかった。
        // 残すのは「誰も見ていない駅では湧かせない」だけ (チャンクローダーで回り続けている駅に、
        // 誰も見ないまま乗客が溜まるのを防ぐ)。
        if (!this.hasViewer(level)) {
            return;
        }
        // 今ホームで待っている人数。乗り込み始めた人 (ENTERING 以降) は数えないので、
        // 列車に乗った瞬間に空きができて次が湧く。
        int waitingCount = level.getEntitiesOfClass(PassengerEntity.class,
                this.platformArea().inflate(6.0D, 3.0D, 6.0D),
                p -> p.getState() == PassengerEntity.State.WAITING
                        || p.getState() == PassengerEntity.State.BOARDING).size();
        int shortfall = capacity - waitingCount;
        if (shortfall <= 0) {
            return;
        }

        float hour = StationTag.hourOf(level.getDayTime());
        int bits = registry.tagBits(this.worldPosition);
        // 需要が高い時間帯ほどまとめて湧く。低くても必ず 1 人は湧かせる。
        int burst = Math.round(StationTag.originWeight(bits, hour));
        burst = Math.max(1, Math.min(SPAWN_BURST, burst));
        int toSpawn = Math.min(shortfall, burst);

        for (int i = 0; i < toSpawn; i++) {
            if (!this.spawnOne(level, hour, registry)) {
                return; //行き先が無い等。次の間隔でやり直す
            }
        }
    }

    /** 待機乗客を 1 人湧かせる。行き先が取れなければ false。 */
    private boolean spawnOne(ServerLevel level, float hour, StationRegistry registry) {
        // 行き先は到着需要の重み付き抽選 (朝はオフィス街行き、夕方は住宅街行きが多い)。
        BlockPos dest = registry.pickDestination(this.worldPosition, hour, level.random);
        if (dest == null) {
            return false; //行き先 (他の駅) が無ければ湧かせない
        }
        BlockPos waitSpot = this.stopTargets.get(level.random.nextInt(this.stopTargets.size()));

        PassengerEntity p = PassengerMod.PASSENGER.get().create(level);
        if (p == null) {
            return false;
        }
        // ★停止位置目標 (ホームの開けた足場) の真上に出す。
        // 寄せて出していたが、駅ブロックが壁の裏だと乗客が壁の中に湧いて動けなくなっていた。
        p.moveTo(waitSpot.getX() + 0.5D, waitSpot.getY(), waitSpot.getZ() + 0.5D,
                level.random.nextFloat() * 360.0F, 0.0F);
        p.initPassenger(this.worldPosition, dest, waitSpot);
        p.finalizeSpawn(level, level.getCurrentDifficultyAt(this.worldPosition), MobSpawnType.TRIGGERED, null);
        level.addFreshEntity(p);
        return true;
    }

    /**
     * この駅を視界に入れているプレイヤーが 1 人でも居るか。
     * 誰も見ていない駅では湧かせない
     * (チャンクローダーで回り続けている駅に、誰も見ないまま乗客が溜まるのを防ぐ)。
     */
    private boolean hasViewer(ServerLevel level) {
        double r = level.getServer().getPlayerList().getViewDistance() * 16.0D;
        double rSq = r * r;
        for (net.minecraft.server.level.ServerPlayer player : level.players()) {
            if (player.distanceToSqr(this.worldPosition.getX() + 0.5D,
                    this.worldPosition.getY() + 0.5D,
                    this.worldPosition.getZ() + 0.5D) <= rSq) {
                return true;
            }
        }
        return false;
    }

    /** ブロック破壊時: 駅登録を解除する。 */
    public void onDestroyed() {
        if (this.level instanceof ServerLevel sl) {
            StationRegistry.get(sl).remove(this.worldPosition);
        }
    }
}
