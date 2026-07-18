package com.portofino.realtrainmodunofficial.item;

import com.portofino.realtrainmodunofficial.ClientHooks;
import com.portofino.realtrainmodunofficial.entity.CarEntity;
import com.portofino.realtrainmodunofficial.registry.RealTrainModUnofficialEntities;
import com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition;
import com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class CarItem extends Item {
    public CarItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();
        String selectedId = com.portofino.realtrainmodunofficial.compat.LegacyItemStackBridge.getSelectedModelId(stack);
        //専用サーバー保険: コンポーネントが同期・保持されない環境では、サーバー側に控えた選択を使う。
        //(クライアントでは控えが空なので従来通り選択画面が開く)
        if ((selectedId == null || selectedId.isBlank()) && context.getPlayer() != null) {
            selectedId = com.portofino.realtrainmodunofficial.vehicle.ServerVehicleSelection.get(context.getPlayer().getUUID());
        }
        // モデル未選択時は spawn せずに直接選択画面を開く。
        // useOn で PASS しても use() は自動では呼ばれないため、ここで client 側に
        // フックして選択画面を開かないと UI が出ないまま。
        if (selectedId == null || selectedId.isBlank()) {
            if (level.isClientSide() && context.getPlayer() != null) {
                ClientHooks.openCarSelectScreen(context.getPlayer(), stack);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        VehicleDefinition def = VehicleRegistry.getById(selectedId);
        if (def == null || !def.isCarType()) {
            if (level.isClientSide() && context.getPlayer() != null) {
                ClientHooks.openCarSelectScreen(context.getPlayer(), stack);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos();
        Vec3 spawnPos = Vec3.atBottomCenterOf(pos.above());
        EntityType<CarEntity> type = RealTrainModUnofficialEntities.CAR.get();
        CarEntity car = type.create(level);
        if (car == null) {
            return InteractionResult.FAIL;
        }
        car.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, context.getPlayer() != null ? context.getPlayer().getYRot() : 0f, 0f);
        car.setVehicleId(selectedId);
        level.addFreshEntity(car);
        return InteractionResult.sidedSuccess(false);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            ClientHooks.openCarSelectScreen(player, player.getItemInHand(hand));
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String selectedId = com.portofino.realtrainmodunofficial.compat.LegacyItemStackBridge.getSelectedModelId(stack);
        if (selectedId != null && !selectedId.isBlank()) {
            VehicleDefinition def = VehicleRegistry.getById(selectedId);
            String name = def != null ? def.getDisplayName() : selectedId;
            tooltip.add(Component.translatable("tooltip.realtrainmodunofficial.model.selected", name));
        } else {
            tooltip.add(Component.translatable("tooltip.realtrainmodunofficial.model.none"));
        }
    }
}
