package jp.ngt.rtm.block;

import jp.ngt.ngtlib.block.BlockLiquidBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * 溶けた金属 (銑鉄/鋼鉄) の液体ブロック。本家 {@code BlockMeltedMetal} の移植。
 *
 * <p>鋼鉄は満たされて静止すると鋼材 (steel_slab, temperature 15) に固まる。
 * 水に触れると爆発する。触れた物は燃える。
 */
public class BlockMeltedMetal extends BlockLiquidBase {

    /** true = 鋼鉄 (steel_l)。false = 銑鉄 (pig_iron_l)。 */
    private final boolean steel;

    public BlockMeltedMetal(boolean steel) {
        super(Properties.of().mapColor(MapColor.FIRE)
            .strength(100.0F).lightLevel(state -> 15).noCollission());
        this.steel = steel;
    }

    @Override
    protected int tickRate() {
        return 10;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.tick(state, level, pos, random);
        //本家: 鋼鉄はメタ 0 (残り 1 単位) で、露出していて流動が終わっていれば 1/5x1/5 で固まる
        BlockState now = level.getBlockState(pos);
        if (this.steel && now.is(this) && now.getValue(AMOUNT) == 0 && random.nextInt(5) == 0) {
            boolean exposed = false;
            boolean settled = true;
            for (Direction face : Direction.values()) {
                BlockState neighbor = level.getBlockState(pos.relative(face));
                if (neighbor.isAir()) {
                    exposed = true;
                }
                if (neighbor.getBlock() instanceof BlockLiquidBase
                        && neighbor.getValue(AMOUNT) > 0) {
                    settled = false;
                }
            }
            if (exposed && settled && random.nextInt(5) == 0) {
                level.setBlock(pos, com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlocks
                    .STEEL_SLAB.get().defaultBlockState()
                    .setValue(BlockMetalSlab.TEMPERATURE, 15), 2);
            }
        }
    }

    /** 本家: 炉の火/排ガスへは流れ込まない (容量 15 = 押しのける)。 */
    @Override
    protected int canFlowLiquid(Level level, int x, int y, int z) {
        BlockState state = level.getBlockState(new BlockPos(x, y, z));
        if (state.getBlock() instanceof BlockFurnaceFire) {
            return 15;
        }
        return super.canFlowLiquid(level, x, y, z);
    }

    /** 本家: 隣に水が来たら双方消して爆発。 */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
                                   Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (!level.isClientSide()) {
            for (Direction face : Direction.values()) {
                BlockPos target = pos.relative(face);
                if (level.getFluidState(target).is(net.minecraft.tags.FluidTags.WATER)) {
                    level.removeBlock(target, false);
                    level.removeBlock(pos, false);
                    level.explode(null, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                        8.0F, true, Level.ExplosionInteraction.BLOCK);
                    return;
                }
            }
        }
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        entity.setDeltaMovement(
            (level.random.nextFloat() - level.random.nextFloat()) * 0.2F,
            0.2D,
            (level.random.nextFloat() - level.random.nextFloat()) * 0.2F);
        entity.playSound(SoundEvents.GENERIC_EXTINGUISH_FIRE, 0.4F,
            2.0F + level.random.nextFloat() * 0.4F);
        entity.hurt(level.damageSources().inFire(), 1.0F);
        entity.igniteForSeconds(5.0F);
    }
}
