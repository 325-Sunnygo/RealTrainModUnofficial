package net.neoforged.neoforge.client.event;

import net.minecraft.client.player.LocalPlayer;
import net.neoforged.bus.api.Event;

/**
 * シム: クライアントのログイン/ログアウト。
 *
 * <p>Fabric 側の対応物は {@code ClientPlayConnectionEvents.JOIN / DISCONNECT}。
 * エントリポイントから post する (配線は {@code RtmuFabricClientInit})。
 */
public class ClientPlayerNetworkEvent extends Event {

    private final LocalPlayer player;

    protected ClientPlayerNetworkEvent(LocalPlayer player) {
        this.player = player;
    }

    public LocalPlayer getPlayer() {
        return this.player;
    }

    /** ワールドへ入った。 */
    public static class LoggingIn extends ClientPlayerNetworkEvent {
        public LoggingIn(LocalPlayer player) {
            super(player);
        }
    }

    /** ワールドから抜けた。切断時は player が null になりうる。 */
    public static class LoggingOut extends ClientPlayerNetworkEvent {
        public LoggingOut(LocalPlayer player) {
            super(player);
        }
    }

    /** 別ワールドへ入り直した (次元移動ではなく再ログイン)。 */
    public static class Clone extends ClientPlayerNetworkEvent {
        public Clone(LocalPlayer player) {
            super(player);
        }
    }
}
