package com.portofino.realtrainmodunofficial.item;

import jp.ngt.ngtlib.math.NGTMath;
import jp.ngt.rtm.rail.BlockMarker;
import jp.ngt.rtm.rail.TileEntityLargeRailBase;
import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import jp.ngt.rtm.rail.TileEntityMarker;
import jp.ngt.rtm.rail.util.RailPosition;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 本家 jp.ngt.rtm.item.ItemWrench (KaizPatchX) の忠実移植。
 * モード (本家はダメージ値、ここではコンポーネント):
 *  0:マーカー設置 1:分岐マーカー設置 6:距離表示切替 7:表示モード切替
 *  8:マーカー高さ変更 10:隣接レール接続切替 11:レール→マーカー復元
 *  (2:直線マーカー, 9:アンカー移動 は未移植 — TODO)
 * 空中右クリック: モード切替 / スニーク+右クリック: モードロック
 */
public class RtmWrenchItem extends Item {
    //モード 9 (アンカー移動) 中はアンカー線を右クリックで掴んで動かす (MarkerBlockEntityRenderer)
    private static final int[] MODE_CYCLE = {0, 1, 6, 7, 8, 9, 10, 11, 12};
    /** マーカーをブロック未満の位置に動かす (本家に無い。ペンマーカーの代わり)。 */
    public static final int MODE_OFFSET = 12;
    public static final int MODE_ANCHOR = 9;
    /**
     * RailPosition.direction (8方位) → マーカー blockstate facing
     */
    private static final int[] DIRECTION_META_MAP = {2, 5, 1, 4, 0, 7, 3, 6};

    public RtmWrenchItem() {
        super(new Properties().stacksTo(1));
    }

    //---- モード管理 (本家はダメージ値 + NBT ModeLocked) ----

    public static int getMode(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null ? data.copyTag().getInt("WrenchMode") : 0;
    }

    private static void setMode(ItemStack stack, int mode) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt("WrenchMode", mode));
    }

    public static boolean isModeLocked(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean("ModeLocked");
    }

    private static void setModeLocked(ItemStack stack, boolean locked) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean("ModeLocked", locked));
    }

    private static String modeName(int mode) {
        return switch (mode) {
            case 0 -> "マーカー設置";
            case 1 -> "分岐マーカー設置";
            case 6 -> "距離表示 切替";
            case 7 -> "表示モード切替";
            case 8 -> "マーカー高さ変更";
            case 9 -> "アンカー移動 (レール形状編集)";
            case 10 -> "隣接レール接続 切替";
            case 11 -> "レール→マーカー復元";
            case 12 -> "マーカー位置調整";
            default -> "mode_" + mode;
        };
    }

    private static ChatFormatting modeColor(int mode) {
        return switch (mode) {
            case 0 -> ChatFormatting.RED;
            case 1 -> ChatFormatting.AQUA;
            default -> ChatFormatting.WHITE;
        };
    }

    /**
     * 本家 onItemRightClick: 空中右クリックでモード切替 (スニークでロック切替)
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            boolean locked = isModeLocked(stack);
            setModeLocked(stack, !locked);
            if (!level.isClientSide) {
                player.displayClientMessage(Component.literal(!locked
                        ? "レンチのモード変更をロックしました" : "レンチのモード変更をアンロックしました")
                        .withStyle(!locked ? ChatFormatting.GOLD : ChatFormatting.GREEN), true);
            }
            return InteractionResultHolder.success(stack);
        }

        if (isModeLocked(stack)) {
            if (!level.isClientSide) {
                int mode = getMode(stack);
                player.displayClientMessage(Component.literal("モード変更はロック中: ")
                        .withStyle(ChatFormatting.RED)
                        .append(Component.literal(modeName(mode)).withStyle(modeColor(mode))), true);
            }
            return InteractionResultHolder.success(stack);
        }

        int current = getMode(stack);
        int idx = 0;
        for (int i = 0; i < MODE_CYCLE.length; i++) {
            if (MODE_CYCLE[i] == current) {
                idx = i;
                break;
            }
        }
        int next = MODE_CYCLE[(idx + 1) % MODE_CYCLE.length];
        setMode(stack, next);
        if (!level.isClientSide) {
            player.displayClientMessage(Component.literal("モード: ")
                    .append(Component.literal(modeName(next)).withStyle(modeColor(next))), true);
        }
        return InteractionResultHolder.success(stack);
    }

    /**
     * 本家 onItemUse: 上面クリックでマーカー設置 / レール復元
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (player == null) {
            return InteractionResult.PASS;
        }
        int mode = getMode(stack);
        BlockPos pos = context.getClickedPos();

        //マーカーをクリックした場合は BlockMarker 側 (useItemOn) が処理する。
        //ただしスニーク中はバニラがブロック操作をスキップして item.use() (モードロック切替)
        //へ行ってしまうため、カント設定 GUI はここで開く。
        if (level.getBlockState(pos).getBlock() instanceof BlockMarker) {
            if (player.isShiftKeyDown()) {
                if (level.isClientSide
                        && level.getBlockEntity(pos) instanceof jp.ngt.rtm.rail.TileEntityMarker marker) {
                    com.portofino.realtrainmodunofficial.ClientHooks.openMarkerCantScreen(marker);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            return InteractionResult.PASS;
        }

        if (context.getClickedFace() != net.minecraft.core.Direction.UP && (mode <= 5 || mode >= 11)) {
            return InteractionResult.PASS;
        }

        if (mode == 11) {
            if (!level.isClientSide) {
                this.revertRailToMarker(level, pos);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        BlockPos above = pos.above();
        switch (mode) {
            case 0 -> this.placeMarker(player, level, above, jp.ngt.rtm.rail.RTMRailBlocks.MARKER.get());
            case 1 -> this.placeMarker(player, level, above, jp.ngt.rtm.rail.RTMRailBlocks.MARKER_SWITCH.get());
            default -> {
                return InteractionResult.PASS;
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * 本家 placeMarker: プレイヤーの向き (8方位) から facing を決めて設置
     */
    private void placeMarker(Player player, Level level, BlockPos pos, Block block) {
        if (level.isClientSide) {
            return;
        }
        if (!level.getBlockState(pos).canBeReplaced()) {
            return;
        }
        int dir = Mth.floor((NGTMath.normalizeAngle(player.getYRot() + 180.0D) / 45.0D) + 0.5D) & 7;
        int meta = dir / 2 + (dir % 2 == 0 ? 0 : 4);
        BlockState state = block.defaultBlockState().setValue(BlockMarker.META, meta);
        level.setBlock(pos, state, 3);
        if (block instanceof BlockMarker marker) {
            //本家 setPlacedBy 相当の初期化 (プレビュー表示)
            marker.setPlacedBy(level, pos, state, player, ItemStack.EMPTY);
        }
    }

    /**
     * 本家 revertRailToMarker: レールを撤去して RailPosition からマーカーを復元
     */
    public void revertRailToMarker(Level level, BlockPos pos) {
        BlockEntity tile = level.getBlockEntity(pos);
        if (!(tile instanceof TileEntityLargeRailBase rail)) {
            return;
        }
        TileEntityLargeRailCore core = rail.getRailCore();
        if (core == null) {
            return;
        }
        RailPosition[] rps = core.getRailPositions();
        //先に撤去 (onRemove の破壊チェーンでレール全体が消える)
        level.removeBlock(pos, false);
        if (rps == null) {
            return;
        }
        for (RailPosition rp : rps) {
            if (rp == null) {
                continue;
            }
            int meta = DIRECTION_META_MAP[rp.direction & 7];
            Block block = rp.switchType == 0
                    ? jp.ngt.rtm.rail.RTMRailBlocks.MARKER.get()
                    : jp.ngt.rtm.rail.RTMRailBlocks.MARKER_SWITCH.get();
            BlockPos markerPos = new BlockPos(rp.blockX, rp.blockY, rp.blockZ);
            level.setBlock(markerPos, block.defaultBlockState().setValue(BlockMarker.META, meta), 2);
            if (level.getBlockEntity(markerPos) instanceof TileEntityMarker marker) {
                marker.setMarkerRP(rp);
            }
        }
    }

    /**
     * 本家 onRightClickMarker: BlockMarker から呼ばれる (C/S 両方)
     */
    public void onRightClickMarker(ItemStack stack, Level level, Player player, TileEntityMarker marker) {
        switch (getMode(stack)) {
            case 6 -> {
                marker.displayDistance ^= true;
                if (!level.isClientSide) {
                    player.displayClientMessage(Component.literal("距離表示: " + (marker.displayDistance ? "ON" : "OFF")), true);
                }
            }
            case 7 -> marker.changeDisplayMode();
            case 8 -> this.changeMarkerHeight(level, player, marker);
            case 10 -> {
                marker.fitNeighbor ^= true;
                if (!level.isClientSide) {
                    player.displayClientMessage(Component.literal("隣接レール接続: " + (marker.fitNeighbor ? "ON" : "OFF")), true);
                }
            }
            case MODE_OFFSET -> {
                //ブロック未満の位置調整。ブロック単位でしか置けないマーカーの弱点を埋める。
                if (level.isClientSide) {
                    com.portofino.realtrainmodunofficial.ClientHooks.openMarkerOffsetScreen(marker);
                }
            }
            default -> {
            }
        }
    }

    /**
     * 本家 changeMarkerHeight
     */
    private void changeMarkerHeight(Level level, Player player, TileEntityMarker marker) {
        marker.setDisplayMode((byte) 2);
        byte b = marker.increaseHeight();
        if (!level.isClientSide && level.getBlockState(marker.getBlockPos()).getBlock() instanceof BlockMarker block) {
            block.makeRailMap(marker, marker.getBlockPos().getX(), marker.getBlockPos().getY(), marker.getBlockPos().getZ(), player);
            player.displayClientMessage(Component.literal("高さ: " + b), true);
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.empty().append(super.getName(stack))
                .append(Component.literal(" (" + modeName(getMode(stack)) + ")"));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (isModeLocked(stack)) {
            tooltip.add(Component.literal("モード変更: ロック中").withStyle(ChatFormatting.GOLD));
        } else {
            tooltip.add(Component.literal("モード変更: 空中で右クリック").withStyle(ChatFormatting.GREEN));
        }
        tooltip.add(Component.literal("スニーク+右クリックでロック切替").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.empty());
        for (int mode : MODE_CYCLE) {
            tooltip.add(Component.literal(mode + " : " + modeName(mode)).withStyle(ChatFormatting.GRAY));
        }
    }
}
