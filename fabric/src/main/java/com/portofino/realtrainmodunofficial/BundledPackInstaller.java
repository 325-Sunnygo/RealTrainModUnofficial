package com.portofino.realtrainmodunofficial;

import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 同梱パックを専用フォルダ (rtm_default_assets) へ展開する。
 *
 * <p><b>mods フォルダのファイルは絶対に消さない。</b>
 * 以前はここで
 * <ul>
 *   <li>同梱パックと同名の zip を mods から削除 (昔の版が mods へコピーしていた名残の掃除)</li>
 *   <li>「もう同梱しなくなったパック」の名前一覧 (hi03CatenaryPack Common v02.zip 等) を
 *       mods から起動のたびに削除</li>
 * </ul>
 * を行っていた。しかしこれは<b>ユーザーが自分で入れたファイルと区別できない</b>。
 * 実害: Baru'sPole は前提パックとして hi03CatenaryPack Common v02.zip を同梱しており、
 * ユーザーがそれを mods へ入れて起動すると RTMU が消してしまい、
 * 「前提パックが不足」と表示されたうえ zip が消えた、という報告が出た。
 *
 * <p>二重ロードを避けたいだけなので、削除ではなく<b>こちらが引く</b>形にする。
 * mods に同名の zip があれば、そちらを正としてこの展開をスキップし、
 * 過去に展開した自前のコピー (rtm_default_assets 側) だけを片付ける。
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

    /**
     * 同梱デフォルトパックは mods ではなく専用フォルダ (rtm_default_assets) に展開する。
     */
    private static void installBundledPacksToDefaultAssets() throws IOException {
        Path assetsDir = DefaultAssetsFolder.ensure();
        Path modsDir = FMLPaths.GAMEDIR.get().resolve("mods");
        for (String category : new String[]{"rail", "installed_object", "vehicle"}) {
            for (Path bundledPack : BundledPackStore.listBundledPacks(category)) {
                String fileName = bundledPack.getFileName().toString();
                Path target = assetsDir.resolve(fileName);
                Path userCopy = modsDir.resolve(fileName);
                if (Files.exists(userCopy)) {
                    //ユーザーが mods に置いたものを正とする。二重ロードを避けるため、
                    //こちらが過去に展開したコピー (自前のファイル) だけを片付ける。
                    RealTrainModUnofficial.LOGGER.info(
                        "[RTMU] 同梱パック {} は mods 側を優先します (同梱版は展開しません)", fileName);
                    Files.deleteIfExists(target);
                    continue;
                }
                Files.copy(bundledPack, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            }
            //パック作者の Readme 等 (zip 以外) も zip と同じフォルダに置く
            for (Path extra : BundledPackStore.listBundledExtras(category)) {
                Path target = assetsDir.resolve(extra.getFileName().toString());
                Files.copy(extra, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
    }
}
