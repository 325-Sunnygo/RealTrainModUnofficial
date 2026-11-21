package jp.ngt.mccompat;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/** パックスクリプト互換: entity.field_70170_p (1.7.10 の World)… */
@SuppressWarnings("unused")
public class WorldCompat {
    public final Level level;
    public final ProviderCompat field_73011_w;
    public final MapStorageCompat field_72988_C;
    public static final class ProviderCompat {
        public final int field_76574_g;
        ProviderCompat(Level level) {field_76574_g=level.dimension().equals(Level.NETHER)?-1:level.dimension().equals(Level.END)?1:0;}
    }
    public static final class MapStorageCompat {
        private final net.minecraft.server.level.ServerLevel level;
        MapStorageCompat(net.minecraft.server.level.ServerLevel level) {this.level=level;}
        public jp.ngt.mccompat.world.MapGenStructureData func_75742_a(Class<?> type,String name) {return level.getDataStorage().get(jp.ngt.mccompat.world.MapGenStructureData.factory(name),name);}
        public void func_75745_a(String name,jp.ngt.mccompat.world.MapGenStructureData data) {level.getDataStorage().set(name,data);data.setDirty();}
    }

    public final java.util.List<jp.ngt.rtm.rail.TileEntityLargeRailCore> field_147482_g=new java.util.AbstractList<>() {
        public jp.ngt.rtm.rail.TileEntityLargeRailCore get(int index) {return jp.ngt.rtm.rail.TileEntityLargeRailCore.getLoadedCores(level).get(index);}
        public int size() {return jp.ngt.rtm.rail.TileEntityLargeRailCore.getLoadedCores(level).size();}
    };
    public boolean func_147437_c(int x,int y,int z) {return level.isEmptyBlock(new BlockPos(x,y,z));}
    public void func_147475_p(int x,int y,int z) {level.removeBlockEntity(new BlockPos(x,y,z));}
    public ChunkProviderCompat func_72863_F() {return new ChunkProviderCompat(level);}
    public static final class ChunkProviderCompat {
        private final Level world;
        ChunkProviderCompat(Level world) {this.world=world;}
        public boolean func_73149_a(int x,int z) {return world.hasChunk(x,z);}
        public ChunkCompat func_73154_d(int x,int z) {return new ChunkCompat(world.getChunk(x,z));}
    }
    public static final class ChunkCompat {
        public final java.util.Map<net.minecraft.core.BlockPos,net.minecraft.world.level.block.entity.BlockEntity> field_150816_i;
        ChunkCompat(net.minecraft.world.level.chunk.LevelChunk chunk) {field_150816_i=chunk.getBlockEntities();}
    }

    /** 1.7.10 SRG: isRemote */
    public final boolean field_72995_K;

    public WorldCompat(Level level) {
        this.level = level;
        this.field_72995_K = level.isClientSide;
        this.field_73011_w=new ProviderCompat(level);
        this.field_72988_C=level instanceof net.minecraft.server.level.ServerLevel server?new MapStorageCompat(server):null;
    }

    public boolean func_72899_e(int x,int y,int z) {return y>=level.getMinBuildHeight() && y<level.getMaxBuildHeight() && level.hasChunk(x>>4,z>>4);}
    public Level getLevel() {
        return this.level;
    }

    /** getCelestialAngle */
    public float func_72929_e(float partialTick) {
        return this.level.getTimeOfDay(partialTick);
    }

    public float getCelestialAngle(float partialTick) {
        return this.func_72929_e(partialTick);
    }

    /** 1.12 SRG: getLightFor(EnumSkyBlock, BlockPos) */
    public int func_175642_b(Object skyBlock, Object pos) {
        return this.getLight(skyBlock, pos);
    }

    /** 1.7.10 SRG: getSavedLightValue(EnumSkyBlock, x, y… */
    public int func_72972_b(Object skyBlock, int x, int y, int z) {
        return this.getLight(skyBlock, new BlockPos(x, y, z));
    }

    private int getLight(Object skyBlock, Object pos) {
        net.minecraft.world.level.LightLayer layer = skyBlock instanceof EnumSkyBlock esb
                ? esb.layer : net.minecraft.world.level.LightLayer.BLOCK;
        BlockPos bp = pos instanceof BlockPos b ? b : BlockPos.ZERO;
        return this.level.getBrightness(layer, bp);
    }

    /** getWorldTime */
    public long func_72820_D() {
        return this.level.getDayTime();
    }

    public long getWorldTime() {
        return this.level.getDayTime();
    }

    public long getTotalWorldTime() {
        return this.level.getGameTime();
    }

    /** func_82737_E = getTotalWorldTime。 */
    public long func_82737_E() {
        return this.level.getGameTime();
    }

    /** func_72839_b = getEntitiesWithinAABBExcludingEnti… */
    public java.util.List<net.minecraft.world.entity.Entity> func_72839_b(Object exclude, Object aabb) {
        net.minecraft.world.phys.AABB box = AxisAlignedBB.unwrap(aabb);
        if (box == null) {
            return java.util.List.of();
        }
        return this.level.getEntities(EntityCompatUtil.unwrapEntity(exclude), box);
    }


    /** getEntityByID。プレイヤーは PlayerCompat ラッパーで返す (SRB3 等… */
    public Object func_73045_a(int id) {
        return wrapEntity(this.level.getEntity(id));
    }

    /** 文字列 ID も受ける (dataMap.getString の値をそのまま渡すスクリプト用) */
    public Object func_73045_a(Object id) {
        if (id == null) {
            return null;
        }
        try {
            int i = id instanceof Number n ? n.intValue() : Integer.parseInt(id.toString().trim());
            return wrapEntity(this.level.getEntity(i));
        } catch (Exception e) {
            return null;
        }
    }

    public net.minecraft.world.entity.Entity getEntityByID(int id) {
        return this.level.getEntity(id);
    }

    private static Object wrapEntity(net.minecraft.world.entity.Entity e) {
        if (e instanceof net.minecraft.world.entity.player.Player p) {
            return PlayerCompat.of(p);
        }
        return e;
    }

    // ===== 1.7.10 ワールド探索/チャンク SRG (NGTO Builder2 の Sno…

    /** func_72901_a = rayTraceBlocks(start, end, flag)。 */
    public MovingObjectPosition func_72901_a(Object start, Object end, boolean flag) {
        net.minecraft.world.phys.Vec3 s = toMcVec(start);
        net.minecraft.world.phys.Vec3 e = toMcVec(end);
        if (s == null || e == null) {
            return null;
        }
        // ★Entity 版 ctor は CollisionContext.of(entity) を通り、…
        net.minecraft.world.level.ClipContext ctx = new net.minecraft.world.level.ClipContext(
                s, e, net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                net.minecraft.world.phys.shapes.CollisionContext.empty());
        return MovingObjectPosition.of(this.level.clip(ctx));
    }

    private static net.minecraft.world.phys.Vec3 toMcVec(Object v) {
        if (v instanceof jp.ngt.ngtlib.math.Vec3 nv) {
            return new net.minecraft.world.phys.Vec3(nv.getX(), nv.getY(), nv.getZ());
        }
        if (v instanceof net.minecraft.world.phys.Vec3 mc) {
            return mc;
        }
        if (v instanceof MovingObjectPosition.Vec3Compat c) {
            return new net.minecraft.world.phys.Vec3(c.field_72450_a, c.field_72448_b, c.field_72449_c);
        }
        return null;
    }

    /** func_72964_e = getChunkFromChunkCoords。 */
    public ChunkCompat func_72964_e(int chunkX, int chunkZ) {
        net.minecraft.world.level.chunk.LevelChunk chunk = this.level.getChunk(chunkX, chunkZ);
        return chunk != null ? new ChunkCompat(chunk) : null;
    }

    /** 1.7.10 field_73010_i = playerEntities。 */
    public final java.util.List<PlayerCompat> field_73010_i = new java.util.AbstractList<PlayerCompat>() {
        @Override
        public PlayerCompat get(int i) {
            return PlayerCompat.of(level.players().get(i));
        }

        @Override
        public int size() {
            return level.players().size();
        }
    };

    // ===== 1.7.10 ブロック操作 SRG (SRB3/NGTO Builder のサーバース…

    /** func_147465_d = setBlock(x,y,z, Block, meta, flag) */
    public boolean func_147465_d(double x, double y, double z, Object block, int meta, int flag) {
        net.minecraft.world.level.block.Block b = asBlock(block);
        if (b == null) {
            return false;
        }
        BlockPos pos = new BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        boolean placed = this.level.setBlock(pos, withMeta(b, meta), flag);
        if (placed) {
            applyLegacyMeta(pos, meta);
        }
        return placed;
    }

    /** 1.7.10 のブロックメタのうち、1.21 でブロック状態に落とせないものをタイルエンティティへ… */
    private void applyLegacyMeta(BlockPos pos, int meta) {
        if (meta < 0 || meta > 5) {
            return;
        }
        if (this.level.getBlockEntity(pos)
                instanceof com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity be) {
            be.setMountFace(meta);
        }
    }

    /** 1.7.10 の「1 ブロック + メタで 16 色」を 1.21 の色別ブロックに読み替える。 */
    private static net.minecraft.world.level.block.state.BlockState withMeta(
            net.minecraft.world.level.block.Block block, int meta) {
        if(block instanceof jp.ngt.rtm.rail.BlockLargeRailCore && block.defaultBlockState().hasProperty(jp.ngt.rtm.rail.BlockLargeRailCore.SECTIONED))return block.defaultBlockState().setValue(jp.ngt.rtm.rail.BlockLargeRailCore.SECTIONED,meta==1);
        return jp.ngt.mccompat.init.Blocks.stateFor(block, meta);
    }

    /** func_147468_f = setBlockToAir */
    public boolean func_147468_f(double x, double y, double z) {
        BlockPos pos = new BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        return this.level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
    }

    /** func_147438_o = getTileEntity(x,y,z) */
    public Object func_147438_o(double x, double y, double z) {
        return wrapBlockEntity(this.level.getBlockEntity(new BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z))));
    }

    /** コマンドブロックだけラッパーに包む。 検知器のスクリプトは block instanceof Ti… */
    private static Object wrapBlockEntity(net.minecraft.world.level.block.entity.BlockEntity be) {
        if (be instanceof net.minecraft.world.level.block.entity.CommandBlockEntity cb) {
            return new jp.ngt.mccompat.tileentity.TileEntityCommandBlock(cb);
        }
        return be;
    }

    /** func_175625_s = getTileEntity(BlockPos) (1.12)。 */
    public net.minecraft.world.level.block.entity.BlockEntity func_175625_s(BlockPos pos) {
        return pos != null ? this.level.getBlockEntity(pos) : null;
    }

    /** func_147439_a = getBlock(x,y,z)。 */
    public net.minecraft.world.level.block.Block func_147439_a(double x, double y, double z) {
        BlockPos pos = new BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        return jp.ngt.mccompat.init.Blocks.canonical(this.level.getBlockState(pos).getBlock());
    }

    /** func_72805_g = getBlockMetadata。 */
    public int func_72805_g(double x, double y, double z) {
        BlockPos pos = new BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        // RTM 設置物のメタは取付面 (1.7.10 の実メタと同じ)。
        if (this.level.getBlockEntity(pos)
                instanceof com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity be
                && be.getMountFace() >= 0) {
            return be.getMountFace();
        }
        return jp.ngt.mccompat.init.Blocks.colorMeta(this.level.getBlockState(pos).getBlock());
    }

    /** func_147471_g = markBlockForUpdate */
    public void func_147471_g(double x, double y, double z) {
        BlockPos pos = new BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        net.minecraft.world.level.block.state.BlockState st = this.level.getBlockState(pos);
        this.level.sendBlockUpdated(pos, st, st, 3);
    }

    /** func_180495_p = getBlockState。 */
    public BlockStateCompat func_180495_p(BlockPos pos) {
        return pos != null ? new BlockStateCompat(this.level.getBlockState(pos)) : null;
    }

    /** 座標 3 個で呼ぶスクリプト向け。 */
    public BlockStateCompat func_180495_p(double x, double y, double z) {
        return new BlockStateCompat(this.level.getBlockState(
                new BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z))));
    }

    private static net.minecraft.world.level.block.Block asBlock(Object block) {
        if (block instanceof net.minecraft.world.level.block.Block b) {
            return b;
        }
        return null;
    }

    /** isRemote 相当のアクセサ */
    public boolean isRemote() {
        return this.level.isClientSide;
    }

    /** 本家 World.func_175688_a (spawnParticle, 1.8+): Enu… */
    public void func_175688_a(Object particleType, double x, double y, double z,
                             double vx, double vy, double vz, int... params) {
        net.minecraft.core.particles.ParticleOptions options = null;
        if (particleType instanceof jp.ngt.mccompat.EnumParticleTypes t) {
            options = t.particle;
        } else if (particleType instanceof net.minecraft.core.particles.ParticleOptions p) {
            options = p;
        }
        if (options != null && this.level != null && this.level.isClientSide) {
            this.level.addParticle(options, x, y, z, vx, vy, vz);
        }
    }

    /** 本家 World.func_72869_a (spawnParticle, 1.7.10): 粒子… */
    public void func_72869_a(String name, double x, double y, double z,
                            double vx, double vy, double vz) {
        if (this.level != null && this.level.isClientSide) {
            this.level.addParticle(jp.ngt.mccompat.EnumParticleTypes.particleByLegacyName(name),
                    x, y, z, vx, vy, vz);
        }
    }

    /** rand 相当 */
    public final java.util.Random rand = new java.util.Random();
}
