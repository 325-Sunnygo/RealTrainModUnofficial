package jp.ngt.ngtlib.math;

public interface ILine {
    
    double[] getPoint(int par1, int par2);

    /** 曲線長で正規化した位置(0.0～1.0)から座標を取得する。 */
    default double[] getPoint(double ratio) {
        double value = Math.max(0.0D, Math.min(1.0D, ratio));
        int split = 1000000;
        return this.getPoint(split, (int) Math.round(value * (double) split));
    }

    
    int getNearlestPoint(int par1, double par2, double par3);

    
    double getSlope(int par1, int par2);

    /** 曲線長で正規化した位置(0.0～1.0)から傾きを取得する。 */
    default double getSlope(double ratio) {
        double value = Math.max(0.0D, Math.min(1.0D, ratio));
        int split = 1000000;
        return this.getSlope(split, (int) Math.round(value * (double) split));
    }

    
    double getLength();
}
