package com.portofino.realtrainmodunofficial.convert;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlocks;
import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectCategory;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectDefinition;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectRegistry;
import jp.ngt.rtm.rail.BlockMarker;
import jp.ngt.rtm.rail.util.RailPosition;
import jp.ngt.rtm.rail.util.RailProperty;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * 変換したワールドを最初に開いたときに、旧 RTM のオブジェクトを RTMU のオブジェクトとして置き直す。
 *
 * <p>ここでやる理由は、モデルパックのレジストリ (どの車両/レール/設置物が入っているか) が
 * <b>ゲームが起動していないと引けない</b>ため。変換ツール側は旧 NBT をそのまま保存しておき、
 * 名前から定義を解決するのはこの復元時に行う。
 *
 * <p>1 tick に少しずつ置いていく (一気に置くとチャンク読み込みでサーバーが固まる)。
 */
public final class LegacyRestorer {

    /** 1 tick に処理する数。 */
    private static final int PER_TICK = 64;

    /** ただのブロック置き換えは軽いので、まとめて多めに処理する。 */
    private static final int BLOCKS_PER_TICK = 8192;

    private static final Deque<RestoreData.ObjectRecord> OBJECTS = new ArrayDeque<>();
    private static final Deque<RestoreData.EntityRecord> ENTITIES = new ArrayDeque<>();
    private static final Deque<RestoreData.BlockRecord> BLOCKS = new ArrayDeque<>();
    private static int blocksPlaced;

    private static Path worldDir;
    private static boolean running;
    private static int placed;
    private static int failed;
    private static int total;
    private static final List<String> MISSING = new ArrayList<>();

    private LegacyRestorer() {
    }

    /** サーバー起動時に呼ぶ。復元データがあれば読み込んでキューに積む。 */
    public static void onServerStarted(MinecraftServer server) {
        reset();
        Path dir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize();
        Path file = dir.resolve(RestoreData.FILE_NAME);
        if (!Files.isRegularFile(file)) {
            return;
        }
        RestoreData data;
        try {
            data = RestoreData.read(file);
        } catch (IOException e) {
            RealTrainModUnofficial.LOGGER.error("[convert] 復元データが読めません: {}", file, e);
            return;
        }
        worldDir = dir;
        blocksPlaced = 0;
        //レールを先に敷く。あとから置く設置物がレールのマスに当たったら 1 段上へ逃がすので、
        //順番が逆だとレールが設置物を上書きしてしまう。
        List<RestoreData.ObjectRecord> ordered = new ArrayList<>(data.objects);
        ordered.sort(java.util.Comparator.comparingInt(r -> isRail(r.type) ? 0 : 1));
        OBJECTS.addAll(ordered);
        ENTITIES.addAll(data.entities);
        //MOD ブロックのバニラ化はレールより先に済ませる (地形が先に戻っていた方が素直)
        BLOCKS.addAll(data.blocks);
        total = OBJECTS.size() + ENTITIES.size();
        running = total > 0 || !BLOCKS.isEmpty();
    }

    /** サーバー tick から呼ぶ。 */
    public static void tick(MinecraftServer server) {
        if (!running) {
            return;
        }
        //まず MOD ブロックをバニラに戻す (数が多いのでまとめて)
        int blockBudget = BLOCKS_PER_TICK;
        while (blockBudget-- > 0 && !BLOCKS.isEmpty()) {
            placeVanillaBlock(server, BLOCKS.poll());
        }
        if (!BLOCKS.isEmpty()) {
            //ブロックを置き切るまで RTM オブジェクトには進まない
            return;
        }

        int budget = PER_TICK;
        while (budget-- > 0 && !OBJECTS.isEmpty()) {
            place(server, OBJECTS.poll());
        }
        while (budget-- > 0 && OBJECTS.isEmpty() && !ENTITIES.isEmpty()) {
            spawn(server, ENTITIES.poll());
        }
        if (OBJECTS.isEmpty() && ENTITIES.isEmpty()) {
            finish();
        }
    }

    /**
     * 旧 MOD のブロックをバニラブロックとして置き直す。
     * <p>
     * Minecraft の変換 (DataFixer) は MOD のブロックを知らないので<b>空気にしてしまう</b>。
     * UpToDateMod のように「新しいバージョンのバニラブロックを 1.7.10 に足す」MOD は、
     * 本来のバニラブロックへそのまま戻せる。
     */
    private static void placeVanillaBlock(MinecraftServer server, RestoreData.BlockRecord rec) {
        try {
            ServerLevel level = levelOf(server, rec.dimension);
            if (level == null) {
                return;
            }
            net.minecraft.world.level.block.state.BlockState state =
                    LegacyVanillaBlocks.toVanilla(rec.name, rec.meta);
            if (state == null) {
                return;
            }
            BlockPos pos = new BlockPos(rec.x, rec.y, rec.z);
            //flag 2 = クライアントへ送るだけ (隣接更新を走らせない = 大量に置いても軽い)
            level.setBlock(pos, state, 2);
            blocksPlaced++;
        } catch (Throwable t) {
            RealTrainModUnofficial.LOGGER.warn("[convert] ブロックの復元に失敗 {},{},{} ({})",
                    rec.x, rec.y, rec.z, rec.name, t);
        }
    }

    private static void finish() {
        running = false;
        if (blocksPlaced > 0) {
        }
        MISSING.stream().distinct().sorted().forEach(name ->
                RealTrainModUnofficial.LOGGER.warn("[convert] モデルが見つかりません (パックを入れてください): {}", name));
        MISSING_POLES.forEach(name ->
                RealTrainModUnofficial.LOGGER.warn("[convert] 信号の土台の柱は復元できません (同じマスに置けないため): {}", name));
        MISSING_POLES.clear();
        if (worldDir != null) {
            try {
                Files.move(worldDir.resolve(RestoreData.FILE_NAME), worldDir.resolve(RestoreData.DONE_NAME),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                RealTrainModUnofficial.LOGGER.warn("[convert] 復元データの後始末に失敗", e);
            }
        }
        reset();
    }

    private static void reset() {
        OBJECTS.clear();
        ENTITIES.clear();
        BLOCKS.clear();
        MISSING.clear();
        worldDir = null;
        running = false;
        placed = 0;
        failed = 0;
        total = 0;
    }

    private static boolean isRail(String type) {
        LegacyIds.Kind kind = LegacyIds.tileKind(type);
        return kind == LegacyIds.Kind.RAIL || kind == LegacyIds.Kind.RAIL_SWITCH || kind == LegacyIds.Kind.TURNTABLE;
    }

    /**
     * レールのマスを避ける。
     * <p>
     * 本家では車止め・列車検知器は<b>エンティティ</b>なのでレールの上に「乗る」だけだが、
     * RTMU ではブロックなので、レールのマスにそのまま置くと<b>レールの土台を踏み抜く</b>。
     * 土台が壊れるとレール全体を撤去する処理が走るため、敷いたレールごと消えてしまう。
     * (実際にこれで復元したレールが 122 ブロックまとめて自壊していた)
     * <p>
     * レールに当たったら 1 段ずつ上へ逃がす。描画は renderOffset で元の位置に戻すので見た目は変わらない。
     */
    private static BlockPos avoidRail(ServerLevel level, BlockPos pos) {
        BlockPos p = pos;
        for (int i = 0; i < 4; i++) {
            if (!(level.getBlockState(p).getBlock() instanceof jp.ngt.rtm.rail.BlockLargeRailBase)) {
                return p;
            }
            p = p.above();
        }
        return p;
    }

    // ---- 配置 ----

    private static void place(MinecraftServer server, RestoreData.ObjectRecord rec) {
        ServerLevel level = levelOf(server, rec.dimension);
        if (level == null) {
            failed++;
            return;
        }
        try {
            LegacyIds.Kind kind = LegacyIds.tileKind(rec.type);
            if (kind == null) {
                failed++;
                return;
            }
            boolean ok = switch (kind) {
                case RAIL, RAIL_SWITCH, TURNTABLE -> placeRail(level, rec);
                case INSTALLED_OBJECT -> placeInstalledObject(level, rec);
                default -> false;
            };
            if (ok) {
                placed++;
            } else {
                failed++;
            }
        } catch (Throwable t) {
            failed++;
            RealTrainModUnofficial.LOGGER.warn("[convert] 配置に失敗 {} @ {},{},{}", rec.type, rec.x, rec.y, rec.z, t);
        }
    }

    /**
     * レール。旧 RTM と RTMU は RailPosition の NBT キーが同一 (BlockPos / Direction / A_* / C_* …) なので、
     * そのまま読み込める。違うのはレール自身の設定で、旧 "State" (ResourceState) が
     * RTMU では "Property" (RailProperty) になっている点だけ。
     */
    private static boolean placeRail(ServerLevel level, RestoreData.ObjectRecord rec) {
        List<RailPosition> rps = readRailPositions(rec.nbt);
        if (rps.size() < 2) {
            return false;
        }
        RailProperty prop = readRailProperty(rec.nbt);

        //★ 先に「死んだ古いレールブロック」を掃除する。
        //
        //旧ワールドのレール土台ブロックは、変換後も RTMU のレール土台ブロックとして<b>残っている</b>
        //(ブロック名が一致するため)。ところが中身のブロックエンティティは旧 MOD のもので読み込めず、
        //空の状態で作り直される。この土台は「自分のコアが見つからない」と判断して自壊し、
        //その巻き添えで<b>新しく敷いたレールごと消えてしまう</b>。
        //コアを持たない = 死んでいる土台だけを先に取り除く (生きている別のレールは壊さない)。
        clearDeadRailBlocks(level, rps, prop);

        //本家の "makeRail" = 土台ブロックも作る、"isCreative" = 資材を消費しない
        boolean ok = BlockMarker.createRail(level, rec.x, rec.y, rec.z, rps, prop, true, true);
        if (ok) {
            //[診断] 敷いた直後のコアと土台の中身を出す (モデルが「適当なもの」になる原因の切り分け)
            List<RailPosition> sorted = new java.util.ArrayList<>(rps);
            sorted.sort(java.util.Comparator.comparingInt(o -> o.blockY));
            RailPosition first = sorted.get(0);
            BlockPos corePos = new BlockPos(first.blockX, first.blockY, first.blockZ);
            net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(corePos);
            if (be instanceof jp.ngt.rtm.rail.TileEntityLargeRailCore core) {
            } else {
                RealTrainModUnofficial.LOGGER.warn("[convert-diag] コアが見つかりません @ {} (be={})", corePos, be);
            }
            //土台の起点を数える
            int okBase = 0;
            int badBase = 0;
            for (int dx = -6; dx <= 6; dx++) {
                for (int dz = -6; dz <= 6; dz++) {
                    BlockPos p2 = corePos.offset(dx, 0, dz);
                    if (level.getBlockEntity(p2) instanceof jp.ngt.rtm.rail.TileEntityLargeRailBase base
                            && !(base instanceof jp.ngt.rtm.rail.TileEntityLargeRailCore)) {
                        int[] sp = base.getStartPoint();
                        if (sp[0] == corePos.getX() && sp[1] == corePos.getY() && sp[2] == corePos.getZ()) {
                            okBase++;
                        } else {
                            badBase++;
                        }
                    }
                }
            }
        }
        if (ok && com.portofino.realtrainmodunofficial.rail.RailRegistry.getById(prop.railModel) == null) {
            RealTrainModUnofficial.LOGGER.warn("[convert] レールのモデル {} が見つかりません (パックを入れてください)", prop.railModel);
        }
        return ok;
    }

    /**
     * レールが通る範囲にある「コアを持たないレール土台/マーカー」を取り除く。
     * <p>
     * 生きているレール (コアが解決できる土台) には触らないので、既に復元済みの別のレールは壊れない。
     */
    private static void clearDeadRailBlocks(ServerLevel level, List<RailPosition> rps, RailProperty prop) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (RailPosition rp : rps) {
            minX = Math.min(minX, rp.blockX);
            minY = Math.min(minY, rp.blockY);
            minZ = Math.min(minZ, rp.blockZ);
            maxX = Math.max(maxX, rp.blockX);
            maxY = Math.max(maxY, rp.blockY);
            maxZ = Math.max(maxZ, rp.blockZ);
        }
        //曲線は端点の外へ膨らむ。道床の幅ぶんも見て余裕をとる。
        final int margin = 8;
        minX -= margin;
        minZ -= margin;
        maxX += margin;
        maxZ += margin;
        minY -= 2;
        maxY += 2;

        //異常に巨大な範囲は掃除しない (安全弁)
        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (volume > 400_000L) {
            return;
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int removed = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);
                    net.minecraft.world.level.block.Block block = level.getBlockState(pos).getBlock();
                    boolean dead = false;
                    if (block instanceof jp.ngt.rtm.rail.BlockMarker) {
                        dead = true;
                    } else if (block instanceof jp.ngt.rtm.rail.BlockLargeRailBase) {
                        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
                        //コアが解決できない = 旧ワールドの残骸
                        dead = !(be instanceof jp.ngt.rtm.rail.TileEntityLargeRailBase base)
                                || base.getRailCore() == null;
                    }
                    if (dead) {
                        level.setBlock(pos.immutable(), Blocks.AIR.defaultBlockState(), 2);
                        removed++;
                    }
                }
            }
        }
        if (removed > 0) {
        }
    }

    private static List<RailPosition> readRailPositions(CompoundTag nbt) {
        List<RailPosition> list = new ArrayList<>();
        if (nbt.contains("Size")) {
            //分岐・クロス: RP0..RPn
            int size = nbt.getByte("Size");
            for (int i = 0; i < size; i++) {
                CompoundTag rp = nbt.getCompound("RP" + i);
                if (!rp.isEmpty()) {
                    list.add(RailPosition.readFromNBT(rp));
                }
            }
        } else if (nbt.contains("StartRP")) {
            list.add(RailPosition.readFromNBT(nbt.getCompound("StartRP")));
            list.add(RailPosition.readFromNBT(nbt.getCompound("EndRP")));
        }
        return list;
    }

    /**
     * 旧 "State" (ResourceStateRail) → RTMU の RailProperty。
     * <pre>
     *   State.ResourceName   → レールのモデル名
     *   State.BlockName      → 道床ブロック (1.12 の名前。バニラはだいたいそのまま通る)
     *   State.BlockMetadata  → 道床のメタ (1.12 の variant。1.21 では使わないが保持する)
     *   State.BlockHeight    → 道床の高さ
     * </pre>
     * RTMU 側が既に "Property" で保存している (= 一度 RTMU で開いたワールド) 場合はそれを使う。
     */
    private static RailProperty readRailProperty(CompoundTag nbt) {
        if (nbt.contains("Property")) {
            return RailProperty.readFromNBT(nbt.getCompound("Property"));
        }
        CompoundTag state = nbt.getCompound("State");
        String model = state.getString("ResourceName");
        Block block = resolveBlock(state.getString("BlockName"));
        int meta = state.contains("BlockMetadata") ? state.getByte("BlockMetadata") : 0;
        float height = state.contains("BlockHeight") ? state.getFloat("BlockHeight") : 0.0625F;
        return new RailProperty(model, block, meta, height);
    }

    /** 1.12 のブロック名 → 1.21 のブロック。見つからなければ砂利 (本家の既定の道床)。 */
    private static Block resolveBlock(String name) {
        if (name == null || name.isBlank()) {
            return Blocks.GRAVEL;
        }
        String id = name.contains(":") ? name : "minecraft:" + name;
        ResourceLocation loc = ResourceLocation.tryParse(id.toLowerCase(Locale.ROOT));
        if (loc == null) {
            return Blocks.GRAVEL;
        }
        Block block = BuiltInRegistries.BLOCK.get(loc);
        return block == Blocks.AIR ? Blocks.GRAVEL : block;
    }

    /**
     * 設置物。旧 RTM はどれも ResourceState を持ち、その "ResourceName" がモデル名
     * ("Fluorescent01" / "CrossingGate01L" 等)。RTMU では設置物はすべて 1 種類のブロック +
     * 定義 ID で表すので、モデル名からレジストリを引いて定義 ID とカテゴリを決める。
     */
    private static boolean placeInstalledObject(ServerLevel level, RestoreData.ObjectRecord rec) {
        String modelName = modelNameOf(rec.nbt);
        if (modelName.isBlank()) {
            return false;
        }
        InstalledObjectDefinition def = findDefinition(modelName);
        if (def == null) {
            MISSING.add(modelName);
            return false;
        }

        BlockPos pos = avoidRail(level, new BlockPos(rec.x, rec.y, rec.z));
        level.setBlock(pos, RealTrainModUnofficialBlocks.INSTALLED_OBJECT.get().defaultBlockState(), 3);
        if (!(level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity be)) {
            return false;
        }

        //向きは水平角 (Yaw) だけ。
        //本家の "Pitch" は<b>設置した時のプレイヤーの視線の角度</b>であって、モデルの傾きではない
        //(実データで -35 / -33 / -42 度などが入っている)。これを縦の傾きに入れると設置物が
        //全部斜めになる。本家もこの値でモデルを傾けてはいないので、使わない。
        be.setDefinition(def.getId(), def.getCategory(), rec.nbt.getFloat("Yaw"));

        //本家はブロックのメタデータに「クリックした面」を入れていた。碍子と看板はそれで向きが決まる。
        InstalledObjectCategory category = def.getCategory();
        if (category == InstalledObjectCategory.INSULATOR || category == InstalledObjectCategory.SIGNBOARD) {
            if (rec.meta >= 0 && rec.meta <= 5) {
                be.setMountFace(rec.meta);
            }
        }
        //蛍光灯は取付方向 (0..7) をメタデータに入れていた
        if (category == InstalledObjectCategory.FLUORESCENT && rec.meta >= 0 && rec.meta <= 7) {
            be.setFluorescentDir((byte) rec.meta);
        }
        //看板は TileEntity に向き (dir 0-3) を持っている
        if (category == InstalledObjectCategory.SIGNBOARD && rec.nbt.contains("dir")) {
            be.setSignDirection((byte) rec.nbt.getInt("dir"));
        }
        //本家の AttachedSide は「柱のどの面が接地しているか」(地面に立っていれば 0 = 下)。
        //RTMU の mountFace は「クリックした面」なので意味が逆。地面に立っている柱に 0 を入れると
        //天井付けとして描かれて倒れるため、そのまま流し込まない。

        //1.7.10 の設置物は自分でモデルをずらせる (offsetX/Y/Z)。そのまま引き継ぐ。
        if (rec.nbt.contains("offsetX") || rec.nbt.contains("offsetY") || rec.nbt.contains("offsetZ")) {
            be.setRenderOffset(rec.nbt.getFloat("offsetX"), rec.nbt.getFloat("offsetY"), rec.nbt.getFloat("offsetZ"));
        }
        be.setChanged();

        //碍子は電線 (connections) を持っている。架線を張り直す。
        if (rec.nbt.contains("connections")) {
            restoreWires(level, rec);
        }

        //本家の信号は「土台の柱」を自分の中に抱えている (BaseBlockData)。RTMU では柱は別の
        //設置物なので、同じマスには置けない。柱が失われることをログに出しておく。
        if (category == InstalledObjectCategory.SIGNAL && rec.nbt.contains("BaseBlockData")) {
            CompoundTag base = rec.nbt.getCompound("BaseBlockData");
            String poleName = modelNameOf(base);
            if (!poleName.isBlank()) {
                MISSING_POLES.add(poleName);
            }
        }
        return true;
    }

    /** 信号の土台の柱 (本家は信号ブロックに内包。RTMU では別ブロックなので置けない)。 */
    private static final java.util.Set<String> MISSING_POLES = new java.util.LinkedHashSet<>();

    /**
     * 碍子が持っている電線 (connections) から架線を張り直す。
     * <p>
     * 本家は「碍子が相手の座標と電線の種類を持つ」形。RTMU は<b>電線そのものが 1 つの設置物</b>で、
     * 両端の座標を持つ (WireItem と同じく 2 点の中間マスに置く)。
     * 電線は両側の碍子が同じものを持っているので、IsRoot が立っている側からだけ張る (二重防止)。
     */
    private static void restoreWires(ServerLevel level, RestoreData.ObjectRecord rec) {
        ListTag conns = rec.nbt.getList("connections", Tag.TAG_COMPOUND);
        for (int i = 0; i < conns.size(); i++) {
            CompoundTag c = conns.getCompound(i);
            if (c.getByte("IsRoot") == 0) {
                continue;
            }
            String wireName = modelNameOf(c);
            InstalledObjectDefinition wireDef = findDefinition(wireName);
            if (wireDef == null) {
                MISSING.add(wireName);
                continue;
            }
            BlockPos from = new BlockPos(rec.x, rec.y, rec.z);
            BlockPos to = new BlockPos(c.getInt("x"), c.getInt("y"), c.getInt("z"));
            BlockPos mid = new BlockPos((from.getX() + to.getX()) >> 1,
                    (from.getY() + to.getY()) >> 1, (from.getZ() + to.getZ()) >> 1);
            if (!level.getBlockState(mid).canBeReplaced()) {
                RealTrainModUnofficial.LOGGER.warn("[convert] 架線の中間 {} にブロックがあるため張れません", mid);
                continue;
            }
            level.setBlock(mid, RealTrainModUnofficialBlocks.INSTALLED_OBJECT.get().defaultBlockState(), 3);
            if (level.getBlockEntity(mid) instanceof InstalledObjectBlockEntity wire) {
                wire.setDefinition(wireDef.getId(), InstalledObjectCategory.WIRE, 0.0F);
                wire.setWireEndpoints(from, to);
                wire.setChanged();
                level.sendBlockUpdated(mid, wire.getBlockState(), wire.getBlockState(), 3);
                placed++;
            }
        }
    }

    /**
     * 旧オブジェクトのモデル名を取り出す。バージョンで保存場所が違う。
     * <pre>
     *   1.12.2 : State.ResourceName    (ResourceState)
     *   1.7.10 : ModelName             (トップレベルの文字列)
     *   1.7.10 の標識/看板 : textureName (テクスチャのパス)
     * </pre>
     * 1.7.10 の State には Color / DataMap / Name しか入っておらず ResourceName が無い。
     * ここを見落としていたため、1.7.10 のワールドでは<b>設置物が 1 つも復元できなかった</b>。
     */
    private static String modelNameOf(CompoundTag nbt) {
        String name = nbt.getCompound("State").getString("ResourceName");
        if (name.isBlank()) {
            name = nbt.getString("ModelName");
        }
        if (name.isBlank()) {
            name = nbt.getString("textureName");
        }
        return name;
    }

    /**
     * モデル名から設置物の定義を探す。パック名は問わない (同名なら最初に見つかったもの)。
     *
     * <p>標識と看板だけは本家が <b>モデル名ではなくテクスチャのパス</b> を持っている
     * ("textures/rrs/rrs_01.png" / "textures/signboard/ngt_a01.png")。RTMU 側はそのファイル名を
     * 定義名にしているので、パスの末尾を取り出して突き合わせる。
     */
    private static InstalledObjectDefinition findDefinition(String modelName) {
        InstalledObjectDefinition def = byName(modelName);
        if (def != null) {
            return def;
        }
        if (modelName.contains("/") || modelName.toLowerCase(Locale.ROOT).endsWith(".png")) {
            //テクスチャそのものが一致する定義 (最優先)
            for (InstalledObjectDefinition d : InstalledObjectRegistry.getAll()) {
                String tex = d.getSignTexture();
                if (tex != null && !tex.isBlank() && sameTexture(tex, modelName)) {
                    return d;
                }
            }
            //定義側のテクスチャのファイル名と突き合わせる
            //(看板は定義名が "SignBoard_ngt_a01" なのに本家は "textures/signboard/ngt_a01.png" を持つ)
            String leaf = leafName(modelName);
            for (InstalledObjectDefinition d : InstalledObjectRegistry.getAll()) {
                String tex = d.getSignTexture();
                if (tex != null && !tex.isBlank() && leaf.equalsIgnoreCase(leafName(tex))) {
                    return d;
                }
            }
            //ファイル名 (拡張子なし) で突き合わせる
            return byName(leaf);
        }
        return null;
    }

    private static InstalledObjectDefinition byName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (InstalledObjectDefinition def : InstalledObjectRegistry.getAll()) {
            if (name.equals(def.getDisplayName())) {
                return def;
            }
        }
        for (InstalledObjectDefinition def : InstalledObjectRegistry.getAll()) {
            if (name.equalsIgnoreCase(def.getDisplayName())) {
                return def;
            }
        }
        return null;
    }

    private static boolean sameTexture(String a, String b) {
        return normalizeTexture(a).equals(normalizeTexture(b));
    }

    private static String normalizeTexture(String path) {
        String p = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        int i = p.indexOf("textures/");
        return i >= 0 ? p.substring(i) : p;
    }

    private static String leafName(String path) {
        String p = path.replace('\\', '/');
        int slash = p.lastIndexOf('/');
        String leaf = slash >= 0 ? p.substring(slash + 1) : p;
        int dot = leaf.lastIndexOf('.');
        return dot > 0 ? leaf.substring(0, dot) : leaf;
    }

    // ---- エンティティ ----

    private static void spawn(MinecraftServer server, RestoreData.EntityRecord rec) {
        ServerLevel level = levelOf(server, rec.dimension);
        if (level == null) {
            failed++;
            return;
        }
        try {
            LegacyIds.Kind kind = LegacyIds.entityKind(rec.type);
            if (kind == LegacyIds.Kind.ENTITY_OBJECT) {
                //本家では車止め・列車検知器・ATC は<b>エンティティ</b>だが、RTMU では設置物 (ブロック)。
                //足元のマスにブロックとして置き直す。
                if (placeEntityAsObject(level, rec)) {
                    placed++;
                } else {
                    failed++;
                }
                return;
            }
            if (kind != LegacyIds.Kind.TRAIN) {
                //車 (VEHICLE) は後回し (RTMU 側の車両システムが別系統のため)
                failed++;
                return;
            }
            String modelName = modelNameOf(rec.nbt);
            if (modelName.isBlank()) {
                failed++;
                return;
            }
            double[] pos = readPos(rec.nbt);
            if (pos == null) {
                failed++;
                return;
            }
            float yaw = readRotation(rec.nbt, 0);

            jp.ngt.rtm.entity.train.EntityTrain train =
                    new jp.ngt.rtm.entity.train.EntityTrain(jp.ngt.rtm.entity.RTMEntities.TRAIN.get(), level);
            train.moveTo(pos[0], pos[1], pos[2], yaw, 0.0F);
            train.setModelName(modelName);
            train.spawnTrain(level);
            placed++;
        } catch (Throwable t) {
            failed++;
            RealTrainModUnofficial.LOGGER.warn("[convert] 列車の復元に失敗 {}", rec.type, t);
        }
    }

    /** 本家がエンティティで持っていた設置物 (車止め等) を、RTMU のブロックとして置く。 */
    private static boolean placeEntityAsObject(ServerLevel level, RestoreData.EntityRecord rec) {
        String modelName = modelNameOf(rec.nbt);
        double[] pos = readPos(rec.nbt);
        if (modelName.isBlank() || pos == null) {
            return false;
        }
        InstalledObjectDefinition def = findDefinition(modelName);
        if (def == null) {
            MISSING.add(modelName);
            return false;
        }
        BlockPos bp = avoidRail(level, BlockPos.containing(pos[0], pos[1], pos[2]));
        level.setBlock(bp, RealTrainModUnofficialBlocks.INSTALLED_OBJECT.get().defaultBlockState(), 3);
        if (!(level.getBlockEntity(bp) instanceof InstalledObjectBlockEntity be)) {
            return false;
        }
        be.setDefinition(def.getId(), def.getCategory(), readRotation(rec.nbt, 0));
        //ブロックの角が原点なので、エンティティの実座標との差を描画オフセットで埋める
        be.setRenderOffset(pos[0] - (bp.getX() + 0.5D), pos[1] - bp.getY(), pos[2] - (bp.getZ() + 0.5D));
        be.setChanged();
        return true;
    }

    private static double[] readPos(CompoundTag nbt) {
        ListTag list = nbt.getList("Pos", Tag.TAG_DOUBLE);
        if (list.size() != 3) {
            return null;
        }
        return new double[]{list.getDouble(0), list.getDouble(1), list.getDouble(2)};
    }

    private static float readRotation(CompoundTag nbt, int index) {
        ListTag list = nbt.getList("Rotation", Tag.TAG_FLOAT);
        return list.size() > index ? list.getFloat(index) : 0.0F;
    }

    private static ServerLevel levelOf(MinecraftServer server, String dimension) {
        ResourceLocation loc = ResourceLocation.tryParse(dimension);
        if (loc == null) {
            return server.overworld();
        }
        ServerLevel level = server.getLevel(
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, loc));
        return level != null ? level : server.overworld();
    }

    /** 復元が進行中か (コマンドの表示用)。 */
    public static boolean isRunning() {
        return running;
    }

    public static String progress() {
        int done = total - OBJECTS.size() - ENTITIES.size();
        return done + " / " + total;
    }
}
