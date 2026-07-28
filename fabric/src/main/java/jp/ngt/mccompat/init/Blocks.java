package jp.ngt.mccompat.init;

import net.minecraft.world.level.block.Block;

/**
 * 1.7.10 net.minecraft.init.Blocks のスクリプト互換 (SRG フィールド名)。
 * SRB3 の underBlock (羊毛) 等が参照する主要ブロックのみ。
 */
public final class Blocks {
    private Blocks() {
    }

    /** air */
    public static final Block field_150350_a = net.minecraft.world.level.block.Blocks.AIR;
    /** stone */
    public static final Block field_150348_b = net.minecraft.world.level.block.Blocks.STONE;
    /** dirt */
    public static final Block field_150346_d = net.minecraft.world.level.block.Blocks.DIRT;
    /** gravel */
    public static final Block field_150351_n = net.minecraft.world.level.block.Blocks.GRAVEL;
    /** wool */
    public static final Block field_150325_L = net.minecraft.world.level.block.Blocks.WHITE_WOOL;
    /** glass */
    public static final Block field_150359_w = net.minecraft.world.level.block.Blocks.GLASS;
    /** glowstone */
    public static final Block field_150426_aN = net.minecraft.world.level.block.Blocks.GLOWSTONE;
    /** iron_block */
    public static final Block field_150339_S = net.minecraft.world.level.block.Blocks.IRON_BLOCK;
    /** stained_hardened_clay (白色ハードクレイ相当) */
    public static final Block field_150406_ce = net.minecraft.world.level.block.Blocks.WHITE_TERRACOTTA;
    /** hardened_clay (無着色テラコッタ、メタ無し) — 信号のブロック検知が向き決めに使う */
    public static final Block field_150405_ch = net.minecraft.world.level.block.Blocks.TERRACOTTA;
    /** glass_pane (無着色、メタ無し) */
    public static final Block field_150410_aZ = net.minecraft.world.level.block.Blocks.GLASS_PANE;

    // ---- レッドストーン出力系 (列車検知器のサーバースクリプトが置く) ----
    // 1.7.10 は「1 ブロック + メタで 16 色」だったので、色付きブロックは白色版を
    // 代表として置き、メタは WorldCompat.func_147465_d が色に読み替える。

    /** redstone_block */
    public static final Block field_150451_bX = net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK;
    /** stained_glass (メタ = 色) */
    public static final Block field_150399_cn = net.minecraft.world.level.block.Blocks.WHITE_STAINED_GLASS;
    /** stained_glass_pane (メタ = 色) */
    public static final Block field_150397_co = net.minecraft.world.level.block.Blocks.WHITE_STAINED_GLASS_PANE;
    /** carpet (メタ = 色) */
    public static final Block field_150404_cg = net.minecraft.world.level.block.Blocks.WHITE_CARPET;
    /** redstone_lamp (消灯) */
    public static final Block field_150379_bu = net.minecraft.world.level.block.Blocks.REDSTONE_LAMP;
    /** redstone_torch */
    public static final Block field_150429_aA = net.minecraft.world.level.block.Blocks.REDSTONE_TORCH;
    /** lever */
    public static final Block field_150442_at = net.minecraft.world.level.block.Blocks.LEVER;

    // ---- 1.7.10 メタ互換: 1.21 の色別ブロック → (白色版=基準ブロック, 色メタ 0-15) ----
    // 信号機のブロック検知スクリプト (searchBlockAndMeta) が色ガラス/羊毛/テラコッタのメタを
    // 灯火状態として読むが、1.21 は色ごとに別ブロックでメタが無い。そこで getBlock (func_147439_a) は
    // 色別ブロックを白色版へ正規化し、getMetadata (func_72805_g) は色番号(0-15)を返すことで
    // 1.7.10 の「1ブロック+メタ16色」を再現する。[[rtmu-api-sweep-complete]] の受け皿方式。
    private static final String[] COLORS_16 = {
        "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    };
    private static final java.util.Map<Block, Block> CANON = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<Block, Integer> META = new java.util.concurrent.ConcurrentHashMap<>();

    /** 色別ブロックを白色版(1.7.10 の基準ブロック)へ正規化。非色ブロックはそのまま返す。 */
    /**
     * func_149716_u = Block.hasTileEntity。
     * レシーバがバニラの Block でシムを挟めないため、スクリプト変換
     * (PackScriptSource.remapVanillaOnlyMethods) がここへ回してくる。
     */
    public static boolean func_149716_u(Object block) {
        if (block instanceof net.minecraft.world.level.block.EntityBlock) {
            return true;
        }
        if (block instanceof Block b) {
            return b.defaultBlockState().hasBlockEntity();
        }
        return false;
    }

    public static Block canonical(Block block) {
        if (block == null) return null;
        Block b = CANON.get(block);
        if (b == null) { decodeColor(block); b = CANON.get(block); }
        return b;
    }

    /** 色別ブロックの 1.7.10 相当メタ (0-15, DyeColor 順)。非色ブロックは 0。 */
    public static int colorMeta(Block block) {
        if (block == null) return 0;
        Integer m = META.get(block);
        if (m == null) { decodeColor(block); m = META.get(block); }
        return m;
    }

    private static void decodeColor(Block block) {
        Block base = block;
        int meta = 0;
        try {
            String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath();
            for (int i = 0; i < COLORS_16.length; i++) {
                String prefix = COLORS_16[i] + "_";
                if (path.startsWith(prefix)) {
                    // 同じ系列の白色版が実在するときだけ色ファミリと判定 (orange_tulip 等は除外)。
                    Block white = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                        .getOptional(net.minecraft.resources.ResourceLocation.withDefaultNamespace(
                            "white_" + path.substring(prefix.length())))
                        .orElse(null);
                    if (white != null) { base = white; meta = i; }
                    break;
                }
            }
        } catch (Exception ignored) {
        }
        CANON.put(block, base);
        META.put(block, meta);
    }
}
