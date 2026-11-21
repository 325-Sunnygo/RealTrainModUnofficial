package net.neoforged.fml.loading;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/** シム: Fabric Loader のパスへ委譲。 */
public enum FMLPaths {
    GAMEDIR,
    MODSDIR,
    CONFIGDIR;

    public Path get() {
        FabricLoader loader = FabricLoader.getInstance();
        return switch (this) {
            case GAMEDIR -> loader.getGameDir();
            case MODSDIR -> loader.getGameDir().resolve("mods");
            case CONFIGDIR -> loader.getConfigDir();
        };
    }
}
