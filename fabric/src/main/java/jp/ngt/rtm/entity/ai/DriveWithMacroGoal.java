package jp.ngt.rtm.entity.ai;

import jp.ngt.rtm.entity.npc.EntityMotorman;
import jp.ngt.rtm.entity.npc.macro.MacroExecutor;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * 本家 jp.ngt.rtm.entity.ai.EntityAIDriveWithMacro の移植。
 * GUI で選んだマクロ (.txt) を相対時刻どおりに実行して運転する。
 */
public class DriveWithMacroGoal extends Goal {

    private final EntityMotorman motorman;
    private MacroExecutor executor;

    public DriveWithMacroGoal(EntityMotorman motorman) {
        this.motorman = motorman;
        this.setFlags(EnumSet.of(Flag.MOVE)); //本家 setMutexBits(1)
    }

    public void setMacro(String[] args) {
        this.executor = new MacroExecutor(args);
    }

    @Override
    public boolean canUse() {
        return this.motorman.getVehicle() instanceof EntityTrainBase
                && this.executor != null && !this.executor.finished();
    }

    @Override
    public void start() {
        this.executor.start(this.motorman.level());
    }

    @Override
    public boolean canContinueToUse() {
        if (!this.canUse()) {
            if (this.executor != null) {
                this.executor.stop(this.motorman.level());
            }
            return false;
        }
        return true;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true; //マクロは相対時刻きっかりに実行する
    }

    @Override
    public void tick() {
        if (this.motorman.getVehicle() instanceof EntityTrainBase train) {
            this.executor.tick(this.motorman.level(), train);
        }
    }
}
