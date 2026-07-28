package jp.kaiz.atsassistmod.item;

import jp.ngt.rtm.entity.train.EntityTrainBase;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 本家 jp.kaiz.atsassistmod.item.TrainProtectionSelector の移植。
 * 列車に乗って右クリック → 運転切替/保安装置切替の GUI を開く。
 */
public class TrainProtectionSelectorItem extends Item {

    public TrainProtectionSelectorItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!(player.getVehicle() instanceof EntityTrainBase)) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.literal("列車に乗ってから使用してください"), true);
            }
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        }
        if (level.isClientSide()) {
            jp.kaiz.atsassistmod.client.AtsaClientHelper.openTrainProtectionSelector();
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }
}
