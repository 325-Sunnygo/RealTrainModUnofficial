package jp.ngt.mccompat.block;

import net.minecraft.core.registries.BuiltInRegistries;

/** 1.7.10 net.minecraft.block.Block の static ユーティリティ… */
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

    /** func_149742_c = canPlaceBlockAt(world, x, y, z)。 */
    public static boolean func_149742_c(Object block, Object world, double x, double y, double z) {
        net.minecraft.world.level.block.Block b = asBlock(block);
        net.minecraft.world.level.Level level = unwrapLevel(world);
        if (b == null || level == null) {
            return false;
        }
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(
                net.minecraft.util.Mth.floor(x), net.minecraft.util.Mth.floor(y), net.minecraft.util.Mth.floor(z));
        try {
            return b.defaultBlockState().canSurvive(level, pos);
        } catch (Throwable t) {
            return false;
        }
    }

    /** isLeaves (1.7.10 の MCP 名) = 葉ブロックか。 */
    public static boolean isLeaves(Object block, Object world, double x, double y, double z) {
        net.minecraft.world.level.block.Block b = asBlock(block);
        return b != null && b.defaultBlockState().is(net.minecraft.tags.BlockTags.LEAVES);
    }

    private static net.minecraft.world.level.block.Block asBlock(Object block) {
        return block instanceof net.minecraft.world.level.block.Block b ? b : null;
    }

    private static net.minecraft.world.level.Level unwrapLevel(Object world) {
        if (world instanceof jp.ngt.mccompat.WorldCompat w) {
            return w.getLevel();
        }
        if (world instanceof net.minecraft.world.level.Level l) {
            return l;
        }
        return null;
    }
}
