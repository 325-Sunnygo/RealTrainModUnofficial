package jp.ngt.rtm.item;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 紙幣・硬貨。本家 {@code new ItemMultiIcon(RTMItem.MoneyType.values())} の移植。
 *
 * <p>本家はメタ 0〜8 で ￥1〜￥10000 を出す。1.21 にメタが無いので
 * {@link RealTrainModUnofficialComponents#ITEM_VARIANT} に本家と同じ id を持たせている。
 */
public class ItemMoney extends Item {

    /** 本家のメタ (MoneyType.id)。 */
    public final int variant;

    public ItemMoney(int variant) {
        super(new Properties());
        this.variant = variant;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item." + RealTrainModUnofficial.MODID + ".money." + this.variant);
    }

    /** 本家 {@code RTMItem.MoneyType}。 */
    public enum MoneyType {
        Y1(0, 1),
        Y5(1, 5),
        Y10(2, 10),
        Y50(3, 50),
        Y100(4, 100),
        Y500(5, 500),
        Y1000(6, 1000),
        Y5000(7, 5000),
        Y10000(8, 10000);

        public final byte id;
        public final short price;

        MoneyType(int par1, int par2) {
            this.id = (byte) par1;
            this.price = (short) par2;
        }

        public static int getPrice(int id) {
            for (MoneyType type : values()) {
                if (type.id == id) {
                    return type.price;
                }
            }
            return 0;
        }
    }
}
