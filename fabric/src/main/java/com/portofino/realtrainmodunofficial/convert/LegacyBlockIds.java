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
 *
 * <p>1.7.10 / 1.12.2 のチャンクは、ブロックを<b>数値 ID</b> でしか持っていない。
 * その ID がどの MOD のどのブロックかは level.dat の中の対応表にしか無い:
 * <pre>
 *   FML > Registries > "minecraft:blocks" > ids = [ {K: "rtm:large_rail_base", V: 234}, ... ]
 * </pre>
 * (1.7.10 は FML > ModItemData = [ {K: "rtm:xxx", V: 234}, ... ] という古い形式)
 */
public final class LegacyBlockIds {

    private LegacyBlockIds() {
    }

    /**
     * ブロックの数値 ID → 登録名 ("rtm:large_rail_base" 等)。読めなければ空。
     */
    public static Map<Integer, String> read(Path worldDir) {
        Map<Integer, String> ids = new HashMap<>();
        try {
            CompoundTag root = NbtIo.readCompressed(worldDir.resolve("level.dat"), NbtAccounter.unlimitedHeap());
            CompoundTag fml = root.getCompound("FML");

            //1.12.2 形式
            CompoundTag registries = fml.getCompound("Registries");
            CompoundTag blocks = registries.getCompound("minecraft:blocks");
            readIdList(blocks.getList("ids", Tag.TAG_COMPOUND), ids);

            //1.7.10 形式 (ModItemData: ブロックとアイテムが混在するが、ブロック ID の範囲だけ使う)
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
     *
     * <p>旧 RTM のレール関連ブロック (土台 / コア / マーカー / 転車台) は、変換後も
     * <b>RTMU の同名ブロックとして残ってしまう</b>。しかし中身のブロックエンティティは
     * 旧 MOD のもので読み込めないため、空のまま「自分のコアが無い」と判断して自壊し、
     * 復元したレールまで巻き添えで壊す。チャンクの段階で消しておく。
     *
     * <p>レール以外の RTM ブロック (鋼材や装飾) は残す。RTMU に同名のものがあればそのまま活きる。
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
