package com.portofino.realtrainmodunofficial.item;

import com.portofino.realtrainmodunofficial.ClientHooks;
import com.portofino.realtrainmodunofficial.client.camera.Teleconverter;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * テレコンバーター (エクステンダー) アイテム。レンズの焦点距離を伸ばす撮り鉄アクセサリ。
 * 所持していると、カメラ構え中に ; キーで着脱できる。焦点距離が倍率ぶん伸びる代わりに
 * 開放 F 値が暗くなる。
 */
public class TeleconverterItem extends Item {

    private final Teleconverter teleconverter;

    public TeleconverterItem(Teleconverter teleconverter) {
        super(new Properties().stacksTo(1));
        this.teleconverter = teleconverter;
    }

    public Teleconverter getTeleconverter() {
        return teleconverter;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        //右クリックでこのテレコンをカメラに装着する (完全クライアント側)。
        if (level.isClientSide) {
            ClientHooks.attachTeleconverter(teleconverter.id);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("焦点距離 " + fmt(teleconverter.factor) + "倍 / 開放 -"
            + teleconverter.stopLoss + "段").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("右クリックで装着 (レンズを付け直すと外れる)").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static String fmt(float v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(v);
    }
}
