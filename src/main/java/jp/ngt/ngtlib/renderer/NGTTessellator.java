package jp.ngt.ngtlib.renderer;

import java.util.ArrayList;
import java.util.List;

/**
 * 本家 jp.ngt.ngtlib.renderer.NGTTessellator (Tessellator ラッパ) のスクリプト互換。
 * 頂点列を蓄積し、draw() で GLRecorder に DRAW_TESS として記録する。
 * 再生側が現在のテクスチャ差し替え/輝度で頂点を emit する。
 */
@SuppressWarnings("unused")
public final class NGTTessellator {
    public static final NGTTessellator instance = new NGTTessellator();

    private static final int GL_QUADS = 7;

    private final List<float[]> verts = new ArrayList<>();
    private int mode = GL_QUADS;
    private float r = 1.0F, g = 1.0F, b = 1.0F, a = 1.0F;

    private NGTTessellator() {
    }

    public void startDrawingQuads() {
        this.startDrawing(GL_QUADS);
    }

    public void startDrawing(int mode) {
        this.mode = mode;
        this.verts.clear();
        this.r = this.g = this.b = this.a = 1.0F;
    }

    public void setColorRGBA(int red, int green, int blue, int alpha) {
        this.r = red / 255.0F;
        this.g = green / 255.0F;
        this.b = blue / 255.0F;
        this.a = alpha / 255.0F;
    }

    public void setColorRGBA_I(int color, int alpha) {
        this.setColorRGBA((color >>> 16) & 0xFF, (color >>> 8) & 0xFF, color & 0xFF, alpha);
    }

    public void setColorRGBA_F(float red, float green, float blue, float alpha) {
        this.r = red;
        this.g = green;
        this.b = blue;
        this.a = alpha;
    }

    public void setColorOpaque_F(float red, float green, float blue) {
        this.setColorRGBA_F(red, green, blue, 1.0F);
    }

    /**
     * 本家 Tessellator.setColorOpaque_I: パック済み RGB を不透明色として設定する。
     *
     * <p><b>これが未実装だったため、本家の電線スクリプトが動いていなかった。</b>
     * RenderBasicWire.js は {@code tessellator.setColorOpaque_I(renderer.getColor(tileEntity))} を
     * 呼ぶので、TypeError でスクリプトごと落ち、呼び出し側が「何も描かなかった」と判断して
     * RTMU 独自のハードコード色 (ほぼ黒) のフォールバック描画に切り替わっていた。
     * 結果、既定のワイヤー (BasicWireBlack / シンプルカテナリー) が真っ黒な電線に見えていた。
     */
    public void setColorOpaque_I(int color) {
        this.setColorRGBA((color >>> 16) & 0xFF, (color >>> 8) & 0xFF, color & 0xFF, 255);
    }

    public void setNormal(float x, float y, float z) {
    }

    public void setBrightness(int packed) {
        GLRecorder rec = GLRecorder.active();
        if (rec != null) {
            rec.brightness(packed);
        }
    }

    public void addVertexWithUV(double x, double y, double z, double u, double v) {
        this.verts.add(new float[]{(float) x, (float) y, (float) z, (float) u, (float) v,
                this.r, this.g, this.b, this.a});
    }

    public void addVertex(double x, double y, double z) {
        this.addVertexWithUV(x, y, z, 0.0D, 0.0D);
    }

    public void draw() {
        GLRecorder rec = GLRecorder.active();
        if (rec != null && !this.verts.isEmpty()) {
            float[] flat = new float[this.verts.size() * 9];
            for (int i = 0; i < this.verts.size(); i++) {
                System.arraycopy(this.verts.get(i), 0, flat, i * 9, 9);
            }
            rec.drawTess(new GLRecorder.TessDraw(this.mode, flat));
        }
        this.verts.clear();
    }
}
