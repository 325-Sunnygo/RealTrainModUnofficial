package com.portofino.realtrainmodunofficial.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * IC カード (本家 icCard)。
 *
 * <p>見た目を<b>常にきらめかせる</b> (エンチャントの光沢)。
 * 実際にエンチャントを付けているわけではないので、
 * エンチャント一覧には何も出ないし、金床や砥石の対象にもならない。
 */
public class IcCardItem extends Item {

    public IcCardItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
