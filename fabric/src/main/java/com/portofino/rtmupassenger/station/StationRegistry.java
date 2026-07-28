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
 * ワールドに置かれた駅ブロックの一覧 (位置 → タグビット) の SavedData。
 * 行き先抽選は時刻とタグの到着需要 (StationTag#destWeight) の重み付きで行う
 * (朝はオフィス街行きが多い、夕方は住宅街行きが多い、など)。
 */
public final class StationRegistry extends SavedData {

    private static final String NAME = "rtmupassenger_stations";

    /** 駅位置 → タグビット (1 << StationTag.ordinal の OR)。 */
    private final Map<BlockPos, Integer> stations = new HashMap<>();

    public static StationRegistry get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                // ★バニラの Factory は引数 3 つ。3 つ目 (変換種別) は RTMU 独自の中身なので null。
                // null を許すのは DimensionDataStorageMixin。
                new SavedData.Factory<>(StationRegistry::new, StationRegistry::load, null), NAME);
    }

    public StationRegistry() {
    }

    private static StationRegistry load(CompoundTag tag, HolderLookup.Provider provider) {
        StationRegistry reg = new StationRegistry();
        if (tag.contains("StationList")) {
            ListTag list = tag.getList("StationList", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag c = list.getCompound(i);
                reg.stations.put(BlockPos.of(c.getLong("Pos")), c.getInt("Tags"));
            }
        } else {
            // 旧形式 (タグ無しの位置配列) からの引き継ぎ
            for (long l : tag.getLongArray("Stations")) {
                reg.stations.put(BlockPos.of(l), 0);
            }
        }
        return reg;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        this.stations.forEach((pos, bits) -> {
            CompoundTag c = new CompoundTag();
            c.putLong("Pos", pos.asLong());
            c.putInt("Tags", bits);
            list.add(c);
        });
        tag.put("StationList", list);
        return tag;
    }

    public void add(BlockPos pos) {
        if (this.stations.putIfAbsent(pos.immutable(), 0) == null) {
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

    public int tagBits(BlockPos pos) {
        return this.stations.getOrDefault(pos, 0);
    }

    /** タグビットを直接設定する (駅設定 GUI から)。駅が未登録なら登録もする。 */
    public void setBits(BlockPos pos, int bits) {
        this.stations.put(pos.immutable(), bits);
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
        this.stations.put(pos.immutable(), next == null ? 0 : (1 << next.ordinal()));
        this.setDirty();
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
        for (Map.Entry<BlockPos, Integer> e : this.stations.entrySet()) {
            if (e.getKey().equals(from)) {
                continue;
            }
            float w = Math.max(0.05F, StationTag.destWeight(e.getValue(), hour));
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
