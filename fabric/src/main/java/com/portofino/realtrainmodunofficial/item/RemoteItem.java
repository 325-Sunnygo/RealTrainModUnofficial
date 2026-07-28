package com.portofino.realtrainmodunofficial.item;

import com.portofino.realtrainmodunofficial.remote.RemotePairings;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * リモコン: 2 つのブロックを無線レッドストーンで結ぶ。
 * 持ってシフト+右クリックで 1 つ目を選ぶと「ペアリング1」、もう 1 つを選ぶと
 * 「ペアリング2 → 完了」。
 */
public class RemoteItem extends Item {

    private static final String PENDING_KEY = "RemotePendingPos";

    public RemoteItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        Level level = ctx.getLevel();
        // シフト+右クリックのみペアリング操作 (通常の右クリックは何もしない)。
        if (player == null || !player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide || !(level instanceof ServerLevel sl)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos clicked = ctx.getClickedPos();
        ItemStack stack = ctx.getItemInHand();
        BlockPos pending = readPending(stack);
        if (pending == null) {
            writePending(stack, clicked);
            player.displayClientMessage(Component.literal(
                "§e[リモコン] ペアリング1: (" + clicked.toShortString() + ") §7— もう1つのブロックを選択"), true);
        } else if (pending.equals(clicked)) {
            player.displayClientMessage(Component.literal(
                "§c同じブロックです。別のブロックを選んでください"), true);
        } else {
            RemotePairings.get(sl).add(pending, clicked);
            clearPending(stack);
            player.displayClientMessage(Component.literal(
                "§a[リモコン] ペアリング2: (" + clicked.toShortString() + ") — §fペアリング完了！"), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("シフト+右クリックで2つのブロックをペアリング").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("片方がレッドストーンで動くと、もう片方も無線で連動")
            .withStyle(ChatFormatting.GRAY));
        BlockPos pending = readPending(stack);
        if (pending != null) {
            tooltip.add(Component.literal("ペアリング1済み: (" + pending.toShortString() + ")")
                .withStyle(ChatFormatting.YELLOW));
        }
    }

    // ---- 保留中の 1 つ目 (アイテム NBT) ----

    private static BlockPos readPending(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag tag = data.copyTag();
        return tag.contains(PENDING_KEY) ? BlockPos.of(tag.getLong(PENDING_KEY)) : null;
    }

    private static void writePending(ItemStack stack, BlockPos pos) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putLong(PENDING_KEY, pos.asLong());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static void clearPending(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return;
        }
        CompoundTag tag = data.copyTag();
        tag.remove(PENDING_KEY);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
