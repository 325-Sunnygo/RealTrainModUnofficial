package com.portofino.realtrainmodunofficial.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** IC カード (本家 icCard)。 見た目を常にきらめかせる (エンチャントの光沢)。 */
public class IcCardItem extends Item {

    public IcCardItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
