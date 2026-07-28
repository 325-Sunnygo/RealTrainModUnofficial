package com.portofino.realtrainmodunofficial.item;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlocks;
import com.portofino.realtrainmodunofficial.blockentity.MiniatureBlockEntity;
import jp.ngt.mcte.item.ItemMiniature;
import jp.ngt.ngtlib.block.BlockSet;
import jp.ngt.ngtlib.block.NGTObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * ミニチュアアイテム (neo mcte)。本家 MCTE ItemMiniature 相当。
 * 操作:
 */
public class MiniatureItem extends Item {

    /** 取り込み範囲の上限 (負荷対策)。 */
    private static final int MAX_VOLUME = 33 * 33 * 33;

    public MiniatureItem() {
        super(new Properties().stacksTo(1));
    }

    /**
     * インベントリや手元で中身を描く (neo mcte)。
     * NeoForge 21.1 の作法。
     */
    // ★@Override を付けないこと: これは NeoForge が足したメソッドで、バニラには無い
    public void initializeClient(java.util.function.Consumer<
            net.neoforged.neoforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            private com.portofino.realtrainmodunofficial.client.renderer.MiniatureItemRenderer renderer;

            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new com.portofino.realtrainmodunofficial.client.renderer.MiniatureItemRenderer();
                }
                return renderer;
            }
        });
    }

    /**
     * 何もない所を右クリック: 設定画面を開く (本家 MCTE と同じ)。
     * Screen は client 専用なので必ず ClientHooks 経由。
     */
    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(
            Level level, Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            com.portofino.realtrainmodunofficial.ClientHooks.openMiniatureSettingsScreen(
                stack, hand == net.minecraft.world.InteractionHand.OFF_HAND);
        }
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        CompoundTag tag = getTag(stack);

        if (player.isShiftKeyDown()) {
            stack.remove(DataComponents.CUSTOM_DATA);
            player.displayClientMessage(Component.literal("ミニチュア: 中身をクリアしました"), true);
            return InteractionResult.SUCCESS;
        }

        if (ItemMiniature.hasNGTObject(tag)) {
            return place(context, level, player, stack, tag);
        }
        return capture(context, level, player, stack, tag);
    }

    // ---- 設置 ----

    private InteractionResult place(UseOnContext context, Level level, Player player,
                                    ItemStack stack, CompoundTag tag) {
        NGTObject object = ItemMiniature.getNGTObject(tag);
        if (object == null) {
            player.displayClientMessage(Component.literal("ミニチュア: 中身を読めませんでした"), true);
            return InteractionResult.FAIL;
        }
        Direction face = context.getClickedFace();
        BlockPos target = context.getClickedPos().relative(face);
        if (!level.getBlockState(target).canBeReplaced()) {
            return InteractionResult.FAIL;
        }
        if (!player.mayUseItemAt(target, face, stack)) {
            return InteractionResult.FAIL;
        }

        ItemMiniature.MiniatureMode mode = ItemMiniature.getMode(tag);
        if (mode == ItemMiniature.MiniatureMode.ORIGINAL) {
            int placed = expandOriginal(level, target, object);
            player.displayClientMessage(Component.literal(
                "ミニチュア: 実ブロックとして " + placed + " 個を展開しました"), true);
        } else {
            level.setBlock(target, RealTrainModUnofficialBlocks.MINIATURE.get().defaultBlockState(), 3);
            if (level.getBlockEntity(target) instanceof MiniatureBlockEntity be) {
                be.setMiniatureTag(tag);
                // 本家 TileEntityMiniature.setRotation 相当。プレイヤーの向きで正面を決める。
                be.setPlacement(snapYaw(player.getYRot()), (byte) face.get3DDataValue());
            }
        }
        level.playSound(null, target, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    /**
     * Mode=ORIGINAL: 中身を実ブロックとして原寸で展開する。
     * 本家 setOriginalBlocks 相当。ブロックエンティティの NBT も復元する。
     */
    private static int expandOriginal(Level level, BlockPos origin, NGTObject ngto) {
        int placed = 0;
        for (BlockSet set : ngto.blockList) {
            if (set == null || set.state == null || set.state.isAir()) {
                continue;
            }
            BlockPos p = origin.offset(set.x, set.y, set.z);
            level.setBlock(p, set.state, 3);
            if (set.nbt != null && level.getBlockEntity(p) != null) {
                try {
                    level.getBlockEntity(p).loadWithComponents(set.nbt, level.registryAccess());
                    level.getBlockEntity(p).setChanged();
                } catch (Exception ignored) {
                    // 中身の BE が現行バージョンで読めなくても、ブロック自体は置けているので続ける
                }
            }
            placed++;
        }
        return placed;
    }

    /** 15 度刻み。本家 MCTE.rotationInterval 相当の丸め。 */
    private static float snapYaw(float yaw) {
        float f = yaw % 360.0F;
        if (f < 0.0F) {
            f += 360.0F;
        }
        return Math.round(f / 15.0F) * 15.0F;
    }

    // ---- 取り込み ----

    private InteractionResult capture(UseOnContext context, Level level, Player player,
                                      ItemStack stack, CompoundTag tag) {
        BlockPos pos = context.getClickedPos();
        if (!tag.contains("SelStart")) {
            tag.putIntArray("SelStart", new int[]{pos.getX(), pos.getY(), pos.getZ()});
            setTag(stack, tag);
            player.displayClientMessage(Component.literal(
                "ミニチュア: 始点 " + pos.toShortString() + " (もう一方の角を右クリック)"), true);
            return InteractionResult.SUCCESS;
        }

        int[] s = tag.getIntArray("SelStart");
        BlockPos start = new BlockPos(s[0], s[1], s[2]);
        int minX = Math.min(start.getX(), pos.getX());
        int minY = Math.min(start.getY(), pos.getY());
        int minZ = Math.min(start.getZ(), pos.getZ());
        int maxX = Math.max(start.getX(), pos.getX());
        int maxY = Math.max(start.getY(), pos.getY());
        int maxZ = Math.max(start.getZ(), pos.getZ());
        int w = maxX - minX + 1;
        int h = maxY - minY + 1;
        int d = maxZ - minZ + 1;
        if ((long) w * h * d > MAX_VOLUME) {
            tag.remove("SelStart");
            setTag(stack, tag);
            player.displayClientMessage(Component.literal(
                "ミニチュア: 範囲が大きすぎます (" + w + "x" + h + "x" + d + ")"), true);
            return InteractionResult.FAIL;
        }

        List<BlockSet> blocks = new ArrayList<>();
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    BlockPos bp = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(bp);
                    if (state.isAir()) {
                        continue;
                    }
                    CompoundTag beTag = null;
                    var be = level.getBlockEntity(bp);
                    if (be != null) {
                        beTag = be.saveWithoutMetadata(level.registryAccess());
                    }
                    blocks.add(new BlockSet(x - minX, y - minY, z - minZ, state, beTag));
                }
            }
        }
        NGTObject obj = NGTObject.createNGTO(blocks, w, h, d, 0, 0, 0);
        // ★BlocksData として入れる (本家の契約)。スタック単位で完結する。
        ItemMiniature.setNGTObject(obj, tag);
        // 取り込んだ直後は 1 ブロックに収まる縮尺を既定にする
        ItemMiniature.setScale(1.0F / Math.max(1, Math.max(w, Math.max(h, d))), tag);
        ItemMiniature.setMode(tag, ItemMiniature.MiniatureMode.MINIATURE);
        tag.remove("SelStart");
        setTag(stack, tag);
        player.displayClientMessage(Component.literal(
            "ミニチュア: " + w + "x" + h + "x" + d + " (" + blocks.size() + " ブロック) を取り込みました"), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        CompoundTag tag = getTag(stack);
        if (ItemMiniature.hasNGTObject(tag)) {
            CompoundTag data = tag.contains(ItemMiniature.KEY_BLOCKS)
                ? tag.getCompound(ItemMiniature.KEY_BLOCKS) : tag;
            lines.add(Component.literal(String.format("%dx%dx%d",
                data.getInt("SizeX"), data.getInt("SizeY"), data.getInt("SizeZ")))
                .withStyle(ChatFormatting.GRAY));
            lines.add(Component.literal(String.format("縮尺 %.3f / %s",
                    ItemMiniature.getScale(tag), modeName(ItemMiniature.getMode(tag))))
                .withStyle(ChatFormatting.DARK_GRAY));
        } else if (tag.contains("SelStart")) {
            lines.add(Component.literal("始点選択済み — 終点を右クリック").withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.literal("ブロックを右クリックして範囲を選択").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static String modeName(ItemMiniature.MiniatureMode mode) {
        return switch (mode) {
            case SCULPTURE -> "彫刻";
            case ORIGINAL -> "実ブロック展開";
            default -> "模型";
        };
    }

    private static CompoundTag getTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null ? data.copyTag() : new CompoundTag();
    }

    private static void setTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
