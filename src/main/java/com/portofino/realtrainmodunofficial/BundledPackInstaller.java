package com.portofino.realtrainmodunofficial;

import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;

/**
 * Bundled pack を mods フォルダへ同期しつつ、古い不要 pack を掃除する。
 */
public final class BundledPackInstaller {
    private static final Set<String> REMOVED_BUNDLED_PACKS = Set.of(
        "hi03CatenaryPack Common v02.zip",
        "hi03ExpressRailway Catenary w51.zip",
        "hi03ExpressRailway Catenary.zip",
        "hi03ExpressRailway RailAssets.zip",
        "hi03ExpressRailway Rails1067mm.zip"
    );

    private BundledPackInstaller() {
    }

    public static void installDefaultPacks() {
        try {
            installBundledPacksToDefaultAssets();
            removeDeprecatedPacks();
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("Could not clean bundled default packs", e);
        }
    }

    /**
     * 同梱デフォルトパックは mods ではなく専用フォルダ (rtm_default_assets) に展開する。
     * 以前のバージョンが mods にコピーした同名 zip は二重ロード防止のため削除する。
     */
    private static void installBundledPacksToDefaultAssets() throws IOException {
        Path assetsDir = DefaultAssetsFolder.ensure();
        Path modsDir = FMLPaths.GAMEDIR.get().resolve("mods");
        for (String category : new String[]{"rail", "installed_object", "vehicle"}) {
            for (Path bundledPack : BundledPackStore.listBundledPacks(category)) {
                String fileName = bundledPack.getFileName().toString();
                Path target = assetsDir.resolve(fileName);
                Files.copy(bundledPack, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                Path oldCopy = modsDir.resolve(fileName);
                if (Files.exists(oldCopy)) {
                    Files.delete(oldCopy);
                }
            }
            //パック作者の Readme 等 (zip 以外) も zip と同じフォルダに置く
            for (Path extra : BundledPackStore.listBundledExtras(category)) {
                Path target = assetsDir.resolve(extra.getFileName().toString());
                Files.copy(extra, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
    }

    private static void removeDeprecatedPacks() throws IOException {
        Path modsDir = FMLPaths.GAMEDIR.get().resolve("mods");
        if (!Files.isDirectory(modsDir)) {
            return;
        }
        for (String fileName : REMOVED_BUNDLED_PACKS) {
            Path target = modsDir.resolve(fileName);
            if (Files.exists(target)) {
                Files.delete(target);
            }
        }
    }

}
