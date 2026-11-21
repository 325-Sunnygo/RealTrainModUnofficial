package jp.ngt.ngtlib.renderer.model;

/**
 * 本家 jp.ngt.ngtlib.renderer.model.Material の最小移植 (スクリプト可視フィールドのみ)。
 * スクリプトは material.texture を bindTexture に渡す。
 */
public class Material {
    public final byte id;
    public Object texture;

    public Material(Object texture) {
        this((byte) 0, texture);
    }

    public Material(byte id, Object texture) {
        this.id = id;
        this.texture = texture;
    }
}
