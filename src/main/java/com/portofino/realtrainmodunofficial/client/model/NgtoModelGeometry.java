package com.portofino.realtrainmodunofficial.client.model;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * 本家の {@code .ngto} (NGTObject) 車両モデルを読む。
 *
 * <p>NGTO は<b>ポリゴンモデルではなくボクセル</b> (ブロックを並べた構造物) で、本家は
 * {@code NGTOModel} が「ブロックをそのまま描く」ことで車両にしている。RTMU は MQO と同じ
 * バッチ表現しか持たないので、ここでブロックの焼き済みモデル ({@link BakedModel}) から
 * 面を取り出してバッチ用の頂点配列へ変換する。テクスチャはブロックアトラス 1 枚で済む。
 *
 * <h2>ファイル形式</h2>
 * <pre>
 * 外側 NBT (非圧縮) { ByteData: byte[] }   ← ByteData 自体が gzip
 *   └ 内側 NBT { SizeX, SizeY, SizeZ, IdList[{Id, Set{Block, Meta}}], BData: byte[] }
 * </pre>
 * <ul>
 *   <li>{@code BData} は 1 セル 1 バイトのパレット番号。本家は書き出しで 128 を引くので
 *       読むときは {@code (b + 128) & 0xFF}</li>
 *   <li>セルの並びは {@code x * ySize * zSize + y * zSize + z} (本家 {@code getBlockSet})</li>
 *   <li>ブロック名は<b>1.7.10 の名前 + メタ</b> ({@code stained_hardened_clay} + 色番号 等)</li>
 * </ul>
 *
 * <h2>座標</h2>
 * 本家 {@code NGTOModel.renderAll} は {@code scale} を掛けてから
 * {@code translate(-xSize/2, 0, -zSize/2)}。ここでは<b>ボクセル単位</b> (1 ブロック = 1) で
 * 中心寄せまでやり、{@code scale} は描画側の {@code def.getModelScale()} に任せる。
 */
public final class NgtoModelGeometry {

    /** 1 頂点あたり x, y, z, nx, ny, nz, u, v。{@code MqoModelLoader.Batch} と同じ並び。 */
    public static final int STRIDE = 8;

    private NgtoModelGeometry() {
    }

    public static boolean isNgto(String modelFile) {
        if (modelFile == null) {
            return false;
        }
        String lower = modelFile.toLowerCase(Locale.ROOT);
        return lower.endsWith(".ngto") || lower.endsWith(".ngtz");
    }

    public static boolean isNgtz(String modelFile) {
        return modelFile != null && modelFile.toLowerCase(Locale.ROOT).endsWith(".ngtz");
    }

    /** 1 パーツ (.ngto 1 個)。.ngtz は複数パーツを持つ。 */
    public record Part(String name, float[] opaque, float[] translucent) {
    }

    /**
     * {@code .ngtz} は「{@code .ngto} を並べた素の zip」(本家 {@code NGTZ})。
     * エントリ名から {@code .ngto} を取った物がパーツ名で、スクリプトはこの名前で部分描画する。
     * パーツごとに同じ変換 (各自のサイズで X/Z 中心寄せ) を掛ける。
     */
    public static List<Part> buildParts(byte[] fileBytes, String modelFile, float scale) {
        if (!isNgtz(modelFile)) {
            Geometry single = build(fileBytes, scale);
            return single == null ? List.of()
                : List.of(new Part("default", single.opaque(), single.translucent()));
        }
        List<Part> parts = new ArrayList<>();
        try (java.util.zip.ZipInputStream zip =
                 new java.util.zip.ZipInputStream(new ByteArrayInputStream(fileBytes))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                int slash = name.lastIndexOf('/');
                String partName = (slash >= 0 ? name.substring(slash + 1) : name)
                    .replace(".ngto", "").replace(".NGTO", "");
                Geometry geometry = build(zip.readAllBytes(), scale);
                if (geometry != null) {
                    parts.add(new Part(partName, geometry.opaque(), geometry.translucent()));
                }
            }
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("[NGTZ] 読み込みに失敗 {}", modelFile, e);
        }
        return parts;
    }

    /** 不透明バッチと半透明バッチ (ガラス等) に分かれた頂点配列。 */
    public record Geometry(float[] opaque, float[] translucent) {
        public boolean isEmpty() {
            return (opaque == null || opaque.length == 0)
                && (translucent == null || translucent.length == 0);
        }
    }

    public static Geometry build(byte[] fileBytes, float scale) {
        CompoundTag root = readNgto(fileBytes);
        if (root == null) {
            return null;
        }
        int sizeX = Math.max(root.getInt("SizeX"), 1);
        int sizeY = Math.max(root.getInt("SizeY"), 1);
        int sizeZ = Math.max(root.getInt("SizeZ"), 1);
        int[] ids = readCellIds(root, sizeX * sizeY * sizeZ);
        if (ids == null) {
            RealTrainModUnofficial.LOGGER.warn("[NGTO] BData/IData がありません");
            return null;
        }
        Map<Integer, BlockState> palette = readPalette(root);
        BlockState[] cells = new BlockState[ids.length];
        for (int i = 0; i < ids.length; i++) {
            BlockState state = palette.get(ids[i]);
            cells[i] = state == null ? Blocks.AIR.defaultBlockState() : state;
        }

        FloatList opaque = new FloatList();
        FloatList translucent = new FloatList();
        RandomSource random = RandomSource.create();
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return null;
        }
        float offX = sizeX * 0.5F;
        float offZ = sizeZ * 0.5F;
        //本家 NGTOParts.render は glScalef(scale) → glTranslatef(-x/2, 0, -z/2) の順。
        //= 形状も中心寄せも scale 倍。ここで焼き込む (描画側では掛けない)。
        float unit = scale > 0.0F ? scale : 1.0F;
        //同じ BlockState の焼き済みモデルは使い回す (65000 ボクセル分の getBlockModel を避ける)
        Map<BlockState, BakedModel> models = new HashMap<>();
        Map<BlockState, Boolean> translucentCache = new HashMap<>();

        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    BlockState state = cells[index(x, y, z, sizeY, sizeZ)];
                    if (state.isAir()) {
                        continue;
                    }
                    boolean[] exposed = new boolean[6];
                    boolean anyExposed = false;
                    for (Direction dir : Direction.values()) {
                        int nx = x + dir.getStepX();
                        int ny = y + dir.getStepY();
                        int nz = z + dir.getStepZ();
                        boolean open;
                        if (nx < 0 || ny < 0 || nz < 0 || nx >= sizeX || ny >= sizeY || nz >= sizeZ) {
                            open = true;
                        } else {
                            BlockState neighbor = cells[index(nx, ny, nz, sizeY, sizeZ)];
                            //★バニラ Block.shouldRenderFace と同じ判定にする。
                            //  以前は「隣が塞いでいなければ描く」だけだったので、ガラス同士の
                            //  接面など<b>同じ位置に 2 枚</b>出て z ファイティング (チカチカ) していた。
                            //  本家 NGTOModel はバニラのブロック描画をそのまま使うのでこの問題が無い。
                            if (state.skipRendering(neighbor, dir)) {
                                open = false;
                            } else if (!neighbor.canOcclude()) {
                                open = true;
                            } else {
                                open = !neighbor.isFaceSturdy(
                                    net.minecraft.world.level.EmptyBlockGetter.INSTANCE,
                                    net.minecraft.core.BlockPos.ZERO, dir.getOpposite());
                            }
                        }
                        exposed[dir.ordinal()] = open;
                        anyExposed |= open;
                    }
                    if (!anyExposed) {
                        continue;
                    }
                    BakedModel model = models.computeIfAbsent(state,
                        s -> mc.getBlockRenderer().getBlockModel(s));
                    boolean isTranslucent = translucentCache.computeIfAbsent(state,
                        s -> ItemBlockRenderTypes.getChunkRenderType(s) == RenderType.translucent());
                    FloatList sink = isTranslucent ? translucent : opaque;
                    float ox = x - offX;
                    float oz = z - offZ;
                    //方向なしの面 (階段・柵など、culling されない部分)
                    appendQuads(sink, model.getQuads(state, null, random), ox, y, oz, unit);
                    for (Direction dir : Direction.values()) {
                        if (exposed[dir.ordinal()]) {
                            appendQuads(sink, model.getQuads(state, dir, random), ox, y, oz, unit);
                        }
                    }
                }
            }
        }
        Geometry geometry = new Geometry(opaque.toArray(), translucent.toArray());
        RealTrainModUnofficial.LOGGER.info("[NGTO] {}x{}x{} → 頂点 {} (不透明) / {} (半透明)",
            sizeX, sizeY, sizeZ, opaque.size() / STRIDE, translucent.size() / STRIDE);
        return geometry.isEmpty() ? null : geometry;
    }

    private static int index(int x, int y, int z, int sizeY, int sizeZ) {
        //本家 NGTObject.getBlockSet と同じ並び
        return x * sizeY * sizeZ + y * sizeZ + z;
    }

    /** {@link BakedQuad} の頂点 (int 8 個/頂点) を展開して積む。 */
    private static void appendQuads(FloatList sink, List<BakedQuad> quads, float ox, float oy, float oz, float unit) {
        for (BakedQuad quad : quads) {
            int[] data = quad.getVertices();
            if (data.length < 32) {
                continue;
            }
            Direction dir = quad.getDirection();
            float nx = dir.getStepX();
            float ny = dir.getStepY();
            float nz = dir.getStepZ();
            for (int i = 0; i < 4; i++) {
                int o = i * 8;
                sink.add((Float.intBitsToFloat(data[o]) + ox) * unit);
                sink.add((Float.intBitsToFloat(data[o + 1]) + oy) * unit);
                sink.add((Float.intBitsToFloat(data[o + 2]) + oz) * unit);
                sink.add(nx);
                sink.add(ny);
                sink.add(nz);
                sink.add(Float.intBitsToFloat(data[o + 4]));
                sink.add(Float.intBitsToFloat(data[o + 5]));
            }
        }
    }

    /** 外側 NBT → ByteData(gzip) → 内側 NBT。ByteData が無ければそのまま。 */
    private static CompoundTag readNgto(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            CompoundTag outer = NbtIo.read(new java.io.DataInputStream(new ByteArrayInputStream(bytes)),
                NbtAccounter.unlimitedHeap());
            if (outer == null) {
                return null;
            }
            if (!outer.contains("ByteData", Tag.TAG_BYTE_ARRAY)) {
                return outer;
            }
            byte[] inner = outer.getByteArray("ByteData");
            try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(inner))) {
                return NbtIo.read(new java.io.DataInputStream(in), NbtAccounter.unlimitedHeap());
            }
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("[NGTO] 読み込みに失敗", e);
            return null;
        }
    }

    /** {@code BData} (1 バイト/セル・-128 オフセット) か {@code IData}/{@code Blocks} (int/セル)。 */
    private static int[] readCellIds(CompoundTag root, int expected) {
        if (root.contains("IData", Tag.TAG_INT_ARRAY)) {
            return root.getIntArray("IData");
        }
        if (root.contains("Blocks", Tag.TAG_INT_ARRAY)) {
            return root.getIntArray("Blocks");
        }
        if (!root.contains("BData", Tag.TAG_BYTE_ARRAY)) {
            return null;
        }
        byte[] raw = root.getByteArray("BData");
        int[] ids = new int[raw.length];
        for (int i = 0; i < raw.length; i++) {
            //本家は書き出しで 128 を引いている
            ids[i] = (raw[i] + 128) & 0xFF;
        }
        if (ids.length < expected) {
            RealTrainModUnofficial.LOGGER.warn("[NGTO] セル数が足りません ({} < {})", ids.length, expected);
        }
        return ids;
    }

    private static Map<Integer, BlockState> readPalette(CompoundTag root) {
        Map<Integer, BlockState> palette = new HashMap<>();
        palette.put(0, Blocks.AIR.defaultBlockState());
        ListTag list = root.getList("IdList", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            CompoundTag set = entry.getCompound("Set");
            String name = set.getString("Block");
            int meta = set.getInt("Meta");
            palette.put(entry.getInt("Id"), LegacyBlocks.toState(name, meta));
        }
        return palette;
    }

    /**
     * 1.7.10 のブロック名 + メタ を 1.21 の {@link BlockState} にする。
     *
     * <p>1.7.10 は「色違い = 同じブロックのメタ違い」だったので、色付き系は<b>メタが色番号</b>。
     * 1.21 では色ごとに別ブロックなので引き直す。名前がそのまま 1.21 にあるものは
     * (stone / glass / iron_block 等) レジストリから直接引く。
     */
    private static final class LegacyBlocks {
        private static final Map<String, BlockState> CACHE = new HashMap<>();

        static BlockState toState(String name, int meta) {
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
            //★空気を先に返す。byName は「AIR が返ってきたら未知」と見なすので、
            //ここを通さないと<b>パレットの空気が石になり、車体が中身の詰まった塊になる</b>。
            if (name.equals("air") || name.equals("cave_air") || name.equals("void_air")) {
                return Blocks.AIR.defaultBlockState();
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
                RealTrainModUnofficial.LOGGER.warn("[NGTO] 未知のブロック {} (meta {}) → 石で代用", rawName, meta);
                return Blocks.STONE.defaultBlockState();
            }
            //階段は 1.7.10 のメタが 向き(0-3) + 上下反転(4)
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
            Map.entry("sea_lantern", "sea_lantern")
        );

        private static BlockState byName(String name) {
            ResourceLocation id = ResourceLocation.tryParse("minecraft:" + name);
            if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
                return null;
            }
            Block block = BuiltInRegistries.BLOCK.get(id);
            return block == Blocks.AIR ? null : block.defaultBlockState();
        }
    }

    /** float の可変長バッファ (ArrayList<Float> のボクシングを避ける)。 */
    private static final class FloatList {
        private float[] data = new float[4096];
        private int size;

        void add(float value) {
            if (this.size == this.data.length) {
                float[] grown = new float[this.data.length * 2];
                System.arraycopy(this.data, 0, grown, 0, this.size);
                this.data = grown;
            }
            this.data[this.size++] = value;
        }

        int size() {
            return this.size;
        }

        float[] toArray() {
            float[] out = new float[this.size];
            System.arraycopy(this.data, 0, out, 0, this.size);
            return out;
        }
    }
}
