package jp.ngt.rtm.item;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 弾薬。本家 {@code jp.ngt.rtm.item.ItemAmmunition} の移植。
 *
 * <p>本家は 1 アイテム + メタで「弾薬 / 弾 / 薬莢」×弾種を出す。
 * 1.21 にメタが無いので {@link RealTrainModUnofficialComponents#ITEM_VARIANT} に
 * <b>本家と同じ値</b> ({@code BulletType.id * 4 + 0..2}) を持たせている。
 */
public class ItemAmmunition extends Item {

    public ItemAmmunition() {
        super(new Properties());
    }

    /** 本家 {@code getSubItems}: 5.56mm は飛ばし、弾種ごとに 弾薬/弾/薬莢 の 3 つを出す。 */
    public static void forEachSubItem(java.util.function.Consumer<ItemStack> consumer, Item item) {
        for (BulletType type : BulletType.values()) {
            if (type == BulletType.rifle_5_56mm) {
                continue;
            }
            int i0 = type.id * 4;
            consumer.accept(create(item, i0));      //弾薬
            consumer.accept(create(item, i0 + 1));  //弾
            consumer.accept(create(item, i0 + 2));  //薬莢
        }
    }

    public static ItemStack create(Item item, int variant) {
        ItemStack stack = new ItemStack(item);
        stack.set(RealTrainModUnofficialComponents.ITEM_VARIANT.get(), variant);
        return stack;
    }

    public static int getVariant(ItemStack stack) {
        Integer value = stack.get(RealTrainModUnofficialComponents.ITEM_VARIANT.get());
        return value == null ? 0 : value;
    }

    /**
     * 本家 {@code getItemStackDisplayName}: 「弾種名」+「(弾薬)/(弾)/(薬莢)」。
     */
    @Override
    public Component getName(ItemStack stack) {
        int variant = getVariant(stack);
        int sub = variant % 4;
        String suffix = sub == 0 ? "ammo" : (sub == 1 ? "bullet" : "case");
        return Component.translatable("item." + RealTrainModUnofficial.MODID + ".bullet." + (variant / 4))
            .append(Component.translatable("item." + RealTrainModUnofficial.MODID + "." + suffix));
    }

    /** 本家 {@code ItemAmmunition.BulletIconType}。 */
    public enum BulletIconType {
        CANNON,
        HANDGUN,
        RIFLE,
        ROCKET
    }

    /** 本家 {@code ItemAmmunition.BulletType}。値は一切変えていない。 */
    public enum BulletType {
        cannon_40cm(0, 40.0F, false, BulletIconType.CANNON),
        handgun_9mm(1, 8.0F, true, BulletIconType.HANDGUN),
        rifle_5_56mm(2, 12.0F, true, BulletIconType.RIFLE),
        rifle_7_62mm(3, 12.0F, true, BulletIconType.RIFLE),
        rifle_12_7mm(4, 24.0F, true, BulletIconType.RIFLE),
        cannon_Atomic(5, 100.0F, false, BulletIconType.CANNON),
        rocket(6, 50.0F, false, BulletIconType.ROCKET);

        public final byte id;
        public final float damage;
        public final boolean muzzleFlash;
        public final BulletIconType icon;

        BulletType(int par1, float par2, boolean par3, BulletIconType par4) {
            this.id = (byte) par1;
            this.damage = par2;
            this.muzzleFlash = par3;
            this.icon = par4;
        }

        public static BulletType getBulletType(int id) {
            for (BulletType type : values()) {
                if (type.id == id) {
                    return type;
                }
            }
            return handgun_9mm;
        }

        public static BulletType getBulletType(String name) {
            return BulletType.valueOf(name);
        }
    }
}
