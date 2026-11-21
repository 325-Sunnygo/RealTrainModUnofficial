package net.neoforged.bus.api;

/** シム: NeoForge と同じく default メソッドで Event のフラグを操作する。 */
public interface ICancellableEvent {
    default void setCanceled(boolean canceled) {
        ((Event) this).canceled = canceled;
    }

    default boolean isCanceled() {
        return ((Event) this).canceled;
    }
}
