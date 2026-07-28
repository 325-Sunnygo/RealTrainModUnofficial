package net.neoforged.neoforge.common;

import net.neoforged.bus.ShimEventBus;
import net.neoforged.bus.api.IEventBus;

/**
 * シム: GAME バスと MOD バスを 1 本に統合した ShimEventBus。
 * Fabric コールバック → イベント post の配線はエントリポイント側で行う。
 */
public final class NeoForge {
    public static final IEventBus EVENT_BUS = new ShimEventBus();

    private NeoForge() {
    }
}
