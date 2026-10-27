package com.portofino.realtrainmodunofficial;

import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * MOD 同梱デフォルトアセット (E257_DefaultPack 等) の展開先。
 *
 * <p>★ゲーム直下に専用フォルダ (旧: rtm_default_assets) を作らない。
 * 同梱パックは jar の中に埋め込まれており、ファイルパスが要るローダ向けに
 * 既存のキャッシュフォルダ config/realtrainmodunofficial/bundled_pack_cache へ
 * 展開する。ユーザーの目に付く新しいフォルダは増えない。
 */
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
