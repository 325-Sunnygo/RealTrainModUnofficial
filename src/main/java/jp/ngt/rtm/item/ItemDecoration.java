package jp.ngt.rtm.item;

import com.portofino.realtrainmodunofficial.ClientHooks;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlocks;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialComponents;
import jp.ngt.rtm.block.decoration.DecorationModel;
import jp.ngt.rtm.block.tileentity.TileEntityDecoration;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
 * 装飾ブロックのアイテム。本家 {@code jp.ngt.rtm.item.ItemDecoration} の移植。
 *
 * <p>空右クリックでモデル編集画面 (本家 guiIdDecoration)、
 * ブロックへの右クリックでクリック面の隣に装飾ブロックを設置する。
 */
public class ItemDecoration extends Item {

    public ItemDecoration() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            ClientHooks.openDecorationEditScreen();
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!level.isClientSide()) {
            BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
            level.setBlock(pos, RealTrainModUnofficialBlocks.DECORATION.get().defaultBlockState(), 3);
            if (level.getBlockEntity(pos) instanceof TileEntityDecoration tile) {
                tile.setModelName(getModelName(context.getItemInHand()));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Model:" + getModelName(stack)).withStyle(ChatFormatting.GRAY));
    }

    /** 本家はモデル json builtin/entity + RenderDecoration.renderItem。NeoForge 21.1 の作法。 */
    @Override
    public void initializeClient(java.util.function.Consumer<
            net.neoforged.neoforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            private com.portofino.realtrainmodunofficial.client.renderer.DecorationItemRenderer renderer;

            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new com.portofino.realtrainmodunofficial.client.renderer.DecorationItemRenderer();
                }
                return renderer;
            }
        });
    }

    public static void setModel(ItemStack stack, String modelName) {
        stack.set(RealTrainModUnofficialComponents.DECORATION_MODEL.get(),
            modelName == null ? DecorationModel.DEFAULT_MODEL.name : modelName);
    }

    public static String getModelName(ItemStack stack) {
        String name = stack.get(RealTrainModUnofficialComponents.DECORATION_MODEL.get());
        return name == null || name.isEmpty() ? DecorationModel.DEFAULT_MODEL.name : name;
    }
}
