package net.neoforged.bus.api;

/** NeoForge Event 基底のシム。キャンセルは ICancellableEvent 実装イベントのみ有効。 */
public abstract class Event {
    boolean canceled;
}
