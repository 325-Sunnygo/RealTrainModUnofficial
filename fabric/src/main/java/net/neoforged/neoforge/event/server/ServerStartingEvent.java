package net.neoforged.neoforge.event.server;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.Event;

/** シム: サーバー起動中 (ワールドロード前)。Fabric の ServerLifecycleEvents.SERVER_STARTING 相当。 */
public class ServerStartingEvent extends Event {
    private final MinecraftServer server;

    public ServerStartingEvent(MinecraftServer server) {
        this.server = server;
    }

    public MinecraftServer getServer() {
        return this.server;
    }
}
