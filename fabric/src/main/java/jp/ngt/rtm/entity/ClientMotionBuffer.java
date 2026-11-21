package jp.ngt.rtm.entity;

import net.minecraft.util.Mth;

/**
 * クライアント側の位置・姿勢の<b>遅延バッファ</b>。
 *
 * <h2>なぜ要るか</h2>
 * バニラのエンティティ補間は「目標との差を毎 tick 1/inc ずつ詰める」漸近方式。
 * 位置パケットは 1 tick ごとに届き、そのたび {@code inc} が 3 に戻るので、
 *
 * <pre>
 *   定常状態の遅れ g は  g = g − g/3 + d   →  g = 3d   (d = 1 tick の移動量)
 * </pre>
 *
 * となり<b>常に約 3 tick 遅れ、しかも速度が一定にならない</b>。
 * 列車のように速く一定に動く物ではこのガタつきがはっきり見える。
 *
 * <h2>やること</h2>
 * 届いた位置を貯めて、<b>少しだけ後ろを一定の速さで再生する</b>。
 *
 * <p>★<b>到着時刻でタグ付けしてはいけない。</b> それだと通信のゆらぎがそのまま
 * 再生位置のゆらぎになる (実測: 1 tick の進みが 1.30〜2.98m とばらついた。漸近方式より悪い)。
 * サーバーは {@code updateInterval(1)} で<b>毎 tick 1 個</b>送ってくるので、
 * 標本は「サーバーの tick 1 個ぶん」に等間隔で対応している。
 * だから並び順で再生し、再生ヘッドを実時間で 1 tick/50ms 進める。
 * 到着のゆらぎは<b>バッファの深さ</b>が吸収する。
 *
 * <p>遅延は既定 100ms (2 tick)。今の漸近方式が実質 3 tick 遅れているので、
 * <b>遅れは減って滑らかさだけ上がる</b>。
 *
 * <h2>曲線での追加の滑らかさ</h2>
 * 2 点を直線で結ぶと、標本ごとに進行方向がわずかに折れる (カーブで顕著)。
 * 前後 1 点ずつを使う Catmull-Rom で通せば折れ目が消える。
 * 標本が 4 点に満たない間は直線に落とす。
 */
public final class ClientMotionBuffer {

    /** 貯める標本の数。1 tick 1 個で 1 秒分あれば足りる。 */
    private static final int CAPACITY = 24;

    /** 1 tick の長さ (ナノ秒)。 */
    private static final double TICK_NANOS = 50.0D * 1000000.0D;

    /** 何 tick ぶん後ろを再生するか。既定 2 tick (100ms)。 */
    private static double delayTicks = 2.0D;

    public static void setDelayMillis(int millis) {
        delayTicks = Math.max(0.0D, Math.min(300.0D, (double) millis)) / 50.0D;
    }

    public static int getDelayMillis() {
        return (int) Math.round(delayTicks * 50.0D);
    }

    private final double[] x = new double[CAPACITY];
    private final double[] y = new double[CAPACITY];
    private final double[] z = new double[CAPACITY];
    private final float[] yaw = new float[CAPACITY];
    private final float[] pitch = new float[CAPACITY];
    private final float[] roll = new float[CAPACITY];
    /** 次に書く位置。 */
    private int head;
    /** 入っている数。 */
    private int size;
    /** これまでに受け取った総数。標本 i の通し番号 = totalPushed − size + i。 */
    private long totalPushed;
    /** 再生ヘッド (通し番号。小数)。 */
    private double playhead;
    private long lastSampleNanos;
    private boolean started;

    // 直近の取り出し結果
    public double outX, outY, outZ;
    public float outYaw, outPitch, outRoll;

    /** パケットが届いた。並び順で貯める (到着時刻は使わない)。 */
    public void push(double px, double py, double pz, float pyaw, float ppitch, float proll) {
        this.x[this.head] = px;
        this.y[this.head] = py;
        this.z[this.head] = pz;
        //角度は直前の値の近くへ展開して入れる (179→−179 で逆回りしないように)
        if (this.size > 0) {
            int last = (this.head - 1 + CAPACITY) % CAPACITY;
            this.yaw[this.head] = this.yaw[last] + Mth.wrapDegrees(pyaw - this.yaw[last]);
            this.pitch[this.head] = this.pitch[last] + Mth.wrapDegrees(ppitch - this.pitch[last]);
            this.roll[this.head] = this.roll[last] + Mth.wrapDegrees(proll - this.roll[last]);
        } else {
            this.yaw[this.head] = pyaw;
            this.pitch[this.head] = ppitch;
            this.roll[this.head] = proll;
        }
        this.head = (this.head + 1) % CAPACITY;
        this.totalPushed++;
        if (this.size < CAPACITY) {
            this.size++;
        }
    }

    /**
     * 一番新しい標本の姿勢だけ入れ替える。
     *
     * <p>位置はバニラの移動パケット、姿勢は float 同期 (entityData) と<b>別便で届く</b>。
     * 同じ tick でもどちらが先に着くかは決まっていないので、
     * 姿勢が後から来たときにその tick の標本へ入れ直す。
     * これをしないとカーブで<b>向きだけ 1 tick 遅れる</b>。
     */
    public void setLatestRotation(float pyaw, float ppitch, float proll) {
        if (this.size == 0) {
            return;
        }
        int last = (this.head - 1 + CAPACITY) % CAPACITY;
        float base = this.size >= 2 ? this.yaw[(this.head - 2 + CAPACITY) % CAPACITY] : pyaw;
        this.yaw[last] = base + Mth.wrapDegrees(pyaw - base);
        float basePitch = this.size >= 2 ? this.pitch[(this.head - 2 + CAPACITY) % CAPACITY] : ppitch;
        this.pitch[last] = basePitch + Mth.wrapDegrees(ppitch - basePitch);
        float baseRoll = this.size >= 2 ? this.roll[(this.head - 2 + CAPACITY) % CAPACITY] : proll;
        this.roll[last] = baseRoll + Mth.wrapDegrees(proll - baseRoll);
    }

    public int size() {
        return this.size;
    }

    public void clear() {
        this.size = 0;
        this.head = 0;
        this.started = false;
    }

    private int idx(int i) {
        return (this.head - this.size + i + CAPACITY * 2) % CAPACITY;
    }

    /**
     * 再生ヘッドの状態を取り出して {@code out*} に入れる。
     *
     * <p>ヘッドは<b>実時間で 1 tick/50ms 進める</b>ので、標本の到着が乱れても
     * 出てくる動きの速さは変わらない。ずれは毎回わずかに寄せて直す
     * (一気に直すとそこで飛ぶので、5% ずつ)。
     *
     * @return 取り出せたら true。標本が足りなければ false (呼び出し側は従来の方式に落とす)
     */
    public boolean sample() {
        if (this.size < 2) {
            return false;
        }
        long now = System.nanoTime();
        double newest = this.totalPushed - 1;
        double oldest = this.totalPushed - this.size;
        double desired = newest - delayTicks;

        if (!this.started) {
            this.started = true;
            this.playhead = desired;
            this.lastSampleNanos = now;
        } else {
            double dt = (now - this.lastSampleNanos) / TICK_NANOS;
            this.lastSampleNanos = now;
            //暴れた分は捨てる (一時停止・画面切替でまとめて進むのを防ぐ)
            this.playhead += Math.max(0.0D, Math.min(4.0D, dt));
            //ずれをゆっくり直す。急に直すとそこで飛ぶ
            this.playhead += (desired - this.playhead) * 0.05D;
        }
        //貯まっている範囲から出さない (先へ進めない・古すぎる所へ戻さない)
        this.playhead = Math.max(oldest, Math.min(newest, this.playhead));

        int i1i = (int) Math.floor(this.playhead - oldest);
        i1i = Math.max(0, Math.min(this.size - 2, i1i));
        double f = (this.playhead - oldest) - i1i;
        f = Math.max(0.0D, Math.min(1.0D, f));
        int i1 = idx(i1i);
        int i2 = idx(i1i + 1);

        //角度は素直に線形。角度に曲線を当てるとラップの扱いで壊れやすい
        this.outYaw = (float) (this.yaw[i1] + (this.yaw[i2] - this.yaw[i1]) * f);
        this.outPitch = (float) (this.pitch[i1] + (this.pitch[i2] - this.pitch[i1]) * f);
        this.outRoll = (float) (this.roll[i1] + (this.roll[i2] - this.roll[i1]) * f);

        //位置は前後 1 点ずつ取れるなら Catmull-Rom で折れ目を消す
        if (i1i - 1 >= 0 && i1i + 2 <= this.size - 1) {
            int i0 = idx(i1i - 1);
            int i3 = idx(i1i + 2);
            this.outX = catmullRom(this.x[i0], this.x[i1], this.x[i2], this.x[i3], f);
            this.outY = catmullRom(this.y[i0], this.y[i1], this.y[i2], this.y[i3], f);
            this.outZ = catmullRom(this.z[i0], this.z[i1], this.z[i2], this.z[i3], f);
        } else {
            this.outX = this.x[i1] + (this.x[i2] - this.x[i1]) * f;
            this.outY = this.y[i1] + (this.y[i2] - this.y[i1]) * f;
            this.outZ = this.z[i1] + (this.z[i2] - this.z[i1]) * f;
        }
        return true;
    }

    private void set(int i) {
        this.outX = this.x[i];
        this.outY = this.y[i];
        this.outZ = this.z[i];
        this.outYaw = this.yaw[i];
        this.outPitch = this.pitch[i];
        this.outRoll = this.roll[i];
    }

    /** 一様 Catmull-Rom。p1→p2 の間を、前後の点で向きを合わせて通る。 */
    private static double catmullRom(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return 0.5D * ((2.0D * p1)
            + (-p0 + p2) * t
            + (2.0D * p0 - 5.0D * p1 + 4.0D * p2 - p3) * t2
            + (-p0 + 3.0D * p1 - 3.0D * p2 + p3) * t3);
    }
}
