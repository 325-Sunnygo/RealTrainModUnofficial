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
        //本家 ItemVehicle.onItemUse:69 は上面 (par7 == 1) 以外を無視する。
        //側面/底面クリックで車が生成されるのは RTMU 独自の挙動だった。
        if (context.getClickedFace() != net.minecraft.core.Direction.UP) {
            return InteractionResult.PASS;
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
        //本家 ItemVehicle.onItemUse:88
        //  vehicle.rotationYaw = MathHelper.wrapAngleTo180_float(-player.rotationYaw);
        //RTMU は素の getYRot() を入れていたため、向きが本家と左右反転していた。
        float yaw = context.getPlayer() != null
                ? net.minecraft.util.Mth.wrapDegrees(-context.getPlayer().getYRot())
                : 0.0F;
        car.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, yaw, 0f);
        car.setVehicleId(selectedId);
        //本家 ItemVehicle.onItemUse:90
        //  vehicle.getResourceState().readFromNBT(this.getModelState(itemStack).writeToNBT());
        //アイテム側で設定した DataMap をエンティティへ移送する。これが無いと
        //モデル選択画面で入れた DataMap が生成時に消える (NGTO Builder 等の設定込み車両が壊れる)。
        applyItemDataMap(car, stack);
        level.addFreshEntity(car);
        //本家 ItemVehicle.onItemUse:95  クリエイティブ以外はアイテムを 1 個消費する。
        if (context.getPlayer() != null && !context.getPlayer().getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.sidedSuccess(false);
    }

    /**
     * アイテム NBT の DataMap を車へ移送する (本家 ResourceState.readFromNBT 相当)。
     * ブリッジが返す形式は {@code name=(type)value,name=(type)value}。
     * 車の DataMap は文字列マップ ({@link CarEntity.DataMapCompat}) なので型注記は落として値だけ入れる。
     */
    private static void applyItemDataMap(CarEntity car, ItemStack stack) {
        String dataMap = com.portofino.realtrainmodunofficial.compat.LegacyItemStackBridge.getSelectedDataMap(stack);
        if (dataMap == null || dataMap.isBlank()) {
            return;
        }
        for (String entry : dataMap.split(",")) {
            int eq = entry.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = entry.substring(0, eq).trim();
            String value = entry.substring(eq + 1).trim();
            //"(type)" の型注記を落とす
            if (value.startsWith("(")) {
                int close = value.indexOf(')');
                if (close >= 0) {
                    value = value.substring(close + 1);
                }
            }
            if (!key.isEmpty()) {
                car.setScriptDataValue(key, value);
            }
        }
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
