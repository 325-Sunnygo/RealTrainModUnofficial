package com.portofino.rtmuautodrive;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * クライアント専用の入口。<b>サーバーからは絶対に読み込まれない</b>ようにするため、
 * ここだけに Screen 系を触る処理を置く (専用サーバーの dist クラッシュ対策)。
 */
public final class AutoDriveClientHooks {

    private AutoDriveClientHooks() {
    }

    public static void openList(List<AutoDriveNetwork.Entry> entries) {
        Minecraft.getInstance().setScreen(new DispatchListScreen(entries));
    }

    public static void openStationName(BlockPos pos, String name) {
        Minecraft.getInstance().setScreen(new StationNameScreen(pos, name));
    }

    public static void openRoute(BlockPos dispatcher, List<AutoDriveNetwork.RouteEntry> entries) {
        Minecraft.getInstance().setScreen(new RouteScreen(dispatcher, entries));
    }
}
