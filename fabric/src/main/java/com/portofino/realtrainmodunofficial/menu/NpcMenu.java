package com.portofino.realtrainmodunofficial.menu;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialMenus;
import jp.ngt.rtm.entity.npc.EntityNPC;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Equipable;

/**
 * NPC の装備画面。本家 {@code ContainerNPC} (guiIdNPC) の簡易移植。
 * スロットは 手持ち 1 + 防具 4。閉じると NPC 本体の装備へ書き戻し、AI を組み直す。
 */
public class NpcMenu extends AbstractContainerMenu {
    private static final EquipmentSlot[] ARMOR = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private final EntityNPC npc;
    /** NPC の装備を映す作業用 (メニュー自身の slots と名前が被らないように)。 */
    final Container equipment = new SimpleContainer(5);

    /** クライアント側 (中身はサーバーが同期する)。 */
    public NpcMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null);
    }

    public NpcMenu(int containerId, Inventory inventory, EntityNPC npc) {
        super(RealTrainModUnofficialMenus.NPC.get(), containerId);
        this.npc = npc;
        if (npc != null) {
            this.equipment.setItem(0, npc.getMainHandItem());
            for (int i = 0; i < ARMOR.length; ++i) {
                this.equipment.setItem(1 + i, npc.getItemBySlot(ARMOR[i]));
            }
        }

        //手持ち
        this.addSlot(new Slot(this.equipment, 0, 26, 54));
        //防具 (頭→足)
        for (int i = 0; i < ARMOR.length; ++i) {
            final EquipmentSlot armorSlot = ARMOR[i];
            this.addSlot(new Slot(this.equipment, 1 + i, 80, 18 + i * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof Equipable equipable
                        && equipable.getEquipmentSlot() == armorSlot;
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        //プレイヤー
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 102 + row * 18));
            }
        }
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(inventory, col, 8 + col * 18, 160));
        }
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (this.npc != null && !player.level().isClientSide()) {
            this.npc.setItemSlot(EquipmentSlot.MAINHAND, this.equipment.getItem(0));
            for (int i = 0; i < ARMOR.length; ++i) {
                this.npc.setItemSlot(ARMOR[i], this.equipment.getItem(1 + i));
            }
            this.npc.onInventoryChanged();
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return this.npc == null || (this.npc.isAlive() && player.distanceToSqr(this.npc) <= 64.0D);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < 5) {
                if (!this.moveItemStackTo(stack, 5, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, 0, 5, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }
}
