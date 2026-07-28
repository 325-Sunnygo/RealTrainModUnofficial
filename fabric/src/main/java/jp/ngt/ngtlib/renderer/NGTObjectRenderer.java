package jp.ngt.ngtlib.renderer;

/**
 * 本家 jp.ngt.ngtlib.renderer.NGTObjectRenderer のスクリプト互換 (最低限 no-op)。
 * NGTO Builder のミニチュアプレビュー用。
 */
@SuppressWarnings("unused")
public final class NGTObjectRenderer {
    public static final NGTObjectRenderer INSTANCE = new NGTObjectRenderer();

    private NGTObjectRenderer() {
    }

    public void renderNGTObject(Object world, Object ngto, boolean translucent, double par4, int pass) {
    }
}
