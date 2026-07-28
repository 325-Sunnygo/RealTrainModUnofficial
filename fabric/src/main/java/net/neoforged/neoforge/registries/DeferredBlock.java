package net.neoforged.neoforge.registries;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

public class DeferredBlock<T extends Block> extends DeferredHolder<Block, T> {
    protected DeferredBlock(ResourceLocation id, T value) {
        super(id, value);
    }

    public static <T extends Block> DeferredBlock<T> createBlock(ResourceLocation id, T value) {
        return new DeferredBlock<>(id, value);
    }
}
