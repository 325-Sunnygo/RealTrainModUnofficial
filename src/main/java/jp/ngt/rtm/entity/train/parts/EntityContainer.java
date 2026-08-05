package jp.ngt.rtm.entity.train.parts;

import com.portofino.realtrainmodunofficial.cargo.CargoDefinition;
import jp.ngt.rtm.entity.RTMEntities;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * コンテナ。本家 {@code jp.ngt.rtm.entity.train.parts.EntityContainer} の移植。
 *
 * <p>6×7 = 42 スロットの物入れ。設置してあるコンテナに<b>コンテナアイテムを右クリックで積める</b>。
 * 本家 {@code setSize(3.0F, 2.5F)}。
 */
public class EntityContainer extends EntityCargoWithModel implements Container {
    public static final int SLOTS = 6 * 7;

    private final NonNullList<ItemStack> containerSlots = NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    public EntityContainer(EntityType<? extends EntityContainer> type, Level level) {
        super(type, level);
    }

    public EntityContainer(Level level) {
        this(RTMEntities.CONTAINER.get(), level);
    }

    @Override
    protected CargoDefinition.Kind getKind() {
        return CargoDefinition.Kind.CONTAINER;
    }

    @Override
    protected void readCargoFromNBT(CompoundTag nbt) {
        super.readCargoFromNBT(nbt);
        this.containerSlots.clear();
        ContainerHelper.loadAllItems(nbt, this.containerSlots, this.registryAccess());
    }

    @Override
    protected void writeCargoToNBT(CompoundTag nbt) {
        super.writeCargoToNBT(nbt);
        ContainerHelper.saveAllItems(nbt, this.containerSlots, true, this.registryAccess());
    }

    /**
     * 本家 processInitialInteract: コンテナアイテムを持って右クリックすると<b>上に積む</b>。
     * それ以外は中身を開く。スニークはモデル選択 (親クラス)。
     */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        InteractionResult parent = super.interact(player, hand);
        if (parent != InteractionResult.PASS) {
            return parent;
        }

        ItemStack held = player.getItemInHand(hand);
        if (this.isIndependent && held.getItem() instanceof jp.ngt.rtm.item.ItemCargo
            && jp.ngt.rtm.item.ItemCargo.getVariant(held) == 0) {
            if (!this.level().isClientSide()) {
                this.stackOnTop(player, held);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide());
        }

        //本家: バールで右クリックすると壊れる
        if (this.isIndependent && held.is(com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems.CROWBAR_ITEM.get())) {
            if (!this.level().isClientSide()) {
                this.dropCargoItem();
                this.discard();
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide());
        }

        if (this.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
            (id, inv, p) -> new net.minecraft.world.inventory.ChestMenu(
                net.minecraft.world.inventory.MenuType.GENERIC_9x6, id, inv, this, 6),
            net.minecraft.network.chat.Component.translatable(
                "entity.realtrainmodunofficial.rtm_container")));
        return InteractionResult.CONSUME;
    }

    /** 本家: 真上に積まれているコンテナの一番上へ、高さぶんずらして新しいコンテナを置く。 */
    private void stackOnTop(Player player, ItemStack held) {
        double d0 = 1.5D;
        double d1 = 256.0D;
        List<Entity> list = this.level().getEntities(player,
            new AABB(this.getX() - d0, this.getY(), this.getZ() - d0,
                this.getX() + d0, this.getY() + d1, this.getZ() + d0));
        EntityContainer top = this;
        for (Entity entity : list) {
            if (entity instanceof EntityContainer other && other.getY() > top.getY()) {
                top = other;
            }
        }
        CargoDefinition def = top.getDefinition();
        float height = def == null ? 2.5F : def.getContainerHeight();

        EntityContainer cargo = new EntityContainer(this.level());
        cargo.initPlaced(held.copy());
        cargo.moveTo(top.getX(), top.getY() + height, top.getZ(), top.getYRot(), 0.0F);
        cargo.setModelId(jp.ngt.rtm.item.ItemCargo.getModelId(held));
        cargo.readCargoFromItem();
        this.level().addFreshEntity(cargo);
        held.shrink(1);
    }

    // ───── Container ─────

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : this.containerSlots) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.containerSlots.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack stack = ContainerHelper.removeItem(this.containerSlots, slot, amount);
        if (!stack.isEmpty()) {
            this.setChanged();
        }
        return stack;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(this.containerSlots, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.containerSlots.set(slot, stack);
        stack.limitSize(this.getMaxStackSize());
        this.setChanged();
    }

    @Override
    public void setChanged() {
        //中身はアイテム側にも書き戻す (壊すとその中身のまま落ちる)
        this.writeCargoToItem();
    }

    @Override
    public boolean stillValid(Player player) {
        return this.isAlive() && player.distanceToSqr(this) <= 64.0D;
    }

    @Override
    public void clearContent() {
        this.containerSlots.clear();
    }

    /** 本家 getCollisionBox: 列車・台車・床とはぶつからない。 */
    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}
