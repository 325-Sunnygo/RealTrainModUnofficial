package jp.ngt.rtm.entity.npc;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * 商売メニューの 1 行。本家 {@code MenuEntry} の移植 (NBT 形式も同じ item/price)。
 */
public class MenuEntry {
    public final ItemStack item;
    public final int price;
    public final int maxCount;

    public MenuEntry(ItemStack item, int price) {
        this.item = item;
        this.price = price;
        this.maxCount = Math.max(1, 64 / Math.max(1, item.getCount()));
    }

    public CompoundTag writeToNBT(HolderLookup.Provider provider) {
        CompoundTag nbt = new CompoundTag();
        nbt.put("item", this.item.save(provider, new CompoundTag()));
        nbt.putInt("price", this.price);
        return nbt;
    }

    public static MenuEntry readFromNBT(CompoundTag nbt, HolderLookup.Provider provider) {
        ItemStack item = ItemStack.parseOptional(provider, nbt.getCompound("item"));
        if (item.isEmpty()) {
            return null;
        }
        return new MenuEntry(item, nbt.getInt("price"));
    }
}
