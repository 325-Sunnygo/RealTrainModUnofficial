package jp.ngt.ngtlib.math;

import net.minecraft.util.Mth;

/**
 * Port of jp.legacy.legacylib.math.BezierCurve (cubic Bezier on Z/X plane).
 */
public final class BezierCurve implements ILine {
    public static final int QUANTIZE = 32;
    /** レンダラの {@code max} と同一にし、{@code max > split} による重複サンプル（スパイク）を防ぐ。 */
    public static final int MAX_CURVE_RENDER_SPLIT = 384;

    public final double[] sp;
    public final double[] cpS;
    public final double[] cpE;
    public final double[] ep;
    private float[] normalizedParameters;
    private final double length;
    private final int split;

    /**
     * 等間隔化テーブルの分割数。<b>描画用の上限を掛けてはいけない。</b>
     *
     * <p>台車はレール上の位置を「レール長 × 360」の解像度で数えるが、実際に座標へ直すのは
     * このテーブル ({@code normalizedParameters}) で、<b>テーブルの粗さがそのまま
     * 台車が止まれる位置の粗さになる</b>。
     *
     * <p>ここに描画用の上限 (384) を掛けていたため、長いカーブほど点が粗くなっていた:
     * <pre>
     *   30 m のカーブ  → 384 点 =  7.8 cm 刻み
     *   100 m のカーブ → 384 点 = 26   cm 刻み
     * </pre>
     * 1 tick の進みがこの刻みより小さいと<b>次の点に届かず位置が更新されない</b>。
     * 1〜6 km/h は 1 tick あたり 1.4〜8.3 cm なので、ちょうどこの帯で動かなくなる。
     * 走行中も刻み単位で飛ぶため、カーブでガタガタ揺れ、台車 2 つから姿勢を決めている
     * 車体と視点も一緒に揺れる。<b>報告された 2 つの症状は同じここから出ている。</b>
     *
     * <p>本家は {@code 長さ × 32} で上限なし。こちらは 1 tick の最小移動 (1 km/h ≒ 1.4 cm) を
     * 下回るよう<b>1 m あたり 128 点</b>取る。100 m のレールでも float 12800 個 (51 KB) で収まる。
     */
    public static int tableSplitForLength(double arcLength) {
        if (arcLength < 1.0e-4) {
            return 64;
        }
        return Mth.clamp((int) (arcLength * 128.0), 64, 32768);
    }

    /**
     * <b>描画用</b>の分割数。レンダラの {@code max} と揃えて重複サンプルを防ぐ。
     * <p>位置計算には使わないこと ({@link #tableSplitForLength} 参照)。
     */
    public static int splitForLength(double arcLength) {
        if (arcLength < 1.0e-4) {
            return 32;
        }
        int raw = (int) (arcLength * 32.0);
        int s = Mth.clamp(Math.max(raw, 8), 8, 4096);
        return Math.min(s, MAX_CURVE_RENDER_SPLIT);
    }

    public BezierCurve(double p1, double p2, double p3, double p4, double p5, double p6, double p7, double p8) {
        this.sp = new double[]{p1, p2};
        this.cpS = new double[]{p3, p4};
        this.cpE = new double[]{p5, p6};
        this.ep = new double[]{p7, p8};
        this.length = this.calcLength();
        this.split = tableSplitForLength(this.length);
    }

    @Override
    public double[] getPoint(int par1, int par2) {
        return this.getPointFromParameter((double) this.getHomogenizedParameter(par1, par2));
    }

    private double[] getPointFromParameter(double par1) {
        double t = par1 < 0.0D ? 0.0D : (par1 > 1.0D ? 1.0D : par1);
        double tp = 1.0D - t;
        double d0 = t * t * t;
        double d1 = 3.0D * t * t * tp;
        double d2 = 3.0D * t * tp * tp;
        double d3 = tp * tp * tp;
        double x = d0 * this.ep[0] + d1 * this.cpE[0] + d2 * this.cpS[0] + d3 * this.sp[0];
        double y = d0 * this.ep[1] + d1 * this.cpE[1] + d2 * this.cpS[1] + d3 * this.sp[1];
        return new double[]{x, y};
    }

    @Override
    public int getNearlestPoint(int par1, double par2, double par3) {
        int i = 0;
        double pd = Double.MAX_VALUE;
        for (int j = 0; j < par1; ++j) {
            double[] point = this.getPoint(par1, j);
            double dx = par2 - point[1];
            double dy = par3 - point[0];
            double distance = dx * dx + dy * dy;
            if (distance < pd) {
                pd = distance;
                i = j;
            }
        }
        return pd < Double.MAX_VALUE ? i : -1;
    }

    @Override
    public double getSlope(int par1, int par2) {
        return this.getSlopeFromParameter((double) this.getHomogenizedParameter(par1, par2));
    }

    private double getSlopeFromParameter(double par1) {
        double t = par1 < 0.0D ? 0.0D : (par1 > 1.0D ? 1.0D : par1);
        double tp = 1.0D - t;
        double d0 = t * t;
        double d1 = 2.0D * t * tp;
        double d2 = tp * tp;
        double dx = 3.0D * (d0 * (this.ep[0] - this.cpE[0]) + d1 * (this.cpE[0] - this.cpS[0]) + d2 * (this.cpS[0] - this.sp[0]));
        double dy = 3.0D * (d0 * (this.ep[1] - this.cpE[1]) + d1 * (this.cpE[1] - this.cpS[1]) + d2 * (this.cpS[1] - this.sp[1]));
        return Math.atan2(dy, dx);
    }

    private float getHomogenizedParameter(int n, int par2) {
        if (n < 4) {
            return 0.0F;
        } else if (par2 <= 0) {
            return 0.0F;
        } else if (par2 >= n) {
            return 1.0F;
        } else {
            if (this.normalizedParameters == null) {
                this.initNP();
            }
            int i0 = NGTMath.floor((float) par2 * (float) this.split / (float) n);
            if (i0 < 0) i0 = 0;
            if (i0 >= this.normalizedParameters.length) i0 = this.normalizedParameters.length - 1;
            return this.normalizedParameters[i0];
        }
    }

    private void initNP() {
        if (this.split < 1) {
            this.normalizedParameters = new float[]{0.0F};
            return;
        }
        this.normalizedParameters = new float[this.split];
        float ni = 1.0F / (float) this.split;
        float tt = 0.0F;
        double[] p = this.sp;
        double[] q = new double[2];
        float[] dd = new float[this.split + 1];
        dd[0] = 0.0F;
        int i;
        for (i = 1; i < this.split + 1; ++i) {
            tt += ni;
            q = this.getPointFromParameter((double) tt);
            dd[i] = dd[i - 1] + (float) this.getDistance(p[0], q[0], p[1], q[1]);
            p = q;
        }
        float total = dd[this.split];
        if (total < 1.0e-8f) {
            for (i = 0; i < this.split; ++i) {
                this.normalizedParameters[i] = (float) i / (float) this.split;
            }
            return;
        }
        for (i = 1; i < this.split + 1; ++i) {
            dd[i] /= total;
        }
        for (i = 0; i < this.split; ++i) {
            float t = (float) i / (float) this.split;
            int k;
            for (k = 0; k < this.split - 1 && (!(dd[k] <= t) || !(t <= dd[k + 1])); ++k) {
            }
            float denom = dd[k + 1] - dd[k];
            float x;
            if (Math.abs(denom) < 1.0e-8f) {
                x = (float) k / (float) this.split;
            } else {
                x = (t - dd[k]) / denom;
                x = ((float) k * (1.0F - x) + (float) (1 + k) * x) * (1.0F / (float) this.split);
            }
            this.normalizedParameters[i] = x;
        }
    }

    @Override
    public double getLength() {
        return this.length;
    }

    private double calcLength() {
        double x0 = this.sp[0] - this.ep[0];
        double y0 = this.sp[1] - this.ep[1];
        double l0 = Math.sqrt(x0 * x0 + y0 * y0);
        int n = NGTMath.floor(l0 * 2.0D);
        if (n < 1) n = 1;
        float ni = 1.0F / (float) n;
        float tt = 0.0F;
        double[] p = this.sp;
        double[] q = new double[2];
        double[] dd = new double[n + 1];
        dd[0] = 0.0D;
        for (int i = 1; i < n + 1; ++i) {
            tt += ni;
            q = this.getPointFromParameter((double) tt);
            dd[i] = dd[i - 1] + this.getDistance(p[0], q[0], p[1], q[1]);
            p = q;
        }
        return dd[n];
    }

    private double getDistance(double par1, double par2, double par3, double par4) {
        double xDis = Math.abs(par1 - par2);
        double yDis = Math.abs(par3 - par4);
        return Math.sqrt(xDis * xDis + yDis * yDis);
    }
}
