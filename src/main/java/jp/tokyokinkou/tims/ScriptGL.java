package jp.tokyokinkou.tims;

/** パックスクリプト用 GL ブリッジ。 */
public final class ScriptGL {
    private ScriptGL() {
    }

    public static void glPushMatrix() {
        jp.ngt.ngtlib.renderer.GL11Facade.glPushMatrix();
    }

    public static void glPopMatrix() {
        jp.ngt.ngtlib.renderer.GL11Facade.glPopMatrix();
    }

    public static void glTranslatef(double x, double y, double z) {
        jp.ngt.ngtlib.renderer.GL11Facade.glTranslatef(x, y, z);
    }
}
