package jp.ngt.rtm.entity.train.parts;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 貨物の基底。本家 {@code jp.ngt.rtm.entity.train.parts.EntityCargo} の移植。
 *
 * <p>設置してある物 ({@code isIndependent}) と、貨車に載っている物の両方になる。
 * 中身はアイテム側にも書き戻す (壊すとその状態のまま落ちる)。
 */
public abstract class EntityCargo extends EntityVehiclePart {
    private static final EntityDataAccessor<Byte> CARGO_ID =
        SynchedEntityData.defineId(EntityCargo.class, EntityDataSerializers.BYTE);

    protected ItemStack itemCargo = ItemStack.EMPTY;

    public EntityCargo(EntityType<?> type, Level level) {
        super(type, level);
    }

    protected void setItem(ItemStack stack) {
        if (stack.getCount() > 1) {
            stack.setCount(1);
        }
        this.itemCargo = stack;
    }

    public ItemStack getCargoItem() {
        return this.itemCargo;
    }

    /** 設置物として置かれたときの初期化。本家のコンストラクタ相当。 */
    public void initPlaced(ItemStack stack) {
        this.setItem(stack);
        this.isIndependent = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CARGO_ID, (byte) 0);
    }

    public byte getCargoId() {
        return this.entityData.get(CARGO_ID);
    }

    public void setCargoId(byte id) {
        this.entityData.set(CARGO_ID, id);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        if (nbt.contains("ContainerItem")) {
            this.itemCargo = ItemStack.parseOptional(this.registryAccess(), nbt.getCompound("ContainerItem"));
        }
        this.setCargoId(nbt.getByte("cargoId"));
        this.readCargoFromItem();
        super.readAdditionalSaveData(nbt);
    }

    protected abstract void readCargoFromNBT(CompoundTag nbt);

    public void readCargoFromItem() {
        CompoundTag nbt = this.itemCargo.get(RealTrainModUnofficialComponents.CARGO_DATA.get());
        if (nbt != null) {
            this.readCargoFromNBT(nbt);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        this.writeCargoToItem();
        if (!this.itemCargo.isEmpty()) {
            nbt.put("ContainerItem", this.itemCargo.save(this.registryAccess()));
        }
        nbt.putByte("cargoId", this.getCargoId());
        super.addAdditionalSaveData(nbt);
    }

    protected abstract void writeCargoToNBT(CompoundTag nbt);

    public void writeCargoToItem() {
        if (this.itemCargo.isEmpty()) {
            return;
        }
        CompoundTag nbt = this.itemCargo.getOrDefault(
            RealTrainModUnofficialComponents.CARGO_DATA.get(), new CompoundTag()).copy();
        this.writeCargoToNBT(nbt);
        this.itemCargo.set(RealTrainModUnofficialComponents.CARGO_DATA.get(), nbt);
    }

    @Override
    public void onLoadVehicle() {
    }

    /** 本家 attackEntityFrom: 爆発以外でプレイヤーに殴られたら壊れてアイテムに戻る。 */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source) || !this.isAlive()) {
            return false;
        }
        if (source.getEntity() instanceof Player player) {
            if (!this.level().isClientSide() && (this.isIndependent || this.getVehicle() == null)) {
                this.discard();
                if (!player.getAbilities().instabuild) {
                    this.dropCargoItem();
                }
            }
            return true;
        }
        return false;
    }

    /**
     * ★設置した貨物は<b>ワールドに保存する</b>。
     * 親クラス {@link EntityVehiclePart} は座席/床が対象なので保存しない設定だが、
     * それを継いだままだとリロードで置いた貨物が消える。
     */
    @Override
    public boolean shouldBeSaved() {
        return this.isIndependent || super.shouldBeSaved();
    }

    /** 設置した貨物は殴って壊せる。親の「親を失ったら触れない」判定に巻き込まれないようにする。 */
    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean canBeCollidedWith() {
        return !this.isRemoved();
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    protected void dropCargoItem() {
        this.writeCargoToItem();
        if (!this.itemCargo.isEmpty()) {
            this.spawnAtLocation(this.itemCargo);
        }
    }
}
