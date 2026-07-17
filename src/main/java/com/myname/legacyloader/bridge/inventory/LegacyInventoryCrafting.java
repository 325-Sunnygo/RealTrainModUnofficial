package com.myname.legacyloader.bridge.inventory;

import net.minecraft.world.item.ItemStack;

/**
 * 1.7.10 の {@code net.minecraft.inventory.InventoryCrafting} の代役。
 * width×height のグリッドに ItemStack を保持する (レシピ照合に使う)。
 */
public class LegacyInventoryCrafting implements LegacyInventory {

    private final ItemStack[] stacks;
    public final int width;
    public final int height;

    public LegacyInventoryCrafting(int width, int height) {
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
        this.stacks = new ItemStack[this.width * this.height];
        java.util.Arrays.fill(this.stacks, ItemStack.EMPTY);
    }

    // 1.7.10: InventoryCrafting(Container eventHandler, int width, int height)
    public LegacyInventoryCrafting(Object eventHandler, int width, int height) {
        this(width, height);
    }

    @Override
    public int func_70302_i_() {
        return stacks.length;
    }

    @Override
    public ItemStack func_70301_a(int slot) {
        return slot >= 0 && slot < stacks.length ? stacks[slot] : ItemStack.EMPTY;
    }

    @Override
    public void func_70299_a(int slot, ItemStack stack) {
        if (slot >= 0 && slot < stacks.length) {
            stacks[slot] = stack == null ? ItemStack.EMPTY : stack;
        }
    }

    @Override
    public ItemStack func_70298_a(int slot, int amount) {
        if (slot < 0 || slot >= stacks.length || stacks[slot] == null || stacks[slot].isEmpty()) {
            return ItemStack.EMPTY;
        }
        return stacks[slot].split(amount);
    }

    @Override
    public ItemStack func_70304_b(int slot) {
        if (slot < 0 || slot >= stacks.length) {
            return ItemStack.EMPTY;
        }
        ItemStack s = stacks[slot];
        stacks[slot] = ItemStack.EMPTY;
        return s;
    }

    // 1.7.10: getStackInRowAndColumn(int row, int column)
    public ItemStack func_70463_b(int row, int column) {
        if (row < 0 || row >= width || column < 0 || column >= height) {
            return ItemStack.EMPTY;
        }
        return func_70301_a(row + column * width);
    }

    public ItemStack getStackInRowAndColumn(int row, int column) {
        return func_70463_b(row, column);
    }
}
