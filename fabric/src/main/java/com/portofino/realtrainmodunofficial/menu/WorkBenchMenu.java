package com.portofino.realtrainmodunofficial.menu;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialMenus;
import com.portofino.realtrainmodunofficial.recipe.RealTrainModUnofficialRecipes;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * RTM 専用作業台の中身。本家 {@code ContainerRTMWorkBench} の移植。
 *
 * <p>本家は 5x5 のクラフト枠 + 出力 5 枠だが、ここは<b>5x5 + 出力 1 枠</b>。
 * 5x5 に収まらない本家レシピは無いので、出力を 5 枠持つ意味 (同時に複数種) は無い。
 */
public class WorkBenchMenu extends AbstractContainerMenu {
    public static final int GRID = 5;

    private final CraftingContainer craftSlots = new CraftingContainer(this);
    private final ResultContainer resultSlots = new ResultContainer();
    private final ContainerLevelAccess access;
    private final Player player;

    public WorkBenchMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
    }

    public WorkBenchMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(RealTrainModUnofficialMenus.WORK_BENCH.get(), containerId);
        this.access = access;
        this.player = inventory.player;

        //出力
        this.addSlot(new ResultSlot(inventory.player, this.craftSlots, this.resultSlots, 0, 143, 53));
        //5x5
        for (int row = 0; row < GRID; ++row) {
            for (int col = 0; col < GRID; ++col) {
                this.addSlot(new Slot(this.craftSlots, col + row * GRID, 12 + col * 18, 17 + row * 18));
            }
        }
        //プレイヤー
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 122 + row * 18));
            }
        }
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(inventory, col, 8 + col * 18, 180));
        }
    }

    /** 5x5 のクラフト枠。バニラの {@code TransientCraftingContainer} は 3x3 前提なので自前。 */
    public static class CraftingContainer extends SimpleContainer implements net.minecraft.world.inventory.CraftingContainer {
        private final WorkBenchMenu menu;

        public CraftingContainer(WorkBenchMenu menu) {
            super(GRID * GRID);
            this.menu = menu;
        }

        @Override
        public int getWidth() {
            return GRID;
        }

        @Override
        public int getHeight() {
            return GRID;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            this.menu.slotsChanged(this);
        }

        @Override
        public void fillStackedContents(net.minecraft.world.entity.player.StackedContents contents) {
            for (int i = 0; i < this.getContainerSize(); ++i) {
                contents.accountSimpleStack(this.getItem(i));
            }
        }
    }

    @Override
    public void slotsChanged(Container container) {
        this.access.execute((level, pos) -> slotChangedCraftingGrid(level));
    }

    private void slotChangedCraftingGrid(Level level) {
        if (!(this.player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        CraftingInput input = CraftingInput.of(GRID, GRID, this.craftSlots.getItems());
        ItemStack result = ItemStack.EMPTY;
        //まず 5x5 の専用レシピ、無ければバニラの作業台レシピ
        Optional<RecipeHolder<WorkBenchRecipeMarker>> unused = Optional.empty();
        var recipeManager = level.getRecipeManager();
        Optional<RecipeHolder<com.portofino.realtrainmodunofficial.recipe.WorkBenchRecipe>> custom =
            recipeManager.getRecipeFor(RealTrainModUnofficialRecipes.WORK_BENCH_TYPE.get(), input, level);
        if (custom.isPresent()) {
            result = custom.get().value().assemble(input, level.registryAccess());
        } else {
            Optional<RecipeHolder<CraftingRecipe>> vanilla =
                recipeManager.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, level);
            if (vanilla.isPresent()) {
                result = vanilla.get().value().assemble(input, level.registryAccess());
            }
        }
        this.resultSlots.setItem(0, result);
        this.setRemoteSlot(0, result);
        serverPlayer.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(
            this.containerId, this.incrementStateId(), 0, result));
    }

    /** 型合わせのためのダミー (未使用)。 */
    private interface WorkBenchRecipeMarker extends net.minecraft.world.item.crafting.Recipe<CraftingInput> {
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.access.execute((level, pos) -> this.clearContainer(player, this.craftSlots));
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            int craftEnd = 1 + GRID * GRID;
            if (index == 0) {
                if (!this.moveItemStackTo(stack, craftEnd, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickCraft(stack, result);
            } else if (index >= craftEnd) {
                if (!this.moveItemStackTo(stack, 1, craftEnd, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, craftEnd, this.slots.size(), false)) {
                return ItemStack.EMPTY;
            }

            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (stack.getCount() == result.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, stack);
        }
        return result;
    }
}
