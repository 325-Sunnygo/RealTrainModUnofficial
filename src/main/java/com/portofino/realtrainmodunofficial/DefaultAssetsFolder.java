package com.portofino.realtrainmodunofficial;

import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * MOD 同梱デフォルトアセットの展開先。mods / config とは別の専用フォルダ
 * (<gameDir>/rtm_default_assets) に置き、各パックローダのスキャン対象に含める。
 */
public final class DefaultAssetsFolder {
    public static final String NAME = "rtm_default_assets";

    private DefaultAssetsFolder() {
    }

    /** フォルダの Path (存在しなくても返す。作成は ensure で行う)。 */
    public static Path get() {
        return FMLPaths.GAMEDIR.get().resolve(NAME);
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
