package jp.ngt.mccompat;

/** 1.7.10 net.minecraft.util.IIcon のスクリプト互換 (SRG 名)。 */
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
