package jp.ngt.rtm.item;

import jp.ngt.rtm.entity.fluid.EntityFluid;
import jp.ngt.rtm.entity.fluid.FluidType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 柄杓 (かき混ぜ棒)。本家 {@code jp.ngt.rtm.item.ItemPaddle} の移植。
 *
 * <p>銑鉄を叩く / 右クリックすると炉の構造を判定し、条件を満たしていれば鋼鉄になる。
 */
public final class ItemPaddle extends Item {
    /**
     * [[煙突始点],[煙突終点],[天井始点],[天井終点],[熱源始点],[熱源終点]]
     *
     * <pre>
     * ▲▲□※□■■
     * ▲▲□□□□□
     * ※銑鉄、▲熱源、■煙突
     * </pre>
     */
    private static final int[][][] FURNACE_PATTERN = {
        {{-3, 1, 0}, {-2, 1, 0}, {0, 1, 0}, {3, 1, 0}, {2, -1, 0}, {3, 0, 0}},
        {{2, 1, 0}, {3, 1, 0}, {-3, 1, 0}, {0, 1, 0}, {-3, -1, 0}, {-2, 0, 0}},
        {{0, 1, -3}, {0, 1, -2}, {0, 1, 0}, {0, 1, 3}, {0, -1, 2}, {0, 0, 3}},
        {{0, 1, 2}, {0, 1, 3}, {0, 1, -3}, {0, 1, 0}, {0, -1, -3}, {0, 0, -2}}
    };

    public ItemPaddle() {
        super(new Properties().stacksTo(1).durability(Tiers.IRON.getUses()));
    }

    /** サバイバルで右クリック動作を強制させる (本家と同じ)。 */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    private static boolean stir(Player player, EntityFluid fluid) {
        BlockPos pos = fluid.blockPosition();
        float temp = checkFurnaceEnv(fluid.level(), player, pos.getX(), pos.getY(), pos.getZ());
        if (temp > 0.0F) {
            if (temp > fluid.getTemperature()) {
                fluid.setTemperture(temp);
            }
            if (temp >= FluidType.STEEL.meltingPoint) {
                if (fluid.level().random.nextInt(4) == 0) {
                    player.displayClientMessage(Component.translatable("message.paddle.get_steel"), false);
                    fluid.setFluidType(FluidType.STEEL);
                    return true;
                }
            } else {
                player.displayClientMessage(
                    Component.literal(String.format("Low temperature (%5.1f)", temp)), false);
            }
        }
        return false;
    }

    /** 炉が規定構造にマッチしているか。 */
    private static float checkFurnaceEnv(Level level, Player player, int x, int y, int z) {
        float temp = 0.0F;
        int chimney = -1;
        int ceiling = -1;
        int coke = -1;
        int heatSource = -1;
        for (int[][] pattern : FURNACE_PATTERN) {
            boolean fChimney = checkChimney(level, x, y, z, pattern[0], pattern[1]);
            chimney = (chimney == 1) ? chimney : (fChimney ? 1 : 0);
            if (!fChimney) {
                continue;
            }
            boolean fCeiling = checkCeiling(level, x, y, z, pattern[2], pattern[3]);
            ceiling = (ceiling == 1) ? ceiling : (fCeiling ? 1 : 0);
            if (!fCeiling) {
                continue;
            }
            boolean fCoke = checkCokeNotAdjacent(level, x, y, z);
            coke = (coke == 1) ? coke : (fCoke ? 1 : 0);
            if (!fCoke) {
                continue;
            }
            float tempNew = checkHeatSource(level, x, y, z, pattern[4], pattern[5]);
            boolean fHeatSource = tempNew > 0;
            heatSource = (heatSource == 1) ? heatSource : (fHeatSource ? 1 : 0);
            if (fHeatSource && tempNew > temp) {
                temp = tempNew;
            }
        }

        player.displayClientMessage(Component.literal(String.format(
            "Chimney:%s, Ceiling:%s, Coke:%s, HeatSource:%s",
            getMessage(chimney), getMessage(ceiling), getMessage(coke), getMessage(heatSource))), false);
        return temp;
    }

    private static String getMessage(int state) {
        return state == 1 ? "OK" : (state == 0 ? "NG" : "-");
    }

    /** コークスに接していないか。 */
    private static boolean checkCokeNotAdjacent(Level level, int x, int y, int z) {
        List<EntityFluid> list = level.getEntitiesOfClass(EntityFluid.class,
            new AABB(x, y, z, x + 1, y + 1, z + 1));
        for (EntityFluid fluid : list) {
            if (fluid.getFluidType() == FluidType.COKE) {
                return false;
            }
        }
        return true;
    }

    /** いずれか 1 地点が空に面しているか。 */
    private static boolean checkChimney(Level level, int x, int y, int z, int[] start, int[] end) {
        boolean flag = false;
        for (int i = start[0]; i <= end[0]; ++i) {
            for (int j = start[1]; j <= end[1]; ++j) {
                for (int k = start[2]; k <= end[2]; ++k) {
                    flag |= level.canSeeSky(new BlockPos(x + i, y + j, z + k));
                }
            }
        }
        return flag;
    }

    /** すべての地点が空に面していないか。 */
    private static boolean checkCeiling(Level level, int x, int y, int z, int[] start, int[] end) {
        for (int i = start[0]; i <= end[0]; ++i) {
            for (int j = start[1]; j <= end[1]; ++j) {
                for (int k = start[2]; k <= end[2]; ++k) {
                    if (level.canSeeSky(new BlockPos(x + i, y + j, z + k))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** 熱源の温度の平均値、ない場合は 0.0。 */
    private static float checkHeatSource(Level level, int x, int y, int z, int[] start, int[] end) {
        List<EntityFluid> list = level.getEntitiesOfClass(EntityFluid.class,
            new AABB(x + start[0], y + start[1], z + start[2],
                x + end[0] + 1, y + end[1] + 1, z + end[2] + 1));
        double sum = 0.0D;
        int count = 0;
        for (EntityFluid fluid : list) {
            if (fluid.getFluidType() == FluidType.COKE) {
                sum += fluid.getTemperature();
                ++count;
            }
        }
        return count > 0 ? (float) (sum / (double) count) : 0.0F;
    }

    /** @param dir +1 で押す、-1 で引く */
    public static void pushPull(Player player, EntityFluid fluid, float dir) {
        if (fluid.getFluidType() == FluidType.PIG_IRON && stir(player, fluid)) {
            return;
        }
        double x = fluid.getX() - player.getX();
        double z = fluid.getZ() - player.getZ();
        double len = Math.sqrt(x * x + z * z);
        if (len <= 0.0D) {
            return;
        }
        float f0 = dir * 0.1F;
        fluid.setDeltaMovement(fluid.getDeltaMovement().add((x / len) * f0, 0.0D, (z / len) * f0));
    }
}
