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
 * 置かれている列車運転スポナーの一覧。自動運転装置の画面に出すために持つ。
 *
 * <p>チャンクが読み込まれていないスポナーも一覧に出したいので、
 * ブロックエンティティを走査するのではなくワールドに保存する。
 */
public final class DispatcherRegistry extends SavedData {

    private static final String NAME = "rtmuautodrive_dispatchers";

    private final Map<BlockPos, String> dispatchers = new LinkedHashMap<>();

    public static DispatcherRegistry get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(DispatcherRegistry::new, DispatcherRegistry::load), NAME);
    }

    public DispatcherRegistry() {
    }

    private static DispatcherRegistry load(CompoundTag tag, HolderLookup.Provider provider) {
        DispatcherRegistry reg = new DispatcherRegistry();
        ListTag list = tag.getList("Dispatchers", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            reg.dispatchers.put(BlockPos.of(c.getLong("Pos")), c.getString("Name"));
        }
        return reg;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        this.dispatchers.forEach((pos, name) -> {
            CompoundTag c = new CompoundTag();
            c.putLong("Pos", pos.asLong());
            c.putString("Name", name);
            list.add(c);
        });
        tag.put("Dispatchers", list);
        return tag;
    }

    public Map<BlockPos, String> all() {
        return java.util.Collections.unmodifiableMap(this.dispatchers);
    }

    public void setName(BlockPos pos, String name) {
        String prev = this.dispatchers.put(pos.immutable(), name == null ? "" : name);
        if (prev == null || !prev.equals(name)) {
            this.setDirty();
        }
    }

    public void remove(BlockPos pos) {
        if (this.dispatchers.remove(pos) != null) {
            this.setDirty();
        }
    }
}
