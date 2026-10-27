package com.portofino.realtrainmodunofficial.client;

import java.lang.reflect.Method;

/** Optional Iris/Oculus pack detection. Active packs use entity-space vertex submission and vanilla shader suppliers. */
public final class ShaderCompat {
    private static Boolean available;
    private static Object irisApiInstance;
    private static Method isShaderPackInUse;

    private ShaderCompat() {}

    /** 1 フレームに 1 回だけ引いた結果。判定はリフレクションなので描画ループから毎回呼ばない。 */
    private static boolean cachedActive;
    /** シェーダーパックの ON/OFF が切り替わるたびに増える。焼き込みの作り直し判定に使う。 */
    private static int epoch;

    /** フレーム頭で 1 回呼ぶ。切り替わったら #epoch が動く。 */
    public static void refresh() {
        boolean now = isShaderPackInUse();
        if (now != cachedActive) {
            cachedActive = now;
            epoch++;
        }
    }

    /** 直近の #refresh 時点でシェーダーパックが有効か (リフレクション無し)。 */
    public static boolean active() {
        return cachedActive;
    }

    public static int epoch() {
        return epoch;
    }

    public static boolean isShaderPackInUse() {
        try {
            if (available == null) {
                init();
            }
            if (!available) {
                return false;
            }
            Object result = isShaderPackInUse.invoke(irisApiInstance);
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void init() {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            irisApiInstance = api.getMethod("getInstance").invoke(null);
            isShaderPackInUse = api.getMethod("isShaderPackInUse");
            available = irisApiInstance != null;
        } catch (Throwable t) {
            available = false;
        }
    }
}
