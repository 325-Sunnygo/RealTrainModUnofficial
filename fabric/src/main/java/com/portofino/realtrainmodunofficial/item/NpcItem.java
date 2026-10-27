package com.portofino.realtrainmodunofficial.item;

import com.portofino.realtrainmodunofficial.ClientHooks;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialComponents;
import jp.ngt.rtm.entity.npc.EntityNPC;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * NPC アイテム。本家 {@code ItemNPC} のメタ 1 (運転士はメタ 0 で別アイテム)。
 * 右クリックでモデルを選び、地面へ置くとその姿の NPC が立つ。
 */
public class NpcItem extends Item {

    public NpcItem() {
        super(new Properties().stacksTo(1));
    }

    public static String getModelId(ItemStack stack) {
        CompoundTag nbt = stack.get(RealTrainModUnofficialComponents.CARGO_DATA.get());
        return nbt == null ? "" : nbt.getString("NpcModelId");
    }

    public static void setModelId(ItemStack stack, String id) {
        CompoundTag nbt = stack.getOrDefault(
            RealTrainModUnofficialComponents.CARGO_DATA.get(), new CompoundTag()).copy();
        nbt.putString("NpcModelId", id == null ? "" : id);
        stack.set(RealTrainModUnofficialComponents.CARGO_DATA.get(), nbt);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            ClientHooks.openNpcItemModelScreen(hand == InteractionHand.OFF_HAND);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            var pos = context.getClickedPos();
            //本家: プレイヤーの向きを 15 度刻みに丸める
            float interval = 15.0F;
            int yaw = Mth.floor(Mth.wrapDegrees(-player.getYRot() + 180.0F + (interval / 2.0F)) / interval);
            EntityNPC npc = new EntityNPC(level);
            npc.moveTo(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, yaw * interval, 0.0F);
            npc.setYHeadRot(yaw * interval);
            npc.setModelId(getModelId(context.getItemInHand()));
            //本家: 置いた人が飼い主 (owner)。飼い主の一撃で壊せる / 警備が守る相手
            npc.setOwnerUUID(player.getUUID());
            npc.setTame(true, false);
            level.addFreshEntity(npc);
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String id = getModelId(stack);
        tooltip.add(Component.literal("Model:" + (id.isBlank() ? "-" : id)).withStyle(ChatFormatting.GRAY));
    }
}
