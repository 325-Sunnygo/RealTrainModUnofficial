package com.portofino.realtrainmodunofficial;

import jp.ngt.rtm.rail.BlockLargeRailBase;
import jp.ngt.rtm.rail.TileEntityLargeRailBase;
import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import jp.ngt.rtm.rail.util.RailMapBasic;
import jp.ngt.rtm.rail.util.RailPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.UUID;

/**
 * レール敷設時に、敷設プレイヤーの RTMU 設定  に従って
 * 自動カント: カーブ (曲線) のとき曲率に応じたカントを付ける
 * 自動高さ: RailPosition の height を指定レベル (1〜5) に揃える
 */
public final class RtmuAutoRail {

    /** カント最大値 (度)。 */
    private static final float MAX_CANT = 8.0F;

    private RtmuAutoRail() {
    }

    public static void applyTwo(Level world, Player player, RailPosition rpS, RailPosition rpE) {
        if (player == null) {
            return;
        }
        UUID id = player.getUUID();
        int heightLevel = RtmuSettings.serverAutoHeightLevel(id);
        boolean autoCant = RtmuSettings.serverAutoCant(id);
        if (heightLevel <= 0 && !autoCant) {
            return;
        }
        if (heightLevel > 0) {
            byte h = heightForLevel(heightLevel);
            rpS.setHeight(h);
            rpE.setHeight(h);
        }
        if (autoCant) {
            float cant = curveCant(rpS, rpE);
            // 接続端: 隣接レールのカント (ロール) に一致させる。null = 接続レール無し。
            // 隣接が直線 (カント0) なら 0 に合う。カントレールなら同じカントに揃えて連続させる。
            Float adjStart = adjacentRoll(world, rpS);
            Float adjEnd = adjacentRoll(world, rpE);
            if (cant == 0.0F && adjStart == null && adjEnd == null) {
                return; //直線かつ孤立 → カント無し
            }
            // フリー端 (接続なし) はこのカーブのカント C、接続端は隣接レールのロールに一致。
            float rollStart = adjStart != null ? adjStart : cant;
            float rollEnd = adjEnd != null ? adjEnd : cant;
            if (rollStart == 0.0F && rollEnd == 0.0F) {
                return; //両端とも水平 (直線接続 or 平坦) → カント無し
            }
            float center = (rollStart + rollEnd) * 0.5F;
            // getRailRoll: 始点ロール=startRP.cantEdge, 中央=startRP.cantCenter, 終点ロール=-endRP.cantEdge。
            rpS.cantEdge = rollStart;
            rpS.cantCenter = center;
            rpE.cantCenter = center;
            rpE.cantEdge = -rollEnd;
        }
    }

    /**
     * rp の端点に接続する既存レールの「その端点でのロール (カント)」を返す。接続レールが無ければ null。
     * 端点ブロックとその周囲 (水平±1, 垂直±1) を走査して BlockLargeRailBase を探し、
     * そのコアの RailPosition から rp に一致する端点のロールを読む
     * (getRailRoll: 始点=cantEdge, 終点=-cantEdge)。
     */
    private static Float adjacentRoll(Level world, RailPosition rp) {
        if (world == null) {
            return null;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean foundRail = false;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    pos.set(rp.blockX + dx, rp.blockY + dy, rp.blockZ + dz);
                    try {
                        if (!(world.getBlockState(pos).getBlock() instanceof BlockLargeRailBase)) {
                            continue;
                        }
                        foundRail = true;
                        BlockEntity te = world.getBlockEntity(pos);
                        if (!(te instanceof TileEntityLargeRailBase base)) {
                            continue;
                        }
                        TileEntityLargeRailCore core = base.getRailCore();
                        if (core == null) {
                            continue;
                        }
                        RailPosition[] rps = core.getRailPositions();
                        if (rps == null || rps.length != 2) {
                            continue; //分岐等は対象外
                        }
                        if (nearEndpoint(rps[0], rp)) {
                            return rps[0].cantEdge;
                        }
                        if (nearEndpoint(rps[1], rp)) {
                            return -rps[1].cantEdge;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        return foundRail ? Float.valueOf(0.0F) : null;
    }

    private static boolean nearEndpoint(RailPosition erp, RailPosition rp) {
        return erp != null
                && Math.abs(erp.blockX - rp.blockX) <= 1
                && Math.abs(erp.blockZ - rp.blockZ) <= 1
                && Math.abs(erp.blockY - rp.blockY) <= 2;
    }

    public static void applyList(Player player, List<RailPosition> rps) {
        if (player == null || rps == null || rps.isEmpty()) {
            return;
        }
        UUID id = player.getUUID();
        int heightLevel = RtmuSettings.serverAutoHeightLevel(id);
        if (heightLevel > 0) {
            byte h = heightForLevel(heightLevel);
            for (RailPosition rp : rps) {
                rp.setHeight(h);
            }
        }
        // 分岐 (3点以上) のカントは扱いが複雑なので自動カントは 2 点レールのみ対象とする。
    }

    /** level (1〜16) → レール高さ byte (0〜15)。0=OFF は呼び出し側で弾く。任意の高さ (9,10 等) が指定可能。 */
    private static byte heightForLevel(int level) {
        return (byte) (Math.max(1, Math.min(16, level)) - 1);
    }

    /**
     * カーブなら曲率に応じたカント (度)、直線なら 0 を返す。
     * まず一時 RailMap を作って直線/曲線を判定 (この時点で cant は 0 なので副作用なし)。
     */
    private static float curveCant(RailPosition rpS, RailPosition rpE) {
        try {
            RailMapBasic probe = new RailMapBasic(rpS, rpE, RailMapBasic.fixRTMRailMapVersionCurrent);
            if (probe.isStraightTrack()) {
                return 0.0F;
            }
        } catch (Throwable t) {
            return 0.0F;
        }
        double dx = rpE.posX - rpS.posX;
        double dz = rpE.posZ - rpS.posZ;
        double chord = Math.sqrt(dx * dx + dz * dz);
        if (chord < 1.0D) {
            return 0.0F;
        }
        // rpE のアンカーは終点で「戻る向き」を指すので +180 して進行方向に合わせる。
        float signedTurn = Mth.wrapDegrees(rpE.anchorYaw + 180.0F - rpS.anchorYaw);
        float magnitude = Math.min(MAX_CANT, Math.abs(signedTurn) / (float) chord * 1.1F);
        if (magnitude < 0.2F) {
            return 0.0F;
        }
        // カーブの内側が下がる向き。曲がる向き (signedTurn) に対して符号を反転させると内側下がり。
        return -magnitude * Math.signum(signedTurn);
    }
}
