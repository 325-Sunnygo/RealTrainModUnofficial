package jp.ngt.ngtlib.renderer;

/**
 * 本家 jp.ngt.ngtlib.renderer.GLHelper のスクリプト互換ファサード。
 * 発光系 (setLightmapMaxBrightness/setBrightness) は GLRecorder に BRIGHTNESS として記録し、
 * 再生側が packedLight として使用する (1.7.10 の輝度パック形式は 1.21 と同一レイアウト)。
 */
@SuppressWarnings("unused")
public final class GLHelper {
    private GLHelper() {
    }

    public static void disableLighting() {
        // 1.21 側でライティングはパイプライン管理 — brightness 記録のみで表現
        // (フルブライト化はスクリプトが直後の setLightmapMaxBrightness で行う)
    }

    public static void enableLighting() {
        // 本家: ライティング復帰。RTMU では「環境光へ戻す」= brightness 負値を記録
        // (再生側 replay が負値を packedLight リセットとして扱う)。
        GLRecorder r = GLRecorder.active();
        if (r != null) {
            r.brightness(-1);
        }
    }

    public static void setLightmapMaxBrightness() {
        GLRecorder r = GLRecorder.active();
        if (r != null) {
            r.brightness(0xF000F0);
        }
    }

    public static void setBrightness(int packed) {
        GLRecorder r = GLRecorder.active();
        if (r != null) {
            r.brightness(packed);
        }
    }

    public static void setLightmapCoords(float u, float v) {
        GLRecorder r = GLRecorder.active();
        if (r != null) {
            r.brightness(((int) v << 16) | (int) u);
        }
    }

    // マウスピック系 (ActionParts) は未対応 — 安全に no-op
    public static void startMousePicking(float par1) {
    }

    public static int finishMousePicking() {
        return 0;
    }

    public static double getPickedObjDepth(int i) {
        return 0.0D;
    }

    public static int getPickedObjId(int i) {
        return 0;
    }

    // ---- ディスプレイリスト (NGTO Builder のミニチュアプレビューが使用) ----
    // 本家の GL ディスプレイリストを GLRecorder のサブ記録として実装する:
    // generateGLList → 非 0 ハンドル (0 を返すと JS 側の `!glList` 判定が毎フレーム真になり
    // callList 分岐が永久に実行されず、プレビューが表示されない)。
    private static final java.util.Map<Integer, GLRecorder> GL_LISTS =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicInteger NEXT_LIST_ID =
        new java.util.concurrent.atomic.AtomicInteger(1);
    private static GLRecorder compilePrev;
    private static int compilingId;

    public static int generateGLList() {
        return NEXT_LIST_ID.getAndIncrement();
    }

    /** 非 old 分岐は generateGLList(null) と呼ぶ (引数は未使用)。 */
    public static int generateGLList(Object ignored) {
        return generateGLList();
    }

    public static void startCompile(int list) {
        compilePrev = GLRecorder.active();
        compilingId = list;
        GLRecorder rec = new GLRecorder();
        GL_LISTS.put(list, rec);
        GLRecorder.activate(rec);
    }

    public static void endCompile() {
        if (compilePrev != null) {
            GLRecorder.activate(compilePrev);
        } else {
            GLRecorder.deactivate();
        }
        compilePrev = null;
        compilingId = 0;
    }

    public static void callList(int list) {
        GLRecorder current = GLRecorder.active();
        GLRecorder stored = GL_LISTS.get(list);
        if (current != null && stored != null) {
            current.appendFrom(stored);
        }
    }

    public static void deleteGLList(int list) {
        GL_LISTS.remove(list);
    }

    public static void transform(double x, double y, double z, float yaw, float pitch, float roll) {
        GLRecorder r = GLRecorder.active();
        if (r != null) {
            r.translate((float) x, (float) y, (float) z);
            r.rotate(yaw, 0.0F, 1.0F, 0.0F);
            r.rotate(-pitch, 1.0F, 0.0F, 0.0F);
            r.rotate(roll, 0.0F, 0.0F, 1.0F);
        }
    }

    // ---- テクスチャ UV スクロール (方向幕/回転計器) ----
    // 本家は GL_TEXTURE 行列を push/translate/pop するが、1.21 の RTMU 描画では
    // テクスチャ行列を直接いじれない。
    // renderer.setUvOffset に振る経路) が担うため、Java.type で GLHelper を掴んだスクリプト
    // 用にここでは安全な no-op を提供する。
    public static void preMoveTexUV(float u, float v) {
    }

    public static void postMoveTexUV() {
    }

    /** 本家setColor: 0xRRGGBB + alpha(0-255) を現在色に設定 (GLRecorder経由)。 */
    public static void setColor(int rgb, int alpha) {
        float r = (rgb >> 16 & 0xFF) / 255.0F;
        float g = (rgb >> 8 & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;
        float a = alpha / 255.0F;
        jp.ngt.ngtlib.renderer.GLRecorder rec = jp.ngt.ngtlib.renderer.GLRecorder.active();
        if (rec != null) {
            rec.color(r, g, b, a);
        }
    }

    /** 本家clearGLList: 1.21はGLリスト非使用のためno-op。 */
    public static void clearGLList() {
    }

    /** 本家initGLList: no-op。 */
    public static void initGLList() {
    }

    /** 本家isCompiling: GLリスト非使用のため常にfalse。 */
    public static boolean isCompiling() {
        return false;
    }

    /** 本家isValid(DisplayList)。 */
    public static boolean isValid(Object par1) {
        return false;
    }

    /** 本家getShaderProgram: 旧GLSL直叩きは非対応。 */
    public static int getShaderProgram(String vsh, String fsh) {
        return -1;
    }

}
