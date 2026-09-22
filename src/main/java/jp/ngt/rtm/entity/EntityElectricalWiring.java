package jp.ngt.rtm.entity;

import jp.ngt.rtm.electric.IProvideElectricity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * 本家 {@code jp.ngt.rtm.electric.EntityElectricalWiring} の 1.21 移植。
 *
 * <p>配線網につながる設置物エンティティ (ATC / 列車検知器 / 車止め) の基底。
 * 本家は {@code ElectricalWiringManager} がエンティティをノードとして管理する。
 * RTMU の配線 ({@code WireManager}) はブロック座標ベースなので、このクラスは
 * <b>座標つきの供給/消費ノード</b>として振る舞い、{@link IProvideElectricity} を実装する。
 */
public abstract class EntityElectricalWiring extends EntityInstalledObject implements IProvideElectricity {

    protected EntityElectricalWiring(EntityType<?> type, Level level) {
        super(type, level);
    }

    /** 本家 {@code getElectricity}: 配線網へ流す値。 */
    @Override
    public abstract int getElectricity();

    /** 本家 {@code setElectricity(int)}: 配線網から受け取った値。 */
    public abstract void setElectricity(int value);

    /** 本家 {@code IProvideElectricity.setElectricity(x,y,z,level)} (ブロック向け API)。 */
    @Override
    public void setElectricity(int x, int y, int z, int level) {
        this.setElectricity(level);
    }

    /**
     * その座標に居る配線エンティティを返す (RTMU の配線はブロック座標ベースなので、
     * ATC/列車検知器/車止め をブロックと同じ座標のノードとして扱う)。
     */
    public static EntityElectricalWiring find(net.minecraft.world.level.Level level,
                                              net.minecraft.core.BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }
        for (net.minecraft.world.entity.Entity entity : level.getEntitiesOfClass(
                net.minecraft.world.entity.Entity.class,
                new net.minecraft.world.phys.AABB(pos).inflate(0.5D))) {
            if (entity instanceof EntityElectricalWiring wiring) {
                return wiring;
            }
        }
        return null;
    }
}
