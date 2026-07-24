package jp.ngt.ngtlib.world;

import jp.ngt.ngtlib.block.BlockSet;
import jp.ngt.ngtlib.block.NGTObject;

/**
 * 本家 jp.ngt.ngtlib.world.NGTWorld の移植。
 *
 * <p>「NGTObject (ミニチュア) を仮想ワールドとして読む」ためのラッパ。
 * NGTO Builder の Prop ツールのプレビュー描画 (render_Prop.js の renderNGTO) が
 * {@code new NGTWorld(NGTUtil.getClientWorld(), ngto)} を作り、ブロック照会を
 * このクラス越しに行う。ブロック系は<b>実ワールドではなく NGTObject</b> を見る。
 */
@SuppressWarnings("unused")
public class NGTWorld {
    public final Object baseWorld;
    public final Object ngtObject;
    /** 本家フィールド名。スクリプトは {@code ngtWorld.world} / {@code .blockObject} と書く。 */
    public final Object world;
    public final NGTObject blockObject;
    public final int posX, posY, posZ;
    /** 本家: TE を一度読み込んだか (NGTRenderer が見る)。 */
    public boolean tileEntityLoaded;

    private final java.util.List<Object> tileEntityList = new java.util.ArrayList<>();
    private final java.util.List<Object> entityList = new java.util.ArrayList<>();

    public NGTWorld(Object baseWorld, Object ngtObject) {
        this(baseWorld, ngtObject, 0, 0, 0);
    }

    public NGTWorld(Object baseWorld, Object ngtObject, int x, int y, int z) {
        this.baseWorld = baseWorld;
        this.ngtObject = ngtObject;
        this.world = baseWorld;
        this.blockObject = ngtObject instanceof NGTObject o ? o : null;
        this.posX = x;
        this.posY = y;
        this.posZ = z;
    }

    /** ミニチュア内のブロック。範囲外は null。 */
    public BlockSet getBlockSet(int x, int y, int z) {
        return this.blockObject != null ? this.blockObject.getBlockSet(x, y, z) : null;
    }

    public net.minecraft.world.level.block.Block getBlock(int x, int y, int z) {
        BlockSet set = this.getBlockSet(x, y, z);
        return set != null ? set.block : net.minecraft.world.level.block.Blocks.AIR;
    }

    public net.minecraft.world.level.block.state.BlockState getBlockState(int x, int y, int z) {
        BlockSet set = this.getBlockSet(x, y, z);
        return set != null ? set.getState()
                : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
    }

    public int getBlockMetadata(int x, int y, int z) {
        BlockSet set = this.getBlockSet(x, y, z);
        return set != null ? set.metadata : 0;
    }

    public boolean isAirBlock(int x, int y, int z) {
        BlockSet set = this.getBlockSet(x, y, z);
        return set == null || set.getState().isAir();
    }

    /** ミニチュアの範囲内か。 */
    public boolean blockExists(int x, int y, int z) {
        return this.blockObject != null
                && x >= 0 && y >= 0 && z >= 0
                && x < this.blockObject.xSize && y < this.blockObject.ySize && z < this.blockObject.zSize;
    }

    public boolean chunkExists(int x, int z) {
        return this.blockObject != null;
    }

    public Object getChunkFromChunkCoords(int x, int z) {
        return null;
    }

    /** ミニチュア内の TE。RTMU は BlockSet の NBT のみ保持するため実体は持たない。 */
    public Object getTileEntity(int x, int y, int z) {
        return null;
    }

    public java.util.List<Object> getTileEntityList() {
        return this.tileEntityList;
    }

    public void setTileEntityList(java.util.List<Object> list) {
        this.tileEntityList.clear();
        if (list != null) {
            this.tileEntityList.addAll(list);
        }
    }

    public java.util.List<Object> getEntityList() {
        return this.entityList;
    }

    public Object getEntityByID(int id) {
        return null;
    }

    public java.util.List<Object> getEntitiesWithinAABBExcludingEntity(Object entity, Object aabb) {
        return java.util.List.of();
    }

    public java.util.List<Object> getEntitiesWithinAABBExcludingEntity(Object entity, Object aabb, Object selector) {
        return java.util.List.of();
    }

    public java.util.List<Object> selectEntitiesWithinAABB(Object clazz, Object aabb, Object selector) {
        return java.util.List.of();
    }

    public boolean spawnEntityInWorld(Object entity) {
        if (entity != null) {
            this.entityList.add(entity);
        }
        return true;
    }

    /** ミニチュアはプレビュー専用なので書き込みは無視する。 */
    public boolean setBlock(int x, int y, int z, Object block, int meta, int flag) {
        return false;
    }

    public boolean setBlockMetadataWithNotify(int x, int y, int z, int meta, int flag) {
        return false;
    }

    /** ミニチュアは一律に明るく描く (実ワールドの光を持ち込まない)。 */
    public float getLightBrightness(int x, int y, int z) {
        return 1.0F;
    }

    public int getBlockLightValue(int x, int y, int z) {
        return 15;
    }

    public int getLightBrightnessForSkyBlocks(int x, int y, int z, int defaultValue) {
        return 15 << 20 | 15 << 4;
    }

    public int getSkyBlockTypeBrightness(Object skyBlock, int x, int y, int z) {
        return 15;
    }

    public boolean canBlockSeeTheSky(int x, int y, int z) {
        return this.blockObject == null || y >= this.blockObject.ySize - 1;
    }

    public Object getBiomeGenForCoords(int x, int z) {
        return null;
    }

    public int getHeight() {
        return this.blockObject != null ? this.blockObject.ySize : 256;
    }

    public boolean extendedLevelsInChunkCache() {
        return false;
    }

    public boolean isSideSolid(int x, int y, int z, Object side, boolean fallback) {
        BlockSet set = this.getBlockSet(x, y, z);
        return set != null && !set.getState().isAir();
    }

    public void markTileEntityChunkModified(int x, int y, int z, Object tile) {
    }
}
