package jp.ngt.ngtlib.block;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * 1.7.10 のブロック名 + メタ を 1.21 の BlockState に変換する共通処理。
 * 本家 .ngto (NGTObject) の IdList パレットと、NGTO Builder のブロック解決から使う。
 *
 * <p>1.7.10 は「色違い・種類違い = 同じブロックのメタ違い」だったので、
 * 色メタ・木種メタ・階段メタをここで 1.21 の別ブロック/状態へ読み替える。
 */
public final class LegacyBlockStates {
    private LegacyBlockStates() {
    }

    private static final Map<String, BlockState> CACHE = new HashMap<>();

    public static BlockState toState(String name, int meta) {
        if (name == null || name.isBlank()) {
            return Blocks.AIR.defaultBlockState();
        }
        String key = name + "#" + meta;
        BlockState cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        BlockState state = resolve(name, meta);
        CACHE.put(key, state);
        return state;
    }

    private static BlockState resolve(String rawName, int meta) {
        String name = rawName.contains(":") ? rawName.substring(rawName.indexOf(':') + 1) : rawName;
        name = name.toLowerCase(Locale.ROOT);
        // ★空気を先に返す。byName は「AIR が返ってきたら未知」と見なすので、
        // ここを通さないとパレットの空気が石になり、車体が中身の詰まった塊になる。
        if (name.equals("air") || name.equals("cave_air") || name.equals("void_air")) {
            return Blocks.AIR.defaultBlockState();
        }
        // 1.7.10 の植物系 (NGTO Builder2 の Plants ブラシ .ngto が使う)
        BlockState plant = switch (name) {
            case "tallgrass" -> byNameOr((meta & 15) == 2 ? "fern" : "short_grass", "fern");
            case "double_plant" -> switch (meta & 7) {
                case 0 -> byNameOr("sunflower", "short_grass");
                case 1 -> byNameOr("lilac", "short_grass");
                case 3 -> byNameOr("large_fern", "fern");
                case 4 -> byNameOr("rose_bush", "short_grass");
                case 5 -> byNameOr("paeonia", "short_grass");
                default -> byNameOr("tall_grass", "short_grass");
            };
            case "yellow_flower" -> byNameOr("dandelion", "short_grass");
            case "red_flower" -> byNameOr("poppy", "short_grass");
            case "deadbush" -> byNameOr("dead_bush", "short_grass");
            default -> null;
        };
        if (plant != null) {
            return plant;
        }
        DyeColor color = DyeColor.byId(meta & 15);
        BlockState colored = switch (name) {
            case "wool" -> byName(color.getName() + "_wool");
            case "carpet" -> byName(color.getName() + "_carpet");
            case "stained_glass" -> byName(color.getName() + "_stained_glass");
            case "stained_glass_pane" -> byName(color.getName() + "_stained_glass_pane");
            case "stained_hardened_clay" -> byName(color.getName() + "_terracotta");
            case "hardened_clay" -> byName("terracotta");
            case "concrete" -> byName(color.getName() + "_concrete");
            case "concrete_powder" -> byName(color.getName() + "_concrete_powder");
            default -> null;
        };
        if (colored != null) {
            return colored;
        }
        BlockState wood = switch (name) {
            case "planks" -> byName(woodType(meta) + "_planks");
            case "log" -> byName(woodType(meta & 3) + "_log");
            case "log2" -> byName(woodType(4 + (meta & 1)) + "_log");
            case "leaves" -> byName(woodType(meta & 3) + "_leaves");
            default -> null;
        };
        if (wood != null) {
            return wood;
        }
        BlockState direct = byName(name);
        if (direct == null) {
            direct = byName(RENAMED.getOrDefault(name, name));
        }
        if (direct == null) {
            return Blocks.AIR.defaultBlockState();
        }
        // 階段は 1.7.10 のメタが 向き(0-3) + 上下反転(4)
        if (direct.getBlock() instanceof StairBlock) {
            return applyStairMeta(direct, meta);
        }
        return direct;
    }

    /** 1.7.10 の階段メタ: 0=東 1=西 2=南 3=北、+4 で天地逆。 */
    private static BlockState applyStairMeta(BlockState state, int meta) {
        Direction facing = switch (meta & 3) {
            case 0 -> Direction.EAST;
            case 1 -> Direction.WEST;
            case 2 -> Direction.SOUTH;
            default -> Direction.NORTH;
        };
        BlockState out = state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
        return out.setValue(BlockStateProperties.HALF, (meta & 4) != 0 ? Half.TOP : Half.BOTTOM);
    }

    private static String woodType(int meta) {
        return switch (meta & 7) {
            case 1 -> "spruce";
            case 2 -> "birch";
            case 3 -> "jungle";
            case 4 -> "acacia";
            case 5 -> "dark_oak";
            default -> "oak";
        };
    }

    /** 1.7.10 → 1.21 で名前が変わったもの。 */
    private static final Map<String, String> RENAMED = Map.ofEntries(
        Map.entry("quartz_block", "quartz_block"),
        Map.entry("stone_slab", "smooth_stone_slab"),
        Map.entry("wooden_slab", "oak_slab"),
        Map.entry("fence", "oak_fence"),
        Map.entry("wooden_door", "oak_door"),
        Map.entry("trapdoor", "oak_trapdoor"),
        Map.entry("web", "cobweb"),
        Map.entry("snow_layer", "snow"),
        Map.entry("wooden_button", "oak_button"),
        Map.entry("wooden_pressure_plate", "oak_pressure_plate"),
        Map.entry("stone_stairs", "cobblestone_stairs"),
        Map.entry("oak_stairs", "oak_stairs"),
        Map.entry("lit_redstone_lamp", "redstone_lamp"),
        Map.entry("redstone_wire", "redstone_wire"),
        Map.entry("iron_bars", "iron_bars"),
        Map.entry("waterlily", "lily_pad"),
        Map.entry("melon_block", "melon"),
        Map.entry("lit_pumpkin", "jack_o_lantern"),
        Map.entry("mob_spawner", "spawner"),
        Map.entry("noteblock", "note_block"),
        Map.entry("piston_extension", "piston_head"),
        Map.entry("sea_lantern", "sea_lantern"),
        Map.entry("grass", "short_grass"),
        Map.entry("tallgrass", "short_grass"),
        Map.entry("double_plant", "tall_grass")
    );

    private static BlockState byNameOr(String primary, String fallback) {
        BlockState s = byName(primary);
        if (s != null) {
            return s;
        }
        s = byName(fallback);
        return s != null ? s : Blocks.AIR.defaultBlockState();
    }

    private static BlockState byName(String name) {
        ResourceLocation id = ResourceLocation.tryParse("minecraft:" + name);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            return null;
        }
        Block block = BuiltInRegistries.BLOCK.get(id);
        return block == Blocks.AIR ? null : block.defaultBlockState();
    }
}
