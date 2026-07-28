package jp.ngt.ngtlib.renderer.model;

/**
 * 本家 jp.ngt.ngtlib.renderer.model.TextureSet の移植 (スクリプト可視フィールド)。
 */
public class TextureSet {
    public Material material;
    /** 発光テクスチャ (***_light0/1/2.png)。無ければ null。 */
    public jp.ngt.mccompat.ResourceLocation[] subTextures;
    /** α ブレンドするマテリアルか。 */
    public boolean doAlphaBlend;

    public TextureSet(Material material) {
        this.material = material;
    }

    /**
     * 本家 TextureSet(material, subTexturesSize, alpha, args...)。
     * subTextures は args があればそれ、無ければ元テクスチャ名に "_light{i}" を挿した名前。
     */
    public TextureSet(Material material, int subTexturesSize, boolean doAlphaBlend, String... args) {
        this.material = material;
        this.doAlphaBlend = doAlphaBlend;
        if (subTexturesSize <= 0) {
            return;
        }
        this.subTextures = new jp.ngt.mccompat.ResourceLocation[subTexturesSize];
        String base = texturePathOf(material);
        int dot = base == null ? -1 : base.indexOf(".png");
        for (int i = 0; i < subTexturesSize; i++) {
            if (args != null && i < args.length && args[i] != null) {
                this.subTextures[i] = new jp.ngt.mccompat.ResourceLocation(args[i]);
            } else if (dot >= 0) {
                this.subTextures[i] = new jp.ngt.mccompat.ResourceLocation(
                        new StringBuilder(base).insert(dot, "_light" + i).toString());
            }
        }
    }

    private static String texturePathOf(Material material) {
        Object tex = material == null ? null : material.texture;
        if (tex instanceof jp.ngt.mccompat.ResourceLocation rl) {
            return rl.func_110623_a();
        }
        if (tex instanceof net.minecraft.resources.ResourceLocation rl) {
            return rl.getPath();
        }
        return tex == null ? null : String.valueOf(tex);
    }
}
