package jp.ngt.rtm.item;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialSounds;
import jp.ngt.rtm.entity.EntityBullet;
import jp.ngt.rtm.item.ItemAmmunition.BulletType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 銃。本家 {@code jp.ngt.rtm.item.ItemGun} の移植。
 *
 * <p>残弾は<b>耐久値</b> (damage 0 = 満タン / damage == max = 空)。
 * 空の状態で右クリックすると、インベントリの弾倉を 1 つ消費して装填し、空の弾倉を落とす。
 */
public class ItemGun extends Item {
    /** 本家 {@code ItemGun.INTERVAL}。 */
    public static final int INTERVAL = 2;
    /** 本家 config「Sound / sound gun」の既定 100 (= 1.0)。 */
    public static final float GUN_SOUND_VOLUME = 1.0F;

    public final GunType gunType;

    public ItemGun(GunType par1) {
        super(new Properties().stacksTo(1).durability(par1.maxSize));
        this.gunType = par1;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        if (itemstack.getDamageValue() < itemstack.getMaxDamage()) {
            player.startUsingItem(hand);
        } else {
            if (!player.getAbilities().instabuild && !level.isClientSide()) {
                int i0 = this.setMagazine(player);
                if (i0 < itemstack.getMaxDamage()) {
                    itemstack.setDamageValue(i0);
                    player.drop(new ItemStack(this.getMagazineFromGunType(), 1), false, false);
                }
            }
        }
        return InteractionResultHolder.success(itemstack);
    }

    /**
     * 本家 {@code setMagazine}: インベントリから残弾のある弾倉を 1 つ取り出し、その残弾を返す。
     * 見つからなければ 1024 (= 装填しない)。
     */
    private int setMagazine(Player player) {
        Item magazineItem = this.getMagazineFromGunType();
        for (int i = 0; i < player.getInventory().items.size(); ++i) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == magazineItem) {
                if (stack.getDamageValue() < stack.getMaxDamage()) {
                    player.getInventory().setItem(i, ItemStack.EMPTY);
                    return stack.getDamageValue();
                }
            }
        }
        return 1024;
    }

    @Override
    public void onUseTick(Level level, LivingEntity living, ItemStack stack, int remainingUseDuration) {
        if (!(living instanceof Player player)) {
            return;
        }
        if (stack.getDamageValue() == stack.getMaxDamage()) {
            return;
        }
        if (this.onUsingGun(stack, level, player, remainingUseDuration)) {
            if (!player.getAbilities().instabuild && stack.isDamageableItem()) {
                stack.setDamageValue(stack.getDamageValue() + 1);
            }
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return this.gunType.useDuration;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public int getEnchantmentValue() {
        return 1;
    }

    /**
     * 本家 {@code onUsingGun}: アイテムを使っている間 tick ごとに呼ばれる。
     * 連射でない銃は<b>最初の tick だけ</b>撃つ (count が useDuration と等しいとき)。
     */
    protected boolean onUsingGun(ItemStack itemstack, Level level, Player player, int count) {
        if (count % INTERVAL > 0) {
            return false;
        }
        if (!this.gunType.rapidFire && count < this.gunType.useDuration) {
            return false;
        }

        if (!level.isClientSide()) {
            if (this.gunType == GunType.razer_gun) {
                if (hasPermission(player)) {
                    new RazerBullet(player).process(level);
                    return true;
                }
            } else {
                if (hasPermission(player)) {
                    EntityBullet bullet = new EntityBullet(level, player, this.gunType.speed, this.gunType.bulletType);
                    level.addFreshEntity(bullet);
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        RealTrainModUnofficialSounds.GUN.get(), SoundSource.PLAYERS, GUN_SOUND_VOLUME, 1.0F);

                    if (!player.getAbilities().instabuild) {
                        //薬莢ドロップ (本家と同じで メタ = 弾種 * 4 + 2)
                        int variant = this.gunType.bulletType.id * 4 + 2;
                        player.drop(ItemAmmunition.create(RealTrainModUnofficialItems.BULLET_ITEM.get(), variant),
                            false, false);
                    }
                }
            }
        }

        return false;
    }

    /**
     * 本家 {@code PermissionManager.hasPermission(player, RTMCore.USE_GUN)} 相当。
     * 本家は<b>シングルなら無条件で許可</b>し、マルチでは OP か権限付与済みの人だけ。
     * RTMU に権限リストの仕組みが無いので、マルチでは OP (権限レベル 2) を条件にしている。
     */
    private static boolean hasPermission(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        var server = serverPlayer.getServer();
        if (server == null || !server.isDedicatedServer()) {
            return true;
        }
        return serverPlayer.hasPermissions(2);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int max = stack.getMaxDamage();
        tooltip.add(Component.literal("Bullet:" + (max - stack.getDamageValue()) + "/" + max)
            .withStyle(ChatFormatting.GRAY));
    }

    public Item getMagazineFromGunType() {
        return switch (this.gunType) {
            case handgun -> RealTrainModUnofficialItems.MAGAZINE_HANDGUN_ITEM.get();
            case rifle -> RealTrainModUnofficialItems.MAGAZINE_RIFLE_ITEM.get();
            case autoloading_rifle -> RealTrainModUnofficialItems.MAGAZINE_ALR_ITEM.get();
            case sniper_rifle -> RealTrainModUnofficialItems.MAGAZINE_SR_ITEM.get();
            case smg -> RealTrainModUnofficialItems.MAGAZINE_SMG_ITEM.get();
            case amr -> RealTrainModUnofficialItems.MAGAZINE_AMR_ITEM.get();
            //本家はレーザー銃だけ「弾倉 = レーザー銃そのもの」を返す (装填できない)
            case razer_gun -> RealTrainModUnofficialItems.RAZER_GUN_ITEM.get();
        };
    }

    /** 本家 {@code ItemGun.GunType}。値は一切変えていない。 */
    public enum GunType {
        handgun(BulletType.handgun_9mm, 10, 16, 4.5F, false),
        rifle(BulletType.rifle_7_62mm, 5, 16, 7.5F, false),
        autoloading_rifle(BulletType.rifle_7_62mm, 30, 6, 7.5F, true),
        sniper_rifle(BulletType.rifle_7_62mm, 10, 20, 7.5F, false),
        smg(BulletType.handgun_9mm, 30, 6, 4.5F, true),
        amr(BulletType.rifle_12_7mm, 10, 24, 9.0F, false),
        razer_gun(BulletType.rifle_12_7mm, 10, 60, 150.0F, false);

        public final BulletType bulletType;
        public final int maxSize;
        public final int useDuration;
        public final float speed;
        public final boolean rapidFire;

        GunType(BulletType par1, int par2, int par3, float par4, boolean par5) {
            this.bulletType = par1;
            this.maxSize = par2;
            this.useDuration = par3;
            this.speed = par4;
            this.rapidFire = par5;
        }
    }
}
