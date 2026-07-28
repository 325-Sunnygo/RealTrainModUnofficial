package jp.ngt.rtm.rail;

import jp.ngt.rtm.rail.util.RailMap;
import net.minecraft.world.entity.Entity;

/**
 * 本家 jp.ngt.rtm.rail.ILargeRail の忠実移植。
 */
public interface ILargeRail {
    RailMap getRailMap(Entity entity);
}
