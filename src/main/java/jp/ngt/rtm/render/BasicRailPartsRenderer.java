package jp.ngt.rtm.render;

import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** 本家 jp.ngt.rtm.render.BasicRailPartsRenderer (Kaiz… */
@OnlyIn(Dist.CLIENT)
public class BasicRailPartsRenderer extends RailPartsRenderer {
    public BasicRailPartsRenderer() {
        super();
    }

    public BasicRailPartsRenderer(String... args) {
        super(args);
    }

    @Override
    public void renderRailStatic(TileEntityLargeRailCore tile, double x, double y, double z,
                                 float partialTicks, int pass) {
        this.renderStaticParts(tile, x, y, z);
    }

    @Override
    public void renderRailDynamic(TileEntityLargeRailCore tile, double x, double y, double z,
                                  float partialTicks, int pass) {
    }

    @Override
    public boolean shouldRenderObject(TileEntityLargeRailCore tile, String objName, double len, double pos) {
        return true;
    }
}
