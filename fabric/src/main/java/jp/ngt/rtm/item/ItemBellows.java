package jp.ngt.rtm.item;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlocks;
import jp.ngt.rtm.entity.fluid.EntityFluid;
import jp.ngt.rtm.entity.fluid.FluidType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * フイゴ。本家 {@code jp.ngt.rtm.item.ItemBellows} の移植。
 *
 * <p>スロットを右クリックすると、その奥のコークスに空気を送って温度を上げる。
 * 流体を直接右クリックすると<b>冷ます</b> ({@link EntityFluid#interact} 側)。本家と同じ。
 */
public class ItemBellows extends Item {
    public ItemBellows() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!level.isClientSide()) {
            BlockPos pos = context.getClickedPos();
            if (level.getBlockState(pos).is(RealTrainModUnofficialBlocks.SLOT.get())) {
                //本家は clicked 面の反対側を見る
                BlockPos target = pos.relative(context.getClickedFace().getOpposite());
                this.blow(level, target);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    private void blow(Level level, BlockPos pos) {
        List<EntityFluid> list = level.getEntitiesOfClass(EntityFluid.class,
            new AABB(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1, pos.getY() + 3, pos.getZ() + 1));
        for (EntityFluid fluid : list) {
            if (fluid.getFluidType() == FluidType.COKE && fluid.countAir() <= 2) {
                float temp = fluid.getTemperature();
                if (temp > 300.0F && temp < 2000.0F) {
                    fluid.setTemperture(temp + 50.0F);
                }
            }
        }
    }
}
