package jp.ngt.rtm.rail;

import com.mojang.serialization.MapCodec;
import jp.ngt.rtm.rail.util.RailMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * 本家 jp.ngt.rtm.rail.BlockLargeRailBase (KaizPatchX) の忠実移植。
 * 道床(レールベース)ブロック。描画はコア BE のレンダラが行うため INVISIBLE。
 */
public class BlockLargeRailBase extends BaseEntityBlock {
    public static final MapCodec<BlockLargeRailBase> CODEC = simpleCodec(props -> new BlockLargeRailBase(2, props));

    public static final float THICKNESS = 0.0625F;
    /** 2:Gravel, 3:Stone, 4:Snow, 5:Asphalt */
    public final int railTextureType;

    public BlockLargeRailBase(int par1, Properties props) {
        super(props
                .mapColor(MapColor.STONE)
                .strength(1.0F, 15.0F)
                .sound(par1 == 2 ? SoundType.GRAVEL : (par1 == 4 ? SoundType.SNOW : SoundType.STONE))
                .noOcclusion()
                .isValidSpawn((state, level, pos, type) -> false)
                .dynamicShape());
        this.railTextureType = par1;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // ★道床 (レール下の砂利) はこのブロックが描く。本家 1.7.10 も同じで、
        // BlockLargeRailBase.getRenderType() = RTMBlock.renderIdBlockRail →
        // RenderBlockLargeRail (ISimpleBlockRenderingHandler) がチャンクへ焼いていた。
        // 1.21 では BallastModel / BallastFabricModel が同じ 6 面を出す。
        // INVISIBLE に戻すと道床が丸ごと消える (レールの MQO モデルは砂利を描かない)。
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEntityLargeRailBase(pos, state);
    }

    public boolean isCore() {
        return false;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getRailShape(level, pos, false);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean preventMob = false;
        if (context instanceof EntityCollisionContext ecc && ecc.getEntity() instanceof Mob) {
            preventMob = this.preventMobMovement(level, pos);
        }
        return this.getRailShape(level, pos, preventMob);
    }

    protected VoxelShape getRailShape(BlockGetter level, BlockPos pos, boolean preventMob) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof TileEntityLargeRailBase rail)) {
            return Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, THICKNESS, 1.0D);
        }
        if (preventMob) {
            // Mob の通せんぼ用の壁。歩き心地は関係ないので本家どおり平均高さ + 256 の1枚箱。
            float[] fa = rail.getBlockHeights(pos.getX(), pos.getY(), pos.getZ(), THICKNESS, true);
            float height2 = (fa[0] + fa[1] + fa[2] + fa[3]) * 0.25F;
            if (height2 < THICKNESS) {
                height2 = THICKNESS;
            }
            return Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, height2 + 256.0D, 1.0D);
        }
        // 坂は「1ブロック=平らな箱1つ」だと段差になるので、ブロック内を細かく割って
        // レール面に沿わせる (TileEntityLargeRailBase.getRailCollisionShape)。
        return rail.getRailCollisionShape(pos.getX(), pos.getY(), pos.getZ(), THICKNESS);
    }

    /**
     * 本家: ModelSetRail.getConfig.allowCrossing が false なら Mob 通行禁止。
     * TODO(Phase 4): ModelSetRail 移植後に allowCrossing を参照。それまでは通行許可。
     */
    public boolean preventMobMovement(BlockGetter level, BlockPos pos) {
        return false;
    }

    /**
     * 本家 getPickBlock: レールをピックブロックすると RailPosition 込みのコピーアイテムを得る
     * (レールコピー&ペースト機能)。
     */
    @Override
    public net.minecraft.world.item.ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state) {
        BlockEntity tileEntity = level.getBlockEntity(pos);
        if (tileEntity instanceof TileEntityLargeRailBase) {
            TileEntityLargeRailCore coreTile = ((TileEntityLargeRailBase) tileEntity).getRailCore();
            if (coreTile != null) {
                try {
                    return jp.ngt.rtm.item.ItemRail.copyItemFromRail(coreTile);
                } catch (Throwable t) {
                    // ★ここは他 MOD (WAILA 等) から毎tick呼ばれる。
                    // 敷設途中のレールは中身が揃っていないので、落とさず空を返す。
                    return net.minecraft.world.item.ItemStack.EMPTY;
                }
            }
        }
        return net.minecraft.world.item.ItemStack.EMPTY;
    }

    @Override
    protected void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof TileEntityLargeRailBase tile0) {
                TileEntityLargeRailCore core = tile0.getRailCore();
                if (!world.isClientSide) {
                    if (core != null && !core.breaking) {
                        core.breaking = true;
                        RailMap[] maps = core.getAllRailMaps();
                        if (maps != null) {
                            Arrays.stream(maps).filter(java.util.Objects::nonNull)
                                    .forEach(rm -> rm.breakRail(world, core.getProperty(), core));
                        } else {
                            // RailMap 不明でもコアは撤去する
                            world.removeBlock(core.getBlockPos(), false);
                        }
                    } else if (core == null) {
                        jp.ngt.ngtlib.io.NGTLog.debug("[Rail] break at %s: core not found (startPoint=%d,%d,%d)",
                                pos.toShortString(), tile0.getStartPoint()[0], tile0.getStartPoint()[1], tile0.getStartPoint()[2]);
                    }
                }
            } else if (!world.isClientSide) {
                jp.ngt.ngtlib.io.NGTLog.debug("[Rail] break at %s: no rail BE (%s)", pos.toShortString(), String.valueOf(be));
            }
        }
        super.onRemove(state, world, pos, newState, movedByPiston);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // クライアントでも tick する: 分岐の Point.moveCount (転てつアニメーション) は
        // クライアント側 SwitchType.onUpdate で進む (本家 updateEntity は両側で動く)。
        if (this.isCore()) {
            return (lvl, pos, st, be) -> {
                if (be instanceof TileEntityLargeRailCore core) {
                    core.tick();
                }
            };
        }
        return null;
    }

    @Override
    protected void neighborChanged(BlockState state, Level world, BlockPos pos, net.minecraft.world.level.block.Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, world, pos, neighborBlock, neighborPos, movedByPiston);
        // ★ここでは分岐の状態を触らない。本家 BlockLargeRailSwitchBase /
        // BlockLargeRailSwitchCore の onNeighborBlockChange は<b>空実装</b>で、
        // レッドストーンは毎tickのポーリングだけで見ている
        // (SwitchType.onUpdate → Point.onUpdate → rpRoot.checkRSInput、
        //  進路は Point.getActiveRailMap がその場で引く)。
        //
        // 以前ここで onBlockChanged() を呼んでいたが、あれは activeRails を消して
        // 「両端が同時に通電しているか」という別の規則で組み直す。
        // 毎tickのポーリングと規則が食い違うため、<b>レッドストーンをつないだ瞬間に
        // 分岐が切り替わらなくなる</b>。本家と同じく何もしないのが正しい。
    }
}
