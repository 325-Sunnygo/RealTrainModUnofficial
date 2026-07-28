package com.portofino.realtrainmodunofficial.item;

import com.portofino.realtrainmodunofficial.entity.EditorEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * エディタ (neo mcte)。本家 MCTE ItemEditor の移植。
 * 操作:
 */
public class EditorItem extends Item {

    public EditorItem() {
        super(new Properties().stacksTo(1));
    }

    /** 何もない所を右クリック: エディタ画面 (本家 MCTE と同じ)。 */
    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(
            Level level, Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            com.portofino.realtrainmodunofficial.ClientHooks.openEditorScreen();
        } else {
            jp.ngt.mcte.editor.EditorSelection.of(player);
        }
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /**
     * ブロックを右クリックで 1 点目 → 2 点目。スニークで解除。
     * ★選択はクライアントとサーバの両方で同じように更新する (MCTEU と同じ)。
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        BlockPos pos = context.getClickedPos();

        if (level.isClientSide) {
            if (player.isShiftKeyDown()) {
                com.portofino.realtrainmodunofficial.client.ClientSelection.clear();
                com.portofino.realtrainmodunofficial.client.render.SelectionRenderer.forget();
            } else if (!com.portofino.realtrainmodunofficial.client.ClientSelection.hasStart()
                    || com.portofino.realtrainmodunofficial.client.ClientSelection.hasEnd()) {
                com.portofino.realtrainmodunofficial.client.ClientSelection.setStart(pos);
            } else {
                com.portofino.realtrainmodunofficial.client.ClientSelection.setEnd(pos);
            }
            return InteractionResult.SUCCESS;
        }

        jp.ngt.mcte.editor.EditorSelection sel = jp.ngt.mcte.editor.EditorSelection.of(player);
        if (player.isShiftKeyDown()) {
            jp.ngt.mcte.editor.filter.UndoHistory.forget(sel);
            jp.ngt.mcte.editor.EditorSelection.clear(player);
            player.displayClientMessage(Component.translatable("msg.realtrainmodunofficial.editor.cleared"), true);
            return InteractionResult.SUCCESS;
        }
        if (!sel.hasEnd() && !sel.getStart().equals(BlockPos.ZERO) || sel.hasEnd()) {
            // 2 点目 (すでに確定済みなら 1 点目から取り直し)
            if (sel.hasEnd()) {
                sel.setStart(pos);
                player.displayClientMessage(Component.literal("Pos1: " + pos.toShortString()), true);
            } else {
                sel.setEnd(pos);
                player.displayClientMessage(Component.literal("Pos2: " + pos.toShortString()
                    + " (" + describe(sel) + ")"), true);
            }
        } else {
            sel.setStart(pos);
            player.displayClientMessage(Component.literal("Pos1: " + pos.toShortString()), true);
        }
        return InteractionResult.SUCCESS;
    }

    private static String describe(jp.ngt.mcte.editor.EditorSelection sel) {
        AABB b = sel.getSelectionBox();
        return String.format("%dx%dx%d = %d",
            (int) b.getXsize(), (int) b.getYsize(), (int) b.getZsize(), sel.getVolume());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.realtrainmodunofficial.editor.desc")
            .withStyle(ChatFormatting.DARK_GRAY));
    }
}
