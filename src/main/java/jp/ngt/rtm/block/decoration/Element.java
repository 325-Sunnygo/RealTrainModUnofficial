package jp.ngt.rtm.block.decoration;

import jp.ngt.rtm.block.decoration.Face.FaceType;

/**
 * 装飾ブロックの要素 (面の束)。本家 {@code Element} の移植。
 */
public class Element implements Cloneable {
    public String name;
    public Face[] faces;

    @Override
    public Element clone() {
        Element element = new Element();
        element.name = this.name;
        element.faces = new Face[this.faces.length];
        for (int i = 0; i < element.faces.length; ++i) {
            element.faces[i] = this.faces[i].clone();
        }
        return element;
    }

    public void addVec(float[] vec3, boolean lockUV) {
        for (Face face : this.faces) {
            face.addVec(vec3, lockUV);
        }
    }

    /** 本家 getDefaultElement: 各面に別テクスチャを貼った立方体。 */
    public static Element getDefaultElement() {
        Element element = new Element();
        element.name = "default";
        float minU = 0.0F, maxU = 1.0F, minV = 0.0F, maxV = 1.0F;

        Face front = face("front", "minecraft:decoration/deco_platform_side", 0.8F, FaceType.FRONT, new float[][]{
            {0, 1, 1, minU, minV}, {0, 0, 1, minU, maxV}, {1, 0, 1, maxU, maxV}, {1, 1, 1, maxU, minV}});
        Face back = face("back", "minecraft:block/birch_log", 0.8F, FaceType.BACK, new float[][]{
            {1, 1, 0, minU, minV}, {1, 0, 0, minU, maxV}, {0, 0, 0, maxU, maxV}, {0, 1, 0, maxU, minV}});
        Face left = face("left", "realtrainmodunofficial:block/fire_brick", 0.6F, FaceType.LEFT, new float[][]{
            {1, 1, 1, minU, minV}, {1, 0, 1, minU, maxV}, {1, 0, 0, maxU, maxV}, {1, 1, 0, maxU, minV}});
        Face right = face("right", "minecraft:block/bookshelf", 0.6F, FaceType.RIGHT, new float[][]{
            {0, 1, 0, minU, minV}, {0, 0, 0, minU, maxV}, {0, 0, 1, maxU, maxV}, {0, 1, 1, maxU, minV}});
        Face top = face("top", "minecraft:decoration/deco_platform_top", 1.0F, FaceType.TOP, new float[][]{
            {0, 1, 0, minU, minV}, {0, 1, 1, minU, maxV}, {1, 1, 1, maxU, maxV}, {1, 1, 0, maxU, minV}});
        Face bottom = face("bottom", "minecraft:block/stone", 0.5F, FaceType.BOTTOM, new float[][]{
            {0, 0, 1, minU, minV}, {0, 0, 0, minU, maxV}, {1, 0, 0, maxU, maxV}, {1, 0, 1, maxU, minV}});

        element.faces = new Face[]{front, back, left, right, top, bottom};
        return element;
    }

    private static Face face(String name, String texture, float shadow, FaceType type, float[][] vertex) {
        Face face = new Face();
        face.name = name;
        face.texture = texture;
        face.shadow = shadow;
        face.type = type;
        face.vertex = vertex;
        return face;
    }
}
