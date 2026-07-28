package net.neoforged.fml.event.lifecycle;

import net.neoforged.bus.api.Event;

import java.util.concurrent.CompletableFuture;

/** シム: Fabric は初期化が単一スレッド直列なので enqueueWork は即時実行。 */
public class FMLCommonSetupEvent extends Event {
    public CompletableFuture<Void> enqueueWork(Runnable work) {
        work.run();
        return CompletableFuture.completedFuture(null);
    }
}
