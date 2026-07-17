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
        //1.21 側でライティングはパイプライン管理 — brightness 記録のみで表現
    }

    public static void enableLighting() {
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

    //マウスピック系 (ActionParts) は未対応 — 安全に no-op
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

    //ディスプレイリスト系 (NGTO Builder が使用) — 1.21 では毎フレーム再記録のため
    //コンパイルはスキップし、描画呼び出しをそのまま実行させる
    public static int generateGLList() {
        return 0;
    }

    public static void startCompile(int list) {
    }

    public static void endCompile() {
    }

    public static void callList(int list) {
    }

    public static void deleteGLList(int list) {
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
    // テクスチャ行列を直接いじれない。UV オフセットは ScriptModelRenderer 側 (JS シムが
    // renderer.setUvOffset に振る経路) が担うため、Java.type で GLHelper を掴んだスクリプト
    // 用にここでは<b>安全な no-op</b> を提供する。これが無いと DRC1002.js 等が
    // 「preMoveTexUV is not a function」で落ち、素モデル全描画にフォールバックして
    // 隠すはずの部品まで出る (動くと二重に見える) 不具合になっていた。
    public static void preMoveTexUV(float u, float v) {
    }

    public static void postMoveTexUV() {
    }
}
