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
 * 駅ブロックの BlockEntity。作り直し版はシンプルに次のことだけ行う:
 * <ul>
 *   <li>自分を {@link StationRegistry} に登録 (乗客の行き先候補)。</li>
 *   <li>範囲内の停止位置目標 ({@link StopTargetBlock}) を把握。</li>
 *   <li>定期的に、停止位置目標に待機乗客を湧かせる (行き先 = 他の駅)。</li>
 *   <li>停車しドアの開いた列車に、待機乗客を乗せ、目的地の乗客を降ろす。</li>
 * </ul>
 */
public class StationBlockEntity extends BlockEntity {

    /** 列車・ホームの検知半径。 */
    public static final int RADIUS = 8;
    /** 1 駅あたりの待機乗客の上限。 */
    private static final int MAX_WAITING = 6;
    /** 待機乗客を湧かせる間隔 (tick)。 */
    private static final int SPAWN_INTERVAL = 120;
    /**
     * 「列車のドアがこの停止位置目標に付いている」とみなす距離の2乗 (車体軸からの水平距離)。
     * 停止位置目標は車体側面 (軸から約2m) に置くので 4m もあれば十分。隣の駅に停車中の列車は
     * 車体軸から十数 m 離れるため、この閾値では拾わない (=誤って乗降させない)。
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
        //駅を登録し続ける (初回ロード・保険)。
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
            //この駅の停止位置目標にドアが付いている列車だけを扱う。小さいループで
            //隣の駅に停車中の列車を自分の駅と誤認して乗せないため。
            //(降車は乗客自身が乗車中に判定する — 駅チャンクがアンロードでも降りられる)
            if (stopped && doorOpen && this.trainServesThisStation(train)) {
                if (boardable == null) {
                    boardable = train;
                }
            }
        }
        if (boardable == null) {
            return;
        }
        //乗車は<b>停止位置目標ごとに 1 人ずつ</b>: 既に乗り込み中 (BOARDING) の人が居る
        //停止位置目標は、その人が着席するまで次を出さない (ドアでの押し合いを防ぐ)。
        AABB scan = this.platformArea().inflate(6.0D, 3.0D, 6.0D);
        java.util.Set<BlockPos> boarding = new java.util.HashSet<>();
        for (PassengerEntity b : level.getEntitiesOfClass(PassengerEntity.class, scan,
                q -> (q.getState() == PassengerEntity.State.BOARDING
                        || q.getState() == PassengerEntity.State.ENTERING) && q.getWaitSpot() != null)) {
            boarding.add(b.getWaitSpot());
        }
        //各停止位置目標の待ち行列の先頭 (id 最小=先に湧いた)。
        Map<BlockPos, PassengerEntity> fronts = new HashMap<>();
        for (PassengerEntity p : level.getEntitiesOfClass(PassengerEntity.class, scan,
                q -> q.getState() == PassengerEntity.State.WAITING && q.getWaitSpot() != null)) {
            PassengerEntity cur = fronts.get(p.getWaitSpot());
            if (cur == null || p.getId() < cur.getId()) {
                fronts.put(p.getWaitSpot(), p);
            }
        }
        if (!fronts.isEmpty()) {
            PassengerMod.LOGGER.info("[PsgDiag] boardable train found at station {} — fronts={} boarding={}",
                    this.worldPosition, fronts.size(), boarding.size());
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
     * 需要 (タグ × 時刻) に応じて待機乗客を湧かせる。住宅街は朝・オフィス街は夕方に多い等、
     * タグの出発需要が高い時間帯ほど湧きやすい (= NPC の人数が時間帯で変わる)。
     * ワールド全体で {@link #MAX_GLOBAL} 体まで (チャンク外の乗客も数える)。
     */
    private void trySpawn(ServerLevel level) {
        if (this.stopTargets.isEmpty()) {
            return; //停止位置目標が無ければ湧かせない
        }
        //出発需要による抽選 (SPAWN_INTERVAL ごと)。ラッシュ時はほぼ毎回、閑散時はまれ。
        float hour = StationTag.hourOf(level.getDayTime());
        int bits = StationRegistry.get(level).tagBits(this.worldPosition);
        float chance = Math.min(0.9F, 0.30F * StationTag.originWeight(bits, hour));
        if (level.random.nextFloat() >= chance) {
            return;
        }
        //ワールド全体の上限 (RTMU 設定「乗客の最大数」。0=湧かない, 無制限も可)。
        //SavedData で厳密にカウント (チャンク外の乗客も含む)。
        if (PassengerPopulation.get(level).size()
                >= com.portofino.realtrainmodunofficial.RtmuSettings.serverMaxPassengers()) {
            return;
        }
        int waitingCount = level.getEntitiesOfClass(PassengerEntity.class,
                this.platformArea().inflate(6.0D, 3.0D, 6.0D),
                p -> p.getState() == PassengerEntity.State.WAITING
                        || p.getState() == PassengerEntity.State.BOARDING).size();
        if (waitingCount >= MAX_WAITING) {
            return;
        }
        //行き先は到着需要の重み付き抽選 (朝はオフィス街行き、夕方は住宅街行きが多い)。
        BlockPos dest = StationRegistry.get(level).pickDestination(this.worldPosition, hour, level.random);
        if (dest == null) {
            return; //行き先 (他の駅) が無ければ湧かせない
        }
        BlockPos waitSpot = this.stopTargets.get(level.random.nextInt(this.stopTargets.size()));

        PassengerEntity p = PassengerMod.PASSENGER.get().create(level);
        if (p == null) {
            return;
        }
        //★停止位置目標 (ホームの開けた足場) の真上に出す。以前は駅ブロック側へ 2 ブロック
        //  寄せて出していたが、駅ブロックが壁の裏だと乗客が壁の中に湧いて動けなくなっていた。
        //  目標の真上は必ず歩ける場所なので安全。整列は待機処理 (tickWaiting) がナビで行う。
        p.moveTo(waitSpot.getX() + 0.5D, waitSpot.getY(), waitSpot.getZ() + 0.5D,
                level.random.nextFloat() * 360.0F, 0.0F);
        p.initPassenger(this.worldPosition, dest, waitSpot);
        p.finalizeSpawn(level, level.getCurrentDifficultyAt(this.worldPosition), MobSpawnType.TRIGGERED, null);
        level.addFreshEntity(p);
        //総数へ登録 (本当に消えた時に PassengerEntity.remove が解除する)
        PassengerPopulation.get(level).add(p.getUUID());
        PassengerMod.LOGGER.info("[PsgDiag] spawned id={} at door={} dest={} (total={})",
                p.getId(), waitSpot, dest, PassengerPopulation.get(level).size());
    }

    /** ブロック破壊時: 駅登録を解除する。 */
    public void onDestroyed() {
        if (this.level instanceof ServerLevel sl) {
            StationRegistry.get(sl).remove(this.worldPosition);
        }
    }
}
