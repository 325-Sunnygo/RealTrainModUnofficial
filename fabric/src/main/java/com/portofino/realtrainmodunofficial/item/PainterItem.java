package com.portofino.realtrainmodunofficial.item;

import jp.ngt.mcte.editor.filter.UndoHistory;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * ペインター (neo mcte)。本家 MCTE {@code ItemPainter} の移植。
 *
 * <p>クリックした所を中心に、設定した形と大きさでブロックを塗る。
 * エディタで範囲を取るほどでもない細かい修正のための道具。
 *
 * <p>操作:
 * <ul>
 *   <li>ブロックを右クリック: 塗る</li>
 *   <li>スニーク + 右クリック: 消す (空気で塗る)</li>
 *   <li>何もない所を右クリック: 設定画面</li>
 * </ul>
 *
 * <p>設定はスタックの NBT に持つ。本家 {@code PainterSetting} と同じ考え方で、
 * ペインターを複数持てばそれぞれ別の設定にできる。
 */
public class PainterItem extends Item {

    public static final String KEY_BLOCK = "Block";
    public static final String KEY_SIZE = "Size";
    public static final String KEY_SHAPE = "Shape";
    public static final String KEY_ONLY_SOLID = "OnlySolid";

    public static final String SHAPE_SPHERE = "sphere";
    public static final String SHAPE_CUBE = "cube";

    /** 1 回で塗れる上限。 */
    private static final int MAX_BLOCKS = 32768;

    public PainterItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(
            Level level, Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            com.portofino.realtrainmodunofficial.ClientHooks.openPainterSettingsScreen(
                stack, hand == net.minecraft.world.InteractionHand.OFF_HAND);
        }
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide || !(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        CompoundTag tag = getTag(stack);

        BlockState to = player.isShiftKeyDown()
            ? Blocks.AIR.defaultBlockState()
            : resolveBlock(tag, player);
        if (to == null) {
            player.displayClientMessage(Component.translatable("msg.realtrainmodunofficial.painter.no_block"), true);
            return InteractionResult.FAIL;
        }

        int size = Math.max(1, Math.min(16, tag.contains(KEY_SIZE) ? tag.getInt(KEY_SIZE) : 1));
        boolean sphere = !SHAPE_CUBE.equals(tag.getString(KEY_SHAPE));
        boolean onlySolid = tag.getBoolean(KEY_ONLY_SOLID);

        BlockPos center = context.getClickedPos();
        int r = size - 1;
        UndoHistory.Snapshot snapshot = new UndoHistory.Snapshot();
        int changed = 0;
        for (int dy = -r; dy <= r; dy++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (sphere && dx * dx + dy * dy + dz * dz > r * r) {
                        continue;
                    }
                    if (changed >= MAX_BLOCKS) {
                        break;
                    }
                    BlockPos p = center.offset(dx, dy, dz);
                    BlockState cur = server.getBlockState(p);
                    if (cur.equals(to)) {
                        continue;
                    }
                    if (onlySolid && cur.isAir()) {
                        continue;
                    }
                    snapshot.record(server, p, cur);
                    server.setBlock(p, to, 3);
                    changed++;
                }
            }
        }
        if (changed > 0) {
            //ペインターの履歴はプレイヤー単位。エディタとは別に戻せる。
            UndoHistory.push(player.getUUID(), snapshot);
        }
        return InteractionResult.CONSUME;
    }

    /** 設定のブロック。空欄ならオフハンド。 */
    private static BlockState resolveBlock(CompoundTag tag, Player player) {
        String id = tag.getString(KEY_BLOCK);
        if (id != null && !id.isBlank()) {
            try {
                Block b = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id.trim()));
                if (b != Blocks.AIR || id.trim().endsWith("air")) {
                    return b.defaultBlockState();
                }
            } catch (Exception ignored) {
                //不正な指定はオフハンドへ落とす
            }
        }
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof BlockItem bi) {
            return bi.getBlock().defaultBlockState();
        }
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        CompoundTag tag = getTag(stack);
        int size = tag.contains(KEY_SIZE) ? tag.getInt(KEY_SIZE) : 1;
        String shape = SHAPE_CUBE.equals(tag.getString(KEY_SHAPE)) ? "cube" : "sphere";
        String block = tag.getString(KEY_BLOCK);
        lines.add(Component.literal((block == null || block.isBlank() ? "(offhand)" : block)
            + "  " + shape + " r=" + size).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.realtrainmodunofficial.painter.desc")
            .withStyle(ChatFormatting.DARK_GRAY));
    }

    public static CompoundTag getTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null ? data.copyTag() : new CompoundTag();
    }

    public static void setTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
