package jp.ngt.rtm.render;

import net.minecraft.world.entity.Entity;

/**
 * 本家 jp.ngt.rtm.render.EntityPartsRenderer の移植。
 */
public abstract class EntityPartsRenderer extends PartsRenderer {

    public EntityPartsRenderer(String... par1) {
        super(par1);
    }

    public int getTick(Object entity) {
        markTimeAccessed();
        return entity instanceof Entity e ? e.tickCount : 0;
    }
}
