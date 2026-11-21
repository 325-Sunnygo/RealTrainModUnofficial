package net.neoforged.neoforge.registries;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class DeferredItem<T extends Item> extends DeferredHolder<Item, T> {
    protected DeferredItem(ResourceLocation id, T value) {
        super(id, value);
    }

    public static <T extends Item> DeferredItem<T> createItem(ResourceLocation id, T value) {
        return new DeferredItem<>(id, value);
    }

    public ItemStack toStack() {
        return new ItemStack(get());
    }

    public ItemStack toStack(int count) {
        return new ItemStack(get(), count);
    }
}
