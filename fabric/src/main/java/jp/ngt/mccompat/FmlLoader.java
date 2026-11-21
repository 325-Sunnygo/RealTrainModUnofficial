package jp.ngt.mccompat;

/**
 * 1.7.10 Forge {@code cpw.mods.fml.common.Loader} のスクリプト互換。
 * NGTO Builder2 の {@code RTMApiCompat.isModLoaded} が
 * {@code Packages.cpw.mods.fml.common.Loader.isModLoaded(modid)} を呼ぶ。
 */
public final class FmlLoader {
    private FmlLoader() {
    }

    /** Loader.isModLoaded */
    public static boolean isModLoaded(String modid) {
        if (modid == null) {
            return false;
        }
        try {
            return net.neoforged.fml.ModList.get().isLoaded(modid);
        } catch (Throwable t) {
            return false;
        }
    }
}
