package jp.ngt.mccompat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/** レシーバがバニラの実クラスで、シムで包むことも継承することもできない MCP 名メソッドの受け皿。 */
public final class VanillaCompat {

    private VanillaCompat() {
    }

    /** func_177967_a = BlockPos.offset(EnumFacing, n)。 */
    public static BlockPos func_177967_a(Object pos, Object facing, int n) {
        BlockPos p = asPos(pos);
        Direction d = asDirection(facing);
        if (p == null) {
            return null;
        }
        return d == null ? p : p.relative(d, n);
    }

    /** func_176745_a = EnumFacing.getIndex (DOWN=0, UP=1… */
    public static int func_176745_a(Object facing) {
        Direction d = asDirection(facing);
        return d == null ? 0 : d.get3DDataValue();
    }

    /** func_173_d = ItemBlock.getBlock。 */
    public static Block func_179223_d(Object item) {
        if (item instanceof BlockItem bi) {
            return bi.getBlock();
        }
        if (item instanceof Block b) {
            return b;
        }
        return null;
    }

    /** func_174878_a = TileEntity.setPos。 */
    public static void func_174878_a(Object tile, Object pos) {
        if (!(tile instanceof BlockEntity be)) {
            return;
        }
        BlockPos p = asPos(pos);
        if (p == null || p.equals(be.getBlockPos())) {
            return;
        }
        // 1.21 では worldPosition が final のため差し替え不可。
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
