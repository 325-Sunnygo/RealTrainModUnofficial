package com.portofino.realtrainmodunofficial.perf;

/**
 * 軽量化作業用の計測カウンタ。
 *
 * <p>「どの最適化が実際に効いたか」を体感でなく数値で確かめるために置く。推測で直しては
 * 外すのを避けるための土台なので、<b>最適化を入れる前に基準値を取ること</b>。
 *
 * <p><b>クライアント専用クラスを一切参照しないこと。</b>計測点の一つである
 * {@link jp.ngt.ngtlib.io.ScriptUtil} は共通コードで専用サーバーからもロードされる。
 * ここで Minecraft のクライアントクラスに触れると RuntimeDistCleaner に弾かれて
 * 専用サーバーが起動時クラッシュする (過去に同じ事故あり)。表示は client 側の
 * 別クラスが担当する。
 *
 * <p>計測自体のコストは 1 呼び出しあたり {@code System.nanoTime()} 2 回 (数十 ns)。
 * 毎フレーム数十回の呼び出しに対して誤差の範囲だが、{@link #enabled} が false の間は
 * 計測しないので通常プレイには影響しない。
 */
public final class RtmuProfiler {

    /** 計測 ON/OFF。既定 OFF。クライアントのキー操作で切り替える。 */
    public static volatile boolean enabled;

    // ---- 現在フレームの累積 (描画スレッドからのみ触るので同期不要) ----
    private static int scriptCalls;
    private static long scriptNanos;
    private static int vehiclesDrawn;
    private static int vehicleCacheHits;
    private static int vehicleCacheMisses;
    private static int objectCacheHits;
    private static int objectCacheMisses;

    // ---- 直近フレームの確定値 (表示用) ----
    private static int lastScriptCalls;
    private static long lastScriptNanos;
    private static int lastVehiclesDrawn;
    private static int lastVehicleCacheHits;
    private static int lastVehicleCacheMisses;
    private static int lastObjectCacheHits;
    private static int lastObjectCacheMisses;

    // ---- 移動平均 (1 フレームだけ見ても揺れるため) ----
    private static final int WINDOW = 20;
    private static final int[] callsWindow = new int[WINDOW];
    private static final long[] nanosWindow = new long[WINDOW];
    private static int windowIndex;
    private static int windowFilled;

    private RtmuProfiler() {
    }

    /**
     * スクリプト関数呼び出し 1 回ぶんを記録する。
     * {@link jp.ngt.ngtlib.io.ScriptUtil#doScriptFunction} から呼ばれる
     * (描画用 render も含め全てのスクリプト呼び出しがここを通る)。
     */
    public static void addScriptCall(long nanos) {
        if (!enabled) {
            return;
        }
        scriptCalls++;
        scriptNanos += nanos;
    }

    public static void addVehicle(boolean cacheHit) {
        if (!enabled) {
            return;
        }
        vehiclesDrawn++;
        if (cacheHit) {
            vehicleCacheHits++;
        } else {
            vehicleCacheMisses++;
        }
    }

    public static void addObject(boolean cacheHit) {
        if (!enabled) {
            return;
        }
        if (cacheHit) {
            objectCacheHits++;
        } else {
            objectCacheMisses++;
        }
    }

    /** 1 フレーム分を確定して累積をリセットする (描画の最後に呼ぶ)。 */
    public static void endFrame() {
        if (!enabled) {
            return;
        }
        lastScriptCalls = scriptCalls;
        lastScriptNanos = scriptNanos;
        lastVehiclesDrawn = vehiclesDrawn;
        lastVehicleCacheHits = vehicleCacheHits;
        lastVehicleCacheMisses = vehicleCacheMisses;
        lastObjectCacheHits = objectCacheHits;
        lastObjectCacheMisses = objectCacheMisses;

        callsWindow[windowIndex] = scriptCalls;
        nanosWindow[windowIndex] = scriptNanos;
        windowIndex = (windowIndex + 1) % WINDOW;
        if (windowFilled < WINDOW) {
            windowFilled++;
        }

        scriptCalls = 0;
        scriptNanos = 0L;
        vehiclesDrawn = 0;
        vehicleCacheHits = 0;
        vehicleCacheMisses = 0;
        objectCacheHits = 0;
        objectCacheMisses = 0;
    }

    /** 計測を切り替える。OFF にしたら数値もリセットして残り香を残さない。 */
    public static void toggle() {
        enabled = !enabled;
        if (!enabled) {
            reset();
        }
    }

    public static void reset() {
        scriptCalls = 0;
        scriptNanos = 0L;
        vehiclesDrawn = 0;
        vehicleCacheHits = 0;
        vehicleCacheMisses = 0;
        objectCacheHits = 0;
        objectCacheMisses = 0;
        lastScriptCalls = 0;
        lastScriptNanos = 0L;
        lastVehiclesDrawn = 0;
        lastVehicleCacheHits = 0;
        lastVehicleCacheMisses = 0;
        lastObjectCacheHits = 0;
        lastObjectCacheMisses = 0;
        java.util.Arrays.fill(callsWindow, 0);
        java.util.Arrays.fill(nanosWindow, 0L);
        windowIndex = 0;
        windowFilled = 0;
    }

    // ---- 表示用アクセサ ----

    public static int lastScriptCalls() {
        return lastScriptCalls;
    }

    public static double lastScriptMillis() {
        return lastScriptNanos / 1.0e6D;
    }

    public static int lastVehiclesDrawn() {
        return lastVehiclesDrawn;
    }

    public static int lastVehicleCacheHits() {
        return lastVehicleCacheHits;
    }

    public static int lastVehicleCacheMisses() {
        return lastVehicleCacheMisses;
    }

    public static int lastObjectCacheHits() {
        return lastObjectCacheHits;
    }

    public static int lastObjectCacheMisses() {
        return lastObjectCacheMisses;
    }

    /** 移動平均のスクリプト呼び出し回数/フレーム。 */
    public static double avgScriptCalls() {
        if (windowFilled == 0) {
            return 0.0D;
        }
        long sum = 0L;
        for (int i = 0; i < windowFilled; i++) {
            sum += callsWindow[i];
        }
        return (double) sum / windowFilled;
    }

    /** 移動平均のスクリプト実行時間 (ms/フレーム)。<b>これが軽量化の主指標</b>。 */
    public static double avgScriptMillis() {
        if (windowFilled == 0) {
            return 0.0D;
        }
        long sum = 0L;
        for (int i = 0; i < windowFilled; i++) {
            sum += nanosWindow[i];
        }
        return sum / (double) windowFilled / 1.0e6D;
    }
}
