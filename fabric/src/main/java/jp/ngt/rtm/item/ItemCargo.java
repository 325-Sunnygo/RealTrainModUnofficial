package jp.ngt.rtm.item;

import com.portofino.realtrainmodunofficial.ClientHooks;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialComponents;
import com.portofino.realtrainmodunofficial.cargo.CargoDefinition;
import jp.ngt.rtm.entity.train.parts.EntityArtillery;
import jp.ngt.rtm.entity.train.parts.EntityCargo;
import jp.ngt.rtm.entity.train.parts.EntityContainer;
import jp.ngt.rtm.entity.train.parts.EntityTie;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 貨物アイテム。本家 {@code jp.ngt.rtm.item.ItemCargo} の移植。
 *
 * <p>本家はメタ 0=コンテナ / 1=火砲 / 2=貨物用枕木。1.21 にメタが無いので
 * {@link RealTrainModUnofficialComponents#ITEM_VARIANT} に本家と同じ値を持たせている。
 * 選んだモデルは {@code CARGO_DATA} の {@code ModelId}。
 */
public class ItemCargo extends Item {

    public ItemCargo() {
        super(new Properties().stacksTo(16));
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

    public static String getModelId(ItemStack stack) {
        CompoundTag nbt = stack.get(RealTrainModUnofficialComponents.CARGO_DATA.get());
        return nbt == null ? "" : nbt.getString("ModelId");
    }

    public static void setModelId(ItemStack stack, String id) {
        CompoundTag nbt = stack.getOrDefault(
            RealTrainModUnofficialComponents.CARGO_DATA.get(), new CompoundTag()).copy();
        nbt.putString("ModelId", id == null ? "" : id);
        stack.set(RealTrainModUnofficialComponents.CARGO_DATA.get(), nbt);
    }

    /** 本家: 枕木 (メタ 2) 以外は右クリックでモデル選択画面。 */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (getVariant(stack) != 2 && level.isClientSide()) {
            ClientHooks.openCargoItemModelScreen(hand == InteractionHand.OFF_HAND);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            ItemStack stack = context.getItemInHand();
            BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
            ItemStack single = stack.copy();
            single.setCount(1);

            EntityCargo cargo = createCargoEntity(level, getVariant(stack));
            cargo.initPlaced(single);
            //本家: プレイヤーの向きを 15 度刻みに丸める
            float interval = 15.0F;
            int yaw = Mth.floor(Mth.wrapDegrees(-player.getYRot() + 180.0F + (interval / 2.0F)) / interval);
            cargo.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, yaw * interval, 0.0F);
            if (cargo instanceof jp.ngt.rtm.entity.train.parts.EntityCargoWithModel withModel) {
                withModel.setModelId(getModelId(stack));
            }
            cargo.readCargoFromItem();
            level.addFreshEntity(cargo);
            stack.shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    /** 本家 createCargoEntity。 */
    public static EntityCargo createCargoEntity(Level level, int variant) {
        return switch (variant) {
            case 1 -> new EntityArtillery(level);
            case 2 -> new EntityTie(level);
            default -> new EntityContainer(level);
        };
    }

    public static CargoDefinition.Kind kindOf(int variant) {
        return variant == 1 ? CargoDefinition.Kind.FIREARM : CargoDefinition.Kind.CONTAINER;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item." + RealTrainModUnofficial.MODID + ".item_cargo." + getVariant(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (getVariant(stack) == 2) {
            return;
        }
        String id = getModelId(stack);
        tooltip.add(Component.literal("Model:" + (id.isBlank() ? "-" : id)).withStyle(ChatFormatting.GRAY));
    }
}
