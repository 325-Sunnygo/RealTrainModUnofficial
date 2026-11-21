package net.neoforged.fml.event.lifecycle;

import net.neoforged.bus.api.Event;

import java.util.concurrent.CompletableFuture;

/** シム: enqueueWork は即時実行 (クライアント初期化フェーズから呼ぶ前提)。 */
public class FMLClientSetupEvent extends Event {
    public CompletableFuture<Void> enqueueWork(Runnable work) {
        work.run();
        return CompletableFuture.completedFuture(null);
    }
}
