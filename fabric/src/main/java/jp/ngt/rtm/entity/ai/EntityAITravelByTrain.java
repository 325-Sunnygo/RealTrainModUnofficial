package jp.ngt.rtm.entity.ai;

import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectCategory;
import com.portofino.realtrainmodunofficial.item.TicketItem;
import jp.ngt.rtm.entity.npc.EntityNPC;
import jp.ngt.rtm.entity.train.parts.EntityFloor;
import jp.ngt.rtm.entity.vehicle.EntityVehicleBase;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;
import java.util.List;

/**
 * 乗客 NPC の「電車で移動する」AI。本家 {@code EntityAITravelByTrain} +
 * {@code NPCAIEnterStation / NPCAIRideTrain / NPCAILeaveStation / NPCAISerachTurnstile} の移植。
 *
 * <p>改札を探して切符 (手持ちの TicketItem) を使って入場 → 停車中の列車の空き床
 * (EntityFloor) へ座る → 数分乗る → 降りて改札から出場、を繰り返す。
 * 本家との差: 改札は RTMU の設置物 (TICKET_GATE) を探し、activateTicketGate で開ける。
 */
public class EntityAITravelByTrain extends Goal {

    public static final int WAIT_COEFFICIENT = 20;
    public static final int TO_MINUTES = 400;

    private final EntityNPC npc;
    private final double moveSpeed;

    private final EnterStation aiEnterStation;
    private final RideTrain aiRideTrain;
    private final LeaveStation aiLeaveStation;

    private SubTask activeTask;
    private int count;

    public EntityAITravelByTrain(EntityNPC npc, double moveSpeed) {
        this.npc = npc;
        this.moveSpeed = moveSpeed;
        this.aiEnterStation = new EnterStation();
        this.aiRideTrain = new RideTrain();
        this.aiLeaveStation = new LeaveStation();
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.count > 0) {
            --this.count;
            return false;
        }
        if (this.activeTask == null) {
            this.activeTask = this.aiEnterStation;
            if (this.npc.isPassenger()) {
                this.npc.stopRiding();
            }
        } else if (this.activeTask == this.aiRideTrain && this.npc.isPassenger()
                && this.npc.getVehicle() instanceof EntityFloor floor
                && floor.getVehicle() != null && floor.getVehicle().getSpeed() == 0.0F) {
            //停車した: 1/4 で乗り続け、3/4 で降りて出場
            this.npc.stopRiding();
            if (this.npc.getRandom().nextInt(4) == 0) {
                this.activeTask = this.aiRideTrain;
            } else {
                this.activeTask = this.aiLeaveStation;
                this.aiRideTrain.targetTrain = null;
            }
        }
        return this.activeTask.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        boolean flag = this.activeTask.canContinueToUse();
        if (!flag) {
            if (this.activeTask == this.aiEnterStation) {
                if (this.aiEnterStation.openedTurnstile) {
                    this.activeTask = this.aiRideTrain;
                    if (this.activeTask.canUse()) {
                        this.activeTask.start();
                        return true;
                    }
                }
            } else if (this.activeTask == this.aiRideTrain) {
                if (this.npc.isPassenger()) {
                    //乗車成功: (1〜20) x 400tick 待つ
                    this.count = (this.npc.getRandom().nextInt(WAIT_COEFFICIENT) + 1) * TO_MINUTES;
                    return false;
                }
            } else if (this.activeTask == this.aiLeaveStation) {
                this.count = (this.npc.getRandom().nextInt(WAIT_COEFFICIENT) + 1) * TO_MINUTES;
                this.activeTask = null;
                return false;
            }
        }
        return flag;
    }

    @Override
    public void start() {
        this.activeTask.start();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private abstract static class SubTask {
        abstract boolean canUse();

        abstract boolean canContinueToUse();

        abstract void start();
    }

    /** 本家 NPCAISerachTurnstile: 改札を探して前まで歩き、切符で開ける。 */
    private class SearchTurnstile extends SubTask {
        protected BlockPos targetBlockPos;
        protected Path path;
        boolean openedTurnstile;

        @Override
        boolean canUse() {
            return this.setTargetTurnstile();
        }

        private boolean setTargetTurnstile() {
            this.openedTurnstile = false;
            this.targetBlockPos = null;
            BlockPos origin = npc.blockPosition();
            int range = 32;
            double distance = Double.MAX_VALUE;
            //本家: 32x24x32 の総当たり。BE 走査に置き換える (同じ範囲)
            for (BlockPos pos : BlockPos.betweenClosed(
                    origin.offset(-range, -8, -range), origin.offset(range, 16, range))) {
                if (npc.level().getBlockEntity(pos) instanceof InstalledObjectBlockEntity be
                        && be.getCategory() == InstalledObjectCategory.TICKET_GATE) {
                    double dsq = npc.distanceToSqr(pos.getX(), pos.getY(), pos.getZ());
                    if (dsq < distance) {
                        this.targetBlockPos = pos.immutable();
                        distance = dsq;
                    }
                }
            }
            if (this.targetBlockPos != null) {
                this.path = npc.getNavigation().createPath(
                    this.targetBlockPos.getX() + 0.5D, this.targetBlockPos.getY(),
                    this.targetBlockPos.getZ() + 0.5D, 1);
                return this.path != null;
            }
            return false;
        }

        @Override
        boolean canContinueToUse() {
            boolean arrived = npc.getNavigation().isDone();
            if (arrived && this.useTicket()) {
                if (npc.level().getBlockEntity(this.targetBlockPos) instanceof InstalledObjectBlockEntity be) {
                    be.activateTicketGate();
                }
                this.openedTurnstile = true;
                return false;
            }
            return !arrived;
        }

        /** 本家 useTicket: 手持ちの切符を 1 回分消費。IC カードは減らない。 */
        private boolean useTicket() {
            ItemStack held = npc.getMainHandItem();
            if (held.getItem() instanceof TicketItem ticket) {
                npc.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ticket.consume(held));
                return true;
            }
            if (held.getItem() instanceof com.portofino.realtrainmodunofficial.item.IcCardItem) {
                return true;
            }
            return false;
        }

        @Override
        void start() {
            npc.getNavigation().moveTo(this.path, moveSpeed);
        }
    }

    /** 本家 NPCAIEnterStation/NPCAILeaveStation: 開けた改札を通り抜ける。 */
    private class EnterStation extends SearchTurnstile {
        private boolean passed;

        @Override
        boolean canUse() {
            this.passed = false;
            return super.canUse();
        }

        @Override
        boolean canContinueToUse() {
            if (this.passed) {
                return !npc.getNavigation().isDone();
            }
            boolean flag = super.canContinueToUse();
            if (!flag && this.openedTurnstile) {
                //改札の向こう側 (2〜3m 先) へ歩き抜ける
                double vecX = this.targetBlockPos.getX() + 0.5D - npc.getX();
                double vecY = this.targetBlockPos.getY() - npc.getY();
                double vecZ = this.targetBlockPos.getZ() + 0.5D - npc.getZ();
                for (double d0 = 3.0D; d0 > 2.0D; d0 -= 0.25D) {
                    this.path = npc.getNavigation().createPath(
                        npc.getX() + vecX * d0, npc.getY() + vecY * d0, npc.getZ() + vecZ * d0, 1);
                    if (this.path != null) {
                        this.passed = true;
                        this.start();
                        return true;
                    }
                }
            }
            return flag;
        }
    }

    private class LeaveStation extends EnterStation {
    }

    /** 本家 NPCAIRideTrain: 停車中の列車の空き床へ歩いて座る。 */
    private class RideTrain extends SubTask {
        private EntityFloor target;
        private Path path;
        EntityVehicleBase<?> targetTrain;

        @Override
        boolean canUse() {
            return this.setTargetSeat();
        }

        @Override
        boolean canContinueToUse() {
            if (this.target == null || this.target.isRemoved()) {
                return false;
            }
            if (this.targetTrain != null && this.targetTrain.getSpeed() == 0.0F
                    && npc.distanceToSqr(this.targetTrain) <= 1024.0D) {
                if (npc.distanceToSqr(this.target) < 9.0D) {
                    if (this.target.getFirstPassenger() == null) {
                        npc.startRiding(this.target);
                        return false;
                    }
                    return this.setTargetSeat();
                }
                return !npc.getNavigation().isDone();
            }
            return false;
        }

        private boolean setTargetSeat() {
            AABB aabb = new AABB(npc.getX() - 32.0D, npc.getY() - 8.0D, npc.getZ() - 32.0D,
                npc.getX() + 32.0D, npc.getY() + 16.0D, npc.getZ() + 32.0D);
            List<EntityFloor> list = npc.level().getEntitiesOfClass(EntityFloor.class, aabb);
            EntityFloor nextTarget = null;
            double distance = Double.MAX_VALUE;
            for (EntityFloor floor : list) {
                EntityVehicleBase<?> train = floor.getVehicle();
                if (train != null && train != this.targetTrain && train.getSpeed() == 0.0F
                        && floor.getFirstPassenger() == null) {
                    double dsq = npc.distanceToSqr(floor);
                    if (dsq < distance) {
                        nextTarget = floor;
                        distance = dsq;
                    }
                }
            }
            if (nextTarget != null) {
                this.path = npc.getNavigation().createPath(nextTarget, 1);
                if (this.path != null) {
                    this.target = nextTarget;
                    this.targetTrain = nextTarget.getVehicle();
                    return true;
                }
            }
            return false;
        }

        @Override
        void start() {
            npc.getNavigation().moveTo(this.path, moveSpeed);
        }
    }
}
