package jp.ngt.ngtlib.renderer;

/**
 * 本家 jp.ngt.ngtlib.renderer.NGTRenderHelper のスクリプト互換。
 * NGTO Builder の renderStatic が
 * {@code renderCustomModel(ModelObj.model, matId, smoothing, Parts.objNames)} で
 * 設置予定位置のブロック枠 (ツール自身のモデルグループ) を描くのに使う。
 * グループ名を GLRecorder に記録し、再生側が本体モデルから該当グループを描く。
 */
@SuppressWarnings("unused")
public final class NGTRenderHelper {
    private NGTRenderHelper() {
    }

    /** (model, matId, smoothing, objNames) — objNames のグループを現在の変換で描画。 */
    public static void renderCustomModel(Object... args) {
        GLRecorder rec = GLRecorder.active();
        if (rec == null || args == null || args.length < 4) {
            return;
        }
        Object names = args[3];
        if (names instanceof String[] arr) {
            for (String name : arr) {
                if (name != null && !name.isEmpty()) {
                    rec.renderParts(name);
                }
            }
        } else if (names instanceof java.util.Collection<?> col) {
            for (Object name : col) {
                if (name != null) {
                    rec.renderParts(String.valueOf(name));
                }
            }
        } else if (names instanceof String single && !single.isEmpty()) {
            rec.renderParts(single);
        }
    }
}
