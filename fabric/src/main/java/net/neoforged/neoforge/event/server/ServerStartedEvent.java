package net.neoforged.neoforge.event.server;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.Event;

public class ServerStartedEvent extends Event {
    private final MinecraftServer server;

    public ServerStartedEvent(MinecraftServer server) {
        this.server = server;
    }

    public MinecraftServer getServer() {
        return server;
    }
}
