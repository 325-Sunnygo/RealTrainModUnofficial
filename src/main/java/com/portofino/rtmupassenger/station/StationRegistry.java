package com.portofino.rtmupassenger.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ワールドに置かれた駅ブロックの一覧 (位置 → タグビット + 出る人数) の SavedData。
 * 行き先抽選は時刻とタグの到着需要 (StationTag#destWeight) の重み付きで行う
 * (朝はオフィス街行きが多い、夕方は住宅街行きが多い、など)。
 */
public final class StationRegistry extends SavedData {

    private static final String NAME = "rtmupassenger_stations";

    /** 出る人数の既定値。以前はここが固定 6 だった (StationBlockEntity.MAX_WAITING)。 */
    public static final int DEFAULT_CAPACITY = 6;
    /** 出る人数の上限。1 駅に並べられる現実的な数。 */
    public static final int MAX_CAPACITY = 32;

    /**
     * 駅 1 つ分の設定。
     *
     * @param bits     タグビット (1 << StationTag.ordinal の OR)
     * @param capacity その駅で待たせる乗客の上限 (= 出る人数)。0 なら湧かせない (行き先専用駅)
     */
    public record Station(int bits, int capacity) {
    }

    /** 駅位置 → 設定。 */
    private final Map<BlockPos, Station> stations = new HashMap<>();

    /** 出る人数を有効範囲へ丸める。 */
    public static int clampCapacity(int capacity) {
        return capacity < 0 ? 0 : Math.min(capacity, MAX_CAPACITY);
    }

    public static StationRegistry get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(StationRegistry::new, StationRegistry::load), NAME);
    }

    public StationRegistry() {
    }

    private static StationRegistry load(CompoundTag tag, HolderLookup.Provider provider) {
        StationRegistry reg = new StationRegistry();
        if (tag.contains("StationList")) {
            ListTag list = tag.getList("StationList", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag c = list.getCompound(i);
                // 出る人数を持たない旧ワールドは、当時の固定値 (6) のまま読む
                int cap = c.contains("Cap") ? clampCapacity(c.getInt("Cap")) : DEFAULT_CAPACITY;
                reg.stations.put(BlockPos.of(c.getLong("Pos")),
                        new Station(c.getInt("Tags"), cap));
            }
        } else {
            // 旧形式 (タグ無しの位置配列) からの引き継ぎ
            for (long l : tag.getLongArray("Stations")) {
                reg.stations.put(BlockPos.of(l), new Station(0, DEFAULT_CAPACITY));
            }
        }
        return reg;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        this.stations.forEach((pos, station) -> {
            CompoundTag c = new CompoundTag();
            c.putLong("Pos", pos.asLong());
            c.putInt("Tags", station.bits());
            c.putInt("Cap", station.capacity());
            list.add(c);
        });
        tag.put("StationList", list);
        return tag;
    }

    public void add(BlockPos pos) {
        if (this.stations.putIfAbsent(pos.immutable(), new Station(0, DEFAULT_CAPACITY)) == null) {
            this.setDirty();
        }
    }

    public void remove(BlockPos pos) {
        if (this.stations.remove(pos) != null) {
            this.setDirty();
        }
    }

    public int count() {
        return this.stations.size();
    }

    /**
     * 登録されている駅ブロックの座標一覧 (読み取り専用)。
     * 自動運転アドオン (RTMU-AutoDrive) が「進行方向の先にある駅」を探すのに使う。
     */
    public java.util.Set<BlockPos> positions() {
        return java.util.Collections.unmodifiableSet(this.stations.keySet());
    }

    private Station station(BlockPos pos) {
        Station s = this.stations.get(pos);
        return s == null ? new Station(0, DEFAULT_CAPACITY) : s;
    }

    public int tagBits(BlockPos pos) {
        return this.station(pos).bits();
    }

    /**
     * その駅で待たせる乗客の上限 (= 出る人数)。0 なら湧かせない。
     * 駅ごとに変えられる (以前は全駅一律 6 だった)。
     */
    public int capacity(BlockPos pos) {
        return this.station(pos).capacity();
    }

    /** タグビットを直接設定する (駅設定 GUI から)。駅が未登録なら登録もする。 */
    public void setBits(BlockPos pos, int bits) {
        this.stations.put(pos.immutable(), new Station(bits, this.capacity(pos)));
        this.setDirty();
    }

    /** タグと出る人数をまとめて設定する (駅設定 GUI から)。駅が未登録なら登録もする。 */
    public void setStation(BlockPos pos, int bits, int capacity) {
        this.stations.put(pos.immutable(), new Station(bits, clampCapacity(capacity)));
        this.setDirty();
    }

    /** 駅の先頭タグ (表示用)。無ければ null。 */
    @Nullable
    public StationTag firstTag(BlockPos pos) {
        int bits = this.tagBits(pos);
        for (StationTag t : StationTag.values()) {
            if ((bits & (1 << t.ordinal())) != 0) {
                return t;
            }
        }
        return null;
    }

    /** タグを 1 つ先へ切り替える (なし→住宅街→…→観光→なし)。返り値は新しいタグ (null=なし)。 */
    @Nullable
    public StationTag cycleTag(BlockPos pos) {
        StationTag cur = this.firstTag(pos);
        StationTag[] values = StationTag.values();
        StationTag next = cur == null ? values[0]
                : (cur.ordinal() + 1 >= values.length ? null : values[cur.ordinal() + 1]);
        this.setBits(pos, next == null ? 0 : (1 << next.ordinal()));
        return next;
    }

    /**
     * from 以外の駅を、時刻の到着需要で重み付き抽選して返す。他に駅が無ければ null。
     * 朝はオフィス街/教育機関行きが選ばれやすく、夕方は住宅街行きが選ばれやすい。
     */
    @Nullable
    public BlockPos pickDestination(BlockPos from, float hour, RandomSource random) {
        List<BlockPos> cand = new ArrayList<>();
        List<Float> weights = new ArrayList<>();
        float total = 0.0F;
        for (Map.Entry<BlockPos, Station> e : this.stations.entrySet()) {
            if (e.getKey().equals(from)) {
                continue;
            }
            float w = Math.max(0.05F, StationTag.destWeight(e.getValue().bits(), hour));
            cand.add(e.getKey());
            weights.add(w);
            total += w;
        }
        if (cand.isEmpty()) {
            return null;
        }
        float r = random.nextFloat() * total;
        for (int i = 0; i < cand.size(); i++) {
            r -= weights.get(i);
            if (r <= 0.0F) {
                return cand.get(i);
            }
        }
        return cand.get(cand.size() - 1);
    }
}
