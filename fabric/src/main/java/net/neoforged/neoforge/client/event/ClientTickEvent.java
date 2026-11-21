package net.neoforged.neoforge.client.event;

import net.neoforged.bus.api.Event;

public abstract class ClientTickEvent extends Event {
    public static class Pre extends ClientTickEvent {
    }

    public static class Post extends ClientTickEvent {
    }
}
