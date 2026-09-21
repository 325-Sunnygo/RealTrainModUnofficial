package com.portofino.realtrainmodunofficial.item;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialComponents;
import com.portofino.realtrainmodunofficial.ClientHooks;
import com.portofino.realtrainmodunofficial.rail.RailDefinition;
import com.portofino.realtrainmodunofficial.rail.RailRegistry;
import jp.ngt.rtm.rail.TileEntityLargeRailBase;
import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import jp.ngt.rtm.rail.util.RailProperty;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class RailItem extends Item {
    public RailItem() {
        super(new Properties());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.get(RealTrainModUnofficialComponents.RAIL_PREVIEW_START.get()) != null) {
            // コピー済み/調整済みレールは、空振り右クリックで選択UIへ戻さず、そのまま保持する。
            return InteractionResultHolder.pass(stack);
        }

        if (level.isClientSide) {
            ClientHooks.openRailSelectScreen(player, stack);
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        // 敷設済みのレールを右クリック → そのレールのモデルを、今このアイテムで選んでいる
        // モデルに差し替える。引き直さずに見た目だけ変えられるようにするため。
        if (level.getBlockEntity(context.getClickedPos()) instanceof TileEntityLargeRailBase railBase) {
            TileEntityLargeRailCore core = railBase.getRailCore();
            if (core != null) {
                if (!level.isClientSide) {
                    changeRailModel(core, stack, player);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        //★「コピーしたレールを貼り付ける」独自機能は本家に無く、RTMU 独自マーカー
        //  (MarkerBlock) に依存していたため削除した。本家 ItemRail はマーカーを
        //  レンチ/レールアイテムで設置して敷設する方式。
        return InteractionResult.PASS;
    }

    /**
     * 敷設済みレールのモデル差し替え。
     * 本家 ItemRail もレールを右クリックするとモデルをいじれる (シフトで差し替え、
     * 素で重ねレールの追加) が、そちらは「コピーしたレール」アイテム側の話で、
     * モデル選択式の通常レールアイテムからは何もできなかった。
     */
    private static void changeRailModel(TileEntityLargeRailCore core, ItemStack stack, Player player) {
        String selectedId = com.portofino.realtrainmodunofficial.compat.LegacyItemStackBridge
            .getSelectedModelId(stack);
        if (selectedId == null || selectedId.isBlank()) {
            player.displayClientMessage(
                Component.translatable("message.realtrainmodunofficial.rail_model_none"), true);
            return;
        }
        RailDefinition def = RailRegistry.getById(selectedId);
        String name = def != null ? def.getDisplayName() : selectedId;

        RailProperty old = core.getProperty();
        // 道床のブロックと高さは今のレールのものを引き継ぐ (変えるのはモデルだけ)
        RailProperty next = new RailProperty(selectedId, old.block, old.blockMetadata, old.blockHeight);

        // 本家と同じ操作。素で重ね、シフトで差し替え。
        if (!player.isShiftKeyDown()) {
            // 同じ線形に別モデルを重ねる (もう一度で解除)
            boolean had = core.subRails.stream()
                .anyMatch(p -> p.railModel.equals(selectedId));
            core.addSubRail(next);
            player.displayClientMessage(Component.translatable(
                had ? "message.realtrainmodunofficial.rail_subrail_removed"
                    : "message.realtrainmodunofficial.rail_subrail_added", name), true);
            return;
        }

        if (selectedId.equals(old.railModel)) {
            player.displayClientMessage(
                Component.translatable("message.realtrainmodunofficial.rail_model_same"), true);
            return;
        }
        core.replaceRail(next);
        player.displayClientMessage(
            Component.translatable("message.realtrainmodunofficial.rail_model_changed", name), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        String selectedId = stack.get(RealTrainModUnofficialComponents.SELECTED_MODEL_ID.get());
        if (selectedId != null && !selectedId.isBlank()) {
            RailDefinition def = RailRegistry.getById(selectedId);
            String name = def != null ? def.getDisplayName() : selectedId;
            lines.add(Component.translatable("tooltip.realtrainmodunofficial.model.selected", name)
                .withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("tooltip.realtrainmodunofficial.model.none")
                .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /**
     * 本家 ItemRail.getItemStackDisplayName と同じ形。
     * 「レール (1067mm_PC, 砂利)」のように、レールの種類と道床を出す。
     */
    @Override
    public net.minecraft.network.chat.Component getName(net.minecraft.world.item.ItemStack stack) {
        net.minecraft.network.chat.Component base = super.getName(stack);
        String railId = stack.get(RealTrainModUnofficialComponents.SELECTED_MODEL_ID.get());
        if (railId == null || railId.isBlank()) {
            return base;
        }
        RailDefinition def = RailRegistry.getById(railId);
        String railName = def != null && def.getDisplayName() != null && !def.getDisplayName().isBlank()
            ? def.getDisplayName() : railId;

        StringBuilder sb = new StringBuilder(base.getString()).append(" (").append(railName);
        String ballast = stack.get(RealTrainModUnofficialComponents.SELECTED_BALLAST.get());
        if (ballast != null && !ballast.isBlank()) {
            net.minecraft.resources.ResourceLocation rl =
                net.minecraft.resources.ResourceLocation.tryParse(ballast);
            net.minecraft.world.level.block.Block block = rl == null ? null
                : net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(rl);
            if (block != null && block != net.minecraft.world.level.block.Blocks.AIR) {
                sb.append(", ").append(block.getName().getString());
            }
        }
        sb.append(')');
        return net.minecraft.network.chat.Component.literal(sb.toString());
    }
}
