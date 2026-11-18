package net.minecraftforge.fml.common;

/** 旧 Forge (1.7.10/1.12.2) の net.minecraftforge.fml.… */
public final class Loader {

    private static final Loader INSTANCE = new Loader();

    private Loader() {
    }

    /** 本家: Loader.instance — シングルトン取得。 */
    public static Loader instance() {
        return INSTANCE;
    }

    /** 本家: 指定 modid がロード済みか。NeoForge の ModList へ委譲する。 */
    public static boolean isModLoaded(String modid) {
        if (modid == null || modid.isBlank()) {
            return false;
        }
        try {
            return net.neoforged.fml.ModList.get() != null
                    && net.neoforged.fml.ModList.get().isLoaded(modid);
        } catch (Throwable t) {
            // ModList 未初期化タイミング等は「入っていない」扱い (スクリプトを落とさない)
            return false;
        }
    }
}
