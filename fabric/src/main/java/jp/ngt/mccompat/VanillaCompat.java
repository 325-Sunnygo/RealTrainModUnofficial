package jp.ngt.mccompat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * レシーバが<b>バニラの実クラス</b>で、シムで包むことも継承することもできない MCP 名メソッドの受け皿。
 *
 * <p>{@code PackScriptSource.remapVanillaOnlyMethods} が
 * {@code x.func_xxxxx(...)} → {@code VanillaCompat.func_xxxxx(x, ...)} へ書き換えて呼ぶ。
 * ここに無いと Nashorn は「メソッドが無い」で TypeError になるか、
 * FQN 経由なら<b>無音で JavaPackage を返して</b>静かに壊れる。
 */
public final class VanillaCompat {

    private VanillaCompat() {
    }

    /**
     * func_177967_a = BlockPos.offset(EnumFacing, n)。
     * <pre>NGTO Builder.zip!.../Wire/render_Wire.js:539  blockPos.func_177967_a(side, heightOffset)</pre>
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
     * 1.21 の {@code Direction.get3DDataValue()} と同じ並び。
     * <pre>NGTO Builder.zip!.../Wire/render_Wire.js:555  side.func_176745_a()</pre>
     */
    public static int func_176745_a(Object facing) {
        Direction d = asDirection(facing);
        return d == null ? 0 : d.get3DDataValue();
    }

    /**
     * func_179223_d = ItemBlock.getBlock。
     * <pre>NGTO Builder.zip!.../Liner/render_Liner.js:823  stack.func_77973_b().func_179223_d()</pre>
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
     * 1.21 の {@code BlockEntity#worldPosition} は final なので、
     * 本家と同じ「位置を差し替える」操作は再現できない。
     * スクリプト側は NBT から復元した仮 TileEntity に座標を入れる用途で使うため、
     * 位置が既に一致していれば成功、違えば無視する (例外は投げない)。
     * <pre>NGTO Builder.zip!.../Liner/server_Liner.js:425  tile.func_174878_a(new BlockPos(x,y,z))</pre>
     */
    public static void func_174878_a(Object tile, Object pos) {
        if (!(tile instanceof BlockEntity be)) {
            return;
        }
        BlockPos p = asPos(pos);
        if (p == null || p.equals(be.getBlockPos())) {
            return;
        }
        //1.21 では worldPosition が final のため差し替え不可。位置違いは黙って無視する
        //(本家の用途は「NBT 復元した仮 TE に座標を入れる」で、RTMU では生成時に確定している)。
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
