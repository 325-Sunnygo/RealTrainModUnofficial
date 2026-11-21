package jp.ngt.rtm.item;

import jp.ngt.rtm.entity.fluid.FluidType;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** コークス。本家 jp.ngt.rtm.item.ItemCoke の移植。 */
public class ItemCoke extends Item {
    public ItemCoke() {
        super(new Properties());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!context.getLevel().isClientSide()) {
            var loc = context.getClickLocation();
            ItemBucketLiquid.setFluid(context.getLevel(), loc.x, loc.y, loc.z, FluidType.COKE, 1, 0.0F);
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }
}
