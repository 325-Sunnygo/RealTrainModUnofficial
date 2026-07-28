package jp.ngt.rtm.render;

import jp.ngt.ngtlib.renderer.model.TextureSet;

/**
 * 本家 jp.ngt.rtm.render.ModelObject の最小移植。
 * スクリプトは renderer.getModelObject().textures[0].material.texture を参照する。
 * TODO(Phase 4): IModelNGT/TextureSet 完全版に置換。
 */
public class ModelObject {
    public TextureSet[] textures;

    /**
     * 本家 IModelNGT 相当 (スクリプトは modelObj.model を Parts.getObjects に渡す)。
     * 実体は jp.ngt.ngtlib.renderer.model.PolygonModel。
     */
    public jp.ngt.ngtlib.renderer.model.PolygonModel model;

    /** このモデルを描く PartsRenderer (本家は final フィールド)。 */
    public PartsRenderer renderer;
    /** 発光テクスチャ (***_light0) を持つか。 */
    public boolean light;
    /** α ブレンドするマテリアルを含むか。 */
    public boolean alphaBlend;

    public ModelObject(TextureSet[] textures) {
        this.textures = textures != null ? textures : new TextureSet[0];
        for (TextureSet set : this.textures) {
            if (set == null) {
                continue;
            }
            if (set.doAlphaBlend) {
                this.alphaBlend = true;
            }
            if (set.subTextures != null && set.subTextures.length > 0) {
                this.light = true;
            }
        }
    }

    /** 本家getMaterials相当: テクスチャセット。 */
    public TextureSet[] getMaterials(Object map) {
        return this.textures;
    }

    /**
     * 本家 getTextureMap: マテリアル名 → テクスチャの対応表。
     * RTMU は TextureSet 配列で持つので、そこから名前引きの Map を組む。
     */
    public java.util.Map<String, Object> getTextureMap(Object textures) {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < this.textures.length; i++) {
            TextureSet set = this.textures[i];
            if (set == null || set.material == null) {
                continue;
            }
            map.put(String.valueOf(i), set.material.texture);
        }
        return map;
    }

    public java.util.Map<String, Object> getTextureMap() {
        return this.getTextureMap(this.textures);
    }

    /** 本家render: 全グループをGLRecorderに記録。 */
    public void render(Object entity, Object cfg, int pass, float par3) {
        this.renderWithTexture(entity, pass, par3);
    }

    /** 本家renderWithTexture: 全グループをGLRecorderに記録。 */
    public void renderWithTexture(Object entity, int pass, float par3) {
        jp.ngt.ngtlib.renderer.GLRecorder rec = jp.ngt.ngtlib.renderer.GLRecorder.active();
        if (rec == null || this.model == null) {
            return;
        }
        for (jp.ngt.ngtlib.renderer.model.GroupObject group : this.model.groupObjects) {
            rec.renderParts(group.name);
        }
    }

}
