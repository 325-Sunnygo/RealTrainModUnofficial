package jp.ngt.rtm.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;
import jp.ngt.rtm.block.tileentity.TileEntityMirror;

public final class BlockMirror extends BaseEntityBlock {
    public enum MirrorType {Mono_Panel,Hexa_Cube}
    public static final DirectionProperty FACING=BlockStateProperties.FACING;
    public static final IntegerProperty ALPHA=IntegerProperty.create("alpha",0,15);
    public static final MapCodec<BlockMirror> CODEC=RecordCodecBuilder.mapCodec(builder->builder.group(
        Codec.STRING.fieldOf("mirror_type").forGetter((BlockMirror block)->block.mirrorType.name()),propertiesCodec()
    ).apply(builder,(type,properties)->new BlockMirror(MirrorType.valueOf(type),properties)));
    public final MirrorType mirrorType;
    public BlockMirror(MirrorType type){this(type,BlockBehaviour.Properties.of().strength(.3F).sound(SoundType.GLASS).noOcclusion());}
    private BlockMirror(MirrorType type,BlockBehaviour.Properties properties){super(properties);mirrorType=type;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.UP).setValue(ALPHA,15));}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,ALPHA);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getClickedFace());}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new TileEntityMirror(pos,state);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        if(mirrorType==MirrorType.Hexa_Cube)return Shapes.block();
        return switch(state.getValue(FACING)) {
            case UP->Block.box(0,0,0,16,1,16);case DOWN->Block.box(0,15,0,16,16,16);
            case SOUTH->Block.box(0,0,0,16,16,1);case NORTH->Block.box(0,0,15,16,16,16);
            case EAST->Block.box(0,0,0,1,16,16);case WEST->Block.box(15,0,0,16,16,16);
        };
    }
}
