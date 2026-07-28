package jp.ngt.rtm.render;

/**
 * 本家 jp.ngt.rtm.render.OrnamentPartsRenderer の移植。
 * 本家でも中身は空 (TileEntityPartsRenderer をそのまま使うだけ) で、
 * 飾り物のスクリプトが renderClass に名前で指定するためだけに存在する。
 */
public class OrnamentPartsRenderer extends TileEntityPartsRenderer {

    public OrnamentPartsRenderer(String... par1) {
        super(par1);
    }
}
