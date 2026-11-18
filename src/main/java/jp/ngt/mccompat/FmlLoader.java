package jp.ngt.mccompat;

/** 1.7.10 Forge cpw.mods.fml.common.Loader のスクリプト互換。 */
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
