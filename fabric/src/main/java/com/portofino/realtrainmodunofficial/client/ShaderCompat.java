package com.portofino.realtrainmodunofficial.client;

import java.lang.reflect.Method;

/**
 * Detects whether an Iris/Oculus shader pack is currently active, via reflection so
 * Iris is not a hard dependency.
 *
 * <p>Used by the model renderer: the "fullbright" fast direct-GL path renders the
 * train body with flat shading and bypasses the shader pipeline, which makes vertex
 * normal smoothing disappear under shaders. When a shader pack is in use we fall back
 * to the buffered, normal-lit render path so smoothing works. No model/normal/light
 * numeric values are changed — only the render path is chosen.</p>
 */
public final class ShaderCompat {
    private static Boolean available;
    private static Object irisApiInstance;
    private static Method isShaderPackInUse;

    private ShaderCompat() {}

    /** 1 フレームに 1 回だけ引いた結果。判定はリフレクションなので描画ループから毎回呼ばない。 */
    private static boolean cachedActive;
    /** シェーダーパックの ON/OFF が切り替わるたびに増える。焼き込みの作り直し判定に使う。 */
    private static int epoch;

    /** フレーム頭で 1 回呼ぶ。切り替わったら {@link #epoch()} が動く。 */
    public static void refresh() {
        boolean now = isShaderPackInUse();
        if (now != cachedActive) {
            cachedActive = now;
            epoch++;
        }
    }

    /** 直近の {@link #refresh()} 時点でシェーダーパックが有効か (リフレクション無し)。 */
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
