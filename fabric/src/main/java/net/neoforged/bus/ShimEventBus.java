package net.neoforged.bus;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 最小イベントバス。
 * post で「イベント型に代入可能なリスナー全部」へ配送する。
 */
public final class ShimEventBus implements IEventBus {

    private record Listener(Class<?> type, Consumer<Event> handler) {
    }

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Map<Class<?>, List<Listener>> dispatchCache = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Event> void addListener(Consumer<T> listener) {
        // 型引数はランタイム消去で取れない。
        // RTMU では型明示版 or @SubscribeEvent 経由が大半。
        throw new UnsupportedOperationException(
            "型消去のため addListener(Consumer) は使えません。addListener(Class, Consumer) を使ってください");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Event> void addListener(Class<T> type, Consumer<T> listener) {
        Listener l = new Listener(type, (Consumer<Event>) listener);
        listeners.add(l);
        dispatchCache.clear();
    }

    @Override
    public void register(Object target) {
        Class<?> clazz = target instanceof Class<?> c ? c : target.getClass();
        boolean staticOnly = target instanceof Class<?>;
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        for (Method m : clazz.getDeclaredMethods()) {
            if (!m.isAnnotationPresent(SubscribeEvent.class)) {
                continue;
            }
            boolean isStatic = Modifier.isStatic(m.getModifiers());
            if (staticOnly && !isStatic) {
                continue;
            }
            if (m.getParameterCount() != 1 || !Event.class.isAssignableFrom(m.getParameterTypes()[0])) {
                continue;
            }
            try {
                m.setAccessible(true);
                MethodHandle mh = isStatic ? lookup.unreflect(m) : lookup.unreflect(m).bindTo(target);
                Class<?> eventType = m.getParameterTypes()[0];
                listeners.add(new Listener(eventType, ev -> {
                    try {
                        mh.invoke(ev);
                    } catch (RuntimeException | Error e) {
                        throw e;
                    } catch (Throwable t) {
                        throw new RuntimeException("Event handler failed: " + m, t);
                    }
                }));
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Cannot access @SubscribeEvent method: " + m, e);
            }
        }
        dispatchCache.clear();
    }

    @Override
    public <T extends Event> T post(T event) {
        List<Listener> match = dispatchCache.computeIfAbsent(event.getClass(), type ->
            listeners.stream().filter(l -> l.type().isAssignableFrom(type)).toList());
        for (Listener l : match) {
            l.handler().accept(event);
        }
        return event;
    }
}
