package jp.ngt.rtm.rail;

import jp.ngt.rtm.rail.util.RailMap;
import jp.ngt.rtm.rail.util.RailMapBasic;
import jp.ngt.rtm.rail.util.RailPosition;
import jp.ngt.rtm.rail.util.RailProperty;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 本家 jp.ngt.rtm.rail.TileEntityLargeRailCore (KaizPatchX) の忠実移植。
 * NBT 形式は本家準拠: Property / SubRails / StartRP / EndRP / fixRTMRailMapVersion。
 */
public abstract class TileEntityLargeRailCore extends TileEntityLargeRailBase {

    /**
     * 本家 sendPacket: レールコアの状態をクライアントへ配る。
     * SRB3 は敷設の最後に必ずこれを呼ぶ:
     */
    public void sendPacket() {
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            net.minecraft.world.level.block.state.BlockState st = this.getBlockState();
            this.level.sendBlockUpdated(this.getBlockPos(), st, st, 3);
        }
    }
    public boolean breaking;
    protected boolean isCollidedTrain = false;
    public boolean colliding = false;
    private int signal = 0;

    private RailProperty property = RailProperty.getDefaultProperty();
    public final List<RailProperty> subRails = new ArrayList<>();

    protected RailPosition[] railPositions;
    protected RailMap railmap;

    private AABB renderAABB;

    /** レールを再描画するかどうか(明るさ変更等) */
    public boolean shouldRerenderRail;
    // see RailMapBasic.fixRTMRailMapVersion
    protected int fixRTMRailMapVersion;

    // WebCTC (別mod) 用: ロード済みレールコアの一覧。weak なのでチャンクアンロードで自然に消える。
    private static final java.util.Set<TileEntityLargeRailCore> LOADED_CORES =
            java.util.Collections.synchronizedSet(
                    java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));

    /** 指定ワールドのロード済みレールコア一覧 (WebCTC の地図 API が使用)。 */
    public static java.util.List<TileEntityLargeRailCore> getLoadedCores(net.minecraft.world.level.Level level) {
        synchronized (LOADED_CORES) {
            return LOADED_CORES.stream()
                    .filter(c -> c.getLevel() == level && !c.isRemoved())
                    .collect(java.util.stream.Collectors.toList());
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        LOADED_CORES.add(this);
        // ★道床はレールベース側のブロックが描くが、形はこのコアの線形から引く。
        // ベースのチャンクが先に焼かれているとコアが無い状態で焼かれていて道床が出ない。
        // コアが届いた今、レール一帯を焼き直させる。
        this.refreshBallastArea();
    }

    /** レール線形がまだ届いていなくて焼き直せなかった。次の tick で試し直す。 */
    private boolean pendingBallastRefresh;

    /** レール一帯 (道床の敷かれている範囲) の道床を焼き直させる。クライアントのみ。 */
    public void refreshBallastArea() {
        if (this.level == null || !this.level.isClientSide()) {
            return;
        }
        // ★線形がまだ入っていない時点で諦めてはいけない。
        //   新規に敷いたレールは「BE が置かれる → 少し後に線形が届く」順なので、
        //   ここで return すると<b>焼き直しの機会が二度と来ない</b>
        //   (入り直すと直るのはこれ。長い＝浅い分岐ほど派手に崩れて見える)。
        if (!this.isLoaded()) {
            this.pendingBallastRefresh = true;
            return;
        }
        this.pendingBallastRefresh = false;
        // ★レールの線 (焼き込みメッシュ) も一緒に捨てる。道床だけ作り直すと、
        //   ブロック側は新しい線形・レールの線は古い線形になって<b>ずれて見える</b>。
        com.portofino.realtrainmodunofficial.ClientHooks.invalidateRailMesh(this.level,
                this.worldPosition.getX(), this.worldPosition.getY(), this.worldPosition.getZ());
        this.shouldRerenderRail = true;
        int[] size = this.getRailSize();
        int pad = (this.getProperty().getBallastWidth() >> 1) + 2;
        com.portofino.realtrainmodunofficial.ClientHooks.refreshRailBallast(this.level,
                size[0] - pad, size[1] - 3, size[2] - pad,
                size[3] + pad, size[4] + 3, size[5] + pad);
    }

    public TileEntityLargeRailCore(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);

        this.readRailProperties(nbt);
        this.readRailData(nbt);
        // 同期・再読込時にキャッシュを無効化
        this.railmap = null;
        this.shouldRerenderRail = true;
        // 線形や道床ブロックが変わったら道床も焼き直す (level 未設定なら中で弾かれる)
        this.refreshBallastArea();
    }

    public void readRailProperties(CompoundTag nbt) {
        if (nbt.contains("Property")) {
            this.property = RailProperty.readFromNBT(nbt.getCompound("Property"));
            this.subRails.clear();
            ListTag list = nbt.getList("SubRails", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag nbt1 = list.getCompound(i);
                RailProperty property = RailProperty.readFromNBT(nbt1);
                this.subRails.add(property);
            }
        }
    }

    protected void readRailData(CompoundTag nbt) {
        this.railPositions = new RailPosition[2];
        if (nbt.contains("StartRP")) {
            this.railPositions[0] = RailPosition.readFromNBT(nbt.getCompound("StartRP"));
            this.railPositions[1] = RailPosition.readFromNBT(nbt.getCompound("EndRP"));
            this.fixRTMRailMapVersion = nbt.getInt("fixRTMRailMapVersion");
        }
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.saveAdditional(nbt, registries);

        this.writeRailProperties(nbt);
        this.writeRailData(nbt);
    }

    public void writeRailProperties(CompoundTag nbt) {
        CompoundTag nbtProp = new CompoundTag();
        this.property.writeToNBT(nbtProp);
        nbt.put("Property", nbtProp);
        ListTag tagList = new ListTag();
        this.subRails.forEach(property -> {
            CompoundTag nbtProp1 = new CompoundTag();
            property.writeToNBT(nbtProp1);
            tagList.add(nbtProp1);
        });
        nbt.put("SubRails", tagList);
    }

    protected void writeRailData(CompoundTag nbt) {
        if (this.railPositions != null && this.railPositions.length >= 2
                && this.railPositions[0] != null && this.railPositions[1] != null) {
            nbt.put("StartRP", this.railPositions[0].writeToNBT());
            nbt.put("EndRP", this.railPositions[1].writeToNBT());
        }
        nbt.putInt("fixRTMRailMapVersion", this.fixRTMRailMapVersion);
    }

    public void createRailMap() {
        if (this.isLoaded())//同期ができてない状態でのRailMapの生成を防ぐ
        {
            this.railmap = new RailMapBasic(this.railPositions[0], this.railPositions[1], this.fixRTMRailMapVersion);
            this.collisionGrids = null;
            this.collisionVersion++;
        }
    }

    // ===== 当たり判定 (レール曲線をサンプリングして焼く) =====

    /**
     * 列 (x, z) → その列の #COLLISION_SPLIT×#COLLISION_SPLIT マスごとの
     * レール面の高さ (絶対 Y)。
     * Y を含めない「列」で持つのが要点。
     */
    private java.util.Map<Long, float[]> collisionGrids;

    /**
     * レールを引き直すたびに進む。各レールブロックは自分の形状キャッシュがこの世代の
     * ものかを見て、古ければ作り直す。
     */
    private int collisionVersion;

    /** 当たり判定をブロック内で何分割するか。 */
    public static final int COLLISION_SPLIT = 4;

    public int getCollisionVersion() {
        return this.collisionVersion;
    }

    /**
     * このレールの当たり判定グリッドを引く。無ければ作る。
     * @return 指定ブロックの列のマスごとのレール面 (絶対 Y)。レールが通っていなければ null。
     */
    public float[] getCollisionGrid(BlockPos pos) {
        java.util.Map<Long, float[]> grids = this.collisionGrids;
        if (grids == null) {
            grids = this.buildCollisionGrids();
            this.collisionGrids = grids;
        }
        return grids.get(columnKey(pos.getX(), pos.getZ()));
    }

    private static long columnKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    /**
     * レール曲線を直接サンプリングして、ブロックごとの高さグリッドを焼く。
     * 従来は TileEntityLargeRailBase.getBlockHeights が「ブロックの 4 隅から
     * 最も近い曲線上の点」を探して高さを決め、それをブロック内で双線形補間していた。
     */
    private java.util.Map<Long, float[]> buildCollisionGrids() {
        java.util.Map<Long, float[]> grids = new java.util.HashMap<>();
        RailMap[] rms = this.getAllRailMaps();
        if (rms == null) {
            return grids;
        }
        // 道床の外縁まで撒く。createRailList が置くレールブロックは中心から ±(ballastWidth>>1)。
        double halfWidth = (this.getProperty().getBallastWidth() >> 1) + 0.5D;
        // マスの取りこぼしが出ない程度に細かく撒く (マスは 1/COLLISION_SPLIT ブロック)。
        double step = 1.0D / (COLLISION_SPLIT * 2);

        for (RailMap rm : rms) {
            if (rm == null) {
                continue;
            }
            int split = Math.max(8, (int) (rm.getLength() / step));
            for (int i = 0; i <= split; i++) {
                double[] point = rm.getRailPos(split, i);
                double cx = point[1];
                double cz = point[0];
                double height = rm.getRailHeight(split, i);
                float yaw = rm.getRailYaw(split, i);
                float cant = rm.getCant(split, i);
                // レール方向の法線 (yaw + 90°) 方向へ張り出す
                double rad = Math.toRadians(yaw + 90.0F);
                double dx = Math.sin(rad);
                double dz = Math.cos(rad);
                // カントによる左右の高低差。getBlockHeights と同じ符号 (中心から距離 w で sin(cant) * w)
                double cantSlope = jp.ngt.ngtlib.math.NGTMath.sin(cant);

                for (double w = -halfWidth; w <= halfWidth + 1.0E-6D; w += step) {
                    double sx = cx + dx * w;
                    double sz = cz + dz * w;
                    double sy = height + cantSlope * w;
                    int bx = Mth.floor(sx);
                    int bz = Mth.floor(sz);
                    long key = columnKey(bx, bz);
                    float[] grid = grids.get(key);
                    if (grid == null) {
                        grid = new float[COLLISION_SPLIT * COLLISION_SPLIT];
                        java.util.Arrays.fill(grid, Float.NaN);
                        grids.put(key, grid);
                    }
                    int gi = Mth.clamp((int) ((sx - bx) * COLLISION_SPLIT), 0, COLLISION_SPLIT - 1);
                    int gj = Mth.clamp((int) ((sz - bz) * COLLISION_SPLIT), 0, COLLISION_SPLIT - 1);
                    int idx = gj * COLLISION_SPLIT + gi;
                    float cur = grid[idx];
                    if (Float.isNaN(cur) || sy > cur) {
                        grid[idx] = (float) sy;
                    }
                }
            }
        }

        // 撒き漏らしたマス (ブロックの角など) を、そのブロックの最大値で埋める。
        // 穴が残ると床が抜けて見えるので、レール面のあるブロックは必ず全面を塞ぐ。
        for (float[] grid : grids.values()) {
            float max = Float.NaN;
            for (float v : grid) {
                if (!Float.isNaN(v) && (Float.isNaN(max) || v > max)) {
                    max = v;
                }
            }
            if (Float.isNaN(max)) {
                continue;
            }
            for (int i = 0; i < grid.length; i++) {
                if (Float.isNaN(grid[i])) {
                    grid[i] = max;
                }
            }
        }
        return grids;
    }

    /** レール情報の読み込みが完了してるかどうか(=RailPositionが存在する) */
    public boolean isLoaded() {
        return (this.railPositions != null && this.railPositions.length > 0 && Arrays.stream(this.railPositions).allMatch(Objects::nonNull));
    }

    public RailPosition[] getRailPositions() {
        return this.railPositions;
    }

    public void setRailPositions(RailPosition[] par1) {
        this.railPositions = par1;
    }

    public RailProperty getProperty() {
        return this.property;
    }

    public void setProperty(String s, Block block, int p3, float p4) {
        this.property = new RailProperty(s, block, p3, p4);
    }

    public void setProperty(RailProperty p1) {
        this.property = p1;
    }

    public int getSignal() {
        return this.signal;
    }

    public void setSignal(int par1) {
        this.signal = par1;
    }

    public boolean isCollidedTrain() {
        return this.isCollidedTrain;
    }

    public int getFixRTMRailMapVersion() {
        return this.fixRTMRailMapVersion;
    }

    public void setFixRTMRailMapVersion(int version) {
        this.fixRTMRailMapVersion = version;
    }

    @Override
    public TileEntityLargeRailCore getRailCore() {
        return this;
    }

    public void replaceRail(RailProperty state) {
        CompoundTag tag = new CompoundTag();
        state.writeToNBT(tag);
        this.property = RailProperty.readFromNBT(tag);
        this.subRails.clear();
        this.setChanged();
        this.markBlockForUpdate();
    }

    public void addSubRail(RailProperty state) {
        CompoundTag tag = new CompoundTag();
        state.writeToNBT(tag);
        RailProperty newState = RailProperty.readFromNBT(tag);
        RailProperty oldState = this.subRails.stream().filter(state1 -> state1.railModel.equals(newState.railModel)).findFirst().orElse(null);
        if (oldState == null) {
            if (!this.getProperty().railModel.equals(newState.railModel)) {
                this.subRails.add(newState);
            }
        } else {
            this.subRails.remove(oldState);
        }
        this.setChanged();
        this.markBlockForUpdate();
    }

    protected void markBlockForUpdate() {
        if (this.level != null) {
            BlockState state = this.level.getBlockState(this.worldPosition);
            this.level.sendBlockUpdated(this.worldPosition, state, state, 3);
        }
    }


    /** 本家 updateEntity 相当。Block 側の BlockEntityTicker から毎 tick 呼ばれる。 */
    public void tick() {
        // 焼き直しを取りこぼしていたら、線形が届いた時点でやり直す
        if (this.pendingBallastRefresh && this.level != null && this.level.isClientSide()) {
            this.refreshBallastArea();
        }
        if (this.level != null && !this.level.isClientSide) {
            this.isCollidedTrain = this.colliding;
            this.colliding = false;
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TileEntityLargeRailCore be) {
        be.tick();
    }

    @Override
    public RailMap getRailMap(Entity entity) {
        if (this.railmap == null) {
            this.createRailMap();
        }
        return this.railmap;
    }

    public RailMap[] getAllRailMaps() {
        RailMap rm = this.getRailMap(null);
        return rm != null ? new RailMap[]{rm} : null;
    }

    public AABB getRenderBoundingBox() {
        if (!this.isLoaded()) {
            return AABB.INFINITE;
        }

        if (this.renderAABB == null) {
            this.renderAABB = this.getRenderAABB();
            if (this.renderAABB == null) {
                return AABB.INFINITE;
            }//ぬるぽ回避
        }
        return this.renderAABB;
    }

    /**
     * レールの描画用AABBを取得
     * 呼び出しは最初の1回のみ
     */
    protected AABB getRenderAABB() {
        int[] size = this.getRailSize();
        AABB aabb = new AABB(size[0] - 3.5, size[1] - 10, size[2] - 3.5, size[3] + 5.5, size[4] + 2, size[5] + 5.5);
        if (aabb.maxX - aabb.minX <= 3 && aabb.maxZ - aabb.minZ <= 3) {
            return null;
        }
        return aabb;
    }

    /** {XMin, YMin, ZMin, XMax, YMax, ZMax} */
    public int[] getRailSize() {
        int startX = this.railPositions[0].blockX;
        int startY = this.railPositions[0].blockY;
        int startZ = this.railPositions[0].blockZ;
        int endX = this.railPositions[1].blockX;
        int endY = this.railPositions[1].blockY;
        int endZ = this.railPositions[1].blockZ;

        int minX = Math.min(startX, endX);
        int maxX = Math.max(startX, endX);
        int minY = Math.min(startY, endY);
        int maxY = Math.max(startY, endY);
        int minZ = Math.min(startZ, endZ);
        int maxZ = Math.max(startZ, endZ);
        return new int[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    /** レール形状の説明を取得(アイテム表示用) */
    public abstract String getRailShapeName();

    // ================= Remaster 暫定互換 API (旧レンダラ用、Phase 4 で削除予定) =================

    /** @deprecated Remaster 独自。property.railModel を返す。 */
    @Deprecated
    public String getRailDefinitionId() {
        return this.getProperty().railModel;
    }

    /**
     * 本家 TileEntityLargeRailCore.getResourceState。
     * レールのスクリプトは shouldRenderObject の中でこれを呼び、
     * レールの名前 (ResourceName) や設定を見て「このオブジェクトを描くか」を決める。
     */
    public ResourceStateCompat getResourceState() {
        return new ResourceStateCompat(this);
    }

    /** func_174877_v = getPos (1.12 の SRG 名)。パックのスクリプトが呼ぶ。 */
    public net.minecraft.core.BlockPos func_174877_v() {
        return this.getBlockPos();
    }

    /** 本家 ResourceStateRail 相当 (スクリプトが触る範囲だけ)。 */
    public static final class ResourceStateCompat {
        private final TileEntityLargeRailCore rail;

        public ResourceStateCompat(TileEntityLargeRailCore rail) {
            this.rail = rail;
        }

        /** レールのモデル名 ("1067mm_PC" 等)。 */
        public String getResourceName() {
            return this.rail == null ? "" : this.rail.getRailDefinitionId();
        }

        public ModelSetRailCompat getResourceSet() {
            return new ModelSetRailCompat(this.rail);
        }

        public jp.ngt.rtm.modelpack.modelset.DataMapCompat getDataMap() {
            return new jp.ngt.rtm.modelpack.modelset.DataMapCompat();
        }
    }

    /** 本家 ModelSetRail 相当。スクリプトは getConfig から幅や名前を読む。 */
    public static final class ModelSetRailCompat {
        private final TileEntityLargeRailCore rail;

        public ModelSetRailCompat(TileEntityLargeRailCore rail) {
            this.rail = rail;
        }

        public RailConfigCompat getConfig() {
            return new RailConfigCompat(this.rail);
        }

        public boolean isDummy() {
            return this.rail == null;
        }
    }

    /** 本家 RailConfig 相当。 */
    public static final class RailConfigCompat {
        private final TileEntityLargeRailCore rail;

        public RailConfigCompat(TileEntityLargeRailCore rail) {
            this.rail = rail;
            this.railName = rail == null ? "" : rail.getRailDefinitionId();
        }

        /** 本家 RailConfig.railName (スクリプトはフィールド参照もする)。 */
        public final String railName;

        public String getName() {
            return this.railName;
        }

        /** 道床の幅。 */
        public int getBallastWidth() {
            com.portofino.realtrainmodunofficial.rail.RailDefinition def =
                    com.portofino.realtrainmodunofficial.rail.RailRegistry.getById(this.railName);
            return def == null ? 3 : Math.max(1, def.getBallastWidth());
        }
    }

    /** @deprecated Remaster 独自。 */
    @Deprecated
    public AABB getCachedRenderBounds() {
        return this.getRenderBoundingBox();
    }

    /** @deprecated Remaster 独自。分岐の Point 配列 (非分岐は null)。 */
    @Deprecated
    public jp.ngt.rtm.rail.util.Point[] getSwitchPoints() {
        return null;
    }

    /** @deprecated Remaster 独自。開通中セグメント index。 */
    @Deprecated
    public int getActiveSegmentIndex() {
        return 0;
    }

    /** @deprecated Remaster 独自。 */
    @Deprecated
    public int getPreviousSegmentIndex() {
        return 0;
    }

    /** @deprecated Remaster 独自。切替アニメーション進行度 0-1。 */
    @Deprecated
    public float getSwitchProgress(float partialTick) {
        return 1.0F;
    }
}
