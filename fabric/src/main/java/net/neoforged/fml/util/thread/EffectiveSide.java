package net.neoforged.fml.util.thread;

import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * シム: 現在のスレッドが起動中サーバーのメインスレッドなら SERVER。
 * (NeoForge 実装もスレッド判定。描画/クライアントスレッドは CLIENT になる)
 */
public final class EffectiveSide {
    private EffectiveSide() {
    }

    public static LogicalSide get() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && server.isSameThread()) {
            return LogicalSide.SERVER;
        }
        return LogicalSide.CLIENT;
    }
}
