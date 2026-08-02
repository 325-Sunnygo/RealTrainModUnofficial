package jp.ngt.rtm.rail;

import jp.ngt.ngtlib.math.NGTMath;
import jp.ngt.rtm.rail.util.RailMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 本家 jp.ngt.rtm.rail.TileEntityLargeRailBase (KaizPatchX) の忠実移植。
 * 道床(レールベース)ブロック1個ごとの BlockEntity。startPoint でコアを参照する。
 */
public class TileEntityLargeRailBase extends BlockEntity implements ILargeRail {
    private static final int SPLIT = 128;
    protected int[] startPoint = new int[3];

    /** ブロックの当たり判定が設定されている */
    private boolean finishSetupBlockBounds;

    /**
     * {xNzP, xPzP, xPzN, xNzN}
     * 描画と当たり判定の計算に使用
     */
    private float[] blockHeights;

    /**
     * 当たり判定の形状キャッシュ。
     * 毎回マスごとの箱を union すると重い。
     */
    private net.minecraft.world.phys.shapes.VoxelShape cachedShape;
    private int cachedShapeVersion = -1;

    /**
     * 1.7.10 TileEntity の座標 SRG フィールド (xCoord / yCoord / zCoord)。
     * スクリプトが位置を読むのにこの名前を使う:
     */
    public final int field_145851_c;
    public final int field_145848_d;
    public final int field_145849_e;

    public TileEntityLargeRailBase(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.field_145851_c = pos.getX();
        this.field_145848_d = pos.getY();
        this.field_145849_e = pos.getZ();
    }

    public TileEntityLargeRailBase(BlockPos pos, BlockState state) {
        this(RTMRailBlockEntities.LARGE_RAIL_BASE.get(), pos, state);
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);

        this.startPoint[0] = nbt.getInt("spX");
        this.startPoint[1] = nbt.getInt("spY");
        this.startPoint[2] = nbt.getInt("spZ");
        // 同期/再読込時に高さキャッシュを無効化 (当たり判定が古い値に固着しないように)
        this.blockHeights = null;
        this.cachedShape = null;
        this.finishSetupBlockBounds = false;
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.saveAdditional(nbt, registries);

        nbt.putInt("spX", this.startPoint[0]);
        nbt.putInt("spY", this.startPoint[1]);
        nbt.putInt("spZ", this.startPoint[2]);
    }

    // クライアント同期 (本家 S35 相当): フル NBT を送る
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public int[] getStartPoint() {
        return this.startPoint;
    }

    public void setStartPoint(int x, int y, int z) {
        this.startPoint[0] = x;
        this.startPoint[1] = y;
        this.startPoint[2] = z;
    }

    public boolean isTrainOnRail() {
        TileEntityLargeRailCore tile = this.getRailCore();
        if (tile != null) {
            return tile.isCollidedTrain();
        }
        return false;
    }

    /**
     * スクリプト互換 SRG: func_70296_d = markDirty (setChanged)。
     * SRB3 のサーバースクリプトがレール生成後に呼ぶ。
     */
    public void func_70296_d() {
        this.setChanged();
    }

    @Override
    public RailMap getRailMap(Entity entity) {
        TileEntityLargeRailCore tile = this.getRailCore();
        if (tile != null) {
            return ((ILargeRail) tile).getRailMap(entity);
        }
        return null;
    }

    public TileEntityLargeRailCore getRailCore() {
        if (this.level == null) {
            return null;
        }
        BlockEntity tile = this.level.getBlockEntity(new BlockPos(this.startPoint[0], this.startPoint[1], this.startPoint[2]));
        if (tile instanceof TileEntityLargeRailCore) {
            return (TileEntityLargeRailCore) tile;
        }
        return null;
    }

    /**
     * スクリプト互換オーバーロード: Baru's Pole (xx_s/xx_w) 等は world に
     * tileEntity.func_145831_w = jp.ngt.mccompat.WorldCompat を渡す。
     * Level 版へアンラップして委譲する (Object で受けないと Nashorn のオーバーロード解決が失敗する)。
     */
    public static TileEntityLargeRailBase getRailFromCoordinates(Object world, double px, double py, double pz) {
        Level level = null;
        if (world instanceof Level l) {
            level = l;
        } else if (world instanceof jp.ngt.mccompat.WorldCompat wc) {
            level = wc.getLevel();
        }
        return level == null ? null : getRailFromCoordinates(level, px, py, pz);
    }

    public static TileEntityLargeRailBase getRailFromCoordinates(Level world, double px, double py, double pz) {
        int x = Mth.floor(px);
        int y = Mth.floor(py);
        int z = Mth.floor(pz);
        while (y > world.getMinBuildHeight()) {
            Block block = world.getBlockState(new BlockPos(x, y, z)).getBlock();
            if (block instanceof BlockLargeRailBase) {
                break;
            }
            --y;
        }

        BlockEntity tile = world.getBlockEntity(new BlockPos(x, y, z));
        if (tile instanceof TileEntityLargeRailBase) {
            return (TileEntityLargeRailBase) tile;
        }
        return null;
    }

    /**
     * 指定した座標の下にあるレールのRailMapを返す
     * @param entity 列車、車止め、など
     */
    /** スクリプト互換: WorldCompat / PlayerCompat をそのまま渡せる版。 */
    public static RailMap getRailMapFromCoordinates(Object world, Object entity, double px, double py, double pz) {
        Level level = jp.ngt.ngtlib.block.BlockUtil.toLevel(world);
        Entity e = jp.ngt.mccompat.EntityCompatUtil.unwrapEntity(entity);
        return level == null ? null : getRailMapFromCoordinates(level, e, px, py, pz);
    }

    public static RailMap getRailMapFromCoordinates(Level world, Entity entity, double px, double py, double pz) {
        TileEntityLargeRailBase rail = TileEntityLargeRailBase.getRailFromCoordinates(world, px, py, pz);
        if (rail != null) {
            return rail.getRailMap(entity);
        }
        return null;
    }

    /**
     * 坂に沿った当たり判定。
     * 本家は「1ブロック = 4隅の平均高さの平らな箱」1つなので、坂だとブロックごとの段差になる。
     * @param thickness 最低の厚み (本家 BlockLargeRailBase.THICKNESS = 1/16)
     */
    public net.minecraft.world.phys.shapes.VoxelShape getRailCollisionShape(int x, int y, int z, float thickness) {
        TileEntityLargeRailCore core = this.getRailCore();
        if (core == null) {
            // まだコアが解決できていない (読み込み途中)。キャッシュせず薄い板で凌ぐ。
            return net.minecraft.world.phys.shapes.Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, thickness, 1.0D);
        }
        net.minecraft.world.phys.shapes.VoxelShape shape = this.cachedShape;
        if (shape != null && this.cachedShapeVersion == core.getCollisionVersion()) {
            return shape;
        }
        this.cachedShapeVersion = core.getCollisionVersion();

        float[] grid = core.getCollisionGrid(this.worldPosition);
        if (grid == null) {
            // レール曲線がこのブロックを通っていない (道床の外周など)。薄い板。
            shape = net.minecraft.world.phys.shapes.Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, thickness, 1.0D);
            this.cachedShape = shape;
            return shape;
        }

        int n = TileEntityLargeRailCore.COLLISION_SPLIT;
        shape = net.minecraft.world.phys.shapes.Shapes.empty();
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                float surfaceY = grid[j * n + i];
                // グリッドは絶対 Y。ブロック内のローカル高さに直す。
                double h = surfaceY - y;
                if (h < thickness) {
                    h = thickness;
                }
                if (h > 1.0D) {
                    h = 1.0D;
                }
                shape = net.minecraft.world.phys.shapes.Shapes.or(shape,
                        net.minecraft.world.phys.shapes.Shapes.box(
                                (double) i / n, 0.0D, (double) j / n,
                                (double) (i + 1) / n, h, (double) (j + 1) / n));
            }
        }
        shape = shape.optimize();
        this.cachedShape = shape;
        return shape;
    }

    /** {xNzP, xPzP, xPzN, xNzN} */
    public float[] getBlockHeights(int x, int y, int z, float defaultHeight, boolean useCache) {
        if (useCache && this.blockHeights != null) {
            return this.blockHeights;
        }

        if (this.finishSetupBlockBounds || !useCache) {
            float[] fa = this.getBlockHeights(x, y, z, defaultHeight);
            if (fa != null) {
                if (useCache) {
                    this.blockHeights = fa;
                    this.cachedShape = null;
                    if (this.level != null && !this.level.isClientSide) {
                        this.finishSetupBlockBounds = true;
                    }
                }
                return fa;
            }
        } else {
            // 初回: コアが解決できれば計算を有効化 (本家 updateEntity の finishSetupBlockBounds 相当)
            if (this.getRailCore() != null) {
                this.finishSetupBlockBounds = true;
                float[] fa = this.getBlockHeights(x, y, z, defaultHeight);
                if (fa != null) {
                    if (useCache) {
                        this.blockHeights = fa;
                        this.cachedShape = null;
                    }
                    return fa;
                }
            }
        }

        return new float[]{0.0625F, 0.0625F, 0.0625F, 0.0625F};
    }

    private float[] getBlockHeights(int x, int y, int z, float defaultHeight) {
        TileEntityLargeRailCore core = this.getRailCore();
        return blockHeightsOf(core == null ? null : core.getAllRailMaps(), x, y, z, defaultHeight);
    }

    /**
     * レールコアの線形から、そのブロックの 4 隅の高さを出す。
     *
     * <p>道床 (BallastBlock) からも同じ計算が要るので static に切り出してある。
     * レール本体と道床で別々の式にすると、隣り合うブロックの継ぎ目がずれる。
     *
     * @return {xNzP, xPzP, xPzN, xNzN}。出せなければ null
     */
    public static float[] blockHeightsOf(RailMap[] rms, int x, int y, int z, float defaultHeight) {
        if (rms == null) {
            return null;
        }

        float[] fa = new float[]{defaultHeight, defaultHeight, defaultHeight, defaultHeight};
        for (int i = 0; i < fa.length; ++i) {
            int x0 = x + ((i == 1 || i == 2) ? 1 : 0);
            int z0 = z + ((i == 0 || i == 1) ? 1 : 0);
            double distanceSq = Double.MAX_VALUE;

            for (RailMap rm : rms) {
                if (rm == null) {
                    return null;
                }
                int index = rm.getNearlestPoint(SPLIT, x0, z0);
                if (index < 0) {
                    index = 0;
                }

                double[] rpos = rm.getRailPos(SPLIT, index);
                double dSq2 = NGTMath.getDistanceSq(x0, z0, rpos[1], rpos[0]);
                if (dSq2 < distanceSq) {
                    distanceSq = dSq2;

                    double height = rm.getRailHeight(SPLIT, index);
                    float yaw = rm.getRailRotation(SPLIT, index);
                    float cant = rm.getCant(SPLIT, index);
                    float yaw2 = (float) NGTMath.toDegrees(Math.atan2(rpos[1] - x0, rpos[0] - z0));
                    // 最も近いレール上の点からの距離
                    double len = Math.sqrt((rpos[1] - x0) * (rpos[1] - x0) + (rpos[0] - z0) * (rpos[0] - z0));
                    // レールYawに対するベクトル角により左右位置を判断
                    boolean dirFlag = Mth.wrapDegrees(yaw2 - yaw) > 0.0F;
                    double h2 = NGTMath.sin(cant) * len * (dirFlag ? -1.0F : 1.0F);
                    fa[i] = (float) (height - (double) y + h2 + defaultHeight - 0.0625);
                }
            }
        }
        return fa;
    }

    /** 音が響くレールかどうか */
    public boolean isReberbSound() {
        TileEntityLargeRailCore core = this.getRailCore();
        if (core != null && this.level != null) {
            if (!core.getProperty().block.defaultBlockState().isSolidRender(this.level, this.worldPosition)) {
                BlockState state = this.level.getBlockState(this.worldPosition.below());
                return !state.isSolidRender(this.level, this.worldPosition.below());
            }
        }
        return false;
    }

    /** 本家 Lockable: 施錠対象はレールコア。 */
    public Object getTarget(Object world, int x, int y, int z) {
        return this.getRailCore();
    }

    public boolean lock(Object player, String code) {
        return true;
    }

    public boolean unlock(Object player, String code) {
        return true;
    }

    /** 本家 getProhibitedAction: 1 = 破壊禁止。 */
    public int getProhibitedAction() {
        return 1;
    }

}
