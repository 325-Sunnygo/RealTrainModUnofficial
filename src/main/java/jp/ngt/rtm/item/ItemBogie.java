package jp.ngt.rtm.item;

import net.minecraft.world.item.Item;

/** 台車アイテム。本家 jp.ngt.rtm.item.ItemBogie の移植。 */
public class ItemBogie extends Item {
    public ItemBogie() {
        super(new Properties().stacksTo(16));
    }
}
