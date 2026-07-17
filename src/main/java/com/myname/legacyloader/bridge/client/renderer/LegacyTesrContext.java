package com.myname.legacyloader.bridge.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4d;
import org.joml.Matrix4f;

/**
 * 1.7.10 TESR (TileEntitySpecialRenderer) の OBJ 描画を現行 1.21 の描画バッファへ橋渡しする
 * スレッドローカルなコンテキスト。
 *
 * <p>1.7.10 の TESR は GL11 の即時モードで OBJ を描く:
 * <pre>glPushMatrix; glTranslatef; glRotatef; bindTexture; glColor3f; model.renderAll(); glPopMatrix;</pre>
 * ここでは BlockEntityRenderer が render() で {@link #begin} し、mod の renderTileEntityAt を
 * 呼ぶ。mod の glTranslatef/glRotatef は {@link LegacyGL11} の行列に積まれ、bindTexture は
 * {@link #setTexture}、glColor3f は {@link LegacyTessellator} に入る。OBJ の
 * {@code renderAll()} が {@link #emitTriangle} を呼ぶと、PoseStack × GL 行列で頂点を変換して
 * 現行の VertexConsumer に流す。
 */
public final class LegacyTesrContext {

    private static final ThreadLocal<LegacyTesrContext> CURRENT = new ThreadLocal<>();

    private final PoseStack poseStack;
    private final MultiBufferSource bufferSource;
    private final int packedLight;
    private ResourceLocation texture;

    private LegacyTesrContext(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        this.poseStack = poseStack;
        this.bufferSource = bufferSource;
        this.packedLight = packedLight;
    }

    public static LegacyTesrContext current() {
        return CURRENT.get();
    }

    /** TESR 描画開始。GL 行列と色をリセットして新しいコンテキストを積む。 */
    public static LegacyTesrContext begin(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        LegacyGL11.resetMatrixStack();
        LegacyTessellator.resetCurrentColor();
        LegacyTesrContext ctx = new LegacyTesrContext(poseStack, bufferSource, packedLight);
        CURRENT.set(ctx);
        return ctx;
    }

    public static void end() {
        CURRENT.remove();
        LegacyGL11.resetMatrixStack();
    }

    /** bindTexture (TESR.func_147499_a) で現在のテクスチャを設定。 */
    public void setTexture(ResourceLocation texture) {
        this.texture = texture;
    }

    /**
     * OBJ の三角形 1 枚を現行バッファへ。entityCutoutNoCull は QUADS モードなので、
     * 第 4 頂点に p2 を複製した縮退クアッドとして流す (3 頂点のまま流すと
     * 4 頂点区切りがズレて全ジオメトリが歪む)。
     */
    public void emitTriangle(double[] p0, double[] p1, double[] p2,
                             float[] uv0, float[] uv1, float[] uv2,
                             float[] normal) {
        emitQuad(p0, p1, p2, p2, uv0, uv1, uv2, uv2, normal);
    }

    /**
     * OBJ の四角形 1 枚を現行バッファへ。頂点は OBJ ローカル座標 (±0.5 中心)。
     * PoseStack × GL 行列で変換して VertexConsumer に流す。UV は OBJ の vt。
     * 1.7.10 の TESR は背面カリング有効で描く (信号機のフード等は内外二重シェルで、
     * カリング無しだと同座標の表裏 2 枚が Z-fighting する) ため entityCutout を使う。
     * 頂点順は OBJ のまま = 1.7.10 と同じ CCW 前面なのでカリング挙動も一致する。
     */
    public void emitQuad(double[] p0, double[] p1, double[] p2, double[] p3,
                         float[] uv0, float[] uv1, float[] uv2, float[] uv3,
                         float[] normal) {
        if (texture == null) {
            return;
        }
        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityCutout(texture));
        Matrix4d gl = LegacyGL11.currentMatrix();
        Matrix4f pose = poseStack.last().pose();
        int color = LegacyTessellator.currentColor();
        int a = (color >>> 24) & 0xFF;
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = color & 0xFF;
        if (a == 0) {
            a = 255;
        }
        // 法線を GL 行列で回転 (平行移動なし)
        double nx = gl.m00() * normal[0] + gl.m10() * normal[1] + gl.m20() * normal[2];
        double ny = gl.m01() * normal[0] + gl.m11() * normal[1] + gl.m21() * normal[2];
        double nz = gl.m02() * normal[0] + gl.m12() * normal[1] + gl.m22() * normal[2];
        double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nl > 1.0E-6) {
            nx /= nl;
            ny /= nl;
            nz /= nl;
        }
        emitVertex(vc, pose, gl, p0, uv0, r, g, b, a, (float) nx, (float) ny, (float) nz);
        emitVertex(vc, pose, gl, p1, uv1, r, g, b, a, (float) nx, (float) ny, (float) nz);
        emitVertex(vc, pose, gl, p2, uv2, r, g, b, a, (float) nx, (float) ny, (float) nz);
        emitVertex(vc, pose, gl, p3, uv3, r, g, b, a, (float) nx, (float) ny, (float) nz);
    }

    private void emitVertex(VertexConsumer vc, Matrix4f pose, Matrix4d gl, double[] p, float[] uv,
                            int r, int g, int b, int a, float nx, float ny, float nz) {
        // GL 行列で変換 → PoseStack で変換
        double gx = gl.m00() * p[0] + gl.m10() * p[1] + gl.m20() * p[2] + gl.m30();
        double gy = gl.m01() * p[0] + gl.m11() * p[1] + gl.m21() * p[2] + gl.m31();
        double gz = gl.m02() * p[0] + gl.m12() * p[1] + gl.m22() * p[2] + gl.m32();
        float u = uv != null ? uv[0] : 0.0F;
        float v = uv != null ? uv[1] : 0.0F;
        vc.addVertex(pose, (float) gx, (float) gy, (float) gz)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal((float) nx, (float) ny, (float) nz);
    }
}
