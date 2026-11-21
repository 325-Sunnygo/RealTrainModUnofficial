package jp.ngt.ngtlib.renderer;

/** 本家 jp.ngt.ngtlib.renderer.NGTObjectRenderer のスクリプ… */
@SuppressWarnings("unused")
public final class NGTObjectRenderer {
    public static final NGTObjectRenderer INSTANCE = new NGTObjectRenderer();

    private NGTObjectRenderer() {
    }

    public void renderNGTObject(Object world, Object ngto, boolean translucent, double par4, int pass) {
        NGTRenderer.renderNGTObject(world,ngto,translucent,(int)par4,pass);
    }
}
