package jp.ngt.rtm.entity.train.parts;

import jp.ngt.rtm.entity.RTMEntities;
import jp.ngt.rtm.entity.train.EntityBogie;
import jp.ngt.rtm.entity.vehicle.EntityVehicleBase;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.UUID;

/**
 * 貨物用枕木。本家 {@code jp.ngt.rtm.entity.train.parts.EntityTie} の移植。
 *
 * <p>右クリックで近くのエンティティを 1 つ「固定」して載せる。もう一度で降ろす。
 * 本家 {@code setSize(3.0F, 0.125F)}。
 */
public class EntityTie extends EntityCargo {

    public EntityTie(EntityType<? extends EntityTie> type, Level level) {
        super(type, level);
    }

    public EntityTie(Level level) {
        this(RTMEntities.TIE.get(), level);
    }

    @Override
    protected void readCargoFromNBT(CompoundTag nbt) {
        if (this.level() == null || this.level().isClientSide()) {
            return;
        }
        long l0 = nbt.getLong("riderUUID_Most");
        long l1 = nbt.getLong("riderUUID_Least");
        if (l0 == 0L && l1 == 0L) {
            return;
        }
        UUID uuid = new UUID(l0, l1);
        //本家は loadedEntityList を総当りする。1.21 は近傍だけ見る (全走査 API が無い)
        for (Entity entity : this.level().getEntities(this, this.getBoundingBox().inflate(8.0D))) {
            if (uuid.equals(entity.getUUID())) {
                entity.startRiding(this);
                return;
            }
        }
    }

    @Override
    protected void writeCargoToNBT(CompoundTag nbt) {
        Entity passenger = this.getFirstPassenger();
        long l0 = 0L;
        long l1 = 0L;
        if (passenger != null) {
            UUID uuid = passenger.getUUID();
            l0 = uuid.getMostSignificantBits();
            l1 = uuid.getLeastSignificantBits();
        }
        nbt.putLong("riderUUID_Most", l0);
        nbt.putLong("riderUUID_Least", l1);
    }

    @Override
    public void tick() {
        super.tick();
        //本家: 生き物でない乗客は枕木と同じ向きに固定する
        Entity passenger = this.getFirstPassenger();
        if (passenger != null && !(passenger instanceof LivingEntity)) {
            passenger.setYRot(this.getYRot());
            passenger.setXRot(this.getXRot());
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (this.getFirstPassenger() != null) {
            this.getFirstPassenger().stopRiding();
            return InteractionResult.sidedSuccess(this.level().isClientSide());
        }
        if (this.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        double d0 = 1.5D;
        List<Entity> list = this.level().getEntities(this,
            new AABB(this.getX() - d0, this.getY() - 0.5D, this.getZ() - d0,
                this.getX() + d0, this.getY() + 4.5D, this.getZ() + d0),
            entity -> {
                if (entity instanceof EntityVehiclePart || entity instanceof EntityBogie) {
                    return false;
                }
                //★プレイヤーを固定してしまうと右クリックした本人が座る。対象外にする
                if (entity instanceof Player) {
                    return false;
                }
                if (entity instanceof EntityVehicleBase<?>) {
                    return this.getVehicle() != entity;
                }
                return true;
            });

        for (Entity entity : list) {
            entity.startRiding(this);
            player.displayClientMessage(Component.literal(entity + " was fixed."), false);
            return InteractionResult.CONSUME;
        }
        player.displayClientMessage(Component.literal("Fixable entity not found."), false);
        return InteractionResult.FAIL;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty();
    }
}
