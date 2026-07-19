package com.portofino.realtrainmodunofficial.item;

import com.portofino.realtrainmodunofficial.ClientHooks;
import com.portofino.realtrainmodunofficial.client.camera.CameraLens;
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
 * 撮り鉄カメラ用の交換レンズアイテム。所持していると、カメラ構え中に L キーで順に装着できる。
 * レンズごとに焦点距離の範囲と開放 F 値が違い、望遠・大口径ほど背景が大きく圧縮/ボケる。
 *
 * <p>「装着中のレンズ」はカメラ側 (クライアント設定) が覚えるので、このアイテム自体は
 * 状態を持たない (どのレンズかを表す種類でしかない)。
 */
public class LensItem extends Item {

    private final CameraLens lens;

    public LensItem(CameraLens lens) {
        super(new Properties().stacksTo(1));
        this.lens = lens;
    }

    public CameraLens getLens() {
        return lens;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        //右クリックでこのレンズをカメラに装着する (完全クライアント側)。
        if (level.isClientSide) {
            ClientHooks.mountCameraLens(lens.id);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal(lens.shortLabel()).withStyle(ChatFormatting.AQUA));
        if (lens.prime) {
            tooltip.add(Component.literal("単焦点 — ズーム不可・写りは最上級").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal("ズーム — Z/X または ホイールで画角を変える").withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.literal("右クリックで装着").withStyle(ChatFormatting.DARK_GRAY));
    }
}
