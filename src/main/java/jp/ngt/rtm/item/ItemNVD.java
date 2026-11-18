package jp.ngt.rtm.item;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialArmorMaterials;
import net.minecraft.world.item.ArmorItem;

/** 暗視装置。本家 jp.ngt.rtm.item.ItemNVD の移植。 */
public class ItemNVD extends ArmorItem {
    public ItemNVD() {
        super(RealTrainModUnofficialArmorMaterials.nvd(), ArmorItem.Type.HELMET,
            new Properties().durability(ArmorItem.Type.HELMET.getDurability(
                RealTrainModUnofficialArmorMaterials.NVD_DURABILITY_MULTIPLIER)));
    }
}
