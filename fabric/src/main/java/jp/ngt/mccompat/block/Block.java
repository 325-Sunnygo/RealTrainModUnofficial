package jp.ngt.mccompat.block;

import net.minecraft.core.registries.BuiltInRegistries;

/**
 * 1.7.10 net.minecraft.block.Block の static ユーティリティ互換。
 * (スクリプトの instanceof には実 Block クラスがそのまま使えるため、
 * ここでは static メソッドのみ提供する)
 */
public final class Block {
    private Block() {
    }

    /** func_149634_a = getBlockFromItem */
    public static net.minecraft.world.level.block.Block func_149634_a(Object item) {
        if (item instanceof net.minecraft.world.item.Item i) {
            return net.minecraft.world.level.block.Block.byItem(i);
        }
        return net.minecraft.world.level.block.Blocks.AIR;
    }

    /** func_149682_b = getIdFromBlock */
    public static int func_149682_b(Object block) {
        if (block instanceof net.minecraft.world.level.block.Block b) {
            return BuiltInRegistries.BLOCK.getId(b);
        }
        return 0;
    }

    /** func_149729_e = getBlockById */
    public static net.minecraft.world.level.block.Block func_149729_e(int id) {
        return BuiltInRegistries.BLOCK.byId(id);
    }
}
