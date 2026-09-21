package com.portofino.realtrainmodunofficial.client.render;

import jp.ngt.ngtlib.math.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 本家 PartsRenderer.renderLightEffectS の幾何計算 (共通)。
 *
 * <p>列車経路 ({@code GLRecorder} に記録) と 車経路 ({@code ScriptModelRenderer} に
 * 即時描画) の両方から使うため、頂点生成だけをここへ切り出す。
 *
 * <p>頂点は stride 9 = {x,y,z,u,v,r,g,b,a}。色は 0..1。加算合成前提。
 */
public final class LightEffectGeometry {

    public static final int GL_TRIANGLES = 4;
    public static final int GL_TRIANGLE_FAN = 6;

    /** 本家 DIV_NUM。 */
    public static final int DIV_NUM = 32;
    /** 本家 BRIGHTNESS_RATE。 */
    public static final double BRIGHTNESS_RATE = 1.0D / 256.0D;

    public static final class Piece {
        public final int mode;
        public final float[] verts;

        Piece(int mode, float[] verts) {
            this.mode = mode;
            this.verts = verts;
        }
    }

    private LightEffectGeometry() {
    }

    /**
     * @param normal    {@link Vec3} なら視線角を計算する (それ以外/ null は正面扱い)
     * @param viewerVec 光源→視点ベクトル (normal が Vec3 のときのみ使う)。null 可
     * @param type      0 = フレア(TRIANGLE_FAN) / 1 = ボリューム(円錐)
     */
    public static List<Piece> compute(Object normal, Vec3 viewerVec, float rL, float rS,
                                      float length, int color, int type, boolean reverse) {
        List<Piece> out = new ArrayList<>(2);

        boolean useVec = normal instanceof Vec3;
        double viewerAngle = 0.0D;
        double distanceSq = 256.0D;
        if (useVec && viewerVec != null) {
            viewerAngle = Math.toDegrees(((Vec3) normal).getAngle(viewerVec));
            distanceSq = viewerVec.dotProduct(viewerVec);
        }
        if (reverse) {
            viewerAngle = net.minecraft.util.Mth.wrapDegrees((float) (viewerAngle + 180.0D));
        }
        if (viewerAngle > 90.0D) {
            viewerAngle = 180.0D - viewerAngle;
        }
        float lightStrength = 1.0F;
        if (viewerAngle > 45.0D) {
            lightStrength = (float) ((90.0D - viewerAngle) / 45.0D);
        }

        final float angleStep = 360.0F / DIV_NUM;

        if (type == 0) {
            float[] verts = new float[(DIV_NUM + 2) * 9];
            int i = 0;
            putVertex(verts, i++, 0.0D, 0.0D, 0.0D, color, 0xFF);
            for (int k = 0; k <= DIV_NUM; k++) {
                float rad = (float) Math.toRadians(k * angleStep);
                putVertex(verts, i++,
                    Math.cos(rad) * rL * lightStrength,
                    Math.sin(rad) * rL * lightStrength,
                    0.0D, 0x000000, 0x00);
            }
            out.add(new Piece(GL_TRIANGLE_FAN, verts));
        } else if (type == 1) {
            float angle = (float) Math.toDegrees(Math.atan2(rL, length));
            float brightness;
            if (viewerAngle < angle) {
                brightness = (float) (1.0D - (viewerAngle / angle));
            } else {
                float b0 = (float) ((viewerAngle - angle) / (90.0D - angle));
                float b1 = (float) (distanceSq * BRIGHTNESS_RATE);
                if (b1 > 1.0F) {
                    b1 = 1.0F;
                }
                brightness = b0 * b1;
            }
            if (brightness > 0.0F) {
                int alpha = (int) (255.0F * brightness);
                float[] fan = new float[(DIV_NUM + 2) * 9];
                int i = 0;
                putVertex(fan, i++, 0.0D, 0.0D, 0.0D, color, alpha);
                for (int k = 0; k <= DIV_NUM; k++) {
                    float rad = (float) Math.toRadians(-k * angleStep);
                    putVertex(fan, i++,
                        Math.cos(rad) * rL,
                        Math.sin(rad) * rL,
                        length, 0x000000, 0x00);
                }
                out.add(new Piece(GL_TRIANGLE_FAN, fan));

                float b3 = (float) (distanceSq * BRIGHTNESS_RATE);
                if (b3 > 1.0F) {
                    b3 = 1.0F;
                }
                float f3 = rS * b3;
                float[] tris = new float[(DIV_NUM + 1) * 3 * 9];
                int j = 0;
                for (int k = 0; k <= DIV_NUM; k++) {
                    float rad = (float) Math.toRadians(k * angleStep);
                    putVertex(tris, j++, Math.cos(rad) * rL, Math.sin(rad) * rL, length, 0x000000, 0x00);
                    putVertex(tris, j++, 0.0D, 0.0D, 0.0D, color, alpha >> 1);
                    putVertex(tris, j++, Math.cos(rad) * f3, Math.sin(rad) * f3, 0.0D, 0x000000, 0x00);
                }
                out.add(new Piece(GL_TRIANGLES, tris));
            }
        }
        return out;
    }

    /** 頂点配列 (stride 9) へ 1 頂点書く。色は 0..1。 */
    private static void putVertex(float[] arr, int index, double x, double y, double z, int rgb, int alpha) {
        int o = index * 9;
        arr[o] = (float) x;
        arr[o + 1] = (float) y;
        arr[o + 2] = (float) z;
        arr[o + 3] = 0.0F;
        arr[o + 4] = 0.0F;
        arr[o + 5] = ((rgb >>> 16) & 0xFF) / 255.0F;
        arr[o + 6] = ((rgb >>> 8) & 0xFF) / 255.0F;
        arr[o + 7] = (rgb & 0xFF) / 255.0F;
        arr[o + 8] = alpha / 255.0F;
    }
}
