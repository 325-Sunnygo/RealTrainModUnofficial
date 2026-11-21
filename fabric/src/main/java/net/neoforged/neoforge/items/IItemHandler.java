package net.neoforged.neoforge.items;
import net.minecraft.world.item.ItemStack;
public interface IItemHandler { int getSlots(); ItemStack getStackInSlot(int slot); }
