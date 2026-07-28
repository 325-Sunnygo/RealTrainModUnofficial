package com.portofino.realtrainmodunofficial.convert;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 旧ワールドの level.dat にある Forge (FML) のレジストリ表を読む。
 * 1.7.10 / 1.12.2 のチャンクは、ブロックを数値 ID でしか持っていない。
 */
public final class LegacyBlockIds {

    private LegacyBlockIds() {
    }

    /** ブロックの数値 ID → 登録名 ("rtm:large_rail_base" 等)。読めなければ空。 */
    public static Map<Integer, String> read(Path worldDir) {
        Map<Integer, String> ids = new HashMap<>();
        try {
            CompoundTag root = NbtIo.readCompressed(worldDir.resolve("level.dat"), NbtAccounter.unlimitedHeap());
            CompoundTag fml = root.getCompound("FML");

            // 1.12.2 形式
            CompoundTag registries = fml.getCompound("Registries");
            CompoundTag blocks = registries.getCompound("minecraft:blocks");
            readIdList(blocks.getList("ids", Tag.TAG_COMPOUND), ids);

            // 1.7.10 形式 (ModItemData: ブロックとアイテムが混在するが、ブロック ID の範囲だけ使う)
            if (ids.isEmpty()) {
                readIdList(fml.getList("ModItemData", Tag.TAG_COMPOUND), ids);
            }
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("[convert] level.dat のブロック ID 表が読めませんでした", e);
        }
        return ids;
    }

    private static void readIdList(ListTag list, Map<Integer, String> out) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String name = entry.contains("K") ? entry.getString("K") : entry.getString("ItemId");
            int id = entry.contains("V") ? entry.getInt("V") : entry.getInt("ItemType");
            if (!name.isBlank() && id > 0) {
                out.put(id, name);
            }
        }
    }

    /**
     * 取り除くべきブロックの数値 ID。
     * 旧 RTM のレール関連ブロック (土台 / コア / マーカー / 転車台) は、変換後も
     * RTMU の同名ブロックとして残ってしまう。
     */
    public static Set<Integer> railBlockIds(Map<Integer, String> ids) {
        Set<Integer> out = new HashSet<>();
        ids.forEach((id, name) -> {
            String n = name.toLowerCase(Locale.ROOT);
            if (!n.startsWith("rtm:")) {
                return;
            }
            if (n.contains("rail") || n.contains("marker") || n.contains("turntable") || n.contains("ballast")) {
                out.add(id);
            }
        });
        return out;
    }
}
