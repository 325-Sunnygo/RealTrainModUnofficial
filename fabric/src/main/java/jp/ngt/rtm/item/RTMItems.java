package jp.ngt.rtm.item;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 本家 jp.ngt.rtm.RTMItem のアイテム登録 (段階的移植、1.21 DeferredRegister 版)。
 */
public final class RTMItems {
    public static final DeferredRegister.Items REGISTER = DeferredRegister.createItems(RealTrainModUnofficial.MODID);

    //本家: RTMItem.itemLargeRail
    public static final DeferredItem<ItemRail> ITEM_LARGE_RAIL =
            REGISTER.register("item_large_rail", () -> new ItemRail(new Item.Properties()));

    private RTMItems() {
    }
}
