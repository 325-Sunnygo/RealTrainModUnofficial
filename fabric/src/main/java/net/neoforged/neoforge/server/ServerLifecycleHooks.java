package net.neoforged.neoforge.server;

import net.minecraft.server.MinecraftServer;

/**
 * シム: 現在のサーバー参照。エントリポイントが ServerLifecycleEvents で set/clear する。
 */
public final class ServerLifecycleHooks {
    private static volatile MinecraftServer currentServer;

    private ServerLifecycleHooks() {
    }

    public static MinecraftServer getCurrentServer() {
        return currentServer;
    }

    public static void setCurrentServer(MinecraftServer server) {
        currentServer = server;
    }
}
