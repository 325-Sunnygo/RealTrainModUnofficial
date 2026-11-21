package net.neoforged.neoforge.event.entity.player;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;

/**
 * シム: プレイヤー関連イベント。
 * Fabric の対応物は ServerPlayConnectionEvents / ServerPlayerEvents。
 */
public class PlayerEvent extends Event {

    private final Player player;

    protected PlayerEvent(Player player) {
        this.player = player;
    }

    public Player getEntity() {
        return this.player;
    }

    public static class PlayerLoggedInEvent extends PlayerEvent {
        public PlayerLoggedInEvent(Player player) {
            super(player);
        }
    }

    public static class PlayerLoggedOutEvent extends PlayerEvent {
        public PlayerLoggedOutEvent(Player player) {
            super(player);
        }
    }

    public static class PlayerChangedDimensionEvent extends PlayerEvent {
        public PlayerChangedDimensionEvent(Player player) {
            super(player);
        }
    }

    public static class PlayerRespawnEvent extends PlayerEvent {
        private final boolean endConquered;

        public PlayerRespawnEvent(Player player, boolean endConquered) {
            super(player);
            this.endConquered = endConquered;
        }

        public boolean isEndConquered() {
            return this.endConquered;
        }
    }

    public static class Clone extends PlayerEvent {
        private final Player original;

        public Clone(Player player, Player original, boolean wasDeath) {
            super(player);
            this.original = original;
        }

        public Player getOriginal() {
            return this.original;
        }
    }
}
