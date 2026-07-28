package net.neoforged.fml;

/** シム: Fabric の ModContainer の薄いラッパ。 */
public class ModContainer {
    private final net.fabricmc.loader.api.ModContainer fabric;

    public ModContainer(net.fabricmc.loader.api.ModContainer fabric) {
        this.fabric = fabric;
    }

    public ModContainer() {
        this(net.fabricmc.loader.api.FabricLoader.getInstance()
            .getModContainer("realtrainmodunofficial").orElseThrow());
    }

    public String getModId() {
        return fabric.getMetadata().getId();
    }

    public net.fabricmc.loader.api.ModContainer getFabricContainer() {
        return fabric;
    }

    /** NeoForge の ModContainer#getModInfo().getVersion() 相当の簡易版。 */
    public String getVersionString() {
        return fabric.getMetadata().getVersion().getFriendlyString();
    }

    /**
     * NeoForge の {@code getModInfo()} 相当。
     * <p>共有コードが {@code c.getModInfo().getVersion().toString()} の形で書いてあるので、
     * <b>その形のまま通す</b>ために用意している。ここを削ると呼び出し側を書き換えることになり、
     * NeoForge 版との差分が増えて次の取り込みで衝突する。
     */
    public ModInfo getModInfo() {
        return new ModInfo(this.fabric);
    }

    /** {@link #getModInfo()} 用。バージョンだけ持つ。 */
    public static final class ModInfo {
        private final net.fabricmc.loader.api.ModContainer fabric;

        ModInfo(net.fabricmc.loader.api.ModContainer fabric) {
            this.fabric = fabric;
        }

        /** {@code toString()} でそのまま版番号になる (NeoForge の ArtifactVersion と同じ使い方)。 */
        public String getVersion() {
            return this.fabric.getMetadata().getVersion().getFriendlyString();
        }
    }

    /**
     * config 画面登録等の NeoForge 専用フック。Fabric に相当物が無いので受けるだけ。
     * <p>ラムダを直接渡せるよう Supplier で受ける (Object だと関数型として解決できない)。
     */
    public <T> void registerExtensionPoint(Class<T> point, java.util.function.Supplier<? extends T> factory) {
    }

    public <T> void registerExtensionPoint(Class<T> point, T instance) {
    }

    /** 設定ファイルの登録。Fabric では RTMU が自前で properties を読み書きするので no-op。 */
    public void registerConfig(Object type, Object spec) {
    }
}
