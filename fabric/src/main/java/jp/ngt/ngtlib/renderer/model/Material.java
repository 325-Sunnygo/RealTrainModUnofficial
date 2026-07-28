package jp.ngt.ngtlib.renderer.model;

/**
 * 本家 jp.ngt.ngtlib.renderer.model.Material の最小移植 (スクリプト可視フィールドのみ)。
 * スクリプトは material.texture を bindTexture に渡す。
 */
public class Material {
    public Object texture;

    public Material(Object texture) {
        this.texture = texture;
    }
}
