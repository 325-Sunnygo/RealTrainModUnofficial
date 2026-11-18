package jp.tokyokinkou.tims;

/** E233 半自動ドア状態ブリッジ。 */
public final class E233SemiAutoRuntime {
    private E233SemiAutoRuntime() {
    }

    public static SemiState snapshot(Object entity) {
        return new SemiState();
    }

    public static void captureRenderRay(Object entity) {
    }

    public static final class SemiState {
        public final boolean active = false;
        public final float[] move = new float[8];
    }
}
