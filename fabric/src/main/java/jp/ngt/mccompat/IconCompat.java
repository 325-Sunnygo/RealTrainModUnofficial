package jp.ngt.mccompat;

/**
 * 1.7.10 {@code net.minecraft.util.IIcon} のスクリプト互換 (SRG 名)。
 * NGTO Builder2 の render スクリプトが
 * {@code icon.func_94209_e() / func_94206_g() / func_94212_f() / func_94210_h()}
 * (= getMinU / getMinV / getMaxU / getMaxV) を呼ぶ。
 *
 * <p>1.21 に IIcon は無いので、ブロックモデルの代表スプライト (TextureAtlasSprite) の
 * UV をこの形で公開する。クライアント専用のスプライト取得は
 * {@code jp.ngt.mccompat.client.BlockIconLookup} が行う (このクラスは MC 型に依存しない)。
 */
public final class IconCompat {
    private final float minU;
    private final float minV;
    private final float maxU;
    private final float maxV;

    public IconCompat(float minU, float minV, float maxU, float maxV) {
        this.minU = minU;
        this.minV = minV;
        this.maxU = maxU;
        this.maxV = maxV;
    }

    /** getMinU */
    public float func_94209_e() {
        return this.minU;
    }

    /** getMinV */
    public float func_94206_g() {
        return this.minV;
    }

    /** getMaxU */
    public float func_94212_f() {
        return this.maxU;
    }

    /** getMaxV */
    public float func_94210_h() {
        return this.maxV;
    }
}
