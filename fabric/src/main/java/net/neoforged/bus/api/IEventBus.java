package net.neoforged.bus.api;

import java.util.function.Consumer;

/** シム: 実装は ShimEventBus。addListener/register/post のみ提供。 */
public interface IEventBus {
    <T extends Event> void addListener(Consumer<T> listener);

    <T extends Event> void addListener(Class<T> type, Consumer<T> listener);

    void register(Object target);

    <T extends Event> T post(T event);
}
