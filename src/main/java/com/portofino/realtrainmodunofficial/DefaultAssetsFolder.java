package com.portofino.realtrainmodunofficial;

import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;

/** MOD 同梱デフォルトアセット (E257_DefaultPack 等) の展開先。 */
public final class DefaultAssetsFolder {
    private DefaultAssetsFolder() {
    }

    /** フォルダの Path (存在しなくても返す。作成は ensure で行う)。 */
    public static Path get() {
        return FMLPaths.GAMEDIR.get().resolve("config")
            .resolve(RealTrainModUnofficial.MODID).resolve("bundled_pack_cache");
    }

    /** フォルダを作成して返す。 */
    public static Path ensure() {
        Path dir = get();
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("Could not create default assets folder {}", com.portofino.realtrainmodunofficial.util.LogPaths.safe(dir), e);
        }
        return dir;
    }
}
