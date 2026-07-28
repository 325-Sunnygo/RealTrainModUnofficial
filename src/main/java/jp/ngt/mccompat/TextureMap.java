package jp.ngt.mccompat;

/**
 * 1.7.10/1.12 の net.minecraft.client.renderer.texture.TextureMap 互換。
 * NGTO Builder のプレビューが renderer.bindTexture(TextureMap.field_110575_b)
 * (ブロックアトラス) を参照する。1.21 の TextureAtlas.LOCATION_BLOCKS に対応させる。
 */
@SuppressWarnings("unused")
public final class TextureMap {
    private TextureMap() {
    }

    /**
     * field_110575_b = locationBlocksTexture (ブロックアトラス)。
     * ★TextureAtlas.LOCATION_BLOCKS を参照しないこと。
     */
    public static final net.minecraft.resources.ResourceLocation field_110575_b =
        net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/atlas/blocks.png");
}
