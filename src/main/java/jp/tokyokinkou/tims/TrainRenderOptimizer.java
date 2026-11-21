package jp.tokyokinkou.tims;

/** TIMS 描画最適化ブリッジ。RTMU では最適化せず元の描画を使う。 */
public final class TrainRenderOptimizer {
    private TrainRenderOptimizer() {
    }

    public static Object wrap(Object renderer, Object modelObject, Object part) {
        return part;
    }

    public static Coverage coverage(Object modelObject, String prefix) {
        return new Coverage();
    }

    public static Object original(Object part) {
        return part;
    }

    public static Object filterGroup(Object modelObject, Object group, String name) {
        return group;
    }

    public static Object begin(Object entity, float partialTick) {
        return null;
    }

    public static void end(Object state) {
    }

    public static boolean isDistant() {
        return false;
    }

    public static final class Coverage {
        public boolean get(int materialId) {
            return true;
        }
    }
}
