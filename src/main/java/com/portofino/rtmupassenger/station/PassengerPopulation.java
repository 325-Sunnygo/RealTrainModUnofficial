package com.portofino.rtmupassenger.station;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 生存中の乗客 NPC の UUID 集合 (SavedData)。<b>ワールド全体の乗客数上限を厳密に守る</b>ために使う。
 * <p>ロード済みエンティティを数える方式だと、チャンク外 (アンロード中) で待機している乗客が
 * 数えられず上限を超えて湧いてしまう。スポーン時に登録し、本当に消えた時
 * (討伐・discard = {@code RemovalReason.shouldDestroy()}) にだけ解除することで、
 * チャンクのロード状態に関係なく総数を正しく追跡する (チャンクアンロードでは解除しない)。
 */
public final class PassengerPopulation extends SavedData {

    private static final String NAME = "rtmupassenger_population";

    private final Set<UUID> alive = new HashSet<>();

    public static PassengerPopulation get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PassengerPopulation::new, PassengerPopulation::load), NAME);
    }

    public PassengerPopulation() {
    }

    private static PassengerPopulation load(CompoundTag tag, HolderLookup.Provider provider) {
        PassengerPopulation pop = new PassengerPopulation();
        ListTag list = tag.getList("Ids", Tag.TAG_INT_ARRAY);
        for (Tag t : list) {
            try {
                pop.alive.add(NbtUtils.loadUUID(t));
            } catch (Exception ignored) {
            }
        }
        return pop;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (UUID id : this.alive) {
            list.add(NbtUtils.createUUID(id));
        }
        tag.put("Ids", list);
        return tag;
    }

    public int size() {
        return this.alive.size();
    }

    public void add(UUID id) {
        if (this.alive.add(id)) {
            this.setDirty();
        }
    }

    public void remove(UUID id) {
        if (this.alive.remove(id)) {
            this.setDirty();
        }
    }
}
