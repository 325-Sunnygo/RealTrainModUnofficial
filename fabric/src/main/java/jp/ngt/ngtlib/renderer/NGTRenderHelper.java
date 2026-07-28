package jp.ngt.ngtlib.renderer;

import jp.ngt.ngtlib.renderer.model.Face;
import jp.ngt.ngtlib.renderer.model.GroupObject;
import jp.ngt.ngtlib.renderer.model.TextureCoordinate;

import java.nio.FloatBuffer;
import java.util.List;

/**
 * 本家 jp.ngt.ngtlib.renderer.NGTRenderHelper のスクリプト互換。
 * renderCustomModel 系はグループ名を GLRecorder に記録し、再生側が本体モデルから描く。
 * addFace/addQuadGui 系は NGTTessellator へ頂点を積む。
 */
@SuppressWarnings("unused")
public final class NGTRenderHelper {
    private NGTRenderHelper() {
    }

    /** 本家は 1.7.10 の RenderItem を返す。1.21 に相当物が無いので null。 */
    public static Object getItemRenderer() {
        return null;
    }

    /** (model, matId, smoothing, objNames) — objNames のグループを現在の変換で描画。 */
    public static void renderCustomModel(Object... args) {
        renderNames(collectNames(args, 3), false, args.length > 0 ? args[0] : null);
    }

    /** 本家 renderCustomModelAll: 全グループを描画。 */
    public static void renderCustomModelAll(Object... args) {
        renderNames(null, false, args != null && args.length > 0 ? args[0] : null);
    }

    /** 本家 renderCustomModelExcept: 指定グループ<b>以外</b>を描画。 */
    public static void renderCustomModelExcept(Object... args) {
        renderNames(collectNames(args, 3), true, args != null && args.length > 0 ? args[0] : null);
    }

    /**
     * 本家 renderCustomModelEveryParts(model, matId, except, smoothing, [mode,] parts...)。
     * except の位置が引数 2 なので、そこだけ読んで except 判定する。
     */
    public static void renderCustomModelEveryParts(Object... args) {
        boolean except = args != null && args.length > 2 && Boolean.TRUE.equals(toBoolean(args[2]));
        //(…, except, smoothing, parts...) と (…, except, smoothing, mode, parts...) の両方がある
        int from = args != null && args.length > 4 && args[4] instanceof Number ? 5 : 4;
        renderNames(collectNames(args, from), except, args != null && args.length > 0 ? args[0] : null);
    }

    private static Boolean toBoolean(Object o) {
        if (o instanceof Boolean b) {
            return b;
        }
        if (o instanceof Number n) {
            return n.intValue() != 0;
        }
        return Boolean.FALSE;
    }

    /** 引数 from 以降に散らばるグループ名 (可変長 / 配列 / コレクション) を平坦に集める。 */
    private static java.util.List<String> collectNames(Object[] args, int from) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (args == null) {
            return out;
        }
        for (int i = from; i < args.length; i++) {
            flattenInto(args[i], out);
        }
        return out;
    }

    private static void flattenInto(Object value, java.util.List<String> out) {
        if (value == null) {
            return;
        }
        if (value instanceof String s) {
            if (!s.isEmpty()) {
                out.add(s);
            }
        } else if (value instanceof Object[] arr) {
            for (Object o : arr) {
                flattenInto(o, out);
            }
        } else if (value instanceof java.util.Collection<?> col) {
            for (Object o : col) {
                flattenInto(o, out);
            }
        }
    }

    /**
     * names を描画する。except なら「names 以外の全グループ」。
     * except の解決にはモデルのグループ一覧が要るので、model から取れなければ何もしない。
     */
    private static void renderNames(java.util.List<String> names, boolean except, Object model) {
        GLRecorder rec = GLRecorder.active();
        if (rec == null) {
            return;
        }
        if (!except) {
            if (names == null) {
                for (String name : allGroupNames(model)) {
                    rec.renderParts(name);
                }
            } else {
                for (String name : names) {
                    rec.renderParts(name);
                }
            }
            return;
        }
        java.util.Set<String> skip = new java.util.HashSet<>();
        if (names != null) {
            for (String name : names) {
                skip.add(name.trim().toLowerCase(java.util.Locale.ROOT));
            }
        }
        for (String name : allGroupNames(model)) {
            if (!skip.contains(name.trim().toLowerCase(java.util.Locale.ROOT))) {
                rec.renderParts(name);
            }
        }
    }

    /** モデル (PolygonModel か getGroupObjects を持つ何か) から全グループ名を取る。 */
    private static java.util.List<String> allGroupNames(Object model) {
        java.util.List<String> out = new java.util.ArrayList<>();
        List<GroupObject> groups = null;
        if (model instanceof jp.ngt.ngtlib.renderer.model.PolygonModel pm) {
            groups = pm.getGroupObjects();
        } else if (model != null) {
            try {
                Object v = model.getClass().getMethod("getGroupObjects").invoke(model);
                if (v instanceof List<?> list) {
                    for (Object o : list) {
                        if (o instanceof GroupObject g) {
                            out.add(g.name);
                        }
                    }
                    return out;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return out;
            }
        }
        if (groups != null) {
            for (GroupObject g : groups) {
                out.add(g.name);
            }
        }
        return out;
    }

    /** 本家 addFace: 面の頂点を tessellator へ積む。 */
    public static void addFace(Face face, Object tessellator, boolean smoothing) {
        addFaceWithMatrix(face, tessellator, null, -1, smoothing);
    }

    /**
     * 本家 addFaceWithMatrix: matrix 指定時は index 番目の 4x4 行列を掛けてから積む。
     * matrix が null なら素の座標。
     */
    public static void addFaceWithMatrix(Face face, Object tessellator, FloatBuffer matrix, int index, boolean smoothing) {
        if (face == null || !(tessellator instanceof NGTTessellator tess)) {
            return;
        }
        TextureCoordinate[] coords = face.getTextureCoordinates();
        for (int i = 0; i < face.vertices.length; i++) {
            if (face.vertices[i] == null) {
                continue;
            }
            float u = coords != null && i < coords.length ? coords[i].getU() : 0.0F;
            float v = coords != null && i < coords.length ? coords[i].getV() : 0.0F;
            float x = face.vertices[i].getX();
            float y = face.vertices[i].getY();
            float z = face.vertices[i].getZ();
            if (matrix == null || index < 0) {
                tess.addVertexWithUV(x, y, z, u, v);
            } else {
                int m = index << 4;
                float x0 = x * matrix.get(m) + y * matrix.get(m + 4) + z * matrix.get(m + 8) + matrix.get(m + 12);
                float y0 = x * matrix.get(m + 1) + y * matrix.get(m + 5) + z * matrix.get(m + 9) + matrix.get(m + 13);
                float z0 = x * matrix.get(m + 2) + y * matrix.get(m + 6) + z * matrix.get(m + 10) + matrix.get(m + 14);
                tess.addVertexWithUV(x0, y0, z0, u, v);
            }
        }
    }

    public static void addQuadGuiFaceWithUV(float minX, float minY, float maxX, float maxY, float z,
                                            float uMin, float vMin, float uMax, float vMax) {
        NGTTessellator t = NGTTessellator.instance;
        t.addVertexWithUV(minX, maxY, z, uMin, vMax);
        t.addVertexWithUV(maxX, maxY, z, uMax, vMax);
        t.addVertexWithUV(maxX, minY, z, uMax, vMin);
        t.addVertexWithUV(minX, minY, z, uMin, vMin);
    }

    public static void addQuadGuiFace(float minX, float minY, float maxX, float maxY, float z) {
        NGTTessellator t = NGTTessellator.instance;
        t.addVertex(minX, maxY, z);
        t.addVertex(maxX, maxY, z);
        t.addVertex(maxX, minY, z);
        t.addVertex(minX, minY, z);
    }

    public static void addQuadGuiFaceWithSize(float minX, float minY, float width, float height, float z) {
        addQuadGuiFace(minX, minY, minX + width, minY + height, z);
    }

    /** 枠線 (GL_LINES 相当で辺ごとに 2 頂点)。 */
    public static void addQuadGuiFrame(float minX, float minY, float maxX, float maxY, float z) {
        NGTTessellator t = NGTTessellator.instance;
        t.addVertex(minX, maxY, z);
        t.addVertex(maxX, maxY, z);
        t.addVertex(maxX, maxY, z);
        t.addVertex(maxX, minY, z);
        t.addVertex(maxX, minY, z);
        t.addVertex(minX, minY, z);
        t.addVertex(minX, minY, z);
        t.addVertex(minX, maxY, z);
    }

    public static void addQuadGuiFrameWithSize(float minX, float minY, float width, float height, float z) {
        addQuadGuiFrame(minX, minY, minX + width, minY + height, z);
    }

    /** 本家 setColor(int): パック済み RGB を描画色にする。 */
    public static void setColor(int color) {
        GLRecorder rec = GLRecorder.active();
        if (rec != null) {
            rec.color((color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F, 1.0F);
        }
    }

    /** 本家 translate: 変換行列に平行移動を掛ける (行列は 16 要素)。 */
    public static FloatBuffer translate(FloatBuffer buffer, float moveX, float moveY, float moveZ) {
        return multiplyMatrix(buffer, new float[][]{
                {1.0F, 0.0F, 0.0F, 0.0F},
                {0.0F, 1.0F, 0.0F, 0.0F},
                {0.0F, 0.0F, 1.0F, 0.0F},
                {moveX, moveY, moveZ, 1.0F}});
    }

    /** 本家 rotate: 変換行列に回転を掛ける。angle はラジアン、coordinate は 'X'/'Y'/'Z'。 */
    public static FloatBuffer rotate(FloatBuffer buffer, float angle, char coordinate) {
        float sin = (float) Math.sin(angle);
        float cos = (float) Math.cos(angle);
        switch (coordinate) {
            case 'X', 'x' -> {
                return multiplyMatrix(buffer, new float[][]{
                        {1.0F, 0.0F, 0.0F, 0.0F},
                        {0.0F, cos, sin, 0.0F},
                        {0.0F, -sin, cos, 0.0F},
                        {0.0F, 0.0F, 0.0F, 1.0F}});
            }
            case 'Y', 'y' -> {
                return multiplyMatrix(buffer, new float[][]{
                        {cos, 0.0F, -sin, 0.0F},
                        {0.0F, 1.0F, 0.0F, 0.0F},
                        {sin, 0.0F, cos, 0.0F},
                        {0.0F, 0.0F, 0.0F, 1.0F}});
            }
            case 'Z', 'z' -> {
                return multiplyMatrix(buffer, new float[][]{
                        {cos, sin, 0.0F, 0.0F},
                        {-sin, cos, 0.0F, 0.0F},
                        {0.0F, 0.0F, 1.0F, 0.0F},
                        {0.0F, 0.0F, 0.0F, 1.0F}});
            }
            default -> {
                return buffer;
            }
        }
    }

    private static FloatBuffer multiplyMatrix(FloatBuffer fb, float[][] fa) {
        FloatBuffer buffer = FloatBuffer.allocate(16);
        for (int i = 0; i < 4; ++i) {
            for (int j = 0; j < 4; ++j) {
                float f = fb.get(j) * fa[i][0] + fb.get(4 + j) * fa[i][1]
                        + fb.get(8 + j) * fa[i][2] + fb.get(12 + j) * fa[i][3];
                buffer.put(i * 4 + j, f);
            }
        }
        return buffer;
    }
}
