package jp.ngt.mccompat;

/**
 * 1.7.10/1.12 の net.minecraft.client.renderer.texture.TextureMap 互換。
 * NGTO Builder のプレビューが {@code renderer.bindTexture(TextureMap.field_110575_b)}
 * (ブロックアトラス) を参照する。1.21 の TextureAtlas.LOCATION_BLOCKS に対応させる。
 */
@SuppressWarnings("unused")
public final class TextureMap {
    private TextureMap() {
    }

    /**
     * field_110575_b = locationBlocksTexture (ブロックアトラス)。
     *
     * <p>★<b>{@code TextureAtlas.LOCATION_BLOCKS} を参照しないこと。</b>
     * {@code TextureAtlas} はクライアント専用クラスで、<b>専用サーバーには存在しない</b>。
     * このクラスはスクリプトのプリリュードが無条件に {@code Java.type} で掴むため、
     * 静的初期化でクライアント専用クラスに触ると<b>サーバーでスクリプトが丸ごと死ぬ</b>
     * (NGTO Builder のサーバー機能が使えなかった原因)。Fabric は
     * 「Cannot load class ... in environment type SERVER」、NeoForge は NoClassDefFoundError。
     *
     * <p>中身はただの資源 ID なので、クライアント専用クラスを経由せず直接組み立てる。
     * 値はバニラの定義と同じ ({@code minecraft:textures/atlas/blocks.png})。
     */
    public static final net.minecraft.resources.ResourceLocation field_110575_b =
        net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/atlas/blocks.png");
}
