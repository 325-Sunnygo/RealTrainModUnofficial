package com.portofino.realtrainmodunofficial.item;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialComponents;
import com.portofino.realtrainmodunofficial.ClientHooks;
import com.portofino.realtrainmodunofficial.block.MarkerBlock;
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

        //敷設済みのレールを右クリック → そのレールのモデルを、今このアイテムで選んでいる
        //モデルに差し替える。引き直さずに見た目だけ変えられるようにするため。
        if (level.getBlockEntity(context.getClickedPos()) instanceof TileEntityLargeRailBase railBase) {
            TileEntityLargeRailCore core = railBase.getRailCore();
            if (core != null) {
                if (!level.isClientSide) {
                    changeRailModel(core, stack, player);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        if (stack.get(RealTrainModUnofficialComponents.RAIL_PREVIEW_START.get()) == null) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            String selectedId = stack.get(RealTrainModUnofficialComponents.SELECTED_MODEL_ID.get());
            // バニラのブロック設置と同様、クリックした面の隣(地面の上)を基準位置にする。
            // クリックした地面ブロックそのものを渡すとレールが1ブロック低く=地面にめり込んで
            // 削れて見えるため。コピー元レールのコアも地面の1つ上にあったので +1 で高さが合う。
            BlockPos placePos = context.getClickedPos().relative(context.getClickedFace());
            boolean created = MarkerBlock.placeCopiedRailAt(level, placePos, player, stack, selectedId);
            if (created && !player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            return created ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * 敷設済みレールのモデル差し替え。
     *
     * <p>本家 ItemRail もレールを右クリックするとモデルをいじれる (シフトで差し替え、
     * 素で重ねレールの追加) が、そちらは「コピーしたレール」アイテム側の話で、
     * モデル選択式の通常レールアイテムからは何もできなかった。
     * 引き直さずに見た目だけ変えたい、という要望に応えて右クリックで差し替える。
     *
     * <p>シフト右クリックは本家どおり<b>重ねレール</b> (同じ線形に別モデルを重ねる。
     * 三線軌条やガードレールを足すのに使う) の追加/削除にしてある。
     *
     * <p>差し替えると {@code markBlockForUpdate} が飛び、クライアント側の
     * {@code loadAdditional} が {@code shouldRerenderRail} を立てるので、
     * 統合メッシュ (VBO) も焼き直される。
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
        //道床のブロックと高さは今のレールのものを引き継ぐ (変えるのはモデルだけ)
        RailProperty next = new RailProperty(selectedId, old.block, old.blockMetadata, old.blockHeight);

        if (player.isShiftKeyDown()) {
            //本家 ItemRail: 同じ線形に別モデルを重ねる (もう一度で解除)
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
}
