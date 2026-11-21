package com.portofino.realtrainmodunofficial;

import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 同梱パックを専用フォルダ (rtm_default_assets) へ展開する。
 * mods フォルダのファイルは絶対に消さない。
 */
public final class BundledPackInstaller {

    private BundledPackInstaller() {
    }

    public static void installDefaultPacks() {
        try {
            installBundledPacksToDefaultAssets();
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("Could not install bundled default packs", e);
        }
    }

    /** 同梱デフォルトパックは mods ではなく専用フォルダ (rtm_default_assets) に展開する。 */
    private static void installBundledPacksToDefaultAssets() throws IOException {
        Path assetsDir = DefaultAssetsFolder.ensure();
        Path modsDir = FMLPaths.GAMEDIR.get().resolve("mods");
        for (String category : new String[]{"rail", "installed_object", "vehicle"}) {
            for (Path bundledPack : BundledPackStore.listBundledPacks(category)) {
                String fileName = bundledPack.getFileName().toString();
                Path target = assetsDir.resolve(fileName);
                Path userCopy = modsDir.resolve(fileName);
                if (Files.exists(userCopy)) {
                    // ユーザーが mods に置いたものを正とする。二重ロードを避けるため、
                    // こちらが過去に展開したコピー (自前のファイル) だけを片付ける。
                    RealTrainModUnofficial.LOGGER.info(
                        "[RTMU] 同梱パック {} は mods 側を優先します (同梱版は展開しません)", fileName);
                    Files.deleteIfExists(target);
                    continue;
                }
                Files.copy(bundledPack, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            }
            // パック作者の Readme 等 (zip 以外) も zip と同じフォルダに置く
            for (Path extra : BundledPackStore.listBundledExtras(category)) {
                Path target = assetsDir.resolve(extra.getFileName().toString());
                Files.copy(extra, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
    }
}
