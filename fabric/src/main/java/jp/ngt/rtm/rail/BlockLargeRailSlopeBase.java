package jp.ngt.rtm.rail;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * 本家 jp.ngt.rtm.rail.BlockLargeRailSlopeBase の忠実移植。
 * 本家は高さを metadata (0-15) に格納。1.21 では HEIGHT ブロックステートで代替。
 */
public class BlockLargeRailSlopeBase extends BlockLargeRailBase {
    public static final MapCodec<BlockLargeRailSlopeBase> CODEC = simpleCodec(props -> new BlockLargeRailSlopeBase(2, props));

    public static final IntegerProperty HEIGHT = IntegerProperty.create("height", 0, 15);

    public BlockLargeRailSlopeBase(int par1, Properties props) {
        super(par1, props);
        this.registerDefaultState(this.stateDefinition.any().setValue(HEIGHT, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(HEIGHT);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEntityLargeRailSlopeBase(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int l = state.getValue(HEIGHT);
        float f = (float) (1 + l) * 0.0625F;
        return Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, f, 1.0D);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        //本家 getCollisionBoundingBoxFromPool: 高さ = metadata * 0.0625
        int l = state.getValue(HEIGHT);
        float f = l * 0.0625F;
        if (f <= 0.0F) {
            f = 0.0625F;
        }
        return Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, f, 1.0D);
    }

    @Override
    public boolean isCore() {
        return false;
    }
}
