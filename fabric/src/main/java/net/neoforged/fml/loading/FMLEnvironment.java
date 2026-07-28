package net.neoforged.fml.loading;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.neoforged.api.distmarker.Dist;

/** シム: Fabric の EnvType を Dist に写像。 */
public final class FMLEnvironment {
    public static final Dist dist =
        FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT
            ? Dist.CLIENT : Dist.DEDICATED_SERVER;
    public static final boolean production = !FabricLoader.getInstance().isDevelopmentEnvironment();

    private FMLEnvironment() {
    }
}
