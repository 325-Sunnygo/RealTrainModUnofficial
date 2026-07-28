package jp.ngt.ngtlib.util;

import jp.ngt.mccompat.PlayerCompat;
import jp.ngt.mccompat.WorldCompat;
import net.minecraft.client.Minecraft;

/**
 * 本家 jp.ngt.ngtlib.util.MCWrapperClient のスクリプト互換。
 * getPlayer() は PlayerCompat ラッパーを返す (SRB3 等が SRG フィールドを直接読むため)。
 */
@SuppressWarnings("unused")
public final class MCWrapperClient {
    private MCWrapperClient() {
    }

    public static PlayerCompat getPlayer() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        PlayerCompat compat = PlayerCompat.of(player);
        compat.refresh();
        return compat;
    }

    public static WorldCompat getWorld() {
        var level = Minecraft.getInstance().level;
        return level != null ? new WorldCompat(level) : null;
    }
}
