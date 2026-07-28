package jp.ngt.ngtlib.block;

import jp.ngt.mccompat.MovingObjectPosition;
import jp.ngt.mccompat.PlayerCompat;
import jp.ngt.mccompat.WorldCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 本家 jp.ngt.ngtlib.block.BlockUtil のスクリプト互換 (SRB3/NGTO Builder が使用)。
 * world 引数は WorldCompat / Level のどちらでも受ける。
 */
@SuppressWarnings("unused")
public final class BlockUtil {
    private BlockUtil() {
    }

    public static Level toLevel(Object world) {
        if (world instanceof WorldCompat wc) {
            return wc.getLevel();
        }
        if (world instanceof Level l) {
            return l;
        }
        return null;
    }

    public static boolean setBlock(Object world, double x, double y, double z, Object block, int meta, int flag) {
        Level level = toLevel(world);
        if (level == null || !(block instanceof Block b)) {
            return false;
        }
        return level.setBlock(new BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z)), b.defaultBlockState(), flag);
    }

    public static Block getBlock(Object world, double x, double y, double z) {
        Level level = toLevel(world);
        if (level == null) {
            return net.minecraft.world.level.block.Blocks.AIR;
        }
        return level.getBlockState(new BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z))).getBlock();
    }

    public static int getMetadata(Object world, double x, double y, double z) {
        return 0;
    }

    public static net.minecraft.world.level.block.entity.BlockEntity getTileEntity(Object world, double x, double y, double z) {
        Level level = toLevel(world);
        if (level == null) {
            return null;
        }
        return level.getBlockEntity(new BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z)));
    }

    /**
     * 本家 getMOPFromPlayer(EntityPlayer, distance, liquid): 視線レイキャスト。
     * player は PlayerCompat / 実 Player のどちらでも受ける。
     */
    public static MovingObjectPosition getMOPFromPlayer(Object player, double distance, boolean liquid) {
        Player p = PlayerCompat.unwrap(player);
        if (p == null) {
            return null;
        }
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(distance));
        HitResult hit = p.level().clip(new ClipContext(eye, end,
                ClipContext.Block.OUTLINE,
                liquid ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, p));
        return MovingObjectPosition.of(hit);
    }

    /** 本家getConnectedBlock: 6方向の隣接ブロック(+x,-x,+y,-y,+z,-z)。 */
    public static Block[] getConnectedBlock(Object world, int x, int y, int z) {
        return new Block[]{
            getBlock(world, x + 1, y, z), getBlock(world, x - 1, y, z),
            getBlock(world, x, y + 1, z), getBlock(world, x, y - 1, z),
            getBlock(world, x, y, z + 1), getBlock(world, x, y, z - 1)};
    }

    /** 本家getConnectedBlockAndMetadata。 */
    public static Object[][] getConnectedBlockAndMetadata(Object world, int x, int y, int z) {
        return new Object[][]{
            {getBlock(world, x + 1, y, z), getMetadata(world, x + 1, y, z)},
            {getBlock(world, x - 1, y, z), getMetadata(world, x - 1, y, z)},
            {getBlock(world, x, y + 1, z), getMetadata(world, x, y + 1, z)},
            {getBlock(world, x, y - 1, z), getMetadata(world, x, y - 1, z)},
            {getBlock(world, x, y, z + 1), getMetadata(world, x, y, z + 1)},
            {getBlock(world, x, y, z - 1), getMetadata(world, x, y, z - 1)}};
    }

    /** 本家isConnectedBlock: 各方向が指定ブロック群のどれかか。 */
    public static boolean[] isConnectedBlock(Object world, Block[] blocks, int x, int y, int z) {
        boolean[] b0 = new boolean[6];
        Block[] connected = getConnectedBlock(world, x, y, z);
        for (int i = 0; i < 6; i++) {
            for (Block block : blocks) {
                if (connected[i] == block) {
                    b0[i] = true;
                    break;
                }
            }
        }
        return b0;
    }

    /** 本家isConnectedSolid: 各方向の面がソリッドか。 */
    public static boolean[] isConnectedSolid(Object world, int x, int y, int z) {
        Level level = toLevel(world);
        if (level == null) {
            return new boolean[6];
        }
        int[][] d = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        net.minecraft.core.Direction[] f = {
            net.minecraft.core.Direction.WEST, net.minecraft.core.Direction.EAST,
            net.minecraft.core.Direction.DOWN, net.minecraft.core.Direction.UP,
            net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH};
        boolean[] out = new boolean[6];
        for (int i = 0; i < 6; i++) {
            net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x + d[i][0], y + d[i][1], z + d[i][2]);
            out[i] = level.getBlockState(pos).isFaceSturdy(level, pos, f[i]);
        }
        return out;
    }

    /** 本家rotateBlockPos: 90度単位のXZ回転。 */
    public static int[] rotateBlockPos(byte rotation, int x, int y, int z) {
        if (rotation == 1) {
            return new int[]{-z, y, x};
        } else if (rotation == 2) {
            return new int[]{-x, y, -z};
        } else if (rotation == 3) {
            return new int[]{z, y, -x};
        }
        return new int[]{x, y, z};
    }

    /** 本家getBlockList: 範囲内の指定ブロック座標一覧。 */
    public static java.util.List<int[]> getBlockList(Object world, int x, int y, int z, Object block, int range) {
        java.util.List<int[]> array = new java.util.ArrayList<>();
        int r2 = range * 2;
        for (int i0 = 0; i0 < r2; ++i0) {
            for (int i1 = 0; i1 < r2; ++i1) {
                for (int i2 = 0; i2 < r2; ++i2) {
                    int bx = x - range + i0, by = y - range + i1, bz = z - range + i2;
                    if (getBlock(world, bx, by, bz) == block && !(i0 == range && i1 == range && i2 == range)) {
                        array.add(new int[]{bx, by, bz});
                    }
                }
            }
        }
        return array;
    }

}
