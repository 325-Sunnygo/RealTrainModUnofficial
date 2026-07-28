package com.portofino.realtrainmodunofficial.item;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialComponents;
import com.portofino.realtrainmodunofficial.block.MarkerBlock;
import com.portofino.realtrainmodunofficial.blockentity.LargeRailCoreBlockEntity;
import com.portofino.realtrainmodunofficial.blockentity.MarkerBlockEntity;
import jp.ngt.rtm.rail.util.RailPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * RTM-style wrench: 2-click rail connection + wrench-mode curve adjustment.
 * First click on a marker selects it; second click on another marker creates the rail.
 */
public class WrenchItem extends Item {
    /** Radius for finding a second marker when entering WrenchMode (reduced from 50 to avoid watchdog). */
    // ★探索範囲は設定 (railMarkerSearchRange / Height) を見る。
    // 以前はここだけ 64/32 の決め打ちで、設定を伸ばしてもレンチ経路だけ 64 で頭打ちだった
    // (マーカー右クリック経路は設定を見ていたので「変えても変わらない」に見える)。
    private static int wrenchSearchDistance() {
        return com.portofino.realtrainmodunofficial.Config.railMarkerSearchRange();
    }

    private static int wrenchSearchHeight() {
        return com.portofino.realtrainmodunofficial.Config.railMarkerSearchHeight();
    }

    public WrenchItem() {
        super(new Properties().stacksTo(1));
    }

    // ── KaizPatchX 風アンカー編集セッション (クライアント) ──
    // レンチでマーカーを右クリックすると、そのマーカーのアンカー(曲線の接線方向と強さ)が
    // 視点に追従する編集状態に入る。もう一度右クリックで確定 (ConfigureMarkerPayload 送信)。
    // 緑線プレビューは RailPreviewRenderer が editingMarker を見て描画する。
    public static BlockPos editingMarker;
    public static BlockPos editingPair;
    /** true=視点追従中(編集), false=形を固定して緑線だけ表示中。緑線自体は editingMarker が残る限り表示。 */
    public static boolean followMode;
    /** 編集開始時刻。開始直後の同一クリック連発で即確定するのを防ぐクールダウン用。 */
    private static long editStartTime;
    public static float liveYaw, livePitch, liveLenH = -1.0F, liveLenV, liveCantCenter, liveCantEdge, liveCantRandom;

    /** 設置物オフセット微調整の1ステップ(ブロック単位 = 1/16)。KaizPatchX 風の微調整。 */
    private static final double OFFSET_STEP = 1.0D / 16.0D;
    /** オフセットの上限(各軸 ±2ブロック)。 */
    private static final double OFFSET_LIMIT = 2.0D;

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();

        // ── KaizPatchX 風: 設置物をレンチで微調整 ──
        // クリックした面の法線方向へ 1/16 ずつ押し出す。スニーク併用で逆方向(引き込む)。
        // 位置はBlockEntityのレンダーオフセットに保存され、レンダラが反映する。
        if (level.getBlockEntity(clickedPos)
                instanceof com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity io) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS; // クライアントは腕振りのみ。実処理はサーバ。
            }
            net.minecraft.core.Direction face = context.getClickedFace();
            double sign = player.isShiftKeyDown() ? -1.0D : 1.0D;
            Vec3 cur = io.getRenderOffset();
            double nx = cur.x + face.getStepX() * OFFSET_STEP * sign;
            double ny = cur.y + face.getStepY() * OFFSET_STEP * sign;
            double nz = cur.z + face.getStepZ() * OFFSET_STEP * sign;
            nx = net.minecraft.util.Mth.clamp(nx, -OFFSET_LIMIT, OFFSET_LIMIT);
            ny = net.minecraft.util.Mth.clamp(ny, -OFFSET_LIMIT, OFFSET_LIMIT);
            nz = net.minecraft.util.Mth.clamp(nz, -OFFSET_LIMIT, OFFSET_LIMIT);
            io.setRenderOffset(nx, ny, nz);
            level.sendBlockUpdated(clickedPos, level.getBlockState(clickedPos), level.getBlockState(clickedPos), 3);
            player.displayClientMessage(Component.literal(String.format(
                "オフセット: X=%.3f Y=%.3f Z=%.3f (スニークで逆方向)", nx, ny, nz)), true);
            return InteractionResult.CONSUME;
        }

        boolean clickedMarker = level.getBlockState(clickedPos).getBlock() instanceof MarkerBlock;

        if (level.isClientSide()) {
            // 追従中(編集中)はどこを右クリックしても、その時点の形で「固定」する。
            // ただし編集開始から少し経たないと確定しない (開始クリックの連発で即確定するのを防ぐ)。
            if (editingMarker != null && followMode) {
                if (System.currentTimeMillis() - editStartTime < 400L) {
                    return InteractionResult.SUCCESS; // クールダウン中は無視 (まず視点で動かす)
                }
                confirmEdit(player);
                return InteractionResult.SUCCESS;
            }
            if (clickedMarker) {
                if (clickedPos.equals(editingMarker)) {
                    // 固定中のマーカーを再度右クリック → 再編集 (再び視点追従)。
                    followMode = true;
                    editStartTime = System.currentTimeMillis();
                    player.displayClientMessage(Component.literal("再編集: 視点で曲げ、右クリックで固定"), true);
                } else {
                    startEdit(level, clickedPos, player);
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }
        // サーバ側: マーカークリックは成功扱いにしてブロック設置等に流さない。確定は packet 側で処理。
        return clickedMarker ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    /** 現在のライブアンカーを保存して「固定」する。緑線は残す (追従だけ停止)。 */
    private static void confirmEdit(Player player) {
        BlockPos pos = editingMarker;
        if (pos == null) return;
        // クライアントのマーカーにも即時反映する。サーバ更新だけだと、別マーカーを編集した時に
        // クライアント側でこのマーカーがデフォルト(直線)に戻って緑線が消えて見える (ユーザー報告)。
        if (player.level().getBlockEntity(pos) instanceof MarkerBlockEntity m) {
            m.configure(liveYaw, livePitch, liveLenH, liveLenV, liveCantCenter, liveCantEdge, liveCantRandom);
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
            new com.portofino.realtrainmodunofficial.network.ConfigureMarkerPayload(
                pos, liveYaw, livePitch, liveLenH, liveLenV,
                liveCantCenter, liveCantEdge, liveCantRandom));
        followMode = false;
        player.displayClientMessage(Component.literal("形を固定しました(再編集はマーカーを右クリック / レールアイテムで生成)"), true);
    }

    /** マーカーの現在値を読み込んで編集を開始する (視点追従ON)。 */
    private static void startEdit(Level level, BlockPos clickedPos, Player player) {
        editingMarker = clickedPos.immutable();
        editingPair = findNearestMarkerPos(level, clickedPos);
        followMode = true;
        editStartTime = System.currentTimeMillis();
        if (level.getBlockEntity(clickedPos) instanceof MarkerBlockEntity m) {
            liveYaw = m.getAnchorYaw();
            livePitch = m.getAnchorPitch();
            liveLenH = m.getAnchorLengthHorizontal();
            liveLenV = m.getAnchorLengthVertical();
            liveCantCenter = m.getCantCenter();
            liveCantEdge = m.getCantEdge();
            liveCantRandom = m.getCantRandom();
        }
        player.displayClientMessage(Component.literal("編集開始: 視点で曲げ、置きたい所を右クリックで固定"), true);
    }

    /** 編集対象マーカーの最寄りにある別マーカーの位置を返す (緑線プレビューのペア)。 */
    private static BlockPos findNearestMarkerPos(Level level, BlockPos origin) {
        BlockPos[] best = {null};
        double[] bestSq = {Double.MAX_VALUE};
        // ★総当たりではなく範囲内チャンクの BlockEntity を走査する。
        // 設定を 1024 まで伸ばせるので、決め打ちの 3 重ループだと現実的な時間で終わらない。
        forEachMarkerInRange(level, origin, (p, marker) -> {
            if (p.equals(origin)) {
                return;
            }
            double d = origin.distSqr(p);
            if (d < bestSq[0]) {
                bestSq[0] = d;
                best[0] = p.immutable();
            }
        });
        return best[0];
    }

    private static boolean beginWrenchPreview(Level level, BlockPos clickedPos, MarkerBlockEntity marker, MarkerBlock markerBlock, ItemStack targetStack) {
        RailPosition start = marker.getMarkerRP();
        if (start == null) return false;
        List<RailPosition> ends = findNearbyMarkers(level, clickedPos, start);
        if (ends.isEmpty()) return false;
        CompoundTag tag = new CompoundTag();
        tag.putInt("X", clickedPos.getX());
        tag.putInt("Y", clickedPos.getY());
        tag.putInt("Z", clickedPos.getZ());
        tag.putBoolean("WrenchMode", true);
        tag.putBoolean("BranchMode", ends.size() > 1 || markerBlock.isSwitch);
        tag.put("StartRP", start.writeToNBT());
        ListTag segments = new ListTag();
        for (RailPosition end : ends) {
            CompoundTag segment = new CompoundTag();
            segment.put("EndRP", end.writeToNBT());
            putDefaultAnchors(segment, start, end);
            segments.add(segment);
        }
        tag.put("RailSegments", segments);
        tag.put("EndRP", ends.get(0).writeToNBT());
        copySegmentAnchorsToRoot(tag, segments.getCompound(0));
        targetStack.set(RealTrainModUnofficialComponents.RAIL_PREVIEW_START.get(), tag);
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // 編集中に空中を右クリック → その時点の形で確定 (置きたい所を見て右クリックで固定)。
        if (level.isClientSide() && editingMarker != null && followMode) {
            if (System.currentTimeMillis() - editStartTime >= 400L) {
                confirmEdit(player);
            }
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }
        clearInvalidPreviewTags(player, level);
        ItemStack previewStack = findPreviewStack(player);
        if (!previewStack.isEmpty()) {
            previewStack.remove(RealTrainModUnofficialComponents.RAIL_PREVIEW_START.get());
            if (level.isClientSide()) {
                player.displayClientMessage(Component.literal("レンチ選択を解除しました"), true);
            }
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
        }
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    /** Moves the nearest rail preview control handle to a world-space point. */
    public static boolean moveControlTo(ItemStack stack, Vec3 hit) {
        CompoundTag tag = stack.get(RealTrainModUnofficialComponents.RAIL_PREVIEW_START.get());
        if (tag == null || !tag.contains("StartRP")) return false;
        RailPosition start = RailPosition.readFromNBT(tag.getCompound("StartRP"));
        if (start == null) return false;
        CompoundTag copy = tag.copy();
        ListTag segments = getSegmentList(copy);
        if (segments.isEmpty()) return false;

        int bestIndex = 0;
        boolean bestStart = true;
        double bestDistanceSq = Double.POSITIVE_INFINITY;
        for (int i = 0; i < segments.size(); i++) {
            CompoundTag segment = segments.getCompound(i);
            RailPosition end = RailPosition.readFromNBT(segment.getCompound("EndRP"));
            if (end == null) continue;
            Vec3 startHandle = getStartHandle(segment, start, end);
            Vec3 endHandle = getEndHandle(segment, start, end);
            double sd = hit.distanceToSqr(startHandle);
            if (sd < bestDistanceSq) { bestDistanceSq = sd; bestIndex = i; bestStart = true; }
            double ed = hit.distanceToSqr(endHandle);
            if (ed < bestDistanceSq) { bestDistanceSq = ed; bestIndex = i; bestStart = false; }
        }

        CompoundTag segment = segments.getCompound(bestIndex);
        if (bestStart) putHandle(segment, "Start", hit);
        else putHandle(segment, "End", hit);
        segments.set(bestIndex, segment);
        copy.put("RailSegments", segments);
        copy.put("EndRP", segments.getCompound(0).getCompound("EndRP"));
        copySegmentAnchorsToRoot(copy, segments.getCompound(0));
        stack.set(RealTrainModUnofficialComponents.RAIL_PREVIEW_START.get(), copy);
        return true;
    }

    public static RailPosition applyControlHandle(RailPosition source, CompoundTag tag, boolean startHandle) {
        RailPosition copy = RailPosition.readFromNBT(source.writeToNBT());
        if (copy == null || tag == null) return source;
        String prefix = startHandle ? "Start" : "End";
        if (!tag.contains(prefix + "AnchorX")) return copy;
        double ax = tag.getDouble(prefix + "AnchorX");
        double ay = tag.getDouble(prefix + "AnchorY");
        double az = tag.getDouble(prefix + "AnchorZ");
        double dx = ax - copy.posX, dz = az - copy.posZ;
        copy.anchorYaw = (float) Math.toDegrees(Math.atan2(dx, dz));
        copy.anchorLengthHorizontal = (float) Math.sqrt(dx * dx + dz * dz);
        double dy = ay - copy.posY;
        copy.anchorPitch = copy.anchorLengthHorizontal <= 1.0e-4F ? 0.0F
                : (float) Math.toDegrees(Math.atan2(dy, copy.anchorLengthHorizontal));
        copy.anchorLengthVertical = copy.anchorLengthHorizontal;
        return copy;
    }

    public static Vec3 getStartHandle(CompoundTag tag, RailPosition start, RailPosition end) {
        return getHandle(tag, "Start", start, end);
    }

    public static Vec3 getEndHandle(CompoundTag tag, RailPosition start, RailPosition end) {
        return getHandle(tag, "End", end, start);
    }

    public static ListTag getSegmentList(CompoundTag tag) {
        ListTag sanitized = new ListTag();
        if (tag.contains("RailSegments")) {
            ListTag source = tag.getList("RailSegments", 10);
            for (int i = 0; i < source.size(); i++) {
                CompoundTag segment = source.getCompound(i).copy();
                if (RailPosition.readFromNBT(segment.getCompound("EndRP")) != null) sanitized.add(segment);
            }
            return sanitized;
        }
        if (tag.contains("EndRP")) {
            RailPosition end = RailPosition.readFromNBT(tag.getCompound("EndRP"));
            if (end == null) return sanitized;
            CompoundTag segment = new CompoundTag();
            segment.put("EndRP", tag.getCompound("EndRP"));
            copyRootAnchorsToSegment(tag, segment);
            sanitized.add(segment);
        }
        return sanitized;
    }

    public static ItemStack findPlayerPreviewStack(Player player) {
        return findPreviewStack(player);
    }

    private static ItemStack findPreviewStack(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if ((stack.getItem() instanceof RailItem || stack.getItem() instanceof WrenchItem)
                    && stack.get(RealTrainModUnofficialComponents.RAIL_PREVIEW_START.get()) != null) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack findRailStack(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof RailItem) return stack;
        }
        return ItemStack.EMPTY;
    }

    public static void clearInvalidPreviewTags(Player player, Level level) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            CompoundTag tag = stack.get(RealTrainModUnofficialComponents.RAIL_PREVIEW_START.get());
            if (tag != null && !isPreviewTagValid(level, tag)) {
                stack.remove(RealTrainModUnofficialComponents.RAIL_PREVIEW_START.get());
            }
        }
    }

    private static boolean isPreviewTagValid(Level level, CompoundTag tag) {
        if (!tag.contains("X") || !tag.contains("Y") || !tag.contains("Z")) return false;
        BlockPos startPos = new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
        BlockEntity startBe = level.getBlockEntity(startPos);
        if (startBe instanceof MarkerBlockEntity marker) return marker.getMarkerRP() != null;
        if (startBe instanceof LargeRailCoreBlockEntity core) return core.getFirstRailPosition() != null;
        return false;
    }

    /**
     * 設定範囲内のマーカーを列挙する。
     * MarkerBlock.searchAllMarkers と同じ作り。
     */
    private static void forEachMarkerInRange(Level level, BlockPos origin,
                                             java.util.function.BiConsumer<BlockPos, MarkerBlockEntity> action) {
        int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();
        int range = wrenchSearchDistance();
        int height = wrenchSearchHeight();
        int minCX = (ox - range) >> 4, maxCX = (ox + range) >> 4;
        int minCZ = (oz - range) >> 4, maxCZ = (oz + range) >> 4;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                for (BlockEntity be : level.getChunk(cx, cz).getBlockEntities().values()) {
                    if (!(be instanceof MarkerBlockEntity marker)) {
                        continue;
                    }
                    BlockPos p = be.getBlockPos();
                    if (Math.abs(p.getX() - ox) <= range
                            && Math.abs(p.getY() - oy) <= height
                            && Math.abs(p.getZ() - oz) <= range) {
                        action.accept(p, marker);
                    }
                }
            }
        }
    }

    /** Scans a small radius for other markers (WrenchMode only, SHIFT+click). */
    private static List<RailPosition> findNearbyMarkers(Level level, BlockPos origin, RailPosition start) {
        List<RailPosition> markers = new ArrayList<>();
        forEachMarkerInRange(level, origin, (p, marker) -> {
            RailPosition rp = marker.getMarkerRP();
            if (rp != null && (rp.blockX != start.blockX || rp.blockY != start.blockY
                    || rp.blockZ != start.blockZ)) {
                markers.add(rp);
            }
        });
        markers.sort((a, b) -> {
            double adx = a.posX - start.posX, ady = a.posY - start.posY, adz = a.posZ - start.posZ;
            double bdx = b.posX - start.posX, bdy = b.posY - start.posY, bdz = b.posZ - start.posZ;
            return Double.compare(adx*adx + ady*ady + adz*adz, bdx*bdx + bdy*bdy + bdz*bdz);
        });
        return markers;
    }

    private static void putDefaultAnchors(CompoundTag tag, RailPosition start, RailPosition end) {
        if (!tag.contains("StartAnchorX")) putHandle(tag, "Start", defaultHandle(start, end));
        if (!tag.contains("EndAnchorX"))   putHandle(tag, "End",   defaultHandle(end, start));
    }

    private static Vec3 defaultHandle(RailPosition source, RailPosition target) {
        double dx = target.posX - source.posX, dz = target.posZ - source.posZ;
        double length = Math.min(10.0D, Math.max(2.0D, Math.sqrt(dx*dx + dz*dz) * 0.35D));
        double yaw = Math.toRadians(source.anchorYaw);
        return new Vec3(source.posX + Math.sin(yaw) * length, source.posY, source.posZ + Math.cos(yaw) * length);
    }

    private static Vec3 getHandle(CompoundTag tag, String prefix, RailPosition source, RailPosition target) {
        if (tag.contains(prefix + "AnchorX")) {
            return new Vec3(tag.getDouble(prefix + "AnchorX"), tag.getDouble(prefix + "AnchorY"), tag.getDouble(prefix + "AnchorZ"));
        }
        return defaultHandle(source, target);
    }

    private static void putHandle(CompoundTag tag, String prefix, Vec3 point) {
        tag.putDouble(prefix + "AnchorX", point.x);
        tag.putDouble(prefix + "AnchorY", point.y);
        tag.putDouble(prefix + "AnchorZ", point.z);
    }

    private static void copySegmentAnchorsToRoot(CompoundTag root, CompoundTag segment) {
        copyAnchor(segment, root, "Start");
        copyAnchor(segment, root, "End");
    }

    private static void copyRootAnchorsToSegment(CompoundTag root, CompoundTag segment) {
        copyAnchor(root, segment, "Start");
        copyAnchor(root, segment, "End");
    }

    private static void copyAnchor(CompoundTag source, CompoundTag target, String prefix) {
        if (source.contains(prefix + "AnchorX")) {
            target.putDouble(prefix + "AnchorX", source.getDouble(prefix + "AnchorX"));
            target.putDouble(prefix + "AnchorY", source.getDouble(prefix + "AnchorY"));
            target.putDouble(prefix + "AnchorZ", source.getDouble(prefix + "AnchorZ"));
        }
    }

    private static void showOffsetMessage(Level level, Player player, ItemStack stack) {
        if (!level.isClientSide()) return;
        CompoundTag tag = stack.get(RealTrainModUnofficialComponents.RAIL_PREVIEW_START.get());
        int ox = tag == null ? 0 : tag.getInt("OffsetX");
        int oy = tag == null ? 0 : tag.getInt("OffsetY");
        int oz = tag == null ? 0 : tag.getInt("OffsetZ");
        player.displayClientMessage(Component.literal(
            String.format("レール調整 X:%+.2f Y:%+.2f Z:%+.2f", ox / 16.0D, oy / 16.0D, oz / 16.0D)
        ), true);
    }
}
