package jp.ngt.rtm.render;

import jp.ngt.ngtlib.renderer.model.GroupObject;

import java.util.List;

/** 本家 jp.ngt.rtm.render.BasicPartsRenderer (KaizPatc… */
public class BasicPartsRenderer extends PartsRenderer {
    private Parts main;

    public BasicPartsRenderer(String... args) {
        super(args);
    }

    @Override
    public void init(Object modelSet, Object modelObject) {
        if (modelObject instanceof ModelObject mo && mo.model != null) {
            List<GroupObject> groups = mo.model.getGroupObjects();
            String[] names = groups.stream().map(g -> g.name).toArray(String[]::new);
            this.main = this.registerParts(new Parts(names));
        } else {
            this.main = this.registerParts(new Parts());
        }
        super.init(modelSet, modelObject);
    }

    @Override
    public void render(Object entity, int pass, float partialTick) {
        this.currentPass = pass;
        if (this.main != null) {
            this.main.render(this);
        }
    }

    /** 本家 BasicPartsRenderer.getWorld: 既定は null。 */
    public net.minecraft.world.level.Level getWorld(Object entity) {
        return null;
    }
}
