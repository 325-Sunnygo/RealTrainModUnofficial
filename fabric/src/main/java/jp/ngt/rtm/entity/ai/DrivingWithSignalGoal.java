package jp.ngt.rtm.entity.ai;

import jp.ngt.rtm.electric.SignalLevel;
import jp.ngt.rtm.entity.npc.EntityMotorman;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.entity.train.util.EnumNotch;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.Arrays;
import java.util.EnumSet;

/**
 * 本家 jp.ngt.rtm.entity.ai.EntityAIDrivingWithSignal の移植。
 * 列車に乗っている運転士が、信号現示に応じた速度までノッチを自動操作する。
 *
 * <p>軽量化: ノッチ判断は {@value #UPDATE_INTERVAL} tick おき (本家は毎tick。20Hzで
 * ノッチを更新する意味は無く、体感は変わらない)。
 */
public class DrivingWithSignalGoal extends Goal {

    protected static final int UPDATE_INTERVAL = 4;

    protected final EntityMotorman motorman;
    protected EntityTrainBase train;
    private int cooldown;

    public DrivingWithSignalGoal(EntityMotorman motorman) {
        this.motorman = motorman;
        this.setFlags(EnumSet.of(Flag.MOVE)); //本家 setMutexBits(1)
    }

    @Override
    public boolean canUse() {
        return this.motorman.getVehicle() instanceof EntityTrainBase;
    }

    @Override
    public void start() {
        this.train = (EntityTrainBase) this.motorman.getVehicle();
        this.cooldown = 0;
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void stop() {
        this.train = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true; //間引きは自前の cooldown で行う
    }

    @Override
    public void tick() {
        if (this.train == null) {
            return;
        }
        if (--this.cooldown > 0) {
            return;
        }
        this.cooldown = UPDATE_INTERVAL;
        int signal = this.train.getSignal();
        float prevSpeed = this.train.getSpeed();
        float targetSpeed = SignalLevel.getSpeed(signal, prevSpeed);
        int notch = this.getSuitableNotch(targetSpeed, prevSpeed).id;
        this.train.setNotch(notch);
    }

    /**
     * 本家 getSuitableNotch。
     *
     * @param par1 目標の速度
     * @param par2 現在の速度
     */
    private EnumNotch getSuitableNotch(float par1, float par2) {
        float gap = par1 - par2;
        if (gap > 0.0F) {
            return Arrays.stream(EnumNotch.values())
                    .filter(notch -> notch.max_speed >= par1)
                    .findFirst().orElse(EnumNotch.inertia);
        } else if (gap == 0.0F) {
            return EnumNotch.inertia;
        } else {
            return EnumNotch.brake_4;
        }
    }
}
