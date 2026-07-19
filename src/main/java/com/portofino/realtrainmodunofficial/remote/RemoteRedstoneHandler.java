package com.portofino.realtrainmodunofficial.remote;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * リモコンのペアを毎 tick 評価して無線レッドストーンを反映する。
 * <ul>
 *   <li>ペアの片方が「レッドストーンを受けている / 出している」なら、もう片方を無線給電する
 *       (受信側 {@link InstalledObjectBlockEntity#setRemotePowered})。</li>
 *   <li>ペアのどちらかのブロックが壊れて空気になったら、ペアを解除して全員へ通知する。</li>
 * </ul>
 */
@EventBusSubscriber(modid = RealTrainModUnofficial.MODID)
public final class RemoteRedstoneHandler {

    private RemoteRedstoneHandler() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        //2 tick おきで十分 (レバー操作の反映に体感差は出ない)。
        if (server.getTickCount() % 2 != 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            RemotePairings rp = RemotePairings.get(level);
            if (rp.isEmpty()) {
                continue;
            }
            for (long[] pair : rp.snapshot()) {
                BlockPos a = BlockPos.of(pair[0]);
                BlockPos b = BlockPos.of(pair[1]);
                //どちらかのチャンクが未ロードなら今回はスキップ (壊れたと誤判定しない)。
                if (!level.isLoaded(a) || !level.isLoaded(b)) {
                    continue;
                }
                //どちらかが空気 = 壊された → 解除して通知。
                if (level.getBlockState(a).isAir() || level.getBlockState(b).isAir()) {
                    rp.remove(pair);
                    server.getPlayerList().broadcastSystemMessage(
                        Component.literal("§cペアリングが解除されました"), false);
                    continue;
                }
                boolean activeA = sourceActive(level, a);
                boolean activeB = sourceActive(level, b);
                //相手のレッドストーン状態を自分へ無線給電する (双方向。片側にだけレバーがある想定)。
                applyRemote(level, a, activeB);
                applyRemote(level, b, activeA);
            }
        }
    }

    /** pos が「レッドストーンを受けている」か「自分で出している」か (レバー/転轍機など)。 */
    private static boolean sourceActive(ServerLevel level, BlockPos pos) {
        if (level.getBestNeighborSignal(pos) > 0) {
            return true;
        }
        BlockState s = level.getBlockState(pos);
        if (s.isSignalSource()) {
            for (Direction d : Direction.values()) {
                if (s.getSignal(level, pos, d) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /** pos の設置物へ無線給電を反映する (設置物以外は何もしない)。 */
    private static void applyRemote(ServerLevel level, BlockPos pos, boolean powered) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof InstalledObjectBlockEntity io) {
            io.setRemotePowered(powered);
        }
    }
}
