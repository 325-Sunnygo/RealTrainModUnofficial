package com.portofino.realtrainmodunofficial.menu;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialMenus;
import com.portofino.realtrainmodunofficial.entity.EditorEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * エディタのコンテナ (neo mcte)。本家 MCTE {@code ContainerEditor} の移植。
 *
 * <p>スロット配置も本家そのまま:
 * <ul>
 *   <li>0: 埋めるブロック — (72, 152)</li>
 *   <li>1: 置換先ブロック — (72, 172)</li>
 *   <li>プレイヤーのホットバー 9 個 — (8 + i*20, 142)</li>
 * </ul>
 * ホットバーの間隔が 20 (バニラは 18) なのも本家どおり。
 */
public class EditorMenu extends AbstractContainerMenu {

    private final net.minecraft.world.Container editorInv;
    private final EditorEntity editor;

    /** クライアント側 (エンティティ id を受け取って引き当てる)。 */
    public EditorMenu(int id, Inventory playerInv, FriendlyByteBuf buf) {
        this(id, playerInv, resolve(playerInv, buf.readVarInt()));
    }

    public EditorMenu(int id, Inventory playerInv, EditorEntity editor) {
        super(RealTrainModUnofficialMenus.EDITOR.get(), id);
        this.editor = editor;
        this.editorInv = editor != null ? editor : new SimpleContainer(2);

        addSlot(new Slot(editorInv, EditorEntity.SLOT_FILL, 72, 152));
        addSlot(new Slot(editorInv, EditorEntity.SLOT_REPLACE, 72, 172));
        for (int i = 0; i < 9; i++) {
            addSlot(new Slot(playerInv, i, 8 + i * 20, 142));
        }
    }

    private static EditorEntity resolve(Inventory playerInv, int entityId) {
        var e = playerInv.player.level().getEntity(entityId);
        return e instanceof EditorEntity ed ? ed : null;
    }

    public EditorEntity editor() {
        return editor;
    }

    @Override
    public boolean stillValid(Player player) {
        return editorInv.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        //本家と同じ: エディタ側 <-> ホットバー の往復だけ
        if (index < 2) {
            if (!moveItemStackTo(stack, 2, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, 2, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }
}
