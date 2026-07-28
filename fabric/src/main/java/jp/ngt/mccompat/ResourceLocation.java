package jp.ngt.mccompat;

/**
 * パックスクリプト互換: 1.7.10 の net.minecraft.util.ResourceLocation。
 * スクリプトの `new Packages.net.minecraft.util.ResourceLocation(...)` は
 * ローダの FQN リマップでこのクラスに差し替えられる。
 * SRG 名 (func_110624_b/func_110623_a) もスクリプトから直接呼ばれる。
 */
public class ResourceLocation {
    private final String domain;
    private final String path;

    public ResourceLocation(String domain, String path) {
        this.domain = domain == null ? "minecraft" : domain;
        this.path = path == null ? "" : path;
    }

    public ResourceLocation(String combined) {
        int idx = combined.indexOf(':');
        if (idx >= 0) {
            this.domain = combined.substring(0, idx);
            this.path = combined.substring(idx + 1);
        } else {
            this.domain = "minecraft";
            this.path = combined;
        }
    }

    /**
     * 1.7.10 SRG: getResourceDomain
     */
    public String func_110624_b() {
        return this.domain;
    }

    /**
     * 1.7.10 SRG: getResourcePath
     */
    public String func_110623_a() {
        return this.path;
    }

    public String getResourceDomain() {
        return this.domain;
    }

    public String getResourcePath() {
        return this.path;
    }

    /**
     * 実 ResourceLocation へ (バニラ形式に正規化できない文字はサニタイズ)。
     */
    public net.minecraft.resources.ResourceLocation toReal() {
        String d = this.domain.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.\\-]", "_");
        String p = this.path.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.\\-/]", "_");
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(d, p);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResourceLocation other)) return false;
        return this.domain.equals(other.domain) && this.path.equals(other.path);
    }

    @Override
    public int hashCode() {
        return 31 * this.domain.hashCode() + this.path.hashCode();
    }

    @Override
    public String toString() {
        return this.domain + ":" + this.path;
    }
}
