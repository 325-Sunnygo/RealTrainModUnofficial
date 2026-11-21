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
 * 本家 jp.kaiz.atsassistmod.item.DataMapEditor の移植。
 * 列車に乗って右クリック → その列車の DataMap を一覧表示する (本家も閲覧のみ)。
 */
public class DataMapEditorItem extends Item {

    public DataMapEditorItem() {
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
            jp.kaiz.atsassistmod.client.AtsaClientHelper.openDataMapEditor();
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }
}
