package jp.ngt.rtm.item;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialComponents;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems;
import jp.ngt.rtm.entity.fluid.EntityFluid;
import jp.ngt.rtm.entity.fluid.FluidType;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;

/**
 * 液体入りバケツ。本家 {@code jp.ngt.rtm.item.ItemBucketLiquid} の移植。
 *
 * <p>残量は<b>耐久値</b>で持ち、種別と温度は NBT。1.21 では NBT が無いので
 * {@code CompoundTag} のコンポーネントに同じキー ({@code type} / {@code temperture}) で入れている。
 */
public class ItemBucketLiquid extends Item {
    /** 本家がクリエイティブに出す 2 種。 */
    public static final FluidType[] FLUID_LIST = {FluidType.PIG_IRON, FluidType.STEEL};
    public static final int MAX_COUNT = 16;

    public ItemBucketLiquid() {
        super(new Properties().stacksTo(1).durability(MAX_COUNT - 1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        FluidType type = getFluidType(itemStack);
        if (type == null) {
            return InteractionResultHolder.pass(itemStack);
        }
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(itemStack);
        }
        int amount = MAX_COUNT - itemStack.getDamageValue();
        int x = hit.getBlockPos().getX() + hit.getDirection().getStepX();
        int y = hit.getBlockPos().getY() + hit.getDirection().getStepY();
        int z = hit.getBlockPos().getZ() + hit.getDirection().getStepZ();
        if (!level.isClientSide()) {
            setFluid(level, x, y, z, type, amount, getTemperture(itemStack));
            if (!player.getAbilities().instabuild) {
                player.setItemInHand(hand, new ItemStack(Items.BUCKET));
            }
        }
        return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide());
    }

    /** ブロックを狙って右クリックした場合も同じ動きにする。 */
    @Override
    public net.minecraft.world.InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        return this.use(context.getLevel(), player, context.getHand()).getResult();
    }

    public static boolean setFluid(Level level, int x, int y, int z, FluidType type, int amount, float temperature) {
        return setFluid(level, x + 0.5D, y + 0.5D, z + 0.5D, type, amount, temperature);
    }

    public static boolean setFluid(Level level, double x, double y, double z, FluidType type, int amount, float temperature) {
        if (!level.isClientSide()) {
            double fluc = 2.0F * (0.5D - EntityFluid.R);   //ブロックにめり込まない範囲で
            for (int i = 0; i < amount; ++i) {
                double d0 = (double) (i + 1) / (double) amount;
                EntityFluid fluid = new EntityFluid(level);
                fluid.setPos(
                    x + (level.random.nextDouble() - 0.5D) * fluc * d0,
                    y,
                    z + (level.random.nextDouble() - 0.5D) * fluc * d0);
                fluid.setTemperture(temperature);
                fluid.setFluidType(type);
                level.addFreshEntity(fluid);
            }
        }
        return true;
    }

    public static boolean pickupFluid(Player player, EntityFluid fluid) {
        int count = 0;
        ItemStack held = player.getMainHandItem();
        if (held.is(RealTrainModUnofficialItems.BUCKET_LIQUID_ITEM.get())) {
            count = MAX_COUNT - held.getDamageValue();
            if (count >= MAX_COUNT) {
                return false;   //バケツが満杯
            }
            if (getFluidType(held) != fluid.getFluidType()) {
                return false;   //バケツと液体タイプが異なる
            }
        }
        int oldCount = count;

        if (!player.level().isClientSide()) {
            List<EntityFluid> list = player.level().getEntitiesOfClass(EntityFluid.class,
                fluid.getBoundingBox().inflate(EntityFluid.METABALL_RANGE));
            for (EntityFluid fluid2 : list) {
                if (fluid2.getFluidType() == fluid.getFluidType() && count < MAX_COUNT) {
                    fluid2.discard();
                    ++count;
                }
            }

            if (!player.isCreative() && count > oldCount) {
                player.setItemInHand(InteractionHand.MAIN_HAND,
                    getItem(fluid.getFluidType(), count - 1, fluid.getTemperature()));
            }
        }
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        FluidType type = getFluidType(stack);
        if (type != null) {
            tooltip.add(Component.literal(type.toString()).withStyle(ChatFormatting.GRAY));
            int amount = MAX_COUNT - stack.getDamageValue();
            tooltip.add(Component.literal(String.format("%d/16", amount)).withStyle(ChatFormatting.GRAY));
        }
    }

    public static ItemStack getItem(FluidType type, int amount, float temperature) {
        ItemStack itemstack = new ItemStack(RealTrainModUnofficialItems.BUCKET_LIQUID_ITEM.get());
        itemstack.setDamageValue(MAX_COUNT - amount - 1);
        CompoundTag nbt = new CompoundTag();
        nbt.putString("type", type.toString());
        nbt.putFloat("temperture", temperature);
        itemstack.set(RealTrainModUnofficialComponents.FLUID_DATA.get(), nbt);
        return itemstack;
    }

    public static FluidType getFluidType(ItemStack stack) {
        CompoundTag nbt = stack.get(RealTrainModUnofficialComponents.FLUID_DATA.get());
        if (nbt != null) {
            String type = nbt.getString("type");
            if (!type.isEmpty()) {
                try {
                    return FluidType.valueOf(type);
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
        }
        return null;
    }

    public static float getTemperture(ItemStack stack) {
        CompoundTag nbt = stack.get(RealTrainModUnofficialComponents.FLUID_DATA.get());
        return nbt == null ? 0.0F : nbt.getFloat("temperture");
    }
}
