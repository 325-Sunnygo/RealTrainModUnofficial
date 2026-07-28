package com.portofino.realtrainmodunofficial.client.render;

import java.util.List;

import com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition;

/**
 * 本家 RenderVehicleBase.preRenderBody → RenderUtil.enableCustomLighting の移植。
 * 本家は室内灯の座標ごとに GL の点光源を有効化して車体を照らす。
 */
public final class InteriorLighting {

    /** 本家 glLight の GL_DIFFUSE。 */
    private static final float DIFFUSE = 0.7F;
    /** 本家 glLight の GL_AMBIENT。 */
    private static final float AMBIENT = 0.2F;

    private static float[] positions = null;
    private static float colorR = 1.0F;
    private static float colorG = 1.0F;
    private static float colorB = 1.0F;

    private InteriorLighting() {
    }

    public static boolean isActive() {
        return positions != null;
    }

    /**
     * @param lights   室内灯 (モデル座標)
     * @param rainbow  本家の r < 0.0F 相当 (InteriorLight_Rainbow)。時間で色が回る
     */
    public static void begin(List<VehicleDefinition.LightDefinition> lights, boolean rainbow, long timeMillis) {
        if (lights == null || lights.isEmpty()) {
            positions = null;
            return;
        }
        float[] pos = new float[lights.size() * 3];
        int i = 0;
        for (VehicleDefinition.LightDefinition light : lights) {
            pos[i++] = (float) light.position().x;
            pos[i++] = (float) light.position().y;
            pos[i++] = (float) light.position().z;
        }
        positions = pos;
        if (rainbow) {
            // 本家: hue = (int)(millis / 50 % 24000 * 15) % 360 / 360
            float hue = (float) ((int) ((timeMillis / 50L % 24000L) * 15L) % 360) / 360.0F;
            float[] rgb = hueToRgb(hue);
            colorR = rgb[0];
            colorG = rgb[1];
            colorB = rgb[2];
        } else {
            colorR = 1.0F;
            colorG = 1.0F;
            colorB = 1.0F;
        }
    }

    public static void end() {
        positions = null;
        colorR = 1.0F;
        colorG = 1.0F;
        colorB = 1.0F;
    }

    public static float red() {
        return colorR;
    }

    public static float green() {
        return colorG;
    }

    public static float blue() {
        return colorB;
    }

    /**
     * 頂点 1 個分の明るさ係数。本家の GL ライティングと同じく
     * ambient + diffuse * max(0, dot(N, L)) を光源ごとに足す (距離減衰なし)。
     * @return 1.0 以上になり得る (本家も複数光源で飽和する)。呼び出し側で clamp すること
     */
    public static float factor(float x, float y, float z, float nx, float ny, float nz) {
        float[] pos = positions;
        if (pos == null) {
            return 1.0F;
        }
        float sum = AMBIENT;
        for (int i = 0; i + 2 < pos.length; i += 3) {
            float dx = pos[i] - x;
            float dy = pos[i + 1] - y;
            float dz = pos[i + 2] - z;
            float len = (float) Math.sqrt((double) dx * dx + (double) dy * dy + (double) dz * dz);
            if (len < 1.0E-5F) {
                sum += DIFFUSE;
                continue;
            }
            float dot = (nx * dx + ny * dy + nz * dz) / len;
            if (dot > 0.0F) {
                sum += DIFFUSE * dot;
            }
        }
        return sum;
    }

    /** 本家 convertHueToRGB 相当 (彩度・明度 1 の HSV→RGB)。 */
    private static float[] hueToRgb(float hue) {
        float h = (hue - (float) Math.floor(hue)) * 6.0F;
        int sector = (int) h;
        float f = h - sector;
        float q = 1.0F - f;
        return switch (sector) {
            case 0 -> new float[]{1.0F, f, 0.0F};
            case 1 -> new float[]{q, 1.0F, 0.0F};
            case 2 -> new float[]{0.0F, 1.0F, f};
            case 3 -> new float[]{0.0F, q, 1.0F};
            case 4 -> new float[]{f, 0.0F, 1.0F};
            default -> new float[]{1.0F, 0.0F, q};
        };
    }
}
