package net.neoforged.fml;

import net.fabricmc.loader.api.FabricLoader;

import java.util.Optional;

/** シム: mod 存在チェックとバージョン取得だけ Fabric Loader へ委譲。 */
public final class ModList {
    private static final ModList INSTANCE = new ModList();

    public static ModList get() {
        return INSTANCE;
    }

    public boolean isLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    public Optional<ModContainer> getModContainerById(String modId) {
        return FabricLoader.getInstance().getModContainer(modId).map(ModContainer::new);
    }

    /**
     * NeoForge の ModFileInfo 相当。RTMU は
     * {@code getModFileById(id).getFile().findResource(path...)} の形でしか使わないので、
     * その形だけ Fabric の ModContainer.findPath へ写像する。
     */
    public ModFileShim getModFileById(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
            .map(ModFileShim::new).orElse(null);
    }

    public static final class ModFileShim {
        private final net.fabricmc.loader.api.ModContainer container;

        ModFileShim(net.fabricmc.loader.api.ModContainer container) {
            this.container = container;
        }

        public ModFileShim getFile() {
            return this;
        }

        public java.nio.file.Path findResource(String... path) {
            return container.findPath(String.join("/", path)).orElse(null);
        }

        /**
         * mod 本体のファイル (jar) のパス。
         * <p>NeoForge は jar の実体パスを直接返す。Fabric の {@code getRootPaths()} は
         * jar 内を指す仮想パスなので、そこから元の jar を辿る。開発環境ではクラスの出力
         * ディレクトリになる (jar が無いので当然)。
         */
        public java.nio.file.Path getFilePath() {
            java.util.List<java.nio.file.Path> roots = container.getRootPaths();
            if (roots.isEmpty()) {
                return null;
            }
            java.nio.file.Path root = roots.get(0);
            //jar 内の仮想パスなら、その jar 自身を返す
            java.nio.file.FileSystem fs = root.getFileSystem();
            if (!fs.equals(java.nio.file.FileSystems.getDefault())) {
                try {
                    //ZipFileSystem の識別子は jar の実パス
                    String uri = fs.toString();
                    java.nio.file.Path jar = java.nio.file.Path.of(uri);
                    if (java.nio.file.Files.exists(jar)) {
                        return jar;
                    }
                } catch (Exception ignored) {
                    //辿れなければ仮想パスのまま返す (呼び出し側は存在確認をしている)
                }
            }
            return root;
        }
    }
}
