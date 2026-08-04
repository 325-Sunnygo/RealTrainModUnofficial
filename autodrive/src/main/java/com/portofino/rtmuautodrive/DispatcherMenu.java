package com.portofino.rtmuautodrive;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** 列車運転スポナーの画面 (編成アイテム 1 枠 + 名前欄)。名前欄は画面側で送る。 */
public class DispatcherMenu extends AbstractContainerMenu {

    /**
     * 画面の寸法と枠の位置。<b>画面クラスと必ず揃えること</b>
     * (ここだけ直すと枠と絵がずれる)。
     */
    public static final int WIDTH = 176;
    public static final int HEIGHT = 244;
    /** 編成アイテムの枠。 */
    public static final int SLOT_X = 80;
    public static final int SLOT_Y = 52;
    /** 持ち物の 3 段。 */
    public static final int INV_Y = HEIGHT - 82;
    /** ホットバー。 */
    public static final int HOTBAR_Y = HEIGHT - 24;

    private final Container container;
    private final BlockPos pos;

    /** サーバー側 (ブロックエンティティを直接使う)。 */
    public DispatcherMenu(int id, Inventory inventory, TrainDispatcherBlockEntity be) {
        this(id, inventory, be, be.getBlockPos());
    }

    /** クライアント側 (中身はダミーの入れ物)。 */
    public DispatcherMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, new SimpleContainer(1), buf.readBlockPos());
    }

    private DispatcherMenu(int id, Inventory inventory, Container container, BlockPos pos) {
        super(AutoDriveRegistry.dispatcherMenuType(), id);
        this.container = container;
        this.pos = pos;
        checkContainerSize(container, 1);

        //編成アイテムの枠 (名前欄の下・中央)
        this.addSlot(new Slot(container, 0, SLOT_X, SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof com.portofino.realtrainmodunofficial.item.FormationItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        //プレイヤーのインベントリ (画面が縦に伸びたぶん下げる)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col, 8 + col * 18, HOTBAR_Y));
        }
    }

    public BlockPos getPos() {
        return this.pos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index == 0) {
            //枠 → 持ち物
            if (!this.moveItemStackTo(stack, 1, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, 0, 1, false)) {
            //持ち物 → 枠 (編成アイテムだけ入る)
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }
}
