package com.myname.legacyloader.bridge.client.renderer;

import com.myname.legacyloader.bridge.client.LegacyIcon;

import java.util.ArrayList;
import java.util.List;

public class LegacyTessellator {
    public static final LegacyTessellator field_78398_a = new LegacyTessellator();
    public static final LegacyTessellator instance = field_78398_a;

    private static final ThreadLocal<List<LegacyRenderBlocks.CapturedElement>> CAPTURED_ELEMENTS =
            ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<List<CapturedQuad>> CAPTURED_QUADS =
            ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<String> CURRENT_ICON = new ThreadLocal<>();
    private static final ThreadLocal<double[]> CURRENT_NORMAL =
            ThreadLocal.withInitial(LegacyTessellator::unsetNormal);
    private static final ThreadLocal<Integer> CURRENT_COLOR =
            ThreadLocal.withInitial(() -> 0xFFFFFFFF);
    private static final ThreadLocal<double[]> CAPTURE_ORIGIN =
            ThreadLocal.withInitial(() -> new double[]{0.0D, 0.0D, 0.0D});
    private static final ThreadLocal<List<Vertex>> VERTICES =
            ThreadLocal.withInitial(ArrayList::new);

    public void func_78382_b() {
        VERTICES.get().clear();
        CURRENT_NORMAL.set(unsetNormal());
        CURRENT_COLOR.set(0xFFFFFFFF);
    }

    public void startDrawingQuads() {
        func_78382_b();
    }

    public int func_78381_a() {
        flushQuads();
        VERTICES.get().clear();
        return 0;
    }

    public void draw() {
        func_78381_a();
    }

    public void func_78375_b(float x, float y, float z) {
        CURRENT_NORMAL.set(new double[]{x, y, z});
    }
    public void setNormal(float x, float y, float z) { func_78375_b(x, y, z); }
    public void func_78380_c(int color) {}
    public void setBrightness(int color) { func_78380_c(color); }
    public void func_78386_a(float red, float green, float blue) {
        CURRENT_COLOR.set(packBlockColor(red, green, blue));
    }
    public void setColorOpaque_F(float red, float green, float blue) { func_78386_a(red, green, blue); }
    static void setCurrentColor(float red, float green, float blue) {
        CURRENT_COLOR.set(packBlockColor(red, green, blue));
    }

    /** 現在の色 (ARGB packed)。OBJ/TESR 描画で glColor3f の値を反映するのに使う。 */
    public static int currentColor() {
        Integer c = CURRENT_COLOR.get();
        return c == null ? 0xFFFFFFFF : c;
    }

    /** 色を白 (0xFFFFFFFF) にリセット。TESR 描画開始時に呼ぶ。 */
    public static void resetCurrentColor() {
        CURRENT_COLOR.set(0xFFFFFFFF);
    }

    public void func_78374_a(double x, double y, double z, double u, double v) {
        double[] normal = CURRENT_NORMAL.get();
        VERTICES.get().add(new Vertex(x, y, z, u, v, CURRENT_ICON.get(),
                normal[0], normal[1], normal[2], CURRENT_COLOR.get(), false, false));
    }

    public void addVertexWithUV(double x, double y, double z, double u, double v) {
        func_78374_a(x, y, z, u, v);
    }

    public void func_78377_a(double x, double y, double z) {
        double[] normal = CURRENT_NORMAL.get();
        VERTICES.get().add(new Vertex(x, y, z, 0.0D, 0.0D, CURRENT_ICON.get(),
                normal[0], normal[1], normal[2], CURRENT_COLOR.get(), false, false));
    }

    public void addVertex(double x, double y, double z) {
        func_78377_a(x, y, z);
    }

    public static List<LegacyRenderBlocks.CapturedElement> consumeCapturedElements() {
        field_78398_a.flushQuads();
        VERTICES.get().clear();
        List<LegacyRenderBlocks.CapturedElement> captured = CAPTURED_ELEMENTS.get();
        List<LegacyRenderBlocks.CapturedElement> result = new ArrayList<>(captured);
        captured.clear();
        return result;
    }

    public static List<CapturedQuad> consumeCapturedQuads() {
        field_78398_a.flushQuads();
        VERTICES.get().clear();
        List<CapturedQuad> captured = CAPTURED_QUADS.get();
        List<CapturedQuad> result = new ArrayList<>(captured);
        captured.clear();
        return result;
    }

    public static void clearCapturedElements() {
        VERTICES.get().clear();
        CAPTURED_ELEMENTS.get().clear();
        CAPTURED_QUADS.get().clear();
        CURRENT_ICON.remove();
        CURRENT_NORMAL.set(unsetNormal());
        CURRENT_COLOR.set(0xFFFFFFFF);
        CAPTURE_ORIGIN.set(new double[]{0.0D, 0.0D, 0.0D});
        LegacyGL11.resetMatrixStack();
    }

    public static void setCurrentIcon(LegacyIcon icon) {
        CURRENT_ICON.set(icon != null ? icon.getIconName() : null);
    }

    public static void beginCaptureAt(int x, int y, int z) {
        CAPTURE_ORIGIN.set(new double[]{x, y, z});
        LegacyGL11.resetMatrixStack();
    }

    public static void captureQuad(String iconName, double[][] xyz, double[][] uv) {
        captureQuad(iconName, xyz, uv, 0.0D, 0.0D, 0.0D, false, CURRENT_COLOR.get(), false, false);
    }

    public static void captureQuad(String iconName, double[][] xyz, double[][] uv,
                                   double nx, double ny, double nz, double shade) {
        int color = packBlockColor((float) shade, (float) shade, (float) shade);
        captureQuad(iconName, xyz, uv, nx, ny, nz, true, color, true, true);
    }

    private static void captureQuad(String iconName, double[][] xyz, double[][] uv,
                                    double nx, double ny, double nz, boolean hasNormal, int color,
                                    boolean orientToNormal, boolean oneSided) {
        if (xyz == null || xyz.length < 4) return;
        List<Vertex> quad = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            double u = uv != null && uv.length > i && uv[i] != null && uv[i].length > 0 ? uv[i][0] : 0.0D;
            double v = uv != null && uv.length > i && uv[i] != null && uv[i].length > 1 ? uv[i][1] : 0.0D;
            quad.add(new Vertex(xyz[i][0], xyz[i][1], xyz[i][2], u, v, iconName,
                    hasNormal ? nx : Double.NaN, hasNormal ? ny : Double.NaN, hasNormal ? nz : Double.NaN,
                    color, orientToNormal, oneSided));
        }
        addQuad(quad);
    }

    private void flushQuads() {
        List<Vertex> vertices = VERTICES.get();
        for (int i = 0; i + 3 < vertices.size(); i += 4) {
            addQuad(vertices.subList(i, i + 4));
        }
    }

    private static void addQuad(List<Vertex> quad) {
        List<Vertex> transformed = new ArrayList<>(4);
        double[] origin = CAPTURE_ORIGIN.get();
        for (Vertex vertex : quad) {
            double[] xyz = LegacyGL11.transform(vertex.x, vertex.y, vertex.z);
            transformed.add(new Vertex(xyz[0] - origin[0], xyz[1] - origin[1], xyz[2] - origin[2],
                    vertex.u, vertex.v, vertex.iconName, vertex.nx, vertex.ny, vertex.nz, vertex.color,
                    vertex.orientToNormal, vertex.oneSided));
        }

        if (shouldOrientToNormal(transformed)) {
            orientToLegacyNormal(transformed);
        }

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (Vertex vertex : transformed) {
            minX = Math.min(minX, vertex.x);
            minY = Math.min(minY, vertex.y);
            minZ = Math.min(minZ, vertex.z);
            maxX = Math.max(maxX, vertex.x);
            maxY = Math.max(maxY, vertex.y);
            maxZ = Math.max(maxZ, vertex.z);
        }
        minX = clampModel(minX);
        minY = clampModel(minY);
        minZ = clampModel(minZ);
        maxX = clampModel(maxX);
        maxY = clampModel(maxY);
        maxZ = clampModel(maxZ);
        if (maxX <= minX && maxY <= minY && maxZ <= minZ) return;
        double thickness = 1.0D / 16.0D;
        if (maxX <= minX) { minX = clampModel(minX - thickness / 2.0D); maxX = clampModel(maxX + thickness / 2.0D); }
        if (maxY <= minY) { minY = clampModel(minY - thickness / 2.0D); maxY = clampModel(maxY + thickness / 2.0D); }
        if (maxZ <= minZ) { minZ = clampModel(minZ - thickness / 2.0D); maxZ = clampModel(maxZ + thickness / 2.0D); }

        List<CapturedQuad> capturedQuads = CAPTURED_QUADS.get();
        List<LegacyRenderBlocks.CapturedElement> capturedElements = CAPTURED_ELEMENTS.get();
        if (capturedQuads.size() < 4096) capturedQuads.add(new CapturedQuad(transformed));
        if (capturedElements.size() < 128) {
            capturedElements.add(new LegacyRenderBlocks.CapturedElement(minX, minY, minZ, maxX, maxY, maxZ));
        }
    }

    private static double clampModel(double value) {
        return Math.max(-2.0D, Math.min(4.0D, value));
    }

    private static void orientToLegacyNormal(List<Vertex> quad) {
        if (quad.size() < 4) return;
        Vertex first = quad.get(0);
        if (Double.isNaN(first.nx) || Double.isNaN(first.ny) || Double.isNaN(first.nz)) return;
        double normalLength = first.nx * first.nx + first.ny * first.ny + first.nz * first.nz;
        if (normalLength <= 1.0E-8D) return;

        Vertex a = quad.get(0);
        Vertex b = quad.get(1);
        Vertex c = quad.get(2);
        double ux = b.x - a.x, uy = b.y - a.y, uz = b.z - a.z;
        double vx = c.x - a.x, vy = c.y - a.y, vz = c.z - a.z;
        double gx = uy * vz - uz * vy;
        double gy = uz * vx - ux * vz;
        double gz = ux * vy - uy * vx;
        double dot = gx * first.nx + gy * first.ny + gz * first.nz;
        if (dot < 0.0D) {
            Vertex v1 = quad.get(1);
            quad.set(1, quad.get(3));
            quad.set(3, v1);
        }
    }

    private static boolean shouldOrientToNormal(List<Vertex> quad) {
        return !quad.isEmpty() && quad.get(0).orientToNormal;
    }

    private static double[] unsetNormal() {
        return new double[]{Double.NaN, Double.NaN, Double.NaN};
    }

    private static int packBlockColor(float red, float green, float blue) {
        int r = clampColor(red);
        int g = clampColor(green);
        int b = clampColor(blue);
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }

    private static int clampColor(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255.0F)));
    }

    public static class CapturedQuad {
        public final double[] x = new double[4];
        public final double[] y = new double[4];
        public final double[] z = new double[4];
        public final double[] u = new double[4];
        public final double[] v = new double[4];
        public final int[] color = new int[4];
        public final String iconName;
        public final boolean hasNormal;
        public final boolean oneSided;

        CapturedQuad(List<Vertex> vertices) {
            String icon = null;
            boolean normal = false;
            boolean singleSided = false;
            for (int i = 0; i < 4; i++) {
                Vertex vertex = vertices.get(i);
                x[i] = vertex.x;
                y[i] = vertex.y;
                z[i] = vertex.z;
                u[i] = vertex.u;
                v[i] = vertex.v;
                color[i] = vertex.color;
                normal |= !Double.isNaN(vertex.nx) && !Double.isNaN(vertex.ny) && !Double.isNaN(vertex.nz);
                singleSided |= vertex.oneSided;
                if (icon == null) icon = vertex.iconName;
            }
            this.iconName = icon;
            this.hasNormal = normal;
            this.oneSided = singleSided;
        }
    }

    private static class Vertex {
        final double x;
        final double y;
        final double z;
        final double u;
        final double v;
        final String iconName;
        final double nx;
        final double ny;
        final double nz;
        final int color;
        final boolean orientToNormal;
        final boolean oneSided;

        Vertex(double x, double y, double z, double u, double v, String iconName,
               double nx, double ny, double nz, int color, boolean orientToNormal, boolean oneSided) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.u = u;
            this.v = v;
            this.iconName = iconName;
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
            this.color = color;
            this.orientToNormal = orientToNormal;
            this.oneSided = oneSided;
        }
    }
}
