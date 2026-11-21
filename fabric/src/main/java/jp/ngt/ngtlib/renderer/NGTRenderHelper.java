package jp.ngt.ngtlib.renderer;

import jp.ngt.ngtlib.renderer.model.Face;
import jp.ngt.ngtlib.renderer.model.GroupObject;
import jp.ngt.ngtlib.renderer.model.TextureCoordinate;

import java.nio.FloatBuffer;
import java.util.List;

/**
 * 本家 jp.ngt.ngtlib.renderer.NGTRenderHelper のスクリプト互換。
 * renderCustomModel 系はグループ名を GLRecorder に記録し、再生側が本体モデルから描く。
 */
@SuppressWarnings("unused")
public final class NGTRenderHelper {
    private NGTRenderHelper() {
    }

    /** Legacy world-item entry point records the real native stack. */
    public static LegacyItemRenderer getItemRenderer() {
        return LegacyItemRenderer.INSTANCE;
    }

    public static void renderCustomModel(Object model, byte material, boolean smoothing, String... names) {
        renderCustomModelEveryParts(model,material,false,smoothing,names);
    }
    public static void renderCustomModelAll(Object model, byte material, boolean smoothing) {
        renderCustomModelEveryParts(model,material,false,smoothing);
    }
    public static void renderCustomModelExcept(Object model, byte material, boolean smoothing, String... names) {
        renderCustomModelEveryParts(model,material,true,smoothing,names);
    }
    public static void renderCustomModelEveryParts(Object model, byte material, boolean except, boolean smoothing, String... names) {
        renderCustomModelEveryParts(model,material,except,smoothing,GL11Facade.GL_TRIANGLES,names);
    }
    public static void renderCustomModelEveryParts(Object model, byte material, boolean except, boolean smoothing, int mode, String... names) {
        if (!(model instanceof jp.ngt.ngtlib.renderer.model.IModelNGT polygon)) {
            throw new IllegalArgumentException("Expected a polygon model");
        }
        IRenderer renderer=PolygonRenderer.INSTANCE;
        renderer.startDrawing(mode);
        for(GroupObject group:polygon.getGroupObjects()) {
            boolean selected=names==null || names.length==0 || (java.util.Arrays.asList(names).contains(group.name) != except);
            if(!selected) continue;
            for(Face face:group.faces) if(face.materialId==material) addFace(face,renderer,smoothing);
        }
        renderer.draw();
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
        if (face == null || !(tessellator instanceof IRenderer tess)) {
            return;
        }
        TextureCoordinate[] coords = face.getTextureCoordinates();
        if (!smoothing && face.faceNormal != null) {
            tess.setNormal(face.faceNormal.getX(), face.faceNormal.getY(), face.faceNormal.getZ());
        }
        for (int i = 0; i < face.vertices.length; i++) {
            if (face.vertices[i] == null) {
                continue;
            }
            if (smoothing && face.vertexNormals != null && i < face.vertexNormals.length && face.vertexNormals[i] != null) {
                var normal = face.vertexNormals[i];
                tess.setNormal(normal.getX(), normal.getY(), normal.getZ());
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
