package jp.kaiz.atsassistmod.block;

import jp.kaiz.atsassistmod.block.tileentity.GroundUnitBlockEntity;
import jp.kaiz.atsassistmod.controller.SpeedOrder;
import jp.kaiz.atsassistmod.controller.TrainControllerManager;
import jp.kaiz.atsassistmod.controller.trainprotection.TrainProtectionType;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.entity.train.util.TrainState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 本家 jp.kaiz.atsassistmod.block.tileentity.TileEntityGroundUnit の移植 (地上子の動作)。
 * 1.21 では BlockEntity ({@link GroundUnitBlockEntity}) が種類ごとの Logic に委譲する。
 *
 * <p>基本動作 (本家 updateEntity): ブロック上 1x3x1 に制御車が来たら 1 編成 1 回だけ onTick。
 * レッドストーン連動が有効なら通電時のみ検知する。
 */
public abstract class GroundUnitLogic {

    //編成単位での管理 (同じ編成に連続発動しない)
    protected long formationID;

    public abstract GroundUnitType getType();

    protected abstract void onTick(GroundUnitBlockEntity be, EntityTrainBase train);

    public void readNBT(CompoundTag tag) {
    }

    public void writeNBT(CompoundTag tag) {
    }

    /** 検知範囲 (本家: x,y,z 〜 x+1,y+3,z+1)。 */
    protected AABB detectBox(BlockPos pos) {
        return new AABB(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1, pos.getY() + 3, pos.getZ() + 1);
    }

    protected static List<EntityTrainBase> findTrains(Level level, AABB box) {
        return level.getEntitiesOfClass(EntityTrainBase.class, box);
    }

    /** 本家 updateEntity のデフォルト実装。 */
    public void tick(Level level, BlockPos pos, GroundUnitBlockEntity be) {
        if (!be.isLinkRedStone() || level.hasNeighborSignal(pos)) { //レッドストーン確認
            List<EntityTrainBase> list = findTrains(level, this.detectBox(pos));
            if (!list.isEmpty()) {
                EntityTrainBase train = list.get(0);
                if (train.isControlCar() && train.getFormation() != null) {
                    if (this.formationID != train.getFormation().id) {
                        this.onTick(be, train);
                        this.formationID = train.getFormation().id;
                    }
                    return;
                }
            }
        }
        this.formationID = 0;
    }

    //パケット/GUI 用共通インターフェース (本家のまま)

    public interface Speed {
        void setSpeedLimit(int speedLimit);

        int getSpeedLimit();
    }

    public interface Distance {
        void setDistance(double distance);

        double getDistance();
    }

    public interface TrainDistance {
        void setUseTrainDistance(boolean useTrainDistance);

        boolean isUseTrainDistance();
    }

    //------------------------------------------------------------------ 各種類

    public static class None extends GroundUnitLogic {
        @Override
        public GroundUnitType getType() {
            return GroundUnitType.None;
        }

        @Override
        protected void onTick(GroundUnitBlockEntity be, EntityTrainBase train) {
        }
    }

    /** ATC 速度制限予告: distance 先から speedLimit 制限 (autoBrake でパターン減速)。 */
    public static class ATCSpeedLimitNotice extends GroundUnitLogic implements Speed, Distance, TrainDistance {
        private int speedLimit;
        private double distance;
        private boolean autoBrake;
        private boolean useTrainDistance;

        @Override
        protected void onTick(GroundUnitBlockEntity be, EntityTrainBase train) {
            SpeedOrder speedOrder = new SpeedOrder(this.speedLimit,
                    this.isUseTrainDistance() ? this.distance - train.getConfig().trainDistance : this.distance,
                    this.autoBrake);
            TrainControllerManager.getTrainController(train).addSpeedOrder(speedOrder);
        }

        @Override
        public void readNBT(CompoundTag tag) {
            this.speedLimit = tag.getInt("speedLimit");
            this.distance = tag.getDouble("distance");
            this.autoBrake = tag.getBoolean("autoBrake");
            this.useTrainDistance = tag.getBoolean("trainDistance");
        }

        @Override
        public void writeNBT(CompoundTag tag) {
            tag.putInt("speedLimit", this.speedLimit);
            tag.putDouble("distance", this.distance);
            tag.putBoolean("autoBrake", this.autoBrake);
            tag.putBoolean("trainDistance", this.useTrainDistance);
        }

        @Override
        public GroundUnitType getType() {
            return GroundUnitType.ATC_SpeedLimit_Notice;
        }

        @Override
        public void setSpeedLimit(int speedLimit) {
            this.speedLimit = speedLimit;
        }

        @Override
        public int getSpeedLimit() {
            return this.speedLimit;
        }

        @Override
        public void setDistance(double distance) {
            this.distance = distance;
        }

        @Override
        public double getDistance() {
            return this.distance;
        }

        public void setAutoBrake(boolean autoBrake) {
            this.autoBrake = autoBrake;
        }

        public boolean isAutoBrake() {
            return autoBrake;
        }

        @Override
        public void setUseTrainDistance(boolean useTrainDistance) {
            this.useTrainDistance = useTrainDistance;
        }

        @Override
        public boolean isUseTrainDistance() {
            return this.useTrainDistance;
        }
    }

    /** ATC 速度制限解除 (1 つ)。useTrainDistance = 編成最後尾で解除。 */
    public static class ATCSpeedLimitCancel extends GroundUnitLogic implements TrainDistance {
        protected boolean useTrainDistance;

        @Override
        public void readNBT(CompoundTag tag) {
            this.useTrainDistance = tag.getBoolean("lateCancel");
        }

        @Override
        public void writeNBT(CompoundTag tag) {
            tag.putBoolean("lateCancel", useTrainDistance);
        }

        @Override
        protected void onTick(GroundUnitBlockEntity be, EntityTrainBase train) {
            if (train.getFormation() != null && this.formationID != train.getFormation().id) {
                TrainControllerManager.getTrainController(train).removeSpeedLimit();
                this.formationID = train.getFormation().id;
            }
        }

        /** 本家: 検知範囲 y+4、最後尾検知オプション付き。 */
        @Override
        public void tick(Level level, BlockPos pos, GroundUnitBlockEntity be) {
            if (!be.isLinkRedStone() || level.hasNeighborSignal(pos)) {
                AABB detect = new AABB(pos.getX(), pos.getY(), pos.getZ(),
                        pos.getX() + 1, pos.getY() + 4, pos.getZ() + 1);
                List<EntityTrainBase> list = findTrains(level, detect);
                if (!list.isEmpty()) {
                    EntityTrainBase train = list.get(0);
                    if (train.getFormation() == null) {
                        this.formationID = 0;
                        return;
                    }
                    if (this.useTrainDistance) {
                        if (train.getFormation().size() == 1) {
                            this.onTick(be, train);
                            return;
                        } else if (!train.isControlCar()
                                && (train.getConnectedTrain(0) == null || train.getConnectedTrain(1) == null)) {
                            this.onTick(be, train);
                            return;
                        }
                    } else {
                        if (train.isControlCar()) {
                            this.onTick(be, train);
                            return;
                        }
                    }
                }
            }
            this.formationID = 0;
        }

        @Override
        public GroundUnitType getType() {
            return GroundUnitType.ATC_SpeedLimit_Cancel;
        }

        @Override
        public void setUseTrainDistance(boolean useTrainDistance) {
            this.useTrainDistance = useTrainDistance;
        }

        @Override
        public boolean isUseTrainDistance() {
            return this.useTrainDistance;
        }
    }

    /** ATC 速度制限全解除。 */
    public static class ATCSpeedLimitReset extends ATCSpeedLimitCancel {
        @Override
        protected void onTick(GroundUnitBlockEntity be, EntityTrainBase train) {
            if (train.getFormation() != null && this.formationID != train.getFormation().id) {
                TrainControllerManager.getTrainController(train).removeAllSpeedLimit();
                this.formationID = train.getFormation().id;
            }
        }

        @Override
        public GroundUnitType getType() {
            return GroundUnitType.ATC_SpeedLimit_Reset;
        }
    }

    /** TASC 停止位置予告: distance 先に定位置停止。 */
    public static class TASCStopPositionNotice extends GroundUnitLogic implements Distance, TrainDistance {
        private double distance;
        private boolean trainDistance;

        @Override
        public void readNBT(CompoundTag tag) {
            this.distance = tag.getDouble("distance");
            this.trainDistance = tag.getBoolean("trainDistance");
        }

        @Override
        public void writeNBT(CompoundTag tag) {
            tag.putDouble("distance", this.distance);
            tag.putBoolean("trainDistance", this.trainDistance);
        }

        @Override
        protected void onTick(GroundUnitBlockEntity be, EntityTrainBase train) {
            TrainControllerManager.getTrainController(train).tascController.enable(
                    this.isUseTrainDistance() ? this.distance + 1.5d - train.getConfig().trainDistance : this.distance + 1.5d);
        }

        @Override
        public GroundUnitType getType() {
            return GroundUnitType.TASC_StopPotion_Notice;
        }

        @Override
        public void setDistance(double distance) {
            this.distance = distance;
        }

        @Override
        public double getDistance() {
            return this.distance;
        }

        @Override
        public void setUseTrainDistance(boolean useTrainDistance) {
            this.trainDistance = useTrainDistance;
        }

        @Override
        public boolean isUseTrainDistance() {
            return this.trainDistance;
        }
    }

    /** TASC 無効化。 */
    public static class TASCDisable extends GroundUnitLogic {
        @Override
        protected void onTick(GroundUnitBlockEntity be, EntityTrainBase train) {
            TrainControllerManager.getTrainController(train).tascController.disable();
        }

        @Override
        public GroundUnitType getType() {
            return GroundUnitType.TASC_Cancel;
        }
    }

    /** TASC 停止位置補正。 */
    public static class TASCStopPositionCorrection extends GroundUnitLogic implements Distance, TrainDistance {
        private double distance;
        private boolean trainDistance;

        @Override
        public void readNBT(CompoundTag tag) {
            this.distance = tag.getDouble("distance");
            this.trainDistance = tag.getBoolean("trainDistance");
        }

        @Override
        public void writeNBT(CompoundTag tag) {
            tag.putDouble("distance", this.distance);
            tag.putBoolean("trainDistance", this.trainDistance);
        }

        @Override
        protected void onTick(GroundUnitBlockEntity be, EntityTrainBase train) {
            TrainControllerManager.getTrainController(train).tascController.setStopDistance(
                    this.isUseTrainDistance() ? this.distance + 1.5d - train.getConfig().trainDistance : this.distance + 1.5d);
        }

        @Override
        public GroundUnitType getType() {
            return GroundUnitType.TASC_StopPotion_Correction;
        }

        @Override
        public void setDistance(double distance) {
            this.distance = distance;
        }

        @Override
        public double getDistance() {
            return this.distance;
        }

        @Override
        public void setUseTrainDistance(boolean useTrainDistance) {
            this.trainDistance = useTrainDistance;
        }

        @Override
        public boolean isUseTrainDistance() {
            return this.trainDistance;
        }
    }

    /** TASC 停止位置: 停車中の編成両数をレッドストーン出力。 */
    public static class TASCStopPosition extends GroundUnitLogic {

        @Override
        public void tick(Level level, BlockPos pos, GroundUnitBlockEntity be) {
            List<EntityTrainBase> list = findTrains(level, this.detectBox(pos));
            if (!list.isEmpty()) {
                EntityTrainBase train = list.get(0);
                if (be.isLinkRedStone()) { //制御車以外でも
                    this.onTick(be, train);
                    return;
                } else {
                    if (train.isControlCar()) {
                        this.onTick(be, train);
                        return;
                    }
                }
            }
            be.setRedStoneOutput(0);
        }

        @Override
        protected void onTick(GroundUnitBlockEntity be, EntityTrainBase train) {
            be.setRedStoneOutput(train.getSpeed() == 0F && train.getFormation() != null
                    ? train.getFormation().size() : 0);
        }

        @Override
        public GroundUnitType getType() {
            return GroundUnitType.TASC_StopPotion;
        }
    }

    /** ATO 出発信号: ATO を有効化して目標速度を設定。 */
    public static class ATODepartureSignal extends GroundUnitLogic implements Speed {
        private int speedLimit;

        @Override
        public void readNBT(CompoundTag tag) {
            this.speedLimit = tag.getInt("speedLimit");
        }

        @Override
        public void writeNBT(CompoundTag tag) {
            tag.putInt("speedLimit", this.speedLimit);
        }

        @Override
        protected void onTick(GroundUnitBlockEntity be, EntityTrainBase train) {
            TrainControllerManager.getTrainController(train).enableATO(this.speedLimit);
        }

        @Override
        public GroundUnitType getType() {
            return GroundUnitType.ATO_Departure_Signal;
        }

        @Override
        public void setSpeedLimit(int speedLimit) {
            this.speedLimit = speedLimit;
        }

        @Override
        public int getSpeedLimit() {
            return this.speedLimit;
        }
    }

    /** ATO 無効化。 */
    public static class ATODisable extends GroundUnitLogic {
        @Override
        protected void onTick(GroundUnitBlockEntity be, EntityTrainBase train) {
            TrainControllerManager.getTrainController(train).disableATO();
        }

        @Override
        public GroundUnitType getType() {
            return GroundUnitType.ATO_Cancel;
        }
    }

    /** ATO 目標速度変更。 */
    public static class ATOChangeSpeed extends GroundUnitLogic implements Speed {
        private int speedLimit;

        @Override
        public void readNBT(CompoundTag tag) {
            this.speedLimit = tag.getInt("speedLimit");
        }

        @Override
        public void writeNBT(CompoundTag tag) {
            tag.putInt("speedLimit", this.speedLimit);
        }

        @Override
        protected void onTick(GroundUnitBlockEntity be, EntityTrainBase train) {
            TrainControllerManager.getTrainController(train).setMaxSpeed(this.speedLimit);
        }

        @Override
        public GroundUnitType getType() {
            return GroundUnitType.ATO_Change_Speed;
        }

        @Override
        public void setSpeedLimit(int speedLimit) {
            this.speedLimit = speedLimit;
        }

        @Override
        public int getSpeedLimit() {
            return this.speedLimit;
        }
    }

    /** 列車状態設定: 通過した列車の TrainState を一括設定 (通電時のみ)。 */
    public static class TrainStateSet extends GroundUnitLogic {
        private byte[] states;

        public TrainStateSet() {
            states = new byte[]{-1, -9, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
        }

        @Override
        public void readNBT(CompoundTag tag) {
            byte[] read = tag.getByteArray("state");
            if (read.length == 12) {
                this.states = read;
            }
        }

        @Override
        public void writeNBT(CompoundTag tag) {
            tag.putByteArray("state", states);
        }

        @Override
        protected void onTick(GroundUnitBlockEntity be, EntityTrainBase train) {
            for (int i = 0; i < 12; i++) {
                if (i == 3) {
                    continue;
                }
                if (states[i] < TrainState.getStateType(i).min) {
                    continue;
                }
                if (i == TrainState.TrainStateType.State_TrainDir.id) {
                    train.setTrainDirection(states[i]);
                    continue;
                }
                train.setTrainStateData(i, states[i]);
            }
        }

        @Override
        public GroundUnitType getType() {
            return GroundUnitType.TrainState_Set;
        }

        public byte[] getStates() {
            return this.states;
        }

        public void setStates(byte[] states) {
            this.states = states;
        }

        /** 本家: 通電時のみ、範囲は xz±1・y+3。linkRedStone は「制御車以外でも」の意味。 */
        @Override
        public void tick(Level level, BlockPos pos, GroundUnitBlockEntity be) {
            if (level.hasNeighborSignal(pos)) {
                AABB detect = new AABB(pos.getX() - 1, pos.getY(), pos.getZ() - 1,
                        pos.getX() + 2, pos.getY() + 3, pos.getZ() + 2);
                List<EntityTrainBase> list = findTrains(level, detect);
                if (!list.isEmpty()) {
                    EntityTrainBase train = list.get(0);
                    if (be.isLinkRedStone()) { //制御車以外でも
                        this.onTick(be, train);
                    } else {
                        if (train.isControlCar()) {
                            this.onTick(be, train);
                        }
                    }
                }
            }
        }
    }

    /** 保安装置切替 (地上子で ATACS 区間などへ切替える)。 */
    public static class ChangeTrainProtection extends GroundUnitLogic {
        private int tpType;

        public ChangeTrainProtection() {
            this(TrainProtectionType.NONE);
        }

        public ChangeTrainProtection(TrainProtectionType type) {
            this.tpType = type.id;
        }

        @Override
        public void readNBT(CompoundTag tag) {
            this.tpType = tag.getInt("tpType");
        }

        @Override
        public void writeNBT(CompoundTag tag) {
            tag.putInt("tpType", this.tpType);
        }

        @Override
        protected void onTick(GroundUnitBlockEntity be, EntityTrainBase train) {
            TrainControllerManager.getTrainController(train)
                    .setTrainProtection(TrainProtectionType.getType(this.tpType));
        }

        @Override
        public GroundUnitType getType() {
            return GroundUnitType.CHANGE_TP;
        }

        public void setTPType(TrainProtectionType type) {
            this.tpType = type.id;
        }

        public TrainProtectionType getTPType() {
            return TrainProtectionType.getType(this.tpType);
        }
    }

    /** ATACS 解除 (本家仕様: 置くと CHANGE_TP(開放) に変換されるレガシー地上子)。 */
    public static class ATACSDisable extends GroundUnitLogic {
        @Override
        protected void onTick(GroundUnitBlockEntity be, EntityTrainBase train) {
        }

        @Override
        public void tick(Level level, BlockPos pos, GroundUnitBlockEntity be) {
            //本家: このタイプは CHANGE_TP(NONE) に自己変換する
            be.convertTo(GroundUnitType.CHANGE_TP);
        }

        @Override
        public GroundUnitType getType() {
            return GroundUnitType.ATACS_Disable;
        }
    }
}
