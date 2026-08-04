package com.portofino.rtmuautodrive;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * サーバー側からも読まれるクラス。
 *
 * <p>★ここに {@code Screen} など<b>クライアント専用クラスを直接書かないこと</b>。
 * 専用サーバーで「invalid dist DEDICATED_SERVER」になって起動ごと落ちる。
 * 画面を開く処理は {@link AutoDriveClientHooks} に閉じ込め、
 * 実際に呼ばれるまで読み込まれないようにしてある。
 */
public final class ClientBridge {

    private ClientBridge() {
    }

    // ---- サーバー → クライアント ----

    /** 列車運転スポナーの一覧を送る。 */
    public static void sendList(ServerPlayer player, AutoDriveNetwork.ListPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    /** 停車/通過の一覧を送る。 */
    public static void sendRoute(ServerPlayer player, AutoDriveNetwork.RoutePayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    /** 駅列車ブロックの名前を付ける画面を開かせる。 */
    public static void openStationName(Player player, BlockPos pos, String name) {
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new AutoDriveNetwork.OpenStationName(pos, name));
        }
    }

    // ---- クライアントで画面を開く ----

    public static void openList(List<AutoDriveNetwork.Entry> entries) {
        AutoDriveClientHooks.openList(entries);
    }

    public static void openStationNameScreen(BlockPos pos, String name) {
        AutoDriveClientHooks.openStationName(pos, name);
    }

    public static void openRoute(BlockPos dispatcher, List<AutoDriveNetwork.RouteEntry> entries) {
        AutoDriveClientHooks.openRoute(dispatcher, entries);
    }
}
