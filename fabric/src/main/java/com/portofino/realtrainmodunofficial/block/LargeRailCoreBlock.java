package com.portofino.realtrainmodunofficial.block;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialComponents;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlockEntities;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems;
import com.portofino.realtrainmodunofficial.blockentity.LargeRailCoreBlockEntity;
import com.portofino.realtrainmodunofficial.blockentity.RailCollisionBlockEntity;
import jp.ngt.rtm.rail.util.RailMap;
import jp.ngt.rtm.rail.util.RailPosition;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import com.portofino.realtrainmodunofficial.item.WrenchItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class LargeRailCoreBlock extends BaseEntityBlock {
    public static final MapCodec<LargeRailCoreBlock> CODEC = simpleCodec(LargeRailCoreBlock::new);
    private static final VoxelShape SHAPE = Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 0.0625D, 1.0D);

    public LargeRailCoreBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    public LargeRailCoreBlock() {
        this(BlockBehaviour.Properties.of().sound(SoundType.METAL).strength(0.5F, 6.0F).noOcclusion());
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return SHAPE;
    }

    @Override
    public void onRemove(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && state.getBlock() != newState.getBlock()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof LargeRailCoreBlockEntity core) {
                RailMap[] maps = core.getAllRailMaps();
                if (maps.length > 0) {
                    // Prevent collision blocks from recursively trying to delete this core.
                    boolean prev = jp.ngt.rtm.rail.util.RailMap.suppressRailRemoval;
                    jp.ngt.rtm.rail.util.RailMap.suppressRailRemoval = true;
                    try {
                        for (RailMap map : maps) {
                            if (map != null) {
                                map.removeRailBlocks(level);
                            }
                        }
                        removeRemainingCollisionBlocks(level, pos, maps);
                    } finally {
                        jp.ngt.rtm.rail.util.RailMap.suppressRailRemoval = prev;
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LargeRailCoreBlockEntity(pos, state);
    }

    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.getItemInHand(hand).getItem() instanceof WrenchItem) {
            if (!level.isClientSide()) {
                if (level.getBlockEntity(pos) instanceof LargeRailCoreBlockEntity core) {
                    int total = core.getAllRailMaps().length;
                    if (total >= 2 && core.cycleSwitch()) {
                        player.displayClientMessage(
                            Component.literal("分岐切替: " + (core.getActiveSegmentIndex() + 1) + "/" + total + " 番線"),
                            true);
                    }
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return InteractionResult.PASS;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        // 中ボタン(ピックブロック): レールコアから種類+形状(長さ)をコピーした RailItem を返す。
        if (level.getBlockEntity(pos) instanceof LargeRailCoreBlockEntity core) {
            return createRailCloneStack(pos, core);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, net.minecraft.world.level.block.Block block, BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof LargeRailCoreBlockEntity core) {
            core.requestSwitchStateRefresh();
        }
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, RealTrainModUnofficialBlockEntities.LARGE_RAIL_CORE.get(), LargeRailCoreBlockEntity::tick);
    }

    private static void removeRemainingCollisionBlocks(Level level, BlockPos corePos, RailMap[] maps) {
        int minX = corePos.getX() - 2;
        int maxX = corePos.getX() + 2;
        int minY = corePos.getY() - 2;
        int maxY = corePos.getY() + 2;
        int minZ = corePos.getZ() - 2;
        int maxZ = corePos.getZ() + 2;
        for (RailMap map : maps) {
            if (map == null) {
                continue;
            }
            int split = RailMap.curveSplitForLength(map.getHorizontalPathLength());
            int samples = Math.max(16, split + 1);
            for (int i = 0; i < samples; i++) {
                int j = samples <= 1 ? 0 : (int) Math.round((double) split * i / (samples - 1));
                int index = Math.min(split, j);
                double[] point = map.getRailPos(split, index);
                int x = (int) Math.floor(point[1]);
                int y = (int) Math.floor(map.getRailHeight(split, index));
                int z = (int) Math.floor(point[0]);
                minX = Math.min(minX, x - 2);
                maxX = Math.max(maxX, x + 2);
                minY = Math.min(minY, y - 2);
                maxY = Math.max(maxY, y + 2);
                minZ = Math.min(minZ, z - 2);
                maxZ = Math.max(maxZ, z + 2);
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos scanPos = new BlockPos(x, y, z);
                    BlockEntity be = level.getBlockEntity(scanPos);
                    if (be instanceof RailCollisionBlockEntity collision && corePos.equals(collision.getCorePos())) {
                        level.removeBlock(scanPos, false);
                    }
                }
            }
        }
    }

    /**
     * Builds a copyable rail item from a placed rail core.
     * The returned stack reuses the same preview tag format as edited rails,
     * so right-clicking a marker can recreate the copied geometry.
     */
    public static ItemStack createRailCloneStack(BlockPos corePos, LargeRailCoreBlockEntity core) {
        ItemStack stack = new ItemStack(RealTrainModUnofficialItems.RAIL_ITEM.get());
        String railDefinitionId = core.getRailDefinitionId();
        if (railDefinitionId != null && !railDefinitionId.isBlank()) {
            stack.set(RealTrainModUnofficialComponents.SELECTED_MODEL_ID.get(), railDefinitionId);
        }

        RailPosition[] positions = core.getRailPositions();
        if (positions.length < 2 || positions[0] == null) {
            return stack;
        }

        CompoundTag preview = new CompoundTag();
        preview.putInt("X", corePos.getX());
        preview.putInt("Y", corePos.getY());
        preview.putInt("Z", corePos.getZ());
        // placeRailFromItem がコピー元の実ブロック不在でも復元できるように、
        // レンチ由来と同じ互換フラグを持たせる。
        preview.putBoolean("WrenchMode", true);
        preview.put("StartRP", positions[0].writeToNBT());

        ListTag segments = new ListTag();
        for (int i = 0; i + 1 < positions.length; i += 2) {
            RailPosition end = positions[i + 1];
            if (end == null) {
                continue;
            }
            CompoundTag segment = new CompoundTag();
            segment.put("EndRP", end.writeToNBT());
            segments.add(segment);
        }
        if (!segments.isEmpty()) {
            preview.putBoolean("BranchMode", segments.size() > 1);
            preview.put("RailSegments", segments);
            preview.put("EndRP", segments.getCompound(0).getCompound("EndRP"));
            stack.set(RealTrainModUnofficialComponents.RAIL_PREVIEW_START.get(), preview);
        }
        return stack;
    }
}
