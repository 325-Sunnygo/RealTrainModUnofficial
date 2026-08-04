package com.portofino.rtmuautodrive;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 自動運転装置。持って右クリックすると、置いてある列車運転スポナーの一覧が出る。
 * 名前の横の「発車」を押すと、その編成がスポーンして駅まで自動運転で走る。
 */
public class AutoDriverItem extends Item {

    public AutoDriverItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            //一覧はサーバーが持っている。頼んで、返ってきた所で画面を開く
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new AutoDriveNetwork.RequestList());
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<net.minecraft.network.chat.Component> lines, TooltipFlag flag) {
        lines.add(net.minecraft.network.chat.Component.translatable("item.rtmuautodrive.auto_driver.desc")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
