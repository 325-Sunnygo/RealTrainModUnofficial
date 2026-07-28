package jp.ngt.ngtlib.io;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 本家 NGTLib jp.ngt.ngtlib.io.NGTLog の移植。
 * ログ API (debug) はスクリプトからも呼ばれるためシグネチャを本家のまま維持。
 * チャット送信系は 1.7.10 の ICommandSender/IChatComponent → 1.21 の Player/Component に適合。
 */
public final class NGTLog {
    private static final Logger logger = LogManager.getLogger("NGT");

    private NGTLog() {
    }

    public static void debug(String par1) {
        debug(par1, new Object[0]);
    }

    public static void debug(String par1, Object... par2) {
        if (par2 == null || par2.length == 0) {
            logger.log(Level.INFO, par1);
        } else {
            logger.log(Level.INFO, String.format(par1, par2));
        }
    }

    /**
     * フォーマットはこちらで行う
     */
    public static void sendChatMessage(Player player, String message, Object... objects) {
        if (player == null) {
            return;
        }
        player.displayClientMessage(Component.translatable(message, objects), false);
    }

    /**
     * PlayerCompat ラッパー (SRB3/NGTO のスクリプトが渡す) も受ける版
     */
    public static void sendChatMessage(Object player, String message, Object... objects) {
        Player real = jp.ngt.mccompat.PlayerCompat.unwrap(player);
        sendChatMessage(real, message, objects);
    }

    /**
     * フォーマットはこちらで行う
     */
    public static void sendChatMessageToAll(String message, Object... objects) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            debug("[NGTLog] Can't send message. This is client.");
        } else {
            server.getPlayerList().broadcastSystemMessage(Component.translatable(message, objects), false);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static void showChatMessage(Component component) {
        net.minecraft.client.Minecraft.getInstance().gui.getChat().addMessage(component);
    }

    @OnlyIn(Dist.CLIENT)
    public static void showChatMessage(String message, Object... objects) {
        showChatMessage(Component.literal(String.format(message, objects)));
    }
}
