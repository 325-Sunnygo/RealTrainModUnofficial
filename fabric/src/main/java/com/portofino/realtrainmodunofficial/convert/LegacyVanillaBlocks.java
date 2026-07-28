package com.portofino.realtrainmodunofficial.convert;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * 旧 1.7.10 の MOD ブロックを、1.21 のバニラブロックに読み替える。
 *
 * <p>対象は <b>UpToDateMod (yuma140902)</b>。1.7.10 に「新しいバージョンのバニラブロック」を
 * 足す MOD なので、素直に本来のバニラブロックへ戻せる。
 * (深層岩・磨かれた花崗岩・コンクリート・海のランタン・階段/ハーフ/塀 など)
 *
 * <p>Minecraft 自身の変換 (DataFixer) は MOD のブロックを知らないので<b>空気にしてしまう</b>。
 * そこで変換時に「どこに何があったか」を記録しておき、ワールドを開いたときに
 * バニラブロックとして置き直す。
 *
 * <p>向き (階段・ハーフ・原木の軸) は 1.7.10 のメタデータから復元する。
 * メタの意味はバニラと同じなので、そのまま解釈できる。
 */
public final class LegacyVanillaBlocks {

    /** 変換対象の MOD の名前空間。 */
    public static final String MOD_ID = "uptodatemod";

    private static final String[] COLORS = {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    };

    /** 木の種類 (メタ順は 1.7.10 のバニラと同じ)。 */
    private static final String[] WOODS = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};

    /** 登録名 → (メタ → バニラのブロック id)。 */
    private static final Map<String, IntFunction<String>> MAP = new HashMap<>();

    private static void put(String name, String vanilla) {
        MAP.put(name, meta -> vanilla);
    }

    private static void put(String name, IntFunction<String> byMeta) {
        MAP.put(name, byMeta);
    }

    static {
        // ---- 石の変種 (1 ブロック + メタ) ----
        put("stone", meta -> switch (meta & 7) {
            case 1 -> "granite";
            case 2 -> "polished_granite";
            case 3 -> "diorite";
            case 4 -> "polished_diorite";
            case 5 -> "andesite";
            case 6 -> "polished_andesite";
            default -> "stone";
        });
        put("smooth_stone", "smooth_stone");

        // ---- 深層岩 ----
        put("deepslate", "deepslate");
        put("cobbled_deepslate", "cobbled_deepslate");
        put("deepslate_bricks", "deepslate_bricks");
        put("deepslate_bticks", "deepslate_bricks");   //MOD 側の綴り間違いにも対応
        put("deepslate_tiles", "deepslate_tiles");
        put("polished_deepslate", "polished_deepslate");
        put("deepslate_coal_ore", "deepslate_coal_ore");
        put("deepslate_iron_ore", "deepslate_iron_ore");
        put("deepslate_gold_ore", "deepslate_gold_ore");
        put("deepslate_diamond_ore", "deepslate_diamond_ore");
        put("deepslate_emerald_ore", "deepslate_emerald_ore");
        put("deepslate_lapis_ore", "deepslate_lapis_ore");
        put("deepslate_redstone_ore", "deepslate_redstone_ore");
        put("deepslate_redstone_ore_lit", "deepslate_redstone_ore");

        // ---- プリズマリン / 海 ----
        put("prismarine_block", "prismarine");
        put("prismarine_brick", "prismarine_bricks");
        put("prismarine_bricks", "prismarine_bricks");
        put("dark_prismarine_block", "dark_prismarine");
        put("sea_lantern", "sea_lantern");
        put("sponge", meta -> (meta & 1) == 1 ? "wet_sponge" : "sponge");

        // ---- ネザー / エンド ----
        put("magma_block", "magma_block");
        put("nether_wart_block", "nether_wart_block");
        put("red_nether_bricks", "red_nether_bricks");
        put("end_stone_bricks", "end_stone_bricks");
        put("purpur_block", "purpur_block");
        put("purpur_pillar", "purpur_pillar");
        put("bone_block", "bone_block");

        // ---- 砂岩・クォーツ ----
        put("smooth_sandstone", "smooth_sandstone");
        put("smooth_red_sandstone", "smooth_red_sandstone");
        put("cut_sandstone", "cut_sandstone");
        put("cut_red_sandstone", "cut_red_sandstone");
        put("red_sandstone", "red_sandstone");
        put("smooth_quartz", "smooth_quartz");

        // ---- 土・道 ----
        put("coarse_dirt", "coarse_dirt");
        put("grass_path", "dirt_path");   //1.17 で改名

        // ---- 色つきブロック ----
        put("concrete", meta -> COLORS[meta & 15] + "_concrete");
        put("concrete_powder", meta -> COLORS[meta & 15] + "_concrete_powder");
        for (String c : COLORS) {
            put("glazed_terracotta_" + c, c + "_glazed_terracotta");
        }

        // ---- 木 ----
        //「全面樹皮」の原木 (1.13 の wood)
        put("wood", meta -> WOODS[Math.min(meta & 7, WOODS.length - 1)] + "_wood");
        for (String w : WOODS) {
            put("planks_" + planksKey(w), w + "_planks");
            put("fence_" + planksKey(w), w + "_fence");
            put("fence_gate_" + planksKey(w), w + "_fence_gate");
            put("door_" + planksKey(w), w + "_door");
            put("trap_door_" + planksKey(w), w + "_trapdoor");
            put("button_" + planksKey(w), w + "_button");
            put("pressure_plate_" + planksKey(w), w + "_pressure_plate");
            //MOD は stripped_log_oak / stripped_oak_log の両方の名前を持つ
            put("stripped_log_" + planksKey(w), "stripped_" + w + "_log");
            put("stripped_" + w + "_log", "stripped_" + w + "_log");
        }
        put("trap_door_iron", "iron_trapdoor");

        // ---- その他 ----
        put("lantern", "lantern");
        put("barrel", "barrel");
        put("sweet_berry_bush", "sweet_berry_bush");

        // ---- 階段 / ハーフ / 塀 ----
        //MOD の登録名は "stairs_<素材>" / "slab_<素材>" / "wall_<素材>"。
        //バニラは "<素材>_stairs" のように順番が逆なので入れ替える。
        for (String m : new String[]{
                "granite", "polished_granite", "diorite", "polished_diorite", "andesite", "polished_andesite",
                "stone", "mossy_cobblestone", "mossy_stone_bricks", "end_stone_bricks",
                "red_sandstone", "smooth_sandstone", "cut_sandstone", "smooth_red_sandstone",
                "smooth_quartz", "purpur", "prismarine", "prismarine_bricks", "dark_prismarine",
                "red_nether_bricks", "cobbled_deepslate", "polished_deepslate",
                "deepslate_bricks", "deepslate_tiles"}) {
            put("stairs_" + m, m + "_stairs");
            put("slab_" + m, m + "_slab");
            put("wall_" + m, m + "_wall");
        }
        //綴り違い / 別名
        put("slab_dark_prismairne", "dark_prismarine_slab");
        put("wall_prismarine_brick", "prismarine_brick_wall");
        put("wall_bricks", "brick_wall");
        put("wall_nether_bricks", "nether_brick_wall");
        put("wall_stone_bricks", "stone_brick_wall");
        put("wall_sandstone", "sandstone_wall");
        put("polished_granite_slab_side", "polished_granite_slab");
        put("polished_diorite_slab_side", "polished_diorite_slab");
        put("polished_andesite_slab_side", "polished_andesite_slab");
        put("purpur_slab", "purpur_slab");
        put("purpur_stairs", "purpur_stairs");
    }

    /** MOD の登録名は fence_dark_oak / planks_big_oak のように木の名前が一部違う。 */
    private static String planksKey(String wood) {
        return switch (wood) {
            case "dark_oak" -> "dark_oak";
            default -> wood;
        };
    }

    private LegacyVanillaBlocks() {
    }

    /** その名前を変換できるか (名前空間は除いた登録名)。 */
    public static boolean canMap(String name) {
        return MAP.containsKey(normalize(name));
    }

    private static String normalize(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        int colon = n.indexOf(':');
        return colon >= 0 ? n.substring(colon + 1) : n;
    }

    /**
     * 旧 MOD ブロック + メタ → 1.21 のブロック状態。変換できなければ null。
     */
    public static BlockState toVanilla(String name, int meta) {
        IntFunction<String> mapper = MAP.get(normalize(name));
        if (mapper == null) {
            return null;
        }
        String id = mapper.apply(meta);
        Block block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.withDefaultNamespace(id)).orElse(null);
        if (block == null) {
            RealTrainModUnofficial.LOGGER.warn("[convert] バニラに minecraft:{} がありません ({})", id, name);
            return null;
        }
        return applyMeta(block.defaultBlockState(), meta);
    }

    /**
     * 1.7.10 のメタデータから向きを復元する。メタの意味はバニラと同じ。
     * <ul>
     *   <li>階段: 下位 2bit = 向き、4 のビット = 上下反転</li>
     *   <li>ハーフ: 8 のビット = 上付き</li>
     *   <li>原木: 上位 2bit = 軸</li>
     * </ul>
     */
    private static BlockState applyMeta(BlockState state, int meta) {
        //階段
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                && state.hasProperty(BlockStateProperties.HALF)) {
            Direction facing = switch (meta & 3) {
                case 0 -> Direction.EAST;
                case 1 -> Direction.WEST;
                case 2 -> Direction.SOUTH;
                default -> Direction.NORTH;
            };
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                    .setValue(BlockStateProperties.HALF, (meta & 4) != 0 ? Half.TOP : Half.BOTTOM);
        }
        //ハーフブロック
        if (state.hasProperty(BlockStateProperties.SLAB_TYPE)) {
            state = state.setValue(BlockStateProperties.SLAB_TYPE,
                    (meta & 8) != 0 ? SlabType.TOP : SlabType.BOTTOM);
        }
        //原木・柱 (軸)
        if (state.hasProperty(BlockStateProperties.AXIS)) {
            Direction.Axis axis = switch ((meta >> 2) & 3) {
                case 1 -> Direction.Axis.X;
                case 2 -> Direction.Axis.Z;
                default -> Direction.Axis.Y;
            };
            state = state.setValue(BlockStateProperties.AXIS, axis);
        }
        return state;
    }
}
