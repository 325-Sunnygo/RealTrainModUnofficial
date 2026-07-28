package jp.ngt.rtm.render;

/**
 * 本家 jp.ngt.rtm.render.OrnamentPartsRenderer の移植。
 *
 * <p>本家でも中身は空 (TileEntityPartsRenderer をそのまま使うだけ) で、
 * 飾り物のスクリプトが renderClass に名前で指定するためだけに存在する。
 * 蛍光灯 (RenderFluorescent.js / RenderFluorescentCovered.js) と
 * 架線柱 (RenderConnectablePole.js) がこれを指す。
 */
public class OrnamentPartsRenderer extends TileEntityPartsRenderer {

    public OrnamentPartsRenderer(String... par1) {
        super(par1);
    }
}
