package com.portofino.rtmuautodrive;

import jp.ngt.rtm.entity.train.EntityTrainBase;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 自動運転中の編成の一覧 (ワールドに保存される) と、その運転状態 (メモリのみ)。
 *
 * <p>保存するのは<b>列車の UUID だけ</b>。運転の途中経過 (減速中/停車中/待ち時間) は
 * 再起動で作り直せるので保存しない。
 */
public final class AutoDriveState extends SavedData {

    private static final String NAME = "rtmuautodrive_trains";

    /** 何 tick おきに運転操作をするか。本家の運転士 AI (4) と同じ。 */
    private static final int DRIVE_INTERVAL = 4;

    /** 停車時間などを tick で数えるとき、1 回の呼び出しが何 tick 分かを知るのに使う。 */
    public static int driveInterval() {
        return DRIVE_INTERVAL;
    }

    /** 自動運転を入れた列車 (制御車) の UUID。 */
    private final Set<UUID> trains = new LinkedHashSet<>();

    /** 停車駅 1 つ分。door は 0=両側 / 1=左 / 2=右。 */
    public record RouteStop(net.minecraft.core.BlockPos pos, int door) {
    }

    /** 列車ごとの停車駅。通過駅はここに入らない。 */
    private final Map<UUID, java.util.List<RouteStop>> routes = new HashMap<>();

    /** 列車ごとの停車時間 (tick)。 */
    private final Map<UUID, Integer> dwellTicks = new HashMap<>();

    /** 列車ごとの方向幕番号。走行中も毎回入れ直す (他の処理に消されないように)。 */
    private final Map<UUID, Integer> rollsigns = new HashMap<>();

    /** 運転の途中経過。保存しない。 */
    private final Map<UUID, AutoDriver> drivers = new HashMap<>();

    // ---- チャンクの強制読み込み ----
    //
    // ★RTMU 本体のチャンクローダー (State_ChunkLoader) だけでは足りない。
    //   あれは<b>列車自身の tick でチケットを張る</b>ので、チャンクが読まれていない間は
    //   そもそも tick が回らず、永久に読み込まれないまま止まってしまう
    //   (しかも「編成の先頭車だけが張る」ので、こちらが握っている車両が先頭でないと張られない)。
    //   そこで自動運転中の編成は<b>この mod 側でバニラの強制ロードを掛ける</b>。

    /** 強制ロードする半径 (1 = 3x3 チャンク)。 */
    private static final int FORCE_RADIUS = 1;

    /** 列車ごとに今どのチャンクを強制ロードしているか (ChunkPos.toLong)。 */
    private final Map<UUID, Long> forced = new HashMap<>();

    /** その列車のまわりを強制ロードし直す。 */
    private void updateForcedChunks(ServerLevel level, UUID id, net.minecraft.world.level.ChunkPos center) {
        Long prev = this.forced.get(id);
        long now = center.toLong();
        if (prev != null && prev == now) {
            return;
        }
        if (prev != null) {
            setForced(level, new net.minecraft.world.level.ChunkPos(prev), false, id);
        }
        setForced(level, center, true, id);
        this.forced.put(id, now);
        this.setDirty();
    }

    /** 強制ロードをやめる。 */
    private void releaseForcedChunks(ServerLevel level, UUID id) {
        Long prev = this.forced.remove(id);
        if (prev != null) {
            setForced(level, new net.minecraft.world.level.ChunkPos(prev), false, id);
            this.setDirty();
        }
    }

    /**
     * 3x3 チャンクの強制ロードを入り切りする。
     * ★他の列車が同じチャンクを使っているうちは外さない。
     */
    private void setForced(ServerLevel level, net.minecraft.world.level.ChunkPos center,
                           boolean add, UUID self) {
        for (int dx = -FORCE_RADIUS; dx <= FORCE_RADIUS; dx++) {
            for (int dz = -FORCE_RADIUS; dz <= FORCE_RADIUS; dz++) {
                int cx = center.x + dx;
                int cz = center.z + dz;
                if (!add && this.usedByOther(cx, cz, self)) {
                    continue;
                }
                level.setChunkForced(cx, cz, add);
            }
        }
    }

    /** 別の自動運転列車がそのチャンクを使っているか。 */
    private boolean usedByOther(int cx, int cz, UUID self) {
        for (Map.Entry<UUID, Long> e : this.forced.entrySet()) {
            if (e.getKey().equals(self)) {
                continue;
            }
            net.minecraft.world.level.ChunkPos other = new net.minecraft.world.level.ChunkPos(e.getValue());
            if (Math.abs(other.x - cx) <= FORCE_RADIUS && Math.abs(other.z - cz) <= FORCE_RADIUS) {
                return true;
            }
        }
        return false;
    }


    public static AutoDriveState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(AutoDriveState::new, AutoDriveState::load), NAME);
    }

    public AutoDriveState() {
    }

    private static AutoDriveState load(CompoundTag tag, HolderLookup.Provider provider) {
        AutoDriveState state = new AutoDriveState();
        ListTag list = tag.getList("Trains", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            net.minecraft.nbt.CompoundTag c = list.getCompound(i);
            try {
                UUID id = UUID.fromString(c.getString("Id"));
                state.trains.add(id);
                java.util.List<RouteStop> stops = new ArrayList<>();
                long[] positions = c.getLongArray("Stops");
                int[] doors = c.getIntArray("Doors");
                for (int j = 0; j < positions.length; j++) {
                    stops.add(new RouteStop(net.minecraft.core.BlockPos.of(positions[j]),
                            j < doors.length ? doors[j] : 0));
                }
                state.routes.put(id, stops);
                state.dwellTicks.put(id, c.contains("Dwell") ? c.getInt("Dwell") : 200);
                state.rollsigns.put(id, c.getInt("Rollsign"));
                if (c.contains("Forced")) {
                    state.forced.put(id, c.getLong("Forced"));
                }
            } catch (IllegalArgumentException ignored) {
                //壊れた UUID は捨てる
            }
        }
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        this.trains.forEach(id -> {
            net.minecraft.nbt.CompoundTag c = new net.minecraft.nbt.CompoundTag();
            c.putString("Id", id.toString());
            java.util.List<RouteStop> stops = this.stopsOf(id);
            c.putLongArray("Stops", stops.stream().mapToLong(st -> st.pos().asLong()).toArray());
            c.putIntArray("Doors", stops.stream().mapToInt(RouteStop::door).toArray());
            c.putInt("Dwell", this.dwellOf(id));
            c.putInt("Rollsign", this.rollsignOf(id));
            Long f = this.forced.get(id);
            if (f != null) {
                c.putLong("Forced", f);
            }
            list.add(c);
        });
        tag.put("Trains", list);
        return tag;
    }

    public boolean isEnabled(UUID id) {
        return this.trains.contains(id);
    }

    /**
     * 自動運転を入れる。運転士の乗務とチャンクローダー ON もここで行う。
     *
     * @param stops 停まる駅 (駅列車ブロック) の座標。通過駅は含めない
     */
    public void enable(ServerLevel level, EntityTrainBase train, java.util.List<RouteStop> stops,
                       int dwell, int rollsign) {
        this.trains.add(train.getUUID());
        this.routes.put(train.getUUID(), java.util.List.copyOf(stops));
        this.dwellTicks.put(train.getUUID(), Math.max(0, dwell));
        this.rollsigns.put(train.getUUID(), Math.max(0, Math.min(127, rollsign)));
        this.drivers.put(train.getUUID(), new AutoDriver());
        AutoDriver.prepare(level, train, rollsign);
        this.updateForcedChunks(level, train.getUUID(),
                new net.minecraft.world.level.ChunkPos(train.blockPosition()));
        this.setDirty();
    }

    /** 停車駅を持たない (どこにも停まらない) 自動運転。 */
    public void enable(ServerLevel level, EntityTrainBase train) {
        this.enable(level, train, java.util.List.of(), 200, 0);
    }

    /** その列車の停車時間 (tick)。 */
    public int dwellOf(UUID id) {
        return this.dwellTicks.getOrDefault(id, 200);
    }

    /** その列車の方向幕番号。 */
    public int rollsignOf(UUID id) {
        return this.rollsigns.getOrDefault(id, 0);
    }

    /** その列車の停車駅。 */
    public java.util.List<RouteStop> stopsOf(UUID id) {
        return this.routes.getOrDefault(id, java.util.List.of());
    }

    /** 自動運転を切る。運転士を降ろしてチャンクローダーも OFF にする。 */
    public void disable(ServerLevel level, EntityTrainBase train) {
        this.trains.remove(train.getUUID());
        this.drivers.remove(train.getUUID());
        this.routes.remove(train.getUUID());
        this.dwellTicks.remove(train.getUUID());
        this.rollsigns.remove(train.getUUID());
        this.releaseForcedChunks(level, train.getUUID());
        AutoDriver.release(level, train);
        this.setDirty();
    }

    /** 毎 tick。DRIVE_INTERVAL おきに全編成を運転する。 */
    public void tick(ServerLevel level) {
        if (this.trains.isEmpty() || level.getGameTime() % DRIVE_INTERVAL != 0L) {
            return;
        }
        List<UUID> gone = null;
        for (UUID id : this.trains) {
            Entity entity = level.getEntity(id);
            if (entity == null) {
                //まだ読み込まれていない。強制ロードは張ったままにしておく
                //(張り直しは entity が見つかってから)
                continue;
            }
            if (!(entity instanceof EntityTrainBase train) || train.isRemoved()) {
                if (gone == null) {
                    gone = new ArrayList<>();
                }
                gone.add(id);
                continue;
            }
            //列車が動いたら強制ロードも一緒に動かす
            this.updateForcedChunks(level, id, new net.minecraft.world.level.ChunkPos(train.blockPosition()));
            this.drivers.computeIfAbsent(id, k -> new AutoDriver())
                    .drive(level, train, this.stopsOf(id), this.dwellOf(id), this.rollsignOf(id));
        }
        if (gone != null) {
            gone.forEach(id -> {
                this.trains.remove(id);
                this.drivers.remove(id);
                this.routes.remove(id);
                this.dwellTicks.remove(id);
                this.rollsigns.remove(id);
                this.releaseForcedChunks(level, id);
            });
            this.setDirty();
        }
    }
}
