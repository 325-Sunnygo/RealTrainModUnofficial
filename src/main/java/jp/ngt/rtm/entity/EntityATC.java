package jp.ngt.rtm.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 本家 jp.ngt.rtm.entity.EntityATC の 1.21 移植。 ATC 地上子。 */
public class EntityATC extends EntityElectricalWiring {
    private int signalLevel;

    public EntityATC(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    public int getElectricity() {
        return -1; // 本家どおり (供給はせず、受け取った値をレールへ流すだけ)
    }

    @Override
    public void setElectricity(int value) {
        this.signalLevel = value;
        this.setSignalToRail(value);
    }

    /** 本家 setSignalToRail: 真下のレールコアへ signal を書く。 */
    private void setSignalToRail(int value) {
        jp.ngt.rtm.rail.TileEntityLargeRailBase rail = this.findRail();
        if (rail != null) {
            jp.ngt.rtm.rail.TileEntityLargeRailCore core = rail.getRailCore();
            if (core != null) {
                core.setSignal(value);
            }
        }
    }

    /** 本家は真下 8 マス以内のレールを探す。 */
    private jp.ngt.rtm.rail.TileEntityLargeRailBase findRail() {
        for (int i = 0; i < 8; ++i) {
            BlockPos pos = BlockPos.containing(this.getX(), this.getY() - i, this.getZ());
            if (this.level().getBlockEntity(pos)
                    instanceof jp.ngt.rtm.rail.TileEntityLargeRailBase base) {
                return base;
            }
        }
        return null;
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        // 本家 EntityATC.attackEntityFrom: ダメージを受けた時点でレール信号を…
        if (!this.level().isClientSide()) {
            this.setSignalToRail(0);
        }
        return super.hurt(source, amount);
    }

    @Override
    protected void dropItems() {
        this.spawnAtLocation(new ItemStack(
            com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems.ATC_ITEM.get()));
    }

    @Override
    public String getSubType() {
        return "Antenna_Send";
    }

    @Override
    protected String getDefaultName() {
        return "ATC_01";
    }

    @Override
    protected ItemStack getItem() {
        return new ItemStack(com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems.ATC_ITEM.get());
    }
}
