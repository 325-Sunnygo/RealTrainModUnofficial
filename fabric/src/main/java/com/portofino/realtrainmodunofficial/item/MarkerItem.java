package com.portofino.realtrainmodunofficial.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

public class MarkerItem extends BlockItem {
    private final boolean diagonal;

    public MarkerItem(Block block, boolean diagonal) {
        super(block, new Properties());
        this.diagonal = diagonal;
    }

    public boolean isDiagonal() {
        return diagonal;
    }
}
