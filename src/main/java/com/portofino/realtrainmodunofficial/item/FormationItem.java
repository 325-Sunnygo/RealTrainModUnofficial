package com.portofino.realtrainmodunofficial.item;

import com.portofino.realtrainmodunofficial.ClientHooks;
import com.portofino.realtrainmodunofficial.formation.FormationSpawner;
import com.portofino.realtrainmodunofficial.formation.TrainFormation;
import com.portofino.realtrainmodunofficial.formation.TrainFormationData;
import com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition;
import com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 編成アイテム。
 *
 * <p>何も無い所を右クリック → 編成の編集画面 (＋ボタンで車両を足す / 車両ボタンで差し替え)。
 * 線路を右クリック → 保存した編成を<b>連結した状態でまとめて設置</b>する。
 *
 * <p>1 両ずつ置く {@link TrainItem} とは別物。あちらはそのまま。
 */
public class FormationItem extends Item {

    /** 連打で編成が二重に出ないようにする。 */
    private static final int SPAWN_COOLDOWN_TICKS = 10;

    public FormationItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            ClientHooks.openFormationScreen(stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        // シフト右クリックは編集画面 (線路の上でも編成をいじれるように)
        if (player.isShiftKeyDown()) {
            if (level.isClientSide) {
                ClientHooks.openFormationScreen(context.getItemInHand());
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResult.PASS;
        }
        if (spawnFormation(level, player, context.getItemInHand(), context.getClickedPos())) {
            player.getCooldowns().addCooldown(this, SPAWN_COOLDOWN_TICKS);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.FAIL;
    }

    /** 保存した編成をクリックした線路へ設置する。 */
    private boolean spawnFormation(Level level, Player player, ItemStack stack, BlockPos clicked) {
        TrainFormation formation = TrainFormationData.getFormation(stack);
        if (formation == null || formation.isEmpty()) {
            player.displayClientMessage(
                Component.translatable("message.realtrainmodunofficial.formation.empty"), true);
            return false;
        }
        FormationSpawner.Result result = FormationSpawner.spawn(
            level, clicked, player.getYRot(), formation.getAllVehicles());
        switch (result) {
            case OK -> {
                return true;
            }
            case NO_RAIL -> player.displayClientMessage(
                Component.translatable("message.realtrainmodunofficial.train.must_be_on_rail"), true);
            case NOT_ENOUGH_RAIL -> player.displayClientMessage(
                Component.translatable("message.realtrainmodunofficial.formation.not_enough_rail"), true);
            case OCCUPIED -> player.displayClientMessage(
                Component.translatable("message.realtrainmodunofficial.train.already_exists"), true);
            default -> player.displayClientMessage(
                Component.translatable("message.realtrainmodunofficial.formation.empty"), true);
        }
        return false;
    }

    /** 「編成 (3両)」のように両数を出す。 */
    @Override
    public Component getName(ItemStack stack) {
        TrainFormation formation = TrainFormationData.getFormation(stack);
        Component base = super.getName(stack);
        if (formation == null || formation.isEmpty()) {
            return base;
        }
        return Component.literal(base.getString() + " (" + formation.getCarCount() + "両)");
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        TrainFormation formation = TrainFormationData.getFormation(stack);
        if (formation == null || formation.isEmpty()) {
            lines.add(Component.translatable("tooltip.realtrainmodunofficial.formation.empty")
                .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        // 先頭から順に何両目が何か。長い編成は省略する
        int shown = Math.min(formation.getCarCount(), 8);
        for (int i = 0; i < shown; i++) {
            String id = formation.getVehicle(i);
            VehicleDefinition def = VehicleRegistry.getById(id);
            String name = def != null ? def.getDisplayName() : id;
            lines.add(Component.literal(" " + (i + 1) + ": " + name).withStyle(ChatFormatting.GRAY));
        }
        if (formation.getCarCount() > shown) {
            lines.add(Component.literal(" … 他 " + (formation.getCarCount() - shown) + " 両")
                .withStyle(ChatFormatting.DARK_GRAY));
        }
        lines.add(Component.translatable("tooltip.realtrainmodunofficial.formation.hint")
            .withStyle(ChatFormatting.DARK_GRAY));
    }
}
