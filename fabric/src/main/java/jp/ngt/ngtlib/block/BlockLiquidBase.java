package jp.ngt.ngtlib.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 有限セル式の液体。本家 (ngtlib) {@code BlockLiquidBase} の移植。
 *
 * <p>本家はメタ 0〜15 で量を持つ (0 = 1 単位、15 = 満杯 16 単位)。
 * 5 tick ごとに下 → 横 4 方向へ 1 単位ずつ流れ、周囲のブロックを溶かす。
 * 1.21 にメタが無いので {@code AMOUNT} プロパティで同じ値を持つ。
 */
public abstract class BlockLiquidBase extends Block {

    /** 本家のメタ。量 (0 = 1 単位 … 15 = 満杯)。 */
    public static final IntegerProperty AMOUNT = IntegerProperty.create("amount", 0, 15);

    public BlockLiquidBase(Properties properties) {
        super(properties.noOcclusion().noLootTable()
            .pushReaction(PushReaction.DESTROY)
            .replaceable());
        this.registerDefaultState(this.stateDefinition.any().setValue(AMOUNT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AMOUNT);
    }

    /** 本家 tickRate。 */
    protected int tickRate() {
        return 5;
    }

    /** 当たり判定なし・見た目は量に応じた高さ。 */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                                           BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                                  BlockPos pos, CollisionContext context) {
        int amount = state.getValue(AMOUNT);
        return Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, (amount + 1) / 16.0D, 1.0D);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        level.scheduleTick(pos, this, this.tickRate());
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
                                   Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (level.getBlockState(pos).is(this)) {
            level.scheduleTick(pos, this, this.tickRate());
        }
    }

    /** 本家 updateTick。 */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.getBlockState(pos).is(this)) {
            return;
        }
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        int meta = level.getBlockState(pos).getValue(AMOUNT);
        int prevMeta = meta;
        meta = this.setLiquid(level, x, y, z, x, y - 1, z, meta);
        if (meta == prevMeta) {
            if (meta >= 0 && this.canFlowLiquid(level, x - 1, y - 1, z) > 0
                    || this.canFlowLiquid(level, x - 1, y, z) + meta > 15) {
                meta = this.setLiquid(level, x, y, z, x - 1, y, z, meta);
            }
            if (meta >= 0 && this.canFlowLiquid(level, x + 1, y - 1, z) > 0
                    || this.canFlowLiquid(level, x + 1, y, z) + meta > 15) {
                meta = this.setLiquid(level, x, y, z, x + 1, y, z, meta);
            }
            if (meta >= 0 && this.canFlowLiquid(level, x, y - 1, z - 1) > 0
                    || this.canFlowLiquid(level, x, y, z - 1) + meta > 15) {
                meta = this.setLiquid(level, x, y, z, x, y, z - 1, meta);
            }
            if (meta >= 0 && this.canFlowLiquid(level, x, y - 1, z + 1) > 0
                    || this.canFlowLiquid(level, x, y, z + 1) + meta > 15) {
                meta = this.setLiquid(level, x, y, z, x, y, z + 1, meta);
            }
        }
        if (level.getBlockState(pos).is(this)) {
            this.meltNeighborBlocks(level, pos, random);
        }
        if (meta != prevMeta) {
            level.scheduleTick(pos, this, this.tickRate());
        }
    }

    /** 本家 canFlowLiquid: 空気=15、自分=残り容量、その他=-1。 */
    protected int canFlowLiquid(Level level, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return 15;
        }
        return state.is(this) ? 14 - state.getValue(AMOUNT) : -1;
    }

    /** 本家 setLiquid: 1 単位を相手へ移す。返り値 = 自分の新しいメタ (-1 = 消滅)。 */
    protected int setLiquid(Level level, int x, int y, int z, int targetX, int targetY, int targetZ, int myMetadata) {
        int i0 = this.canFlowLiquid(level, targetX, targetY, targetZ);
        if (i0 < 0) {
            return myMetadata;
        }
        BlockPos target = new BlockPos(targetX, targetY, targetZ);
        level.setBlock(target, this.defaultBlockState().setValue(AMOUNT, clamp(15 - i0)), 2);
        level.scheduleTick(target, this, this.tickRate());
        BlockPos self = new BlockPos(x, y, z);
        if (myMetadata > 0) {
            --myMetadata;
            level.setBlock(self, this.defaultBlockState().setValue(AMOUNT, clamp(myMetadata)), 2);
            return myMetadata;
        }
        level.removeBlock(self, false);
        return -1;
    }

    /** 本家 addLiquid: amount 単位を注ぐ。返り値 = 入りきらなかった量。 */
    public static int addLiquid(Level level, int x, int y, int z, Block block, int amount, boolean checkBlock) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(pos);
        if (!checkBlock || state.isAir() || (state.is(block) && block instanceof BlockLiquidBase)) {
            int current = state.is(block) ? state.getValue(AMOUNT) : 0;
            int i0 = current + amount;
            int i1 = i0 & 15;
            level.setBlock(pos, block.defaultBlockState().setValue(AMOUNT, i1), 2);
            level.scheduleTick(pos, block, 5);
            return i0 > i1 ? i0 - i1 : 0;
        }
        return amount;
    }

    protected static int clamp(int par1) {
        return Math.max(0, Math.min(15, par1));
    }

    private void meltNeighborBlocks(ServerLevel level, BlockPos pos, RandomSource random) {
        Direction face = Direction.values()[random.nextInt(6)];
        this.meltBlock(level, pos.relative(face));
    }

    /**
     * 本家 meltBlock。1.21 に Material が無いので同等の分類で移植:
     * 可燃物/土/柔らかい石 → 発火、ガラス/氷/雪 → 消滅、TNT → 起爆、
     * 岩盤・水・溶岩・自分たち → 何もしない。
     */
    protected void meltBlock(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        //本家 RTMMaterial.fireproof = 耐火レンガ/熱風炉レンガ
        if (state.isAir() || state.is(Blocks.BEDROCK)
                || state.is(Blocks.WATER) || state.is(Blocks.LAVA)
                || state.getBlock() instanceof BlockLiquidBase
                || state.getBlock() instanceof jp.ngt.rtm.block.BlockFireBrick) {
            return;
        }
        if (state.getBlock() instanceof TntBlock) {
            level.removeBlock(pos, false);
            TntBlock.explode(level, pos);
            return;
        }
        if (state.is(Blocks.GLASS) || state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE)
                || state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)
                || state.is(net.minecraft.tags.BlockTags.IMPERMEABLE)) {
            level.removeBlock(pos, false);
            return;
        }
        if (state.ignitedByLava() || state.is(net.minecraft.tags.BlockTags.DIRT)
                || state.is(net.minecraft.tags.BlockTags.LEAVES)
                || state.getDestroySpeed(level, pos) < 3.5F) {
            this.setFire(level, pos);
        }
    }

    private void setFire(ServerLevel level, BlockPos pos) {
        for (Direction face : Direction.values()) {
            BlockPos target = pos.relative(face.getOpposite());
            if (level.getBlockState(target).isAir()) {
                level.setBlock(target, BaseFireBlock.getState(level, target), 2);
            }
        }
    }
}
