package com.portofino.realtrainmodunofficial.client;

import net.minecraft.core.BlockPos;

/**
 * クライアントが持つ選択範囲 (neo mcte)。MCTEU MCTEUnoffficialClient.pos1/pos2 と同じ持ち方。
 * ★ワールドに実体を置かない。
 */
public final class ClientSelection {

    private static BlockPos pos1;
    private static BlockPos pos2;

    /**
     * 直前までの選択 (neo mcte 追加)。
     * 広い範囲を組み直したあとで「さっきの範囲に戻したい」ことが多い。
     */
    private static final java.util.ArrayDeque<BlockPos[]> HISTORY = new java.util.ArrayDeque<>();
    private static final int MAX_HISTORY = 16;

    private ClientSelection() {
    }

    public static BlockPos pos1() {
        return pos1;
    }

    public static BlockPos pos2() {
        return pos2;
    }

    public static boolean hasStart() {
        return pos1 != null;
    }

    public static boolean hasEnd() {
        return pos1 != null && pos2 != null;
    }

    /** 1 点目。2 点目は未確定に戻す (本家 MCTE / MCTEU と同じ)。 */
    public static void setStart(BlockPos pos) {
        pos1 = pos;
        pos2 = null;
    }

    /** 始点だけ動かす (2 点目は保つ)。 */
    public static void setStartKeepEnd(BlockPos pos) {
        pos1 = pos;
    }

    public static void setEnd(BlockPos pos) {
        // 確定するたびに 1 つ前の範囲を控える
        if (pos1 != null && pos2 != null && !pos2.equals(pos)) {
            HISTORY.push(new BlockPos[]{pos1, pos2});
            while (HISTORY.size() > MAX_HISTORY) {
                HISTORY.removeLast();
            }
        }
        pos2 = pos;
    }

    /** 1 つ前の範囲へ戻す。戻せたら true。 */
    public static boolean back() {
        BlockPos[] prev = HISTORY.poll();
        if (prev == null) {
            return false;
        }
        pos1 = prev[0];
        pos2 = prev[1];
        return true;
    }

    public static int historyDepth() {
        return HISTORY.size();
    }

    public static void clear() {
        pos1 = null;
        pos2 = null;
        HISTORY.clear();
    }

    /** 選択範囲 (両端を含む)。未確定なら 1 点目だけの 1 ブロック。無選択なら null。 */
    public static net.minecraft.world.phys.AABB box() {
        if (pos1 == null) {
            return null;
        }
        BlockPos b = pos2 != null ? pos2 : pos1;
        return new net.minecraft.world.phys.AABB(
            Math.min(pos1.getX(), b.getX()), Math.min(pos1.getY(), b.getY()), Math.min(pos1.getZ(), b.getZ()),
            Math.max(pos1.getX(), b.getX()) + 1.0D,
            Math.max(pos1.getY(), b.getY()) + 1.0D,
            Math.max(pos1.getZ(), b.getZ()) + 1.0D);
    }
}
