package jp.ngt.mcte.editor.filter;

import jp.ngt.mcte.editor.EditorSelection;
import jp.ngt.ngtlib.block.BlockSet;
import jp.ngt.ngtlib.block.NGTObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 編集フィルタ一式 (neo mcte)。本家 MCTE editor/filter の移植。
 * 本家は 1 フィルタ 1 ファイルだったが、どれも数十行なのでここにまとめてある。
 */
public final class Filters {

    private Filters() {
    }

    /** 組み込みフィルタ。 */
    private static final List<EditFilter> BUILT_IN = List.of(
        new Delete(),
        new Fill(),
        new Replace(),
        new FillSurface(),
        new Random(),
        new Cut(),
        new Copy(),
        new Paste(),
        new Move(),
        new Hollow(),
        new DeleteEntity(),
        new PerlinNoise(),
        new Clone(),
        new Rotate(),
        new Mirror(),
        new Miniature(),
        new Array(),
        new Ellipsoid(),
        new Cylinder(),
        new Outline(),
        new Walls(),
        new Overlay(),
        new Smooth(),
        new Export(),
        new Import(),
        new Analyze()
    );

    /**
     * 一覧に出る順。組み込み + mcte/filter/*.js の独自フィルタ。
     * スクリプトは 1 回だけ読む。追加したら再起動 (本家も同じ)。
     */
    public static final List<EditFilter> REGISTRY = build();

    private static List<EditFilter> build() {
        List<EditFilter> all = new ArrayList<>(BUILT_IN);
        try {
            all.addAll(CustomFilter.loadAll());
        } catch (Throwable t) {
            // スクリプトが壊れていても組み込みは使えるようにする
            jp.ngt.ngtlib.io.NGTLog.debug("[Filters] 独自フィルタの読み込みに失敗: " + t);
        }
        return java.util.List.copyOf(all);
    }

    public static EditFilter byName(String name) {
        for (EditFilter f : REGISTRY) {
            if (f.name().equalsIgnoreCase(name)) {
                return f;
            }
        }
        return null;
    }

    /** コピー内容。エディタごとに 1 個。 */
    private static final Map<UUID, NGTObject> CLIPBOARD = new ConcurrentHashMap<>();

    public static NGTObject clipboardOf(EditorSelection editor) {
        return editor == null ? null : CLIPBOARD.get(editor.getUUID());
    }

    // ---- 共通ヘルパ ----

    /**
     * エディタのスロット → オフハンド、の順で引く。
     * 本家はエディタのスロット (ContainerEditor の 72,152 / 72,172) だけを見る。
     */
    private static BlockState slotOrOffhand(EditorSelection editor, Player player, int slot) {
        if (editor != null) {
            BlockState s = editor.slotBlock(slot);
            if (s != null) {
                return s;
            }
        }
        return offhandBlock(player);
    }

    /** オフハンドのブロック。持っていなければ null。 */
    private static BlockState offhandBlock(Player player) {
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof BlockItem bi) {
            return bi.getBlock().defaultBlockState();
        }
        return null;
    }

    /** 文字列 (例 minecraft:stone) からブロック。空/不正なら null。 */
    private static Block blockByName(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            ResourceLocation loc = ResourceLocation.parse(id.trim());
            Block b = BuiltInRegistries.BLOCK.get(loc);
            // 未登録は AIR が返るので、明示的に air を指定した場合以外は null 扱い
            return b == Blocks.AIR && !loc.getPath().equals("air") ? null : b;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isWater(BlockState s) {
        return s.getFluidState().isSource() || !s.getFluidState().isEmpty();
    }

    // ---- 各フィルタ ----

    /** 範囲を消す。本家 EditFilterDelete。 */
    public static final class Delete extends EditFilter {
        @Override
        public String name() {
            return "Delete";
        }

        @Override
        protected void initConfig(FilterConfig cfg) {
            cfg.addBoolean("IgnoreWater", false);
        }

        @Override
        public int apply(ServerLevel level, Player player, EditorSelection editor) {
            boolean ignoreWater = config().getBoolean("IgnoreWater");
            return EditorOps.replace(level, editor, Blocks.AIR.defaultBlockState(),
                (pos, cur) -> !cur.isAir() && !(ignoreWater && isWater(cur)));
        }
    }

    /** 範囲を埋める。本家 EditFilterFill。 */
    public static final class Fill extends EditFilter {
        @Override
        public String name() {
            return "Fill";
        }

        @Override
        protected void initConfig(FilterConfig cfg) {
            cfg.addBoolean("OnlyAir", false);
            cfg.addString("Block", "");
        }

        @Override
        public int apply(ServerLevel level, Player player, EditorSelection editor) {
            BlockState to = resolve(editor, player);
            if (to == null) {
                return 0;
            }
            boolean onlyAir = config().getBoolean("OnlyAir");
            return EditorOps.replace(level, editor, to,
                (pos, cur) -> !onlyAir || cur.isAir());
        }

        private BlockState resolve(EditorSelection editor, Player player) {
            Block named = blockByName(config().getString("Block"));
            if (named != null) {
                return named.defaultBlockState();
            }
            return slotOrOffhand(editor, player, EditorSelection.SLOT_FILL);
        }
    }

    /** A を B に置き換える。本家 EditFilterReplace 相当。 */
    public static final class Replace extends EditFilter {
        @Override
        public String name() {
            return "Replace";
        }

        @Override
        protected void initConfig(FilterConfig cfg) {
            cfg.addString("From", "");
            cfg.addString("To", "");
        }

        @Override
        public int apply(ServerLevel level, Player player, EditorSelection editor) {
            Block from = blockByName(config().getString("From"));
            BlockState fromState = from != null ? from.defaultBlockState()
                : (editor != null ? editor.slotBlock(EditorSelection.SLOT_FILL) : null);
            Block toBlock = blockByName(config().getString("To"));
            BlockState to = toBlock != null ? toBlock.defaultBlockState()
                : slotOrOffhand(editor, player, EditorSelection.SLOT_REPLACE);
            if (fromState == null || to == null) {
                return 0;
            }
            Block fromBlock = fromState.getBlock();
            return EditorOps.replace(level, editor, to, (pos, cur) -> cur.is(fromBlock));
        }
    }

    /** 範囲の外殻だけ埋める。本家 EditFilterFillSurface。 */
    public static final class FillSurface extends EditFilter {
        @Override
        public String name() {
            return "FillSurface";
        }

        @Override
        protected void initConfig(FilterConfig cfg) {
            cfg.addInt("Thickness", 1, 1, 16);
            cfg.addString("Block", "");
        }

        @Override
        public int apply(ServerLevel level, Player player, EditorSelection editor) {
            Block named = blockByName(config().getString("Block"));
            BlockState to = named != null ? named.defaultBlockState() : slotOrOffhand(editor, player, EditorSelection.SLOT_FILL);
            if (to == null) {
                return 0;
            }
            int t = config().getInt("Thickness");
            AABB b = editor.getSelectionBox();
            int x0 = (int) Math.floor(b.minX), x1 = (int) Math.ceil(b.maxX) - 1;
            int y0 = (int) Math.floor(b.minY), y1 = (int) Math.ceil(b.maxY) - 1;
            int z0 = (int) Math.floor(b.minZ), z1 = (int) Math.ceil(b.maxZ) - 1;
            return EditorOps.replace(level, editor, to, (pos, cur) ->
                pos.getX() < x0 + t || pos.getX() > x1 - t
                    || pos.getY() < y0 + t || pos.getY() > y1 - t
                    || pos.getZ() < z0 + t || pos.getZ() > z1 - t);
        }
    }

    /** 確率で埋める。本家 EditFilterRandom。 */
    public static final class Random extends EditFilter {
        @Override
        public String name() {
            return "Random";
        }

        @Override
        protected void initConfig(FilterConfig cfg) {
            cfg.addFloat("Probability", 0.5F, 0.0F, 1.0F);
            cfg.addBoolean("OnlyAir", true);
            cfg.addString("Block", "");
        }

        @Override
        public int apply(ServerLevel level, Player player, EditorSelection editor) {
            Block named = blockByName(config().getString("Block"));
            BlockState to = named != null ? named.defaultBlockState() : slotOrOffhand(editor, player, EditorSelection.SLOT_FILL);
            if (to == null) {
                return 0;
            }
            float p = config().getFloat("Probability");
            boolean onlyAir = config().getBoolean("OnlyAir");
            // ワールドの乱数を使う (シード依存にしない。毎回違ってよい処理のため)
            var rng = level.getRandom();
            return EditorOps.replace(level, editor, to,
                (pos, cur) -> (!onlyAir || cur.isAir()) && rng.nextFloat() < p);
        }
    }

    /** 範囲を控える。本家 EditFilterCopy。 */
    public static final class Copy extends EditFilter {
        @Override
        public String name() {
            return "Copy";
        }

        @Override
        public int apply(ServerLevel level, Player player, EditorSelection editor) {
            AABB b = editor.getSelectionBox();
            int x0 = (int) Math.floor(b.minX), y0 = (int) Math.floor(b.minY), z0 = (int) Math.floor(b.minZ);
            int w = (int) b.getXsize(), h = (int) b.getYsize(), d = (int) b.getZsize();
            List<BlockSet> blocks = new ArrayList<>();
            EditorOps.forEach(editor, pos -> {
                BlockState s = level.getBlockState(pos);
                if (s.isAir()) {
                    return;
                }
                var be = level.getBlockEntity(pos);
                blocks.add(new BlockSet(pos.getX() - x0, pos.getY() - y0, pos.getZ() - z0, s,
                    be == null ? null : be.saveWithoutMetadata(level.registryAccess())));
            });
            if (blocks.isEmpty()) {
                return 0;
            }
            // ★原点を記録する。貼り付け時に「元はどこにあったか」が分からないと
            // RTMU レールの絶対座標を補正できない。
            CLIPBOARD.put(editor.getUUID(), NGTObject.createNGTO(blocks, w, h, d, x0, y0, z0));
            return blocks.size();
        }
    }

    /** 控えた内容を選択範囲の最小角へ貼る。本家 EditFilterPaste。 */
    public static final class Paste extends EditFilter {
        @Override
        public String name() {
            return "Paste";
        }

        @Override
        protected void initConfig(FilterConfig cfg) {
            cfg.addBoolean("SkipAir", true);
        }

        @Override
        public int apply(ServerLevel level, Player player, EditorSelection editor) {
            NGTObject obj = CLIPBOARD.get(editor.getUUID());
            if (obj == null) {
                return 0;
            }
            AABB b = editor.getSelectionBox();
            BlockPos origin = new BlockPos((int) Math.floor(b.minX), (int) Math.floor(b.minY), (int) Math.floor(b.minZ));

            UndoHistory.Snapshot snapshot = new UndoHistory.Snapshot();
            int count = 0;
            for (BlockSet set : obj.blockList) {
                if (set == null || set.state == null) {
                    continue;
                }
                if (config().getBoolean("SkipAir") && set.state.isAir()) {
                    continue;
                }
                if (count >= EditorOps.MAX_BLOCKS) {
                    break;
                }
                BlockPos p = origin.offset(set.x, set.y, set.z);
                snapshot.record(level, p, level.getBlockState(p));
                level.setBlock(p, set.state, 3);
                if (set.nbt != null && level.getBlockEntity(p) != null) {
                    try {
                        // RTMU のレールは絶対座標を持つのでずらした量ぶん補正する
                        net.minecraft.nbt.CompoundTag t = set.nbt.copy();
                        RailPaste.shift(t, origin.getX() - obj.origX,
                            origin.getY() - obj.origY, origin.getZ() - obj.origZ);
                        level.getBlockEntity(p).loadWithComponents(t, level.registryAccess());
                        level.getBlockEntity(p).setChanged();
                        RailPaste.finish(level, p);
                    } catch (Exception ignored) {
                        // 読めない BE は飛ばす。ブロック自体は置けている
                    }
                }
                count++;
            }
            if (count > 0) {
                UndoHistory.push(editor, snapshot);
            }
            return count;
        }
    }

    /** 控えてから消す。本家 EditFilterCut。 */
    public static final class Cut extends EditFilter {
        private final Copy copy = new Copy();

        @Override
        public String name() {
            return "Cut";
        }

        @Override
        public int apply(ServerLevel level, Player player, EditorSelection editor) {
            int copied = copy.apply(level, player, editor);
            if (copied == 0) {
                return 0;
            }
            return EditorOps.replace(level, editor, Blocks.AIR.defaultBlockState(),
                (pos, cur) -> !cur.isAir());
        }
    }

    /** 範囲内のエンティティを消す。本家 EditFilterDeleteEntity。 */
    public static final class DeleteEntity extends EditFilter {
        @Override
        public String name() {
            return "DeleteEntity";
        }

        @Override
        protected void initConfig(FilterConfig cfg) {
            cfg.addBoolean("KeepPlayers", true);
            cfg.addBoolean("KeepItemFrames", false);
        }

        @Override
        public int apply(ServerLevel level, Player player, EditorSelection editor) {
            boolean keepPlayers = config().getBoolean("KeepPlayers");
            boolean keepFrames = config().getBoolean("KeepItemFrames");
            AABB box = editor.getSelectionBox();
            int n = 0;
            for (var e : level.getEntities(null, box)) {
                if (e instanceof Player && keepPlayers) {
                    continue;
                }
                if (keepFrames && e instanceof net.minecraft.world.entity.decoration.ItemFrame) {
                    continue;
                }
                e.discard();
                n++;
            }
            return n;
        }
    }

    /**
     * 中を空洞にする (neo mcte 追加)。外殻だけ残す。
     * 本家には無い。建物の内側を一発で抜きたい場面が多いので足した。
     */
    public static final class Hollow extends EditFilter {
        @Override
        public String name() {
            return "Hollow";
        }

        @Override
        protected void initConfig(FilterConfig cfg) {
            cfg.addInt("Thickness", 1, 1, 16);
        }

        @Override
        public int apply(ServerLevel level, Player player, EditorSelection editor) {
            int t = config().getInt("Thickness");
            AABB b = editor.getSelectionBox();
            int x0 = (int) Math.floor(b.minX), x1 = (int) Math.ceil(b.maxX) - 1;
            int y0 = (int) Math.floor(b.minY), y1 = (int) Math.ceil(b.maxY) - 1;
            int z0 = (int) Math.floor(b.minZ), z1 = (int) Math.ceil(b.maxZ) - 1;
            return EditorOps.replace(level, editor, Blocks.AIR.defaultBlockState(), (pos, cur) -> {
                if (cur.isAir()) {
                    return false;
                }
                // 外殻 t 枚は残す
                return pos.getX() >= x0 + t && pos.getX() <= x1 - t
                    && pos.getY() >= y0 + t && pos.getY() <= y1 - t
                    && pos.getZ() >= z0 + t && pos.getZ() <= z1 - t;
            });
        }
    }

    /**
     * 範囲ごと動かす (neo mcte 追加)。
     * 本家は Copy → 範囲を選び直して Paste → 元を Delete という手順が必要だった。
     */
    public static final class Move extends EditFilter {
        @Override
        public String name() {
            return "Move";
        }

        @Override
        protected void initConfig(FilterConfig cfg) {
            cfg.addInt("X", 0, -256, 256);
            cfg.addInt("Y", 0, -256, 256);
            cfg.addInt("Z", 0, -256, 256);
        }

        @Override
        public int apply(ServerLevel level, Player player, EditorSelection editor) {
            int dx = config().getInt("X");
            int dy = config().getInt("Y");
            int dz = config().getInt("Z");
            if (dx == 0 && dy == 0 && dz == 0) {
                return 0;
            }
            // まず全部読む (書きながら読むと、ずらし方向によって自分を上書きしてしまう)
            List<BlockPos> from = new ArrayList<>();
            List<BlockState> states = new ArrayList<>();
            List<net.minecraft.nbt.CompoundTag> tags = new ArrayList<>();
            EditorOps.forEach(editor, pos -> {
                BlockState s = level.getBlockState(pos);
                if (s.isAir()) {
                    return;
                }
                var be = level.getBlockEntity(pos);
                from.add(pos.immutable());
                states.add(s);
                tags.add(be == null ? null : be.saveWithoutMetadata(level.registryAccess()));
            });
            if (from.isEmpty()) {
                return 0;
            }

            UndoHistory.Snapshot snapshot = new UndoHistory.Snapshot();
            // 元を消す
            for (BlockPos p : from) {
                snapshot.record(level, p, level.getBlockState(p));
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
            // 移動先へ置く
            for (int i = 0; i < from.size(); i++) {
                BlockPos p = from.get(i).offset(dx, dy, dz);
                snapshot.record(level, p, level.getBlockState(p));
                level.setBlock(p, states.get(i), 3);
                var tag = tags.get(i);
                if (tag != null && level.getBlockEntity(p) != null) {
                    try {
                        net.minecraft.nbt.CompoundTag t = tag.copy();
                        RailPaste.shift(t, dx, dy, dz);
                        level.getBlockEntity(p).loadWithComponents(t, level.registryAccess());
                        level.getBlockEntity(p).setChanged();
                        RailPaste.finish(level, p);
                    } catch (Exception ignored) {
                        // 読めない BE は飛ばす
                    }
                }
            }
            UndoHistory.push(editor, snapshot);
            // 選択範囲も一緒に動かす (動かした先をそのまま触れるように)
            editor.setStart(editor.getStart().offset(dx, dy, dz));
            editor.setEnd(editor.getEnd().offset(dx, dy, dz));
            return from.size();
        }
    }

    /**
     * ノイズで埋める。本家 EditFilterPerlinNoise。
     * 本家は NGTLib の PerlinNoise を使っていた。
     */
    public static final class PerlinNoise extends EditFilter {
        @Override
        public String name() {
            return "PerlinNoise";
        }

        @Override
        protected void initConfig(FilterConfig cfg) {
            cfg.addInt("Octaves", 3, 1, 8);
            cfg.addFloat("Persistence", 0.5F, 0.0F, 4.0F);
            cfg.addFloat("ScaleX", 0.0625F, 0.0F, 4.0F);
            cfg.addFloat("ScaleY", 0.0625F, 0.0F, 4.0F);
            cfg.addFloat("ScaleZ", 0.0625F, 0.0F, 4.0F);
            cfg.addFloat("Threshold", 0.0F, -1.0F, 1.0F);
            cfg.addString("Block", "");
        }

        @Override
        public int apply(ServerLevel level, Player player, EditorSelection editor) {
            Block named = blockByName(config().getString("Block"));
            BlockState to = named != null ? named.defaultBlockState() : slotOrOffhand(editor, player, EditorSelection.SLOT_FILL);
            if (to == null) {
                return 0;
            }
            int octaves = config().getInt("Octaves");
            float persistence = config().getFloat("Persistence");
            float sx = config().getFloat("ScaleX");
            float sy = config().getFloat("ScaleY");
            float sz = config().getFloat("ScaleZ");
            float threshold = config().getFloat("Threshold");
            long seed = level.getSeed();
            return EditorOps.replace(level, editor, to, (pos, cur) ->
                octave(pos.getX() * sx, pos.getY() * sy, pos.getZ() * sz, octaves, persistence, seed) >= threshold);
        }

        private static float octave(double x, double y, double z, int octaves, float persistence, long seed) {
            float total = 0.0F;
            float amp = 1.0F;
            float max = 0.0F;
            double freq = 1.0D;
            for (int i = 0; i < octaves; i++) {
                total += value(x * freq, y * freq, z * freq, seed + i) * amp;
                max += amp;
                amp *= persistence;
                freq *= 2.0D;
            }
            return max == 0.0F ? 0.0F : total / max;
        }

        /** 格子点のハッシュを三線形補間した値ノイズ (-1..1)。 */
        private static float value(double x, double y, double z, long seed) {
            int xi = (int) Math.floor(x), yi = (int) Math.floor(y), zi = (int) Math.floor(z);
            double fx = smooth(x - xi), fy = smooth(y - yi), fz = smooth(z - zi);
            double x00 = lerp(hash(xi, yi, zi, seed), hash(xi + 1, yi, zi, seed), fx);
            double x10 = lerp(hash(xi, yi + 1, zi, seed), hash(xi + 1, yi + 1, zi, seed), fx);
            double x01 = lerp(hash(xi, yi, zi + 1, seed), hash(xi + 1, yi, zi + 1, seed), fx);
            double x11 = lerp(hash(xi, yi + 1, zi + 1, seed), hash(xi + 1, yi + 1, zi + 1, seed), fx);
            return (float) lerp(lerp(x00, x10, fy), lerp(x01, x11, fy), fz);
        }

        private static double smooth(double t) {
            return t * t * (3.0D - 2.0D * t);
        }

        private static double lerp(double a, double b, double t) {
            return a + (b - a) * t;
        }

        private static double hash(int x, int y, int z, long seed) {
            long h = seed;
            h = h * 6364136223846793005L + x * 341873128712L;
            h = h * 6364136223846793005L + y * 132897987541L;
            h = h * 6364136223846793005L + z * 2685821657736338717L;
            h ^= h >>> 33;
            return ((h & 0xFFFFFF) / 8388608.0D) - 1.0D;
        }
    }

    /**
     * 選択範囲を指定量ずらして繰り返し複製する。本家 GuiEditor の Clone。
     * コピー → 移動先へ貼り付け、を Repeat 回。元は残す。
     */
    public static final class Clone extends EditFilter {
        private final Copy copy = new Copy();

        @Override
        public String name() {
            return "Clone";
        }

        @Override
        protected void initConfig(FilterConfig cfg) {
            cfg.addInt("X", 0, -256, 256);
            cfg.addInt("Y", 0, -256, 256);
            cfg.addInt("Z", 0, -256, 256);
            cfg.addInt("Repeat", 1, 1, 64);
        }

        @Override
        public int apply(ServerLevel level, Player player, EditorSelection editor) {
            int dx = config().getInt("X");
            int dy = config().getInt("Y");
            int dz = config().getInt("Z");
            int repeat = Math.max(1, config().getInt("Repeat"));
            if (dx == 0 && dy == 0 && dz == 0) {
                return 0;
            }
            if (copy.apply(level, player, editor) == 0) {
                return 0;
            }
            NGTObject obj = CLIPBOARD.get(editor.getUUID());
            if (obj == null) {
                return 0;
            }
            AABB b = editor.getSelectionBox();
            BlockPos origin = new BlockPos((int) Math.floor(b.minX), (int) Math.floor(b.minY), (int) Math.floor(b.minZ));

            UndoHistory.Snapshot snapshot = new UndoHistory.Snapshot();
            int count = 0;
            for (int r = 1; r <= repeat; r++) {
                BlockPos base = origin.offset(dx * r, dy * r, dz * r);
                for (BlockSet set : obj.blockList) {
                    if (set == null || set.state == null || set.state.isAir()) {
                        continue;
                    }
                    if (count >= EditorOps.MAX_BLOCKS) {
                        break;
                    }
                    BlockPos p = base.offset(set.x, set.y, set.z);
                    snapshot.record(level, p, level.getBlockState(p));
                    level.setBlock(p, set.state, 3);
                    if (set.nbt != null && level.getBlockEntity(p) != null) {
                        try {
                            net.minecraft.nbt.CompoundTag t = set.nbt.copy();
                            RailPaste.shift(t, base.getX() - obj.origX,
                                base.getY() - obj.origY, base.getZ() - obj.origZ);
                            level.getBlockEntity(p).loadWithComponents(t, level.registryAccess());
                            level.getBlockEntity(p).setChanged();
                            RailPaste.finish(level, p);
                        } catch (Exception ignored) {
                            // 読めない BE は飛ばす
                        }
                    }
                    count++;
                }
            }
            if (count > 0) {
                UndoHistory.push(editor, snapshot);
            }
            return count;
        }
    }

    /**
     * コピー内容を回した状態に差し替える (本家 EditorTransform)。
     * 回すのはクリップボード。貼り付けたときに回った形で出る。
     */
    public static final class Rotate extends EditFilter {
        @Override
        public String name() {
            return "Rotate";
        }

        @Override
        protected void initConfig(FilterConfig cfg) {
            cfg.addEnum("Axis", "Y", List.of("X", "Y", "Z"));
        }

        @Override
        public int apply(ServerLevel level, Player player, EditorSelection editor) {
            NGTObject obj = CLIPBOARD.get(editor.getUUID());
            if (obj == null) {
                return 0;
            }
            String axis = config().getString("Axis");
            List<BlockSet> out = new ArrayList<>();
            int w = obj.xSize, h = obj.ySize, d = obj.zSize;
            int nw = w, nh = h, nd = d;
            for (BlockSet s : obj.blockList) {
                if (s == null || s.state == null) {
                    continue;
                }
                int x = s.x, y = s.y, z = s.z;
                int rx, ry, rz;
                switch (axis) {
                    case "X" -> {
                        rx = x; ry = d - 1 - z; rz = y;
                        nh = d; nd = h;
                    }
                    case "Z" -> {
                        rx = h - 1 - y; ry = x; rz = z;
                        nw = h; nh = w;
                    }
                    default -> {
                        // Y 軸 90 度: (x,z) -> (d-1-z, x)
                        rx = d - 1 - z; ry = y; rz = x;
                        nw = d; nd = w;
                    }
                }
                // ★形だけでなくブロックの向きも回す。
                // これが無いと階段・ハーフ・レール・看板が回す前の向きのまま残り、
                // 回した建物がその場で作り直しになっていた (本家 MCTE も形だけ)。
                BlockState st = "Y".equals(axis) || axis == null || axis.isEmpty()
                    ? s.state.rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90)
                    : s.state;
                out.add(new BlockSet(rx, ry, rz, st, s.nbt));
            }
            CLIPBOARD.put(editor.getUUID(), NGTObject.createNGTO(out, nw, nh, nd, 0, 0, 0));
            return out.size();
        }
    }

    /** コピー内容を反転する (本家 EditorTransform)。 */
    public static final class Mirror extends EditFilter {
        @Override
        public String name() {
            return "Mirror";
        }

        @Override
        protected void initConfig(FilterConfig cfg) {
            cfg.addEnum("Axis", "X", List.of("X", "Y", "Z"));
        }

        @Override
        public int apply(ServerLevel level, Player player, EditorSelection editor) {
            NGTObject obj = CLIPBOARD.get(editor.getUUID());
            if (obj == null) {
                return 0;
            }
            String axis = config().getString("Axis");
            List<BlockSet> out = new ArrayList<>();
            for (BlockSet s : obj.blockList) {
                if (s == null || s.state == null) {
                    continue;
                }
                int x = s.x, y = s.y, z = s.z;
                switch (axis) {
                    case "Y" -> y = obj.ySize - 1 - y;
                    case "Z" -> z = obj.zSize - 1 - z;
                    default -> x = obj.xSize - 1 - x;
                }
                // ★向きも反転する (Rotate と同じ理由)。Y は上下反転なので状態は触らない。
                BlockState st = switch (axis) {
                    case "Z" -> s.state.mirror(net.minecraft.world.level.block.Mirror.FRONT_BACK);
                    case "Y" -> s.state;
                    default -> s.state.mirror(net.minecraft.world.level.block.Mirror.LEFT_RIGHT);
                };
                out.add(new BlockSet(x, y, z, st, s.nbt));
            }
            CLIPBOARD.put(editor.getUUID(), NGTObject.createNGTO(out, obj.xSize, obj.ySize, obj.zSize, 0, 0, 0));
            return out.size();
        }
    }

    /** 選択範囲をミニチュアアイテムにして渡す (本家 GuiEditor の Miniature)。 */
    public static final class Miniature extends EditFilter {
        private final Copy copy = new Copy();

        @Override
        public String name() {
            return "Miniature";
        }

        @Override
        protected void initConfig(FilterConfig cfg) {
            cfg.addFloat("Scale", 0.0F, 0.0F, 16.0F);
            cfg.addString("Name", "");
        }

        @Override
        public int apply(ServerLevel level, Player player, EditorSelection editor) {
            if (copy.apply(level, player, editor) == 0) {
                return 0;
            }
            NGTObject obj = CLIPBOARD.get(editor.getUUID());
            if (obj == null) {
                return 0;
            }
            ItemStack stack = new ItemStack(
                com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems.MINIATURE_ITEM.get());
            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            jp.ngt.mcte.item.ItemMiniature.setNGTObject(obj, tag);
            float scale = config().getFloat("Scale");
            if (scale <= 0.0F) {
                // 0 なら 1 ブロックに収まる縮尺を自動で入れる
                scale = 1.0F / Math.max(1, Math.max(obj.xSize, Math.max(obj.ySize, obj.zSize)));
            }
            jp.ngt.mcte.item.ItemMiniature.setScale(scale, tag);
            jp.ngt.mcte.item.ItemMiniature.setMode(tag, jp.ngt.mcte.item.ItemMiniature.MiniatureMode.MINIATURE);
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(tag));
            String name = config().getString("Name");
            if (name != null && !name.isBlank()) {
                stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    net.minecraft.network.chat.Component.literal(name.trim()));
            }
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            return obj.blockList.size();
        }
    }

    /** 選択範囲を NGTO ファイルへ書き出す (本家 GuiEditor の Export)。 */
    public static final class Export extends EditFilter {
        private final Copy copy = new Copy();

        @Override
        public String name() {
            return "Export";
        }

        @Override
        protected void initConfig(FilterConfig cfg) {
            cfg.addString("Name", "");
        }

        @Override
        public int apply(ServerLevel level, Player player, EditorSelection editor) {
            String name = sanitize(config().getString("Name"));
            if (name == null || copy.apply(level, player, editor) == 0) {
                return 0;
            }
            NGTObject obj = CLIPBOARD.get(editor.getUUID());
            if (obj == null) {
                return 0;
            }
            try {
                obj.exportToFile(
                    com.portofino.realtrainmodunofficial.network.MiniatureFiles.dir().resolve(name).toFile());
            } catch (Exception e) {
                return 0;
            }
            return obj.blockList.size();
        }
    }

    /** NGTO ファイルをクリップボードへ読み込む (本家 GuiEditor の Import)。貼り付けで出す。 */
    public static final class Import extends EditFilter {
        @Override
        public String name() {
            return "Import";
        }

        @Override
        protected void initConfig(FilterConfig cfg) {
            cfg.addString("Name", "");
        }

        @Override
        public int apply(ServerLevel level, Player player, EditorSelection editor) {
            String name = sanitize(config().getString("Name"));
            if (name == null) {
                return 0;
            }
            java.io.File file =
                com.portofino.realtrainmodunofficial.network.MiniatureFiles.dir().resolve(name).toFile();
            if (!file.isFile()) {
                return 0;
            }
            NGTObject obj;
            try {
                obj = NGTObject.importFromFile(file);
            } catch (Exception e) {
                return 0;
            }
            if (obj == null) {
                return 0;
            }
            CLIPBOARD.put(editor.getUUID(), obj);
            return obj.blockList.size();
        }
    }

    /** ファイル名の検査。パス区切りを含むものは弾く (任意のファイルを触らせない)。 */
    private static String sanitize(String name) {
        if (name == null) {
            return null;
        }
        String n = name.trim();
        if (n.isEmpty() || n.contains("/") || n.contains("\\") || n.contains("..")) {
            return null;
        }
        return n.endsWith(com.portofino.realtrainmodunofficial.network.MiniatureFiles.EXTENSION)
            ? n : n + com.portofino.realtrainmodunofficial.network.MiniatureFiles.EXTENSION;
    }

    /**
     * 選択範囲に何が何個あるかを数える (neo mcte 追加)。
     * 置換する前に「何を置き換えるのか」を知りたい場面が多い。
     */
    public static final class Analyze extends EditFilter {
        @Override
        public String name() {
            return "Analyze";
        }

        @Override
        protected void initConfig(FilterConfig cfg) {
            cfg.addInt("Top", 10, 1, 50);
            cfg.addBoolean("CountAir", false);
        }

        @Override
        public int apply(ServerLevel level, Player player, EditorSelection editor) {
            boolean countAir = config().getBoolean("CountAir");
            java.util.Map<Block, Integer> counts = new java.util.HashMap<>();
            int[] total = {0};
            EditorOps.forEach(editor, pos -> {
                BlockState s = level.getBlockState(pos);
                if (!countAir && s.isAir()) {
                    return;
                }
                counts.merge(s.getBlock(), 1, Integer::sum);
                total[0]++;
            });
            if (counts.isEmpty()) {
                player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("Analyze: empty"), false);
                return 0;
            }
            int top = config().getInt("Top");
            List<java.util.Map.Entry<Block, Integer>> sorted = new ArrayList<>(counts.entrySet());
            sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                String.format("Analyze: %,d blocks / %d kinds", total[0], counts.size()))
                .withStyle(net.minecraft.ChatFormatting.AQUA), false);
            for (int i = 0; i < Math.min(top, sorted.size()); i++) {
                var e = sorted.get(i);
                double pct = 100.0D * e.getValue() / total[0];
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    String.format("  %-28s %,7d  %5.1f%%",
                        BuiltInRegistries.BLOCK.getKey(e.getKey()), e.getValue(), pct))
                    .withStyle(net.minecraft.ChatFormatting.GRAY), false);
            }
            // ワールドは変えないが「0 件」扱いにすると失敗に見えるので総数を返す
            return total[0];
        }
    }

    /**
     * 配列複製 (neo mcte 追加)。
     * Clone は同じ向きのまま並べるだけなので、螺旋階段・曲線に沿った柱・
     * 円形の建物のように「1 個ずつ回しながら並べる」ものが作れなかった。
     */
    public static final class Array extends EditFilter {
        private final Copy copy = new Copy();

        @Override
        public String name() {
            return "Array";
        }

        @Override
        protected void initConfig(FilterConfig cfg) {
            cfg.addInt("X", 0, -256, 256);
            cfg.addInt("Y", 0, -256, 256);
            cfg.addInt("Z", 0, -256, 256);
            cfg.addInt("Repeat", 4, 1, 64);
            // 1 個進むごとの回転量 (×90 度)。1 なら 4 個で 1 周する
            cfg.addInt("RotateStep", 1, 0, 3);
            // 回した分だけ進む向きも回すか。螺旋階段は true、柱を回すだけなら false
            cfg.addBoolean("RotateOffset", true);
        }

        @Override
        public int apply(ServerLevel level, Player player, EditorSelection editor) {
            int dx = config().getInt("X");
            int dy = config().getInt("Y");
            int dz = config().getInt("Z");
            int repeat = Math.max(1, config().getInt("Repeat"));
            int rotStep = config().getInt("RotateStep");
            boolean rotateOffset = config().getBoolean("RotateOffset");
            if (dx == 0 && dy == 0 && dz == 0 && rotStep == 0) {
                return 0;
            }
            if (copy.apply(level, player, editor) == 0) {
                return 0;
            }
            NGTObject obj = CLIPBOARD.get(editor.getUUID());
            if (obj == null) {
                return 0;
            }
            AABB b = editor.getSelectionBox();
            BlockPos origin = new BlockPos((int) Math.floor(b.minX), (int) Math.floor(b.minY), (int) Math.floor(b.minZ));

            UndoHistory.Snapshot snapshot = new UndoHistory.Snapshot();
            int count = 0;
            // 進む向き。RotateOffset なら 1 個ごとに一緒に回す
            int ox = 0, oy = 0, oz = 0;
            int stepX = dx, stepZ = dz;
            for (int r = 1; r <= repeat && count < EditorOps.MAX_BLOCKS; r++) {
                if (rotateOffset) {
                    for (int i = 0; i < rotStep; i++) {
                        int t = stepX;
                        stepX = -stepZ;
                        stepZ = t;
                    }
                }
                ox += stepX;
                oy += dy;
                oz += stepZ;

                int turns = (rotStep * r) & 3;
                BlockPos base = origin.offset(ox, oy, oz);
                for (BlockSet set : obj.blockList) {
                    if (set == null || set.state == null || set.state.isAir()) {
                        continue;
                    }
                    if (count >= EditorOps.MAX_BLOCKS) {
                        break;
                    }
                    int rx = set.x, ry = set.y, rz = set.z;
                    int w = obj.xSize, d = obj.zSize;
                    BlockState st = set.state;
                    for (int i = 0; i < turns; i++) {
                        int t = d - 1 - rz;
                        rz = rx;
                        rx = t;
                        int tw = w;
                        w = d;
                        d = tw;
                        st = st.rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90);
                    }
                    BlockPos p = base.offset(rx, ry, rz);
                    snapshot.record(level, p, level.getBlockState(p));
                    level.setBlock(p, st, 3);
                    if (set.nbt != null && level.getBlockEntity(p) != null) {
                        try {
                            net.minecraft.nbt.CompoundTag t = set.nbt.copy();
                            RailPaste.shift(t, p.getX() - (obj.origX + set.x),
                                p.getY() - (obj.origY + set.y), p.getZ() - (obj.origZ + set.z));
                            level.getBlockEntity(p).loadWithComponents(t, level.registryAccess());
                            level.getBlockEntity(p).setChanged();
                            RailPaste.finish(level, p);
                        } catch (Exception ignored) {
                            // 読めない BE は飛ばす
                        }
                    }
                    count++;
                }
            }
            if (count > 0) {
                UndoHistory.push(editor, snapshot);
            }
            return count;
        }
    }

    /**
     * 選択範囲に内接する楕円体で埋める (neo mcte 追加)。
     * ドームや丸屋根を手で積むと必ず歪む。範囲を取って実行するだけで正確な形が出る。
     */
    public static final class Ellipsoid extends EditFilter {
        @Override
        public String name() {
            return "Ellipsoid";
        }

        @Override
        protected void initConfig(FilterConfig cfg) {
            cfg.addString("Block", "");
            cfg.addBoolean("Hollow", false);
            // 上半分だけ (ドーム)
            cfg.addBoolean("TopHalf", false);
        }

        @Override
        public int apply(ServerLevel level, Player player, EditorSelection editor) {
            Block named = blockByName(config().getString("Block"));
            BlockState to = named != null ? named.defaultBlockState()
                : slotOrOffhand(editor, player, EditorSelection.SLOT_FILL);
            if (to == null) {
                return 0;
            }
            boolean hollow = config().getBoolean("Hollow");
            boolean topHalf = config().getBoolean("TopHalf");
            AABB b = editor.getSelectionBox();
            double cx = (b.minX + b.maxX) * 0.5D, cy = (b.minY + b.maxY) * 0.5D, cz = (b.minZ + b.maxZ) * 0.5D;
            double rx = Math.max(0.5D, b.getXsize() * 0.5D);
            double ry = Math.max(0.5D, b.getYsize() * 0.5D);
            double rz = Math.max(0.5D, b.getZsize() * 0.5D);
            double baseY = topHalf ? cy : Double.NEGATIVE_INFINITY;
            return EditorOps.replace(level, editor, to, (pos, cur) -> {
                double px = pos.getX() + 0.5D, py = pos.getY() + 0.5D, pz = pos.getZ() + 0.5D;
                if (py < baseY) {
                    return false;
                }
                double d = norm(px - cx, rx) + norm(py - cy, ry) + norm(pz - cz, rz);
                if (d > 1.0D) {
                    return false;
                }
                if (!hollow) {
                    return true;
                }
                // 1 ブロック内側が外に出るなら殻
                double di = norm(px - cx, rx - 1.0D) + norm(py - cy, ry - 1.0D) + norm(pz - cz, rz - 1.0D);
                return di > 1.0D;
            });
        }

        private static double norm(double d, double r) {
            return r <= 0.0D ? (d == 0.0D ? 0.0D : 4.0D) : (d / r) * (d / r);
        }
    }

    /**
     * 選択範囲に内接する円柱で埋める (neo mcte 追加)。
     * 塔・煙突・トンネルの断面。軸は縦だけでなく横にも取れる。
     */
    public static final class Cylinder extends EditFilter {
        @Override
        public String name() {
            return "Cylinder";
        }

        @Override
        protected void initConfig(FilterConfig cfg) {
            cfg.addString("Block", "");
            cfg.addEnum("Axis", "Y", List.of("X", "Y", "Z"));
            cfg.addBoolean("Hollow", false);
        }

        @Override
        public int apply(ServerLevel level, Player player, EditorSelection editor) {
            Block named = blockByName(config().getString("Block"));
            BlockState to = named != null ? named.defaultBlockState()
                : slotOrOffhand(editor, player, EditorSelection.SLOT_FILL);
            if (to == null) {
                return 0;
            }
            String axis = config().getString("Axis");
            boolean hollow = config().getBoolean("Hollow");
            AABB b = editor.getSelectionBox();
            double cx = (b.minX + b.maxX) * 0.5D, cy = (b.minY + b.maxY) * 0.5D, cz = (b.minZ + b.maxZ) * 0.5D;
            double ex = Math.max(0.5D, b.getXsize() * 0.5D);
            double ey = Math.max(0.5D, b.getYsize() * 0.5D);
            double ez = Math.max(0.5D, b.getZsize() * 0.5D);
            return EditorOps.replace(level, editor, to, (pos, cur) -> {
                double px = pos.getX() + 0.5D, py = pos.getY() + 0.5D, pz = pos.getZ() + 0.5D;
                double u, v, ru, rv;
                switch (axis) {
                    case "X" -> { u = py - cy; v = pz - cz; ru = ey; rv = ez; }
                    case "Z" -> { u = px - cx; v = py - cy; ru = ex; rv = ey; }
                    default -> { u = px - cx; v = pz - cz; ru = ex; rv = ez; }
                }
                double d = (u / ru) * (u / ru) + (v / rv) * (v / rv);
                if (d > 1.0D) {
                    return false;
                }
                if (!hollow) {
                    return true;
                }
                double ru2 = Math.max(0.001D, ru - 1.0D), rv2 = Math.max(0.001D, rv - 1.0D);
                return (u / ru2) * (u / ru2) + (v / rv2) * (v / rv2) > 1.0D;
            });
        }
    }

    /** 選択範囲の辺だけを埋める (neo mcte 追加)。範囲の当たりを付けるのに使う。 */
    public static final class Outline extends EditFilter {
        @Override
        public String name() {
            return "Outline";
        }

        @Override
        protected void initConfig(FilterConfig cfg) {
            cfg.addString("Block", "");
        }

        @Override
        public int apply(ServerLevel level, Player player, EditorSelection editor) {
            Block named = blockByName(config().getString("Block"));
            BlockState to = named != null ? named.defaultBlockState()
                : slotOrOffhand(editor, player, EditorSelection.SLOT_FILL);
            if (to == null) {
                return 0;
            }
            AABB b = editor.getSelectionBox();
            return EditorOps.replace(level, editor, to, (pos, cur) -> edges(b, pos) >= 2);
        }
    }

    /** 選択範囲の側面 4 枚だけを埋める (neo mcte 追加)。壁を一気に立てる。 */
    public static final class Walls extends EditFilter {
        @Override
        public String name() {
            return "Walls";
        }

        @Override
        protected void initConfig(FilterConfig cfg) {
            cfg.addString("Block", "");
            // 上下の面も張ると箱になる
            cfg.addBoolean("WithFloorCeil", false);
        }

        @Override
        public int apply(ServerLevel level, Player player, EditorSelection editor) {
            Block named = blockByName(config().getString("Block"));
            BlockState to = named != null ? named.defaultBlockState()
                : slotOrOffhand(editor, player, EditorSelection.SLOT_FILL);
            if (to == null) {
                return 0;
            }
            boolean withY = config().getBoolean("WithFloorCeil");
            AABB b = editor.getSelectionBox();
            int x0 = (int) Math.floor(b.minX), x1 = (int) Math.ceil(b.maxX) - 1;
            int y0 = (int) Math.floor(b.minY), y1 = (int) Math.ceil(b.maxY) - 1;
            int z0 = (int) Math.floor(b.minZ), z1 = (int) Math.ceil(b.maxZ) - 1;
            return EditorOps.replace(level, editor, to, (pos, cur) ->
                pos.getX() == x0 || pos.getX() == x1 || pos.getZ() == z0 || pos.getZ() == z1
                    || (withY && (pos.getY() == y0 || pos.getY() == y1)));
        }
    }

    /**
     * 地面の上に一層敷く (neo mcte 追加)。
     * 草地に道を敷く、屋根の上に雪を載せる、といった作業が 1 回で済む。
     */
    public static final class Overlay extends EditFilter {
        @Override
        public String name() {
            return "Overlay";
        }

        @Override
        protected void initConfig(FilterConfig cfg) {
            cfg.addString("Block", "");
            // 置き換える対象を「空気のみ」に絞るか。false なら草や雪の上にも重ねる
            cfg.addBoolean("AirOnly", true);
        }

        @Override
        public int apply(ServerLevel level, Player player, EditorSelection editor) {
            Block named = blockByName(config().getString("Block"));
            BlockState to = named != null ? named.defaultBlockState()
                : slotOrOffhand(editor, player, EditorSelection.SLOT_FILL);
            if (to == null) {
                return 0;
            }
            boolean airOnly = config().getBoolean("AirOnly");
            return EditorOps.replace(level, editor, to, (pos, cur) -> {
                if (airOnly ? !cur.isAir() : !(cur.isAir() || cur.canBeReplaced())) {
                    return false;
                }
                BlockState below = level.getBlockState(pos.below());
                // 真下が中身の詰まったブロックのときだけ載せる
                return !below.isAir() && !below.equals(to) && below.isSolidRender(level, pos.below());
            });
        }
    }

    /**
     * でこぼこをならす (neo mcte 追加)。
     * 周り 6 方向のうち何個が埋まっているかを見て、飛び出しは削り、へこみは埋める。
     */
    public static final class Smooth extends EditFilter {
        @Override
        public String name() {
            return "Smooth";
        }

        @Override
        protected void initConfig(FilterConfig cfg) {
            // 削る閾値: 周りがこれ以下なら消す
            cfg.addInt("EraseAt", 1, 0, 5);
            // 埋める閾値: 周りがこれ以上なら埋める
            cfg.addInt("FillAt", 5, 1, 6);
            cfg.addInt("Passes", 1, 1, 4);
        }

        @Override
        public int apply(ServerLevel level, Player player, EditorSelection editor) {
            int eraseAt = config().getInt("EraseAt");
            int fillAt = config().getInt("FillAt");
            int passes = Math.max(1, config().getInt("Passes"));
            UndoHistory.Snapshot snapshot = new UndoHistory.Snapshot();
            int[] count = {0};
            for (int pass = 0; pass < passes; pass++) {
                // 1 周ぶんの判定を先に集めてから流す。途中の結果が次の判定に混ざらないようにする
                List<BlockPos> erase = new ArrayList<>();
                List<BlockPos> fill = new ArrayList<>();
                EditorOps.forEach(editor, pos -> {
                    BlockState cur = level.getBlockState(pos);
                    int n = neighbors(level, pos);
                    if (!cur.isAir() && n <= eraseAt) {
                        erase.add(pos.immutable());
                    } else if (cur.isAir() && n >= fillAt) {
                        fill.add(pos.immutable());
                    }
                });
                for (BlockPos p : erase) {
                    if (count[0] >= EditorOps.MAX_BLOCKS) {
                        break;
                    }
                    snapshot.record(level, p, level.getBlockState(p));
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                    count[0]++;
                }
                for (BlockPos p : fill) {
                    if (count[0] >= EditorOps.MAX_BLOCKS) {
                        break;
                    }
                    BlockState mat = materialAround(level, p);
                    if (mat == null) {
                        continue;
                    }
                    snapshot.record(level, p, level.getBlockState(p));
                    level.setBlock(p, mat, 3);
                    count[0]++;
                }
                if (erase.isEmpty() && fill.isEmpty()) {
                    break;
                }
            }
            if (count[0] > 0) {
                UndoHistory.push(editor, snapshot);
            }
            return count[0];
        }

        /** 6 方向のうち埋まっている数。 */
        private static int neighbors(ServerLevel level, BlockPos pos) {
            int n = 0;
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                BlockState s = level.getBlockState(pos.relative(d));
                if (!s.isAir() && !s.canBeReplaced()) {
                    n++;
                }
            }
            return n;
        }

        /** 埋める材料。真下 → 周り の順に探す。 */
        private static BlockState materialAround(ServerLevel level, BlockPos pos) {
            BlockState below = level.getBlockState(pos.below());
            if (!below.isAir() && below.isSolidRender(level, pos.below())) {
                return below;
            }
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                BlockState s = level.getBlockState(pos.relative(d));
                if (!s.isAir() && s.isSolidRender(level, pos.relative(d))) {
                    return s;
                }
            }
            return null;
        }
    }

    /** 位置が範囲の端に接している軸の数 (0〜3)。 */
    private static int edges(AABB b, BlockPos pos) {
        int n = 0;
        if (pos.getX() == (int) Math.floor(b.minX) || pos.getX() == (int) Math.ceil(b.maxX) - 1) n++;
        if (pos.getY() == (int) Math.floor(b.minY) || pos.getY() == (int) Math.ceil(b.maxY) - 1) n++;
        if (pos.getZ() == (int) Math.floor(b.minZ) || pos.getZ() == (int) Math.ceil(b.maxZ) - 1) n++;
        return n;
    }
}
