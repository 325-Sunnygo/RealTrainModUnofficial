package jp.ngt.mccompat;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * パックスクリプト互換: entity.field_70170_p (1.7.10 の World) のラッパ。
 * スクリプトが呼ぶ SRG メソッド/フィールドを 1.21 Level に委譲する。
 */
@SuppressWarnings("unused")
public class WorldCompat {
    public final Level level;
    /**
     * 1.7.10 SRG: isRemote
     */
    public final boolean field_72995_K;

    public WorldCompat(Level level) {
        this.level = level;
        this.field_72995_K = level.isClientSide;
    }

    public Level getLevel() {
        return this.level;
    }

    /**
     * getCelestialAngle
     */
    public float func_72929_e(float partialTick) {
        return this.level.getTimeOfDay(partialTick);
    }

    public float getCelestialAngle(float partialTick) {
        return this.func_72929_e(partialTick);
    }

    /**
     * 1.12 SRG: getLightFor(EnumSkyBlock, BlockPos)
     */
    public int func_175642_b(Object skyBlock, Object pos) {
        return this.getLight(skyBlock, pos);
    }

    /**
     * 1.7.10 SRG: getSavedLightValue(EnumSkyBlock, x, y, z)
     */
    public int func_72972_b(Object skyBlock, int x, int y, int z) {
        return this.getLight(skyBlock, new BlockPos(x, y, z));
    }

    private int getLight(Object skyBlock, Object pos) {
        net.minecraft.world.level.LightLayer layer = skyBlock instanceof EnumSkyBlock esb
                ? esb.layer : net.minecraft.world.level.LightLayer.BLOCK;
        BlockPos bp = pos instanceof BlockPos b ? b : BlockPos.ZERO;
        return this.level.getBrightness(layer, bp);
    }

    /**
     * getWorldTime
     */
    public long func_72820_D() {
        return this.level.getDayTime();
    }

    public long getWorldTime() {
        return this.level.getDayTime();
    }

    public long getTotalWorldTime() {
        return this.level.getGameTime();
    }

    /**
     * func_82737_E = getTotalWorldTime。
     * <p>E259 の ATS プラグイン (lib_ATS_*.js) が毎 tick これを読む。無いと
     * 全 ATS スクリプトが TypeError で死ぬ。
     */
    public long func_82737_E() {
        return this.level.getGameTime();
    }

    /**
     * func_72839_b = getEntitiesWithinAABBExcludingEntity(entity, aabb)。
     * <p>列車検知器のサーバースクリプトが、自分の当たり判定に触れている列車を探すのに使う。
     * スクリプトは戻り値に .forEach(function(e){...}) を呼ぶ (Java の List で通る)。
     */
    public java.util.List<net.minecraft.world.entity.Entity> func_72839_b(Object exclude, Object aabb) {
        net.minecraft.world.phys.AABB box = AxisAlignedBB.unwrap(aabb);
        if (box == null) {
            return java.util.List.of();
        }
        return this.level.getEntities(EntityCompatUtil.unwrapEntity(exclude), box);
    }

    /**
     * getEntityByID。プレイヤーは PlayerCompat ラッパーで返す
     * (SRB3 等が MCWrapperClient.getPlayer() の戻り値と === 比較するため)。
     */
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

    // ===== 1.7.10 ブロック操作 SRG (SRB3/NGTO Builder のサーバースクリプトが使用) =====

    /** func_147465_d = setBlock(x,y,z, Block, meta, flag) */
    public boolean func_147465_d(double x, double y, double z, Object block, int meta, int flag) {
        net.minecraft.world.level.block.Block b = asBlock(block);
        if (b == null) {
            return false;
        }
        BlockPos pos = new BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        return this.level.setBlock(pos, withMeta(b, meta), flag);
    }

    private static final String[] COLORS_16 = {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    };

    /**
     * 1.7.10 の「1 ブロック + メタで 16 色」を 1.21 の色別ブロックに読み替える。
     *
     * <p>スクリプトは {@code setBlock(x,y,z, Blocks.field_150399_cn, 14, 3)} のように
     * 「色付きガラス + メタ 14 (赤)」と書く。1.21 では色ごとに別ブロックなので、
     * 白色版の登録名 (white_stained_glass) の "white" をメタの色に差し替える。
     * 羊毛・コンクリート・テラコッタ・カーペット等すべて同じ規則で通る。
     */
    private static net.minecraft.world.level.block.state.BlockState withMeta(
            net.minecraft.world.level.block.Block block, int meta) {
        if (meta <= 0) {
            return block.defaultBlockState();
        }
        String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath();
        if (path.startsWith("white_")) {
            String colored = COLORS_16[meta & 15] + path.substring("white".length());
            net.minecraft.world.level.block.Block b2 = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getOptional(net.minecraft.resources.ResourceLocation.withDefaultNamespace(colored))
                    .orElse(null);
            if (b2 != null) {
                return b2.defaultBlockState();
            }
        }
        return block.defaultBlockState();
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

    /**
     * コマンドブロックだけラッパーに包む。
     *
     * <p>検知器のスクリプトは {@code block instanceof TileEntityCommandBlock} で探すが、
     * 1.21 の CommandBlockEntity には 1.7.10 のクラスを継承させられない。ここで包んで
     * instanceof を成立させる。それ以外のブロックエンティティ (レールのコア等) は
     * 素のまま返す — スクリプトが実クラスのメソッドを直接呼ぶため。
     */
    private static Object wrapBlockEntity(net.minecraft.world.level.block.entity.BlockEntity be) {
        if (be instanceof net.minecraft.world.level.block.entity.CommandBlockEntity cb) {
            return new jp.ngt.mccompat.tileentity.TileEntityCommandBlock(cb);
        }
        return be;
    }

    /** func_175625_s = getTileEntity(BlockPos) (1.12) */
    public net.minecraft.world.level.block.entity.BlockEntity func_175625_s(BlockPos pos) {
        return pos != null ? this.level.getBlockEntity(pos) : null;
    }

    /** func_147439_a = getBlock(x,y,z)。色別ブロックは白色版に正規化 (1.7.10 の base+meta 照合用)。 */
    public net.minecraft.world.level.block.Block func_147439_a(double x, double y, double z) {
        BlockPos pos = new BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        return jp.ngt.mccompat.init.Blocks.canonical(this.level.getBlockState(pos).getBlock());
    }

    /**
     * func_72805_g = getBlockMetadata。1.21 にメタは無いが、色ガラス/羊毛/テラコッタ等は
     * 色番号 (0-15, DyeColor 順) をメタとして返す。信号機のブロック検知がこのメタで灯火状態を読むため。
     * それ以外のブロックは 0。
     */
    public int func_72805_g(double x, double y, double z) {
        BlockPos pos = new BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        return jp.ngt.mccompat.init.Blocks.colorMeta(this.level.getBlockState(pos).getBlock());
    }

    /** func_147471_g = markBlockForUpdate */
    public void func_147471_g(double x, double y, double z) {
        BlockPos pos = new BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        net.minecraft.world.level.block.state.BlockState st = this.level.getBlockState(pos);
        this.level.sendBlockUpdated(pos, st, st, 3);
    }

    /** func_180495_p = getBlockState (1.12) */
    public net.minecraft.world.level.block.state.BlockState func_180495_p(BlockPos pos) {
        return pos != null ? this.level.getBlockState(pos) : null;
    }

    private static net.minecraft.world.level.block.Block asBlock(Object block) {
        if (block instanceof net.minecraft.world.level.block.Block b) {
            return b;
        }
        return null;
    }

    /**
     * isRemote 相当のアクセサ
     */
    public boolean isRemote() {
        return this.level.isClientSide;
    }

    /**
     * 本家 World.func_175688_a (spawnParticle, 1.8+): EnumParticleTypes を受ける粒子生成。
     * SL パックが蒸気/煙を出すのに使う ({@code field_70170_p.func_175688_a(EnumParticleTypes.X, ...)})。
     * 末尾の可変長 int (粒子パラメータ) は 1.21 では無視する。クライアント専用。
     */
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

    /**
     * 本家 World.func_72869_a (spawnParticle, 1.7.10): 粒子名 (文字列) を受ける旧経路。
     */
    public void func_72869_a(String name, double x, double y, double z,
                            double vx, double vy, double vz) {
        if (this.level != null && this.level.isClientSide) {
            this.level.addParticle(jp.ngt.mccompat.EnumParticleTypes.particleByLegacyName(name),
                    x, y, z, vx, vy, vz);
        }
    }

    /**
     * rand 相当
     */
    public final java.util.Random rand = new java.util.Random();
}
