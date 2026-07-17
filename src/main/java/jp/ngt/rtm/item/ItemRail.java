package jp.ngt.rtm.item;

import jp.ngt.ngtlib.math.PooledVec3;
import jp.ngt.ngtlib.math.Vec3;
import jp.ngt.rtm.rail.BlockMarker;
import jp.ngt.rtm.rail.TileEntityLargeRailBase;
import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import jp.ngt.rtm.rail.util.RailPosition;
import jp.ngt.rtm.rail.util.RailProperty;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 本家 jp.ngt.rtm.item.ItemRail (KaizPatchX) の忠実移植。
 * - Property NBT (RailModel/BlockName/BlockMetadata/BlockHeight) を CustomData に保持 (本家キー互換)
 * - レールへの使用: 通常=addSubRail(重ね), スニーク=replaceRail(置換)
 * - ピックブロック相当 copyItemFromRail: RailPosition 群 + ShapeName を保持
 * - placeRail: コピーしたレールをプレイヤー向きに回転して再敷設 (レールコピー&ペースト)
 * TODO(Phase 4): ItemWithModel の GUI (モデル選択/設定 GUI) と getSubItems (クリエイティブタブ列挙) は
 * ModelPackManager 移植後に接続。
 */
public class ItemRail extends Item {

    public ItemRail(Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack itemStack = context.getItemInHand();

        Block block = world.getBlockState(pos).getBlock();
        if (block instanceof BlockMarker) {
            //マーカーには BlockMarker 側の処理を通す
            return InteractionResult.PASS;
        }
        if (!world.isClientSide && player != null) {
            BlockEntity tile = world.getBlockEntity(pos);
            if (tile instanceof TileEntityLargeRailBase) {
                TileEntityLargeRailCore core = ((TileEntityLargeRailBase) tile).getRailCore();
                if (core != null) {
                    RailProperty property = ItemRail.getProperty(itemStack);
                    if (property != null) {
                        //本家はシフトで差し替え / 素で重ねレールだったが、通常のレールアイテム
                        //(モデル選択式) を「右クリックでモデル差し替え」にしたので、こちらも
                        //操作を揃える。両方のレールアイテムで挙動が逆だと混乱するため。
                        if (player.isShiftKeyDown()) {
                            core.addSubRail(property);
                        } else {
                            core.replaceRail(property);
                        }
                    }
                }
            } else {
                this.placeRail(world, pos.getX(), pos.getY() + 1, pos.getZ(), itemStack, player);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack itemStack, TooltipContext context, List<Component> list, TooltipFlag flag) {
        RailProperty prop = getProperty(itemStack);
        if (prop == null) {
            return;
        }
        list.add(Component.literal("Model:" + prop.railModel).withStyle(ChatFormatting.GRAY));
        list.add(Component.literal("Height:" + prop.blockHeight).withStyle(ChatFormatting.GRAY));
        String shape = getShapeName(itemStack);
        if (!shape.isEmpty()) {
            list.add(Component.literal(shape).withStyle(ChatFormatting.GRAY));
        }
    }

    // ===== NBT (CustomData) ヘルパー: 本家キー互換 =====

    private static CompoundTag getTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null ? data.copyTag() : new CompoundTag();
    }

    private static void setTag(ItemStack stack, CompoundTag nbt) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
    }

    public static RailProperty getDefaultProperty() {
        return new RailProperty("1067mm_Wood", net.minecraft.world.level.block.Blocks.GRAVEL, 0, 0.0625F);
    }

    public static ItemStack getRailItem(RailProperty prop) {
        ItemStack itemStack = new ItemStack(RTMItems.ITEM_LARGE_RAIL.get(), 1);
        writePropToItem(prop, itemStack);
        return itemStack;
    }

    public static void writePropToItem(RailProperty prop, ItemStack itemStack) {
        CompoundTag nbtP = new CompoundTag();
        prop.writeToNBT(nbtP);
        CompoundTag nbt = getTag(itemStack);
        nbt.put("Property", nbtP);
        setTag(itemStack, nbt);
    }

    public static RailProperty getProperty(ItemStack stack) {
        CompoundTag nbt = getTag(stack);
        return nbt.contains("Property") ? RailProperty.readFromNBT(nbt.getCompound("Property")) : null;
    }

    /**
     * スクリプト互換: ItemStackCompat ラッパー (SRB3 が渡す) も受け、
     * Remaster RailItem (選択モデル方式) のスタックからも RailProperty を作る。
     */
    public static RailProperty getProperty(Object stackLike) {
        ItemStack stack = jp.ngt.mccompat.ItemStackCompat.unwrap(stackLike);
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        if (stack.getItem() instanceof ItemRail) {
            RailProperty prop = getProperty(stack);
            if (prop != null) {
                jp.ngt.rtm.rail.BlockMarker.setLastUsedProperty(prop);
                return prop;
            }
            return RailProperty.getDefaultProperty();
        }
        if (stack.getItem() instanceof com.portofino.realtrainmodunofficial.item.RailItem) {
            String model = com.portofino.realtrainmodunofficial.compat.LegacyItemStackBridge.getSelectedModelId(stack);
            if (model == null || model.isEmpty()) {
                com.portofino.realtrainmodunofficial.rail.RailDefinition def =
                        com.portofino.realtrainmodunofficial.rail.RailRegistry.getSelected();
                model = def != null ? def.getId() : "";
            }
            RailProperty prop = new RailProperty(model == null ? "" : model,
                    net.minecraft.world.level.block.Blocks.GRAVEL, 0, 0.0625F);
            jp.ngt.rtm.rail.BlockMarker.setLastUsedProperty(prop);
            return prop;
        }
        return null;
    }

    private static String getShapeName(ItemStack stack) {
        return getTag(stack).getString("ShapeName");
    }

    private static List<RailPosition> getRPFromItem(ItemStack stack) {
        List<RailPosition> list = new ArrayList<>();
        CompoundTag nbt = getTag(stack);
        byte size = nbt.getByte("Size");
        for (int i = 0; i < size; ++i) {
            list.add(RailPosition.readFromNBT(nbt.getCompound("RP" + i)));
        }
        return list;
    }

    private static void setRPToItem(ItemStack stack, RailPosition[] rps) {
        CompoundTag nbt = getTag(stack);
        //rps が null (レール変換中/破壊直後でコアがまだ RailPosition を持たない) の場合、
        //rps.length で NPE になり WAILA のツールチップ表示だけでゲームが落ちていた。
        //null/空なら Size=0 で書き込む (アイテムは形状情報無しでも成立する)。
        int size = rps == null ? 0 : rps.length;
        nbt.putByte("Size", (byte) size);
        for (int i = 0; i < size; ++i) {
            if (rps[i] != null) {
                nbt.put("RP" + i, rps[i].writeToNBT());
            }
        }
        setTag(stack, nbt);
    }

    public static ItemStack copyItemFromRail(TileEntityLargeRailCore core) {
        ItemStack stack = ItemRail.getRailItem(core.getProperty());
        RailPosition[] rps = core.getRailPositions();
        setRPToItem(stack, rps);
        String shape = core.getRailShapeName();
        CompoundTag nbt = getTag(stack);
        nbt.putString("ShapeName", shape);
        setTag(stack, nbt);
        return stack;
    }

    private boolean placeRail(Level world, int x, int y, int z, ItemStack stack, Player player) {
        List<RailPosition> rps = getRPFromItem(stack);
        if (!rps.isEmpty()) {
            //45刻みへ変換 (本家: BlockMarker.getMarkerDir(null, getFacing(player, false)))
            int playerFacing = Mth.floor(jp.ngt.ngtlib.math.NGTMath.normalizeAngle(player.getYRot() + 180.0D) / 45.0D + 0.5D) & 7;
            playerFacing = playerFacing / 2 + (playerFacing % 2 == 0 ? 0 : 4);
            int i0 = playerFacing & 3;
            int dir = ((6 - i0) & 3) * 2;
            if (playerFacing >= 4) {
                dir = (dir + 7) & 7;
            }
            RailPosition topRP = rps.get(0);//分岐RP前提、BlockMarkerで並べ替え
            int difDir = dir - topRP.direction;
            int origX = topRP.blockX;
            int origY = topRP.blockY;
            int origZ = topRP.blockZ;
            for (RailPosition rp : rps) {
                double dif2X = (rp.blockX + 0.5D) - (origX + 0.5D);
                double dif2Y = (rp.blockY + 0.5D) - (origY + 0.5D);
                double dif2Z = (rp.blockZ + 0.5D) - (origZ + 0.5D);
                Vec3 vec = PooledVec3.create(dif2X, dif2Y, dif2Z);
                vec = vec.rotateAroundY(difDir * 45.0F);
                rp.blockX = Mth.floor(x + 0.5D + vec.getX());//整数座標で計算するとずれる
                rp.blockY = Mth.floor(y + 0.5D + vec.getY());
                rp.blockZ = Mth.floor(z + 0.5D + vec.getZ());
                rp.direction = (byte) ((rp.direction + difDir + 8) & 7);
                rp.anchorYaw = Mth.wrapDegrees(rp.anchorYaw + difDir * 45.0F);
                rp.init();
            }
            RailProperty state = ItemRail.getProperty(stack);
            if (state == null) {
                state = getDefaultProperty();
            }
            boolean isCreative = player.getAbilities().instabuild;
            return BlockMarker.createRail(world, x, y, z, rps, state, true, isCreative);
        }
        return false;
    }
}
