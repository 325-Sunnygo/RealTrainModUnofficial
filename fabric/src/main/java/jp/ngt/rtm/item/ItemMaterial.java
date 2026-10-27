package jp.ngt.rtm.item;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 部品。本家 {@code new ItemMultiIcon(RTMItem.MaterialType.values())} の移植。
 *
 * <p>本家はメタ 0/1/2/3/4/8 で 6 種を出す。1.21 にメタが無いので
 * {@link RealTrainModUnofficialComponents#ITEM_VARIANT} に<b>本家と同じ id</b> を持たせている。
 */
public class ItemMaterial extends Item {

    /** 本家のメタ。表示名と絵をこれで決める。 */
    public final int variant;

    public ItemMaterial(int variant) {
        super(new Properties());
        this.variant = variant;
    }

    public static ItemStack create(Item item, int id) {
        ItemStack stack = new ItemStack(item);
        stack.set(RealTrainModUnofficialComponents.ITEM_VARIANT.get(), id);
        return stack;
    }

    public static int getVariant(ItemStack stack) {
        Integer value = stack.get(RealTrainModUnofficialComponents.ITEM_VARIANT.get());
        return value == null ? 0 : value;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item." + RealTrainModUnofficial.MODID + ".material." + this.variant);
    }

    /** 本家 {@code RTMItem.MaterialType}。id は飛び番 (8) も含めてそのまま。 */
    public enum MaterialType {
        SHAFT(0),
        WHEEL(1),
        DE(2),
        MOTOR(3),
        POWDER(4),
        STEEL_SHEET(8);

        public final byte id;

        MaterialType(int par1) {
            this.id = (byte) par1;
        }
    }
}
