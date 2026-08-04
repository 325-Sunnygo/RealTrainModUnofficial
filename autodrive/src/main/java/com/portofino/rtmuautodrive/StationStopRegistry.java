package com.portofino.rtmuautodrive;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 置かれている駅列車ブロックの一覧。スポナーの停車/通過設定に出すために持つ。
 *
 * <p>チャンクが読み込まれていないスポナーも一覧に出したいので、
 * ブロックエンティティを走査するのではなくワールドに保存する。
 */
public final class StationStopRegistry extends SavedData {

    private static final String NAME = "rtmuautodrive_stations";

    private final Map<BlockPos, String> stations = new LinkedHashMap<>();

    public static StationStopRegistry get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(StationStopRegistry::new, StationStopRegistry::load), NAME);
    }

    public StationStopRegistry() {
    }

    private static StationStopRegistry load(CompoundTag tag, HolderLookup.Provider provider) {
        StationStopRegistry reg = new StationStopRegistry();
        ListTag list = tag.getList("Stations", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            reg.stations.put(BlockPos.of(c.getLong("Pos")), c.getString("Name"));
        }
        return reg;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        this.stations.forEach((pos, name) -> {
            CompoundTag c = new CompoundTag();
            c.putLong("Pos", pos.asLong());
            c.putString("Name", name);
            list.add(c);
        });
        tag.put("Stations", list);
        return tag;
    }

    public Map<BlockPos, String> all() {
        return java.util.Collections.unmodifiableMap(this.stations);
    }

    public void setName(BlockPos pos, String name) {
        String prev = this.stations.put(pos.immutable(), name == null ? "" : name);
        if (prev == null || !prev.equals(name)) {
            this.setDirty();
        }
    }

    public void remove(BlockPos pos) {
        if (this.stations.remove(pos) != null) {
            this.setDirty();
        }
    }
}
