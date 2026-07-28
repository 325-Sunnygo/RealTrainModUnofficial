package com.portofino.realtrainmodunofficial.item;

import com.portofino.realtrainmodunofficial.ClientHooks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 本家 jp.ngt.rtm.item.ItemCamera の移植 + 撮り鉄向けの作り直し。
 *
 * <p>本家は右クリックで GuiCamera を開き、そこからファインダーモードに入る作りだった。
 * RTMU では GUI を挟まず、右クリックでそのままファインダーに入る (すぐ構えられるように)。
 *
 * <p>中身はすべてクライアント側 ({@code client.camera} パッケージ)。サーバーは何も知らない。
 */
public class CameraItem extends Item {

    public CameraItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            ClientHooks.toggleCamera();
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
