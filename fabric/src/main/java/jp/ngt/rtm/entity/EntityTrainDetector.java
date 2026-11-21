package jp.ngt.rtm.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 本家 {@code jp.ngt.rtm.entity.EntityTrainDetector} の 1.21 移植。
 *
 * <p>列車検知器。真下のレールに列車が居るかを見て、配線網へ
 * <b>検知 = STOP(0) / 未検知 = PROCEED(5)</b> を流す ({@code getElectricity})。
 */
public class EntityTrainDetector extends EntityElectricalWiring {
    /** 本家 SignalLevel.PROCEED.level */
    private static final int PROCEED = 5;
    /** 本家 SignalLevel.STOP.level */
    private static final int STOP = 0;

    private boolean findTrain;

    public EntityTrainDetector(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide()) {
            this.findTrain = this.detectTrainOnRail();
        }
    }

    /** 本家 onUpdate: 真下 8 マス以内のレールの在線を見る。 */
    private boolean detectTrainOnRail() {
        for (int i = 0; i < 8; ++i) {
            BlockPos pos = BlockPos.containing(this.getX(), this.getY() - i, this.getZ());
            if (this.level().getBlockEntity(pos)
                    instanceof jp.ngt.rtm.rail.TileEntityLargeRailBase base) {
                return base.isTrainOnRail();
            }
        }
        return false;
    }

    @Override
    public int getElectricity() {
        return this.findTrain ? STOP : PROCEED;
    }

    @Override
    public void setElectricity(int value) {
        // 本家 EntityTrainDetector.setElectricity は空 (入力は無視、出力専用)。
    }

    @Override
    protected void dropItems() {
        this.spawnAtLocation(new ItemStack(
            com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems.TRAIN_DETECTOR_ITEM.get()));
    }

    @Override
    protected String getDefaultName() {
        return "TrainDetector_01";
    }
}
