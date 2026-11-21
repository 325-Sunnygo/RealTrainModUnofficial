package jp.ngt.rtm.block;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlocks;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems;
import jp.ngt.ngtlib.block.BlockLiquidBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 炉の火 / 排ガス。本家 {@code BlockFurnaceFire} の移植。
 *
 * <p>炉の火に鉄鉱石 + コークス (2:1) を投げ込むと、下へ辿って銑鉄 (pig_iron_l) が湧く。
 * 排ガスは炉の火と入れ替わりながら上へ抜けて消える。熱風炉レンガはこの排ガスから熱を溜める。
 * 床が耐火レンガ以外の固体なら火事になる。
 */
public class BlockFurnaceFire extends BlockLiquidBase {

    /** true = 炉の火 (光る)。false = 排ガス。 */
    private final boolean fire;

    public BlockFurnaceFire(boolean fire) {
        super(Properties.of().mapColor(fire ? MapColor.FIRE : MapColor.COLOR_GRAY)
            .strength(100.0F).noCollission()
            .lightLevel(state -> fire ? 15 : 0));
        this.fire = fire;
    }

    public boolean isFire() {
        return this.fire;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.tick(state, level, pos, random);
        BlockState now = level.getBlockState(pos);
        if (!now.is(this)) {
            return;
        }
        int meta = now.getValue(AMOUNT);
        if (this.fire) {
            //本家: メタ 0 で床が耐火レンガ系でない固体なら発火
            if (meta == 0) {
                BlockState below = level.getBlockState(pos.below());
                if (!(below.getBlock() instanceof BlockFireBrick)
                        && below.isRedstoneConductor(level, pos.below())) {
                    level.setBlock(pos, Blocks.FIRE.defaultBlockState(), 2);
                }
            }
        } else {
            //排ガス: 上が炉の火なら入れ替わって上昇、上が空気なら 1/10 で減衰して消える
            BlockPos above = pos.above();
            BlockState aboveState = level.getBlockState(above);
            if (aboveState.getBlock() instanceof BlockFurnaceFire aboveFire && aboveFire.fire) {
                int m1 = aboveState.getValue(AMOUNT);
                level.setBlock(pos, aboveState.getBlock().defaultBlockState().setValue(AMOUNT, m1), 2);
                level.setBlock(above, this.defaultBlockState().setValue(AMOUNT, meta), 2);
                level.scheduleTick(above, this, this.tickRate());
            } else if (aboveState.isAir() && random.nextInt(10) == 0) {
                if (meta > 0) {
                    level.setBlock(pos, this.defaultBlockState().setValue(AMOUNT, meta - 1), 2);
                    level.scheduleTick(pos, this, this.tickRate());
                } else {
                    level.removeBlock(pos, false);
                }
            } else {
                level.scheduleTick(pos, this, this.tickRate() * 4);
            }
        }
    }

    /** 本家 onEntityCollidedWithBlock: 鉄鉱石 + コークス投入で銑鉄を生む。 */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        boolean burn = true;
        if (entity instanceof ItemEntity itemEntity) {
            ItemStack stack = itemEntity.getItem();
            int sizeCoke = 0;
            int sizeIron = 0;
            boolean isIron = false;
            if (stack.is(RealTrainModUnofficialItems.COKE_ITEM.get())) {
                sizeCoke = stack.getCount() / 2;
                burn = false;
            } else if (stack.is(Items.CHARCOAL)) {
                sizeCoke = stack.getCount() / 8;
                burn = false;
            } else if (stack.is(Blocks.IRON_ORE.asItem()) || stack.is(Items.RAW_IRON)) {
                sizeIron = stack.getCount();
                isIron = true;
                burn = false;
            }
            if ((sizeCoke > 0 || sizeIron > 0) && !level.isClientSide()) {
                //同じマスに落ちている相方 (コークス) をかき集める
                List<ItemEntity> list = level.getEntitiesOfClass(ItemEntity.class,
                    new AABB(pos.getX(), pos.getY(), pos.getZ(),
                        pos.getX() + 1.0D, pos.getY() + 1.0D, pos.getZ() + 1.0D));
                for (ItemEntity other : list) {
                    if (other == entity) {
                        continue;
                    }
                    ItemStack otherStack = other.getItem();
                    int i2 = 0;
                    if (otherStack.is(RealTrainModUnofficialItems.COKE_ITEM.get())) {
                        i2 = otherStack.getCount() / 2;
                    } else if (otherStack.is(Items.CHARCOAL)) {
                        i2 = otherStack.getCount() / 8;
                    }
                    if (i2 > 0) {
                        if (sizeCoke + i2 > sizeIron && sizeIron > 0) {
                            otherStack.setCount(sizeCoke + i2 - sizeIron);
                            other.setItem(otherStack);
                            sizeCoke = sizeIron;
                        } else {
                            sizeCoke += i2;
                            other.discard();
                        }
                    }
                }
                if (isIron) {
                    if (sizeCoke == 0) {
                        return;
                    }
                    if (sizeCoke < sizeIron) {
                        stack.shrink(sizeCoke);
                        itemEntity.setItem(stack);
                        sizeIron = sizeCoke;
                    } else {
                        entity.discard();
                    }
                    this.onCollidedIronOre(level, pos.getX(), pos.getY(), pos.getZ(), sizeIron);
                }
            }
            if (burn) {
                entity.setDeltaMovement(
                    (level.random.nextFloat() - level.random.nextFloat()) * 0.2F,
                    0.2D,
                    (level.random.nextFloat() - level.random.nextFloat()) * 0.2F);
                entity.playSound(SoundEvents.GENERIC_BURN, 0.4F,
                    2.0F + level.random.nextFloat() * 0.4F);
            }
        }
        if (burn) {
            entity.hurt(level.damageSources().inFire(), 1.0F);
            entity.igniteForSeconds(5.0F);
        }
    }

    /** 本家 onCollidedIronOre: 炉の火を下へ辿り、最下段の下に銑鉄を注ぐ。 */
    protected void onCollidedIronOre(Level level, int x, int y, int z, int amount) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockState below = level.getBlockState(pos.below());
        if (below.getBlock() instanceof BlockFurnaceFire) {
            BlockState self = level.getBlockState(pos);
            if (self.getBlock() instanceof BlockFurnaceFire selfFire && selfFire.fire
                    && level.random.nextInt(10) == 0) {
                int meta = self.getValue(AMOUNT);
                amount += level.random.nextInt(meta + 1);
                if (level.random.nextInt(5) == 0) {
                    level.setBlock(pos, RealTrainModUnofficialBlocks.EXHAUST_GAS.get()
                        .defaultBlockState().setValue(AMOUNT, 15), 2);
                    level.scheduleTick(pos, RealTrainModUnofficialBlocks.EXHAUST_GAS.get(), 5);
                }
            }
            this.onCollidedIronOre(level, x, y - 1, z, amount);
        } else {
            while (amount > 0 && level.getBlockState(new BlockPos(x, y, z)).getBlock() instanceof BlockFurnaceFire) {
                level.removeBlock(new BlockPos(x, y, z), false);
                amount = BlockLiquidBase.addLiquid(level, x, y, z,
                    RealTrainModUnofficialBlocks.PIG_IRON_L.get(), amount, true);
                ++y;
            }
        }
    }

    /** 本家 randomDisplayTick: 炉の火の煙。 */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (this.fire) {
            BlockState above = level.getBlockState(pos.above());
            if (above.isAir()) {
                double d5 = pos.getX() + random.nextFloat();
                double d7 = pos.getZ() + random.nextFloat();
                level.addParticle(ParticleTypes.POOF, d5, pos.getY() + 1.0D, d7, 0.0D, 0.0D, 0.0D);
                if (random.nextInt(200) == 0) {
                    level.playLocalSound(pos.getX(), pos.getY(), pos.getZ(),
                        SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS,
                        0.2F + random.nextFloat() * 0.2F, 0.9F + random.nextFloat() * 0.15F, false);
                }
            }
        }
    }
}
