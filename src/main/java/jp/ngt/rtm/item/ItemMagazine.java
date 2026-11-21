package jp.ngt.rtm.item;

import jp.ngt.rtm.item.ItemGun.GunType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** 弾倉。本家 jp.ngt.rtm.item.ItemMagazine の移植。 */
public class ItemMagazine extends Item {
    public final GunType magazineType;

    public ItemMagazine(GunType par1) {
        super(new Properties().stacksTo(1).durability(par1.maxSize));
        this.magazineType = par1;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int max = stack.getMaxDamage();
        tooltip.add(Component.literal("Bullet:" + (max - stack.getDamageValue()) + "/" + max)
            .withStyle(ChatFormatting.GRAY));
    }
}
