package jp.ngt.ngtlib.renderer;

import java.util.ArrayList;
import java.util.List;

/**
 * 本家 jp.ngt.ngtlib.renderer.NGTTessellator (Tessellator ラッパ) のスクリプト互換。
 * 頂点列を蓄積し、draw で GLRecorder に DRAW_TESS として記録する。
 */
@SuppressWarnings("unused")
public final class NGTTessellator {
    public static final NGTTessellator instance = new NGTTessellator();

    private static final int GL_QUADS = 7;

    private final List<float[]> verts = new ArrayList<>();
    private int mode = GL_QUADS;
    private float r = 1.0F, g = 1.0F, b = 1.0F, a = 1.0F;
    /** setTranslation/addTranslation の頂点オフセット。 */
    private float xOffset, yOffset, zOffset;
    /** setTextureUV で先に指定された UV (次の addVertex が使う)。 */
    private float textureU, textureV;
    private boolean hasTexture;
    /** disableColor 中は色を送らない (白扱い)。 */
    private boolean colorDisabled;

    private NGTTessellator() {
    }

    public void startDrawingQuads() {
        this.startDrawing(GL_QUADS);
    }

    public void startDrawing(int mode) {
        this.mode = mode;
        this.verts.clear();
        this.r = this.g = this.b = this.a = 1.0F;
        this.xOffset = this.yOffset = this.zOffset = 0.0F;
        this.textureU = this.textureV = 0.0F;
        this.hasTexture = false;
        this.colorDisabled = false;
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
     * これが未実装だったため、本家の電線スクリプトが動いていなかった。
     */
    public void setColorOpaque_I(int color) {
        this.setColorRGBA((color >>> 16) & 0xFF, (color >>> 8) & 0xFF, color & 0xFF, 255);
    }

    /** 本家 setColorOpaque: 0-255 の RGB を不透明色として設定する。 */
    public void setColorOpaque(int red, int green, int blue) {
        this.setColorRGBA(red, green, blue, 255);
    }

    /** 本家 disableColor: 以降の頂点色を無効化する (白で送る)。 */
    public void disableColor() {
        this.colorDisabled = true;
    }

    /**
     * 本家 setTextureUV: 次の addVertex が使う UV を先に指定する。
     * addVertexWithUV を使わず「UV を置いてから座標を置く」書き方のスクリプト用。
     */
    public void setTextureUV(double u, double v) {
        this.hasTexture = true;
        this.textureU = (float) u;
        this.textureV = (float) v;
    }

    /** 本家 setTranslation: 以降の全頂点に加算するオフセットを設定する。 */
    public void setTranslation(double x, double y, double z) {
        this.xOffset = (float) x;
        this.yOffset = (float) y;
        this.zOffset = (float) z;
    }

    /** 本家 addTranslation: 現在のオフセットに加算する。 */
    public void addTranslation(double x, double y, double z) {
        this.xOffset += (float) x;
        this.yOffset += (float) y;
        this.zOffset += (float) z;
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
        this.textureU = (float) u;
        this.textureV = (float) v;
        this.hasTexture = true;
        this.addVertex(x, y, z);
    }

    public void addVertex(double x, double y, double z) {
        float cr = this.colorDisabled ? 1.0F : this.r;
        float cg = this.colorDisabled ? 1.0F : this.g;
        float cb = this.colorDisabled ? 1.0F : this.b;
        float ca = this.colorDisabled ? 1.0F : this.a;
        this.verts.add(new float[]{
                (float) x + this.xOffset, (float) y + this.yOffset, (float) z + this.zOffset,
                this.hasTexture ? this.textureU : 0.0F, this.hasTexture ? this.textureV : 0.0F,
                cr, cg, cb, ca});
    }

    /**
     * 本家 getVertexState 相当の頂点スナップショット。
     * 本家は半透明面の奥行きソート用に使うが、1.21 では BufferBuilder 側が
     * ソートするため、ここは「積んだ頂点をそのまま保持して復元できる」ことだけ保証する。
     */
    public static final class TesselatorVertexState {
        private final List<float[]> verts;
        private final int mode;

        TesselatorVertexState(List<float[]> verts, int mode) {
            this.verts = verts;
            this.mode = mode;
        }

        public int getVertexCount() {
            return this.verts.size();
        }

        public int getDrawMode() {
            return this.mode;
        }
    }

    public TesselatorVertexState getVertexState(float x, float y, float z) {
        List<float[]> copy = new ArrayList<>(this.verts.size());
        for (float[] v : this.verts) {
            copy.add(v.clone());
        }
        return new TesselatorVertexState(copy, this.mode);
    }

    public void setVertexState(Object state) {
        if (!(state instanceof TesselatorVertexState s)) {
            return;
        }
        this.verts.clear();
        for (float[] v : s.verts) {
            this.verts.add(v.clone());
        }
        this.mode = s.mode;
    }

    /** 本家 draw は積んだバイト数を返す。値を見るスクリプトがあるので合わせる。 */
    public int draw() {
        int count = this.verts.size();
        GLRecorder rec = GLRecorder.active();
        if (rec != null && count > 0) {
            float[] flat = new float[count * 9];
            for (int i = 0; i < count; i++) {
                System.arraycopy(this.verts.get(i), 0, flat, i * 9, 9);
            }
            rec.drawTess(new GLRecorder.TessDraw(this.mode, flat));
        }
        this.verts.clear();
        this.xOffset = this.yOffset = this.zOffset = 0.0F;
        return count * 32;
    }
}
