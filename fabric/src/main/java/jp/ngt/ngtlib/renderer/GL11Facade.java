package jp.ngt.ngtlib.renderer;

/**
 * パックスクリプト向け GL11 互換ファサード。
 * ScriptUtil のプリリュードで `var GL11 = Java.type("jp.ngt.ngtlib.renderer.GL11Facade")`
 * とバインドされ、mozilla_compat の importPackage(org.lwjgl.opengl) より優先される。
 * 呼び出しは GLRecorder.active() に記録され、レンダラが PoseStack に再生する。
 * 未対応の関数は無視 (クラッシュさせない)。
 */
@SuppressWarnings("unused")
public final class GL11Facade {
    //よく使われる定数 (値は本物の GL11 と同一)
    public static final int GL_LIGHTING = 0x0B50;
    public static final int GL_BLEND = 0x0BE2;
    public static final int GL_TEXTURE_2D = 0x0DE1;
    public static final int GL_CULL_FACE = 0x0B44;
    public static final int GL_DEPTH_TEST = 0x0B71;
    public static final int GL_SRC_ALPHA = 0x0302;
    public static final int GL_ONE_MINUS_SRC_ALPHA = 0x0303;
    public static final int GL_ONE = 1;
    public static final int GL_ZERO = 0;

    private GL11Facade() {
    }

    private static GLRecorder rec() {
        return GLRecorder.active();
    }

    /**
     * glPushAttrib / glPopAttrib は 1.21 に対応する状態スタックが無い。
     * 記録側 (GLRecorder) は行列とテクスチャ・色しか持たないので、受けるだけの空実装。
     * <p>NGTO Builder 2 が {@code glPushAttrib(GL_ENABLE_BIT)} で囲む箇所があり、
     * 未定義だとそこでスクリプトが止まる。
     */
    public static void glPushAttrib(int mask) {
    }

    public static void glPopAttrib() {
    }

    /** glIsEnabled: 記録経路では実 GL 状態を持たないので、常に false を返す。 */
    public static boolean glIsEnabled(int cap) {
        return false;
    }

    /** glMultMatrix(FloatBuffer): 現在の行列に掛ける。 */
    public static void glMultMatrix(java.nio.FloatBuffer matrix) {
        GLRecorder r = rec();
        if (r == null || matrix == null) {
            return;
        }
        float[] m = new float[16];
        int pos = matrix.position();
        for (int i = 0; i < 16 && matrix.remaining() > i; i++) {
            m[i] = matrix.get(pos + i);
        }
        r.multMatrix(m);
    }

    public static void glMultMatrixf(java.nio.FloatBuffer matrix) {
        glMultMatrix(matrix);
    }

    public static void glPushMatrix() {
        GLRecorder r = rec();
        if (r != null) r.push();
    }

    public static void glPopMatrix() {
        GLRecorder r = rec();
        if (r != null) r.pop();
    }

    public static void glTranslatef(double x, double y, double z) {
        GLRecorder r = rec();
        if (r != null) r.translate((float) x, (float) y, (float) z);
    }

    public static void glTranslated(double x, double y, double z) {
        glTranslatef(x, y, z);
    }

    public static void glRotatef(double deg, double x, double y, double z) {
        GLRecorder r = rec();
        if (r != null) r.rotate((float) deg, (float) x, (float) y, (float) z);
    }

    public static void glRotated(double deg, double x, double y, double z) {
        glRotatef(deg, x, y, z);
    }

    public static void glScalef(double x, double y, double z) {
        GLRecorder r = rec();
        if (r != null) r.scale((float) x, (float) y, (float) z);
    }

    public static void glScaled(double x, double y, double z) {
        glScalef(x, y, z);
    }

    public static void glColor4f(double r0, double g, double b, double a) {
        GLRecorder r = rec();
        if (r != null) r.color((float) r0, (float) g, (float) b, (float) a);
    }

    public static void glColor3f(double r0, double g, double b) {
        glColor4f(r0, g, b, 1.0D);
    }

    //以下は記録不要 (1.21 のレンダーパイプラインが管理) — 無視
    public static void glEnable(int cap) {
    }

    public static void glDisable(int cap) {
    }

    public static void glBlendFunc(int src, int dst) {
    }

    public static void glBindTexture(int target, int tex) {
        //mccompat.TextureUtil の疑似ハンドル → 動的テクスチャ RL に解決して記録
        GLRecorder r = rec();
        if (r != null) {
            r.bindTexture(jp.ngt.mccompat.TextureUtil.getTexture(tex));
        }
    }

    public static void glNormal3f(double x, double y, double z) {
    }

    public static void glLineWidth(double w) {
    }

    public static void glShadeModel(int mode) {
    }

    public static void glDepthMask(boolean flag) {
    }

    public static void glAlphaFunc(int func, double ref) {
    }
}
