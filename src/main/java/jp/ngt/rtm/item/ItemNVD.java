package jp.ngt.rtm.item;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialArmorMaterials;
import net.minecraft.world.item.ArmorItem;

/**
 * 暗視装置。本家 {@code jp.ngt.rtm.item.ItemNVD} の移植。
 * 本家は鉄と同じ性能の頭防具で、着用時の絵だけ差し替えている。
 */
public class ItemNVD extends ArmorItem {
    public ItemNVD() {
        super(RealTrainModUnofficialArmorMaterials.nvd(), ArmorItem.Type.HELMET,
            new Properties().durability(ArmorItem.Type.HELMET.getDurability(
                RealTrainModUnofficialArmorMaterials.NVD_DURABILITY_MULTIPLIER)));
    }
}
