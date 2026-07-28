package jp.masa.signalcontrollermod;

import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * SignalControllerMod (作者: masa300, https://github.com/masa300/SignalControllerMod)
 * の ItemPosSettingTool 1.21.1 移植。
 * 使い方 (原作同様): 信号機を右クリック → 位置を記録、
 * SignalController を右クリック → 記録した位置を NextSignal / DisplayPos として追加。
 * 原作はメタ 0/1 の 2 種、1.21 ではアイテム 2 個 (mode 0=NextSignal, 1=DisplayPos)。
 */
public class ItemPosSettingTool extends Item {
    private final int mode;

    public ItemPosSettingTool(int mode) {
        super(new Properties().stacksTo(1));
        this.mode = mode;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (world.isClientSide || player == null) {
            return InteractionResult.SUCCESS;
        }
        BlockPos clicked = context.getClickedPos();
        BlockEntity tileEntity = world.getBlockEntity(clicked);
        if (tileEntity instanceof InstalledObjectBlockEntity be && be.getCategory() == InstalledObjectCategory.SIGNAL) {
            CompoundTag tag = getTag(stack);
            tag.putIntArray("pos", new int[]{clicked.getX(), clicked.getY(), clicked.getZ()});
            setTag(stack, tag);
            player.displayClientMessage(Component.literal(
                    String.format("Position saved!(%s, %s ,%s)", clicked.getX(), clicked.getY(), clicked.getZ())), false);
        } else if (tileEntity instanceof TileEntitySignalController controller) {
            CompoundTag tag = getTag(stack);
            if (tag.contains("pos")) {
                int[] pos = tag.getIntArray("pos");
                if (this.mode == 0) {
                    boolean added = controller.addNextSignal(new BlockPos(pos[0], pos[1], pos[2]));
                    if (added) {
                        player.displayClientMessage(Component.literal(
                                String.format("NextSignal added (%s, %s ,%s)!", pos[0], pos[1], pos[2])), false);
                    } else {
                        player.displayClientMessage(Component.literal("NextSignal already added"), false);
                    }
                } else {
                    boolean added = controller.addDisplayPos(new BlockPos(pos[0], pos[1], pos[2]));
                    if (added) {
                        player.displayClientMessage(Component.literal(
                                String.format("DisplayPos added (%s, %s ,%s)!", pos[0], pos[1], pos[2])), false);
                    } else {
                        player.displayClientMessage(Component.literal("DisplayPos already added"), false);
                    }
                }
                controller.syncToClient();
            }
        }
        return InteractionResult.SUCCESS;
    }

    private static CompoundTag getTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null ? data.copyTag() : new CompoundTag();
    }

    private static void setTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
