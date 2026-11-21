package jp.ngt.rtm.block.decoration;

/**
 * 装飾ブロックの 1 面。本家 {@code jp.ngt.rtm.block.decoration.Face} の移植。
 * JSON にそのまま出入りする (Gson)。
 */
public class Face implements Cloneable {
    public String name;
    /** 1.12 形式のテクスチャパス (例 {@code minecraft:blocks/log_birch})。 */
    public String texture;
    /** 上:1.0, 前後:0.8, 左右:0.6, 下:0.5 */
    public float shadow;
    public FaceType type;
    /** {x,y,z,u,v} x4 */
    public float[][] vertex;

    @Override
    public Face clone() {
        Face face = new Face();
        face.name = this.name;
        face.texture = this.texture;
        face.shadow = this.shadow;
        face.type = this.type;
        face.vertex = new float[this.vertex.length][];
        for (int i = 0; i < face.vertex.length; ++i) {
            face.vertex[i] = this.vertex[i].clone();
        }
        return face;
    }

    public void addVec(float[] vec3, boolean lockUV) {
        for (float[] vtx : this.vertex) {
            addVecToVertex(vtx, this.type, vec3, lockUV);
        }
    }

    public static void addVecToVertex(float[] vtx, FaceType faceType, float[] vec3, boolean lockUV) {
        for (int i = 0; i < 3; ++i) {
            vtx[i] += vec3[i];
        }
        if (lockUV && faceType != null) {
            float[] uv = faceType.func.vertexToUV(vtx[0], vtx[1], vtx[2]);
            vtx[3] = uv[0];
            vtx[4] = uv[1];
        }
    }

    /** 本家 getDefaultFace: 上向きの 1 面。 */
    public static Face getDefaultFace() {
        float minU = 0.0F, maxU = 1.0F, minV = 0.0F, maxV = 1.0F;
        Face top = new Face();
        top.name = "top";
        top.texture = "minecraft:decoration/deco_platform_top";
        top.shadow = 1.0F;
        top.type = FaceType.TOP;
        top.vertex = new float[][]{
            {0.0F, 1.0F, 1.0F, minU, minV},
            {1.0F, 1.0F, 1.0F, minU, maxV},
            {1.0F, 1.0F, 0.0F, maxU, maxV},
            {0.0F, 1.0F, 0.0F, maxU, minV}
        };
        return top;
    }

    public enum FaceType {
        TOP((x, y, z) -> new float[]{1.0F - z, x}),
        BOTTOM((x, y, z) -> new float[]{z, x}),
        LEFT((x, y, z) -> new float[]{1.0F - z, 1.0F - y}),
        RIGHT((x, y, z) -> new float[]{z, 1.0F - y}),
        FRONT((x, y, z) -> new float[]{x, 1.0F - y}),
        BACK((x, y, z) -> new float[]{1.0F - x, 1.0F - y});

        public final transient UVFunc func;

        FaceType(UVFunc par1) {
            this.func = par1;
        }
    }

    public interface UVFunc {
        float[] vertexToUV(float x, float y, float z);
    }
}
