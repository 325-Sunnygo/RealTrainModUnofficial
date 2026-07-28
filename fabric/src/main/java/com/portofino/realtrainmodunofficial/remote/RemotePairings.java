package com.portofino.realtrainmodunofficial.remote;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/**
 * リモコン (RemoteItem) で結んだブロックのペア一覧 (SavedData)。
 * 各ペアは 2 つのブロック座標。片方がレッドストーンを受ける/出すと、もう片方が無線給電される
 * ({@link RemoteRedstoneHandler} が毎 tick 反映)。どちらかのブロックが壊れたらペアは自動解除。
 */
public final class RemotePairings extends SavedData {

    private static final String NAME = "rtmu_remote_pairings";

    /** {a.asLong(), b.asLong()} のリスト。 */
    private final List<long[]> pairs = new ArrayList<>();

    public static RemotePairings get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                //★バニラの Factory は引数 3 つ。3 つ目 (変換種別) は RTMU 独自の中身なので null。
                //  null を許すのは DimensionDataStorageMixin。
                new SavedData.Factory<>(RemotePairings::new, RemotePairings::load, null), NAME);
    }

    public RemotePairings() {
    }

    private static RemotePairings load(CompoundTag tag, HolderLookup.Provider provider) {
        RemotePairings rp = new RemotePairings();
        ListTag list = tag.getList("Pairs", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            rp.pairs.add(new long[]{c.getLong("A"), c.getLong("B")});
        }
        return rp;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (long[] p : this.pairs) {
            CompoundTag c = new CompoundTag();
            c.putLong("A", p[0]);
            c.putLong("B", p[1]);
            list.add(c);
        }
        tag.put("Pairs", list);
        return tag;
    }

    /** ペアを追加 (既に同じ組があれば追加しない)。 */
    public void add(BlockPos a, BlockPos b) {
        long la = a.asLong();
        long lb = b.asLong();
        for (long[] p : this.pairs) {
            if ((p[0] == la && p[1] == lb) || (p[0] == lb && p[1] == la)) {
                return;
            }
        }
        this.pairs.add(new long[]{la, lb});
        setDirty();
    }

    /** 反復用のスナップショット。 */
    public List<long[]> snapshot() {
        return new ArrayList<>(this.pairs);
    }

    /** 指定のペアを削除する。 */
    public void remove(long[] pair) {
        if (this.pairs.remove(pair)) {
            setDirty();
        } else {
            //snapshot 経由だと参照が違うので値で消す
            this.pairs.removeIf(p -> p[0] == pair[0] && p[1] == pair[1]);
            setDirty();
        }
    }

    /** pos を含むペアを全削除し、消した数を返す。 */
    public int removeInvolving(BlockPos pos) {
        long l = pos.asLong();
        int before = this.pairs.size();
        this.pairs.removeIf(p -> p[0] == l || p[1] == l);
        int removed = before - this.pairs.size();
        if (removed > 0) {
            setDirty();
        }
        return removed;
    }

    public boolean isEmpty() {
        return this.pairs.isEmpty();
    }
}
