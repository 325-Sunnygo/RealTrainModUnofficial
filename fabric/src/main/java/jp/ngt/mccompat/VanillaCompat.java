package jp.ngt.mccompat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * レシーバがバニラの実クラスで、シムで包むことも継承することもできない MCP 名メソッドの受け皿。
 * PackScriptSource.remapVanillaOnlyMethods が
 * x.func_xxxxx(...) → VanillaCompat.func_xxxxx(x, ...) へ書き換えて呼ぶ。
 */
public final class VanillaCompat {

    private VanillaCompat() {
    }

    /**
     * func_177967_a = BlockPos.offset(EnumFacing, n)。
     * NGTO Builder.zip!.../Wire/render_Wire.js:539  blockPos.func_177967_a(side, heightOffset)
     */
    public static BlockPos func_177967_a(Object pos, Object facing, int n) {
        BlockPos p = asPos(pos);
        Direction d = asDirection(facing);
        if (p == null) {
            return null;
        }
        return d == null ? p : p.relative(d, n);
    }

    /**
     * func_176745_a = EnumFacing.getIndex (DOWN=0, UP=1, NORTH=2, SOUTH=3, WEST=4, EAST=5)。
     * 1.21 の Direction.get3DDataValue と同じ並び。
     */
    public static int func_176745_a(Object facing) {
        Direction d = asDirection(facing);
        return d == null ? 0 : d.get3DDataValue();
    }

    /**
     * func_173_d = ItemBlock.getBlock。
     * NGTO Builder.zip!.../Liner/render_Liner.js:823  stack.func_77973_b.func_173_d
     */
    public static Block func_179223_d(Object item) {
        if (item instanceof BlockItem bi) {
            return bi.getBlock();
        }
        if (item instanceof Block b) {
            return b;
        }
        return null;
    }

    /**
     * func_174878_a = TileEntity.setPos。
     * 1.21 の BlockEntity#worldPosition は final なので、
     * 本家と同じ「位置を差し替える」操作は再現できない。
     */
    public static void func_174878_a(Object tile, Object pos) {
        if (!(tile instanceof BlockEntity be)) {
            return;
        }
        BlockPos p = asPos(pos);
        if (p == null || p.equals(be.getBlockPos())) {
            return;
        }
        // 1.21 では worldPosition が final のため差し替え不可。位置違いは黙って無視する
        // (本家の用途は「NBT 復元した仮 TE に座標を入れる」で、RTMU では生成時に確定している)。
    }

    private static BlockPos asPos(Object o) {
        if (o instanceof BlockPos p) {
            return p;
        }
        return null;
    }

    private static Direction asDirection(Object o) {
        if (o instanceof Direction d) {
            return d;
        }
        if (o instanceof Number n) {
            return Direction.from3DDataValue(n.intValue());
        }
        return null;
    }
}
