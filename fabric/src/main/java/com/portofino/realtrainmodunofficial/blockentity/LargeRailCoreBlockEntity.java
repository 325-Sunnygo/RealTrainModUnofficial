package com.portofino.realtrainmodunofficial.blockentity;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlockEntities;
import jp.ngt.rtm.rail.util.Point;
import jp.ngt.rtm.rail.util.RailMap;
import jp.ngt.rtm.rail.util.RailMapBasic;
import jp.ngt.rtm.rail.util.RailMaker;
import jp.ngt.rtm.rail.util.RailPosition;
import jp.ngt.rtm.rail.util.RailMapSwitch;
import jp.ngt.rtm.rail.util.SwitchType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public class LargeRailCoreBlockEntity extends BlockEntity {
    private enum SwitchLayout {
        NONE,
        BASIC,
        SINGLE_CROSS,
        SCISSORS,
        DIAMOND
    }

    private RailPosition[] railPositions;
    private RailMap railMap;
    private String railDefinitionId = "";
    private int activeSegmentIndex;
    private int previousSegmentIndex;
    private float switchProgress = 1.0F;
    private int lastSignalStrength = -1;
    private boolean switchStateDirty = true;
    private transient RailMap[] cachedAllRailMaps = new RailMap[0];
    private transient boolean railMapCacheDirty = true;
    private transient SwitchType switchType;
    private transient AABB cachedRenderBounds;
    private transient boolean renderBoundsDirty = true;

    public LargeRailCoreBlockEntity(BlockPos pos, BlockState blockState) {
        super(RealTrainModUnofficialBlockEntities.LARGE_RAIL_CORE.get(), pos, blockState);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (isSwitchMarkerLayout(railPositions)) {
            tag.putByte("Size", (byte) railPositions.length);
            for (int i = 0; i < railPositions.length; i++) {
                RailPosition position = railPositions[i];
                if (position != null) {
                    tag.put("RP" + i, position.writeToNBT());
                }
            }
        } else if (railPositions != null && railPositions.length >= 2) {
            ListTag list = new ListTag();
            for (int i = 0; i + 1 < railPositions.length; i += 2) {
                RailPosition start = railPositions[i];
                RailPosition end = railPositions[i + 1];
                if (start == null || end == null) continue;
                CompoundTag segment = new CompoundTag();
                segment.put("StartRP", start.writeToNBT());
                segment.put("EndRP", end.writeToNBT());
                list.add(segment);
            }
            tag.put("RailSegments", list);
        }
        tag.putString("RailDefinitionId", railDefinitionId == null ? "" : railDefinitionId);
        tag.putInt("ActiveSegmentIndex", activeSegmentIndex);
        tag.putInt("PreviousSegmentIndex", previousSegmentIndex);
        tag.putFloat("SwitchProgress", switchProgress);
        tag.putInt("LastSignalStrength", lastSignalStrength);
        tag.putBoolean("SwitchStateDirty", switchStateDirty);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.railPositions = null;
        this.railMap = null;
        this.cachedAllRailMaps = new RailMap[0];
        this.railMapCacheDirty = true;
        this.switchType = null;
        this.cachedRenderBounds = null;
        this.renderBoundsDirty = true;
        if (tag.contains("Size")) {
            int size = Byte.toUnsignedInt(tag.getByte("Size"));
            if (size > 0) {
                List<RailPosition> validPositions = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    if (!tag.contains("RP" + i)) {
                        continue;
                    }
                    RailPosition position = RailPosition.readFromNBT(tag.getCompound("RP" + i));
                    if (position != null) {
                        validPositions.add(position);
                    }
                }
                if (validPositions.size() >= 2) {
                    railPositions = validPositions.toArray(new RailPosition[0]);
                    createRailMap();
                }
            }
        } else if (tag.contains("RailSegments")) {
            ListTag list = tag.getList("RailSegments", 10);
            if (!list.isEmpty()) {
                java.util.List<RailPosition> validPositions = new java.util.ArrayList<>(list.size() * 2);
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag segment = list.getCompound(i);
                    RailPosition start = RailPosition.readFromNBT(segment.getCompound("StartRP"));
                    RailPosition end = RailPosition.readFromNBT(segment.getCompound("EndRP"));
                    if (start == null || end == null) {
                        continue;
                    }
                    validPositions.add(start);
                    validPositions.add(end);
                }
                if (validPositions.size() >= 2) {
                    railPositions = validPositions.toArray(new RailPosition[0]);
                    createRailMap();
                }
            }
        } else if (tag.contains("StartRP") && tag.contains("EndRP")) {
            RailPosition start = RailPosition.readFromNBT(tag.getCompound("StartRP"));
            RailPosition end = RailPosition.readFromNBT(tag.getCompound("EndRP"));
            if (start != null && end != null) {
                railPositions = new RailPosition[]{start, end};
                createRailMap();
            }
        }
        this.railDefinitionId = tag.getString("RailDefinitionId");
        this.activeSegmentIndex = tag.getInt("ActiveSegmentIndex");
        this.previousSegmentIndex = tag.getInt("PreviousSegmentIndex");
        this.switchProgress = tag.contains("SwitchProgress") ? tag.getFloat("SwitchProgress") : 1.0F;
        this.lastSignalStrength = tag.contains("LastSignalStrength") ? tag.getInt("LastSignalStrength") : -1;
        // ロード時は switchType を再構築する(loadAdditwith で null 化)が、再構築直後は既定(直進)状態。
        // 保存時の switchStateDirty=false をそのまま使うと再評価されず、レッドストーンブロックが
        // 隣接していても直進へ「リセット」されてしまう。ロード時は必ず再評価して周囲の信号から
        // 分岐状態を復元する(信号が無ければ自然に直進へ戻る)。
        this.switchStateDirty = true;
        clampActiveSegment();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public void setRailPositions(RailPosition[] positions) {
        if (positions == null || positions.length < 2) {
            this.railPositions = null;
            this.railMap = null;
            this.switchStateDirty = true;
            this.cachedAllRailMaps = new RailMap[0];
            this.railMapCacheDirty = true;
            this.switchType = null;
            this.cachedRenderBounds = null;
            this.renderBoundsDirty = true;
            clampActiveSegment();
            return;
        }
        if (isSwitchMarkerLayout(positions)) {
            List<RailPosition> copied = new ArrayList<>(positions.length);
            for (RailPosition position : positions) {
                if (position != null) {
                    copied.add(RailPosition.readFromNBT(position.writeToNBT()));
                }
            }
            this.railPositions = copied.size() >= 2 ? copied.toArray(new RailPosition[0]) : null;
            this.railMap = null;
            this.switchStateDirty = true;
            this.railMapCacheDirty = true;
            this.switchType = null;
            this.cachedRenderBounds = null;
            this.renderBoundsDirty = true;
            clampActiveSegment();
            return;
        }
        java.util.List<RailPosition> sanitized = new java.util.ArrayList<>(positions.length);
        for (int i = 0; i + 1 < positions.length; i += 2) {
            RailPosition start = positions[i];
            RailPosition end = positions[i + 1];
            if (start == null || end == null) {
                continue;
            }
            sanitized.add(RailPosition.readFromNBT(start.writeToNBT()));
            sanitized.add(RailPosition.readFromNBT(end.writeToNBT()));
        }
        this.railPositions = sanitized.size() >= 2 ? sanitized.toArray(new RailPosition[0]) : null;
        this.railMap = null;
        this.switchStateDirty = true;
        this.railMapCacheDirty = true;
        this.switchType = null;
        this.cachedRenderBounds = null;
        this.renderBoundsDirty = true;
        clampActiveSegment();
    }

    public RailPosition[] getRailPositions() {
        return this.railPositions == null ? new RailPosition[0] : this.railPositions.clone();
    }

    public void createRailMap() {
        railMap = null;
        cachedAllRailMaps = new RailMap[0];
        railMapCacheDirty = true;
        switchType = null;
        cachedRenderBounds = null;
        renderBoundsDirty = true;
        if (railPositions != null && railPositions.length >= 2) {
            switchType = buildSwitchType();
            RailMap[] maps = buildRailMaps();
            if (maps.length > 0) {
                railMap = maps[0];
            }
        }
        this.switchStateDirty = true;
        clampActiveSegment();
    }

    public RailMap getRailMap() {
        return railMap;
    }

    /** SRB3 互換(tile.getRailMap(null))。 */
    public RailMap getRailMap(Object ignored) {
        return getRailMap();
    }

    /** SRB3 互換(tile.getRailCore())。RTMU ではコア自身が tile。 */
    public LargeRailCoreBlockEntity getRailCore() {
        return this;
    }

    /**
     * 本家 TileEntityLargeRailBase.getRailFromCoordinates (KaizPatchX の 4 引数版) の移植。
     *
     * <p>指定座標から<b>真下へ降りながらレールを探し</b>、見つかったレールのコアを返す。
     *
     * <p>架線柱のスクリプトはこれで足元の線路を掴み、
     * {@code railMap.getRailRotation(...)} でレールの向きを取って<b>自分の向き (yaw) を線路に合わせる</b>。
     * 無いと yaw が決まらないままスクリプトが TypeError で死に、柱が線路と無関係な向き・
     * フォールバック描画になっていた。
     *
     * <p>スクリプトからは {@code tileEntity.func_145831_w()} の戻り値 (WorldCompat) が渡るので
     * Object で受けて実 Level を取り出す。
     */
    public static LargeRailCoreBlockEntity getRailFromCoordinates(Object world, double px, double py, double pz) {
        return getRailFromCoordinates(world, px, py, pz, Integer.MIN_VALUE);
    }

    /** 本家 2.4.24 の 5 引数版 (minY で打ち切り)。 */
    public static LargeRailCoreBlockEntity getRailFromCoordinates(Object world, double px, double py, double pz, int minY) {
        Level level = unwrapLevel(world);
        if (level == null) {
            return null;
        }
        int x = Mth.floor(px);
        int z = Mth.floor(pz);
        int bottom = Math.max(minY, level.getMinBuildHeight());
        for (int y = Mth.floor(py); y > bottom; y--) {
            LargeRailCoreBlockEntity core = com.portofino.realtrainmodunofficial.entity.BogieTracker
                .findCoreDirect(level, new BlockPos(x, y, z));
            if (core != null) {
                return core;
            }
        }
        return null;
    }

    private static Level unwrapLevel(Object world) {
        if (world instanceof Level level) {
            return level;
        }
        if (world instanceof jp.ngt.mccompat.WorldCompat compat) {
            return compat.getLevel();
        }
        return null;
    }

    /** 本家RTM準拠の分岐レンダリング用: 分岐の Point 配列(分岐でなければ null)。 */
    public jp.ngt.rtm.rail.util.Point[] getSwitchPoints() {
        return switchType != null ? switchType.getPoints() : null;
    }

    public RailMap[] getAllRailMaps() {
        if (!railMapCacheDirty) {
            return cachedAllRailMaps.clone();
        }
        cachedAllRailMaps = buildRailMaps();
        railMapCacheDirty = false;
        return cachedAllRailMaps.clone();
    }

    /**
     * Returns the rail maps that should be used by moving trains.
     */
    public RailMap[] getActiveRailMaps() {
        RailMap[] maps = getAllRailMaps();
        if (maps.length <= 1) {
            return maps;
        }
        if (switchType != null) {
            List<RailMap> active = switchType.getOpenRailMaps();
            return active.toArray(new RailMap[0]);
        }
        SwitchLayout layout = detectSwitchLayout();
        if (layout == SwitchLayout.DIAMOND) {
            return new RailMap[]{maps[0], maps[1]};
        }
        if (layout == SwitchLayout.SINGLE_CROSS) {
            return lastSignalStrength > 0 && maps.length >= 3
                ? new RailMap[]{maps[2]}
                : new RailMap[]{maps[0], maps[1]};
        }
        if (layout == SwitchLayout.SCISSORS) {
            if (lastSignalStrength > 0 && isScissorsStraightSegment(activeSegmentIndex)) {
                return new RailMap[]{maps[activeSegmentIndex]};
            }
            java.util.List<RailMap> active = new java.util.ArrayList<>(2);
            for (int i = 0; i < maps.length; i++) {
                if (!isScissorsStraightSegment(i)) {
                    active.add(maps[i]);
                }
            }
            return active.isEmpty() ? maps : active.toArray(new RailMap[0]);
        }
        int index = Mth.clamp(activeSegmentIndex, 0, maps.length - 1);
        return new RailMap[]{maps[index]};
    }

    public boolean isPassiveCrossing() {
        return switchType != null ? switchType.id == 3 : detectSwitchLayout() == SwitchLayout.DIAMOND;
    }

    public boolean containsActiveRailMap(RailMap target) {
        if (target == null) {
            return false;
        }
        for (RailMap map : getActiveRailMaps()) {
            if (sameRailShape(map, target)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds one branch segment to this core.
     */
    public void appendRailSegment(RailPosition start, RailPosition end) {
        if (start == null || end == null) {
            return;
        }
        int oldLength = railPositions == null ? 0 : railPositions.length;
        RailPosition[] next = new RailPosition[oldLength + 2];
        if (railPositions != null) {
            System.arraycopy(railPositions, 0, next, 0, railPositions.length);
        }
        next[oldLength] = RailPosition.readFromNBT(start.writeToNBT());
        next[oldLength + 1] = RailPosition.readFromNBT(end.writeToNBT());
        setRailPositions(next);
        createRailMap();
        setChanged();
    }

    public void requestSwitchStateRefresh() {
        this.switchStateDirty = true;
    }

    /**
     * Returns the first point stored in this core.
     */
    public RailPosition getFirstRailPosition() {
        if (railPositions != null) {
            for (RailPosition railPosition : railPositions) {
                if (railPosition != null) {
                    return RailPosition.readFromNBT(railPosition.writeToNBT());
                }
            }
        }
        return null;
    }

    /**
     * Updates the active branch from redstone strength.
     */
    public void updateSignalStrength(int signalStrength) {
        if (switchType != null) {
            lastSignalStrength = signalStrength;
            requestSwitchStateRefresh();
            return;
        }
        int segmentCount = getSegmentCount();
        int nextIndex = activeSegmentIndex;
        SwitchLayout layout = detectSwitchLayout();
        if (layout == SwitchLayout.DIAMOND) {
            nextIndex = 0;
        } else if (layout == SwitchLayout.SINGLE_CROSS) {
            nextIndex = signalStrength > 0 && segmentCount >= 3 ? 2 : 0;
        } else if (layout == SwitchLayout.BASIC) {
            nextIndex = segmentCount <= 1 || signalStrength <= 0 ? 0 : 1;
        } else if (layout == SwitchLayout.SCISSORS) {
            nextIndex = signalStrength > 0 ? firstScissorsStraightSegment() : 0;
        } else {
            nextIndex = segmentCount <= 1 || signalStrength <= 0
                ? 0
                : Mth.clamp(signalStrength, 0, segmentCount - 1);
        }
        lastSignalStrength = signalStrength;
        if (nextIndex != activeSegmentIndex) {
            previousSegmentIndex = activeSegmentIndex;
            activeSegmentIndex = nextIndex;
            switchProgress = 0.0F;
            setChanged();
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    public void refreshSwitchState() {
        if (level == null) {
            return;
        }
        this.switchStateDirty = false;

        RailMap[] maps = getAllRailMaps();
        if (maps.length == 0) {
            return;
        }

        if (switchType != null) {
            switchType.onBlockChanged(level);
            int[] openIndices = switchType.getOpenRailIndices();
            int nextIndex = Mth.clamp(openIndices.length == 0 ? 0 : openIndices[0], 0, maps.length - 1);
            int strongest = readSignalAround(worldPosition);
            if (strongest != lastSignalStrength || nextIndex != activeSegmentIndex) {
                previousSegmentIndex = activeSegmentIndex;
                activeSegmentIndex = nextIndex;
                switchProgress = nextIndex != previousSegmentIndex ? 0.0F : switchProgress;
                lastSignalStrength = strongest;
                setChanged();
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
            return;
        }

        SwitchLayout layout = detectSwitchLayout();
        int[] segmentSignals = new int[maps.length];
        int strongest = 0;
        for (int i = 0; i < maps.length; i++) {
            segmentSignals[i] = readSegmentSignal(maps[i]);
            strongest = Math.max(strongest, segmentSignals[i]);
        }

        int nextIndex = activeSegmentIndex;
        if (layout == SwitchLayout.DIAMOND) {
            nextIndex = 0;
        } else if (layout == SwitchLayout.SINGLE_CROSS) {
            nextIndex = segmentSignals.length >= 3 && segmentSignals[2] > 0 ? 2 : 0;
        } else if (layout == SwitchLayout.SCISSORS) {
            nextIndex = strongest > 0 ? findPoweredScissorsStraightSegment(segmentSignals) : firstScissorsDiagonalSegment();
        } else if (layout == SwitchLayout.BASIC) {
            nextIndex = strongest > 0 ? 1 : 0;
        } else if (maps.length > 1) {
            nextIndex = strongest <= 0 ? 0 : Mth.clamp(strongest, 0, maps.length - 1);
        }

        if (strongest != lastSignalStrength || nextIndex != activeSegmentIndex) {
            previousSegmentIndex = activeSegmentIndex;
            activeSegmentIndex = Mth.clamp(nextIndex, 0, Math.max(0, maps.length - 1));
            switchProgress = nextIndex != previousSegmentIndex ? 0.0F : switchProgress;
            lastSignalStrength = strongest;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * Returns the currently selected branch index.
     */
    public int getActiveSegmentIndex() {
        return activeSegmentIndex;
    }

    /**
     * Returns the previous branch index used during client-side animation.
     */
    public int getPreviousSegmentIndex() {
        return previousSegmentIndex;
    }

    /**
     * Returns branch animation progress.
     */
    public float getSwitchProgress(float partialTick) {
        return Mth.clamp(switchProgress + partialTick * 0.04F, 0.0F, 1.0F);
    }

    /**
     * レンチで手動操作: 次の分岐番線に切り替える。
     * 信号が入っていない限り、次のリフレッシュまで維持される。
     */
    public boolean cycleSwitch() {
        RailMap[] maps = getAllRailMaps();
        if (maps == null || maps.length < 2) return false;
        int next = (activeSegmentIndex + 1) % maps.length;
        previousSegmentIndex = activeSegmentIndex;
        activeSegmentIndex = next;
        switchProgress = 0.0F;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        return true;
    }

    /**
     * Ticks redstone state and branch animation.
     */
    public static void tick(Level level, BlockPos pos, BlockState state, LargeRailCoreBlockEntity be) {
        if (be.switchType != null) {
            be.switchType.onUpdate(level);
        }
        if (be.switchStateDirty) {
            be.refreshSwitchState();
        }
        if (be.switchProgress < 1.0F) {
            be.switchProgress = Math.min(1.0F, be.switchProgress + 0.04F);
            if (level.isClientSide()) {
                //NeoForge の requestModelDataUpdate 相当。Fabric にモデルデータの
                //仕組みは無いので、ブロック更新を投げて再描画させる。
                if (be.getLevel() != null) {
                    net.minecraft.world.level.block.state.BlockState st = be.getBlockState();
                    be.getLevel().sendBlockUpdated(be.getBlockPos(), st, st, 3);
                }
            } else {
                be.setChanged();
            }
        }
    }

    public void setRailDefinitionId(String railDefinitionId) {
        this.railDefinitionId = railDefinitionId == null ? "" : railDefinitionId;
        this.setChanged();
    }

    public String getRailDefinitionId() {
        return railDefinitionId;
    }

    public boolean isLoaded() {
        return railPositions != null && railPositions.length >= 2 && railMap != null;
    }

    private int getSegmentCount() {
        return railPositions == null ? 0 : railPositions.length / 2;
    }

    private void clampActiveSegment() {
        int max = Math.max(0, getAllRailMaps().length - 1);
        activeSegmentIndex = Mth.clamp(activeSegmentIndex, 0, max);
        previousSegmentIndex = Mth.clamp(previousSegmentIndex, 0, max);
    }

    public AABB getCachedRenderBounds() {
        if (!renderBoundsDirty && cachedRenderBounds != null) {
            return cachedRenderBounds;
        }
        RailMap[] maps = getAllRailMaps();
        if (maps.length == 0) {
            cachedRenderBounds = new AABB(worldPosition).inflate(1.0D);
            renderBoundsDirty = false;
            return cachedRenderBounds;
        }

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (RailMap map : maps) {
            if (map == null) {
                continue;
            }
            int split = Math.max(8, RailMap.curveSplitForLength(map.getHorizontalPathLength()));
            for (int i = 0; i <= split; i++) {
                double[] point = map.getRailPos(split, i);
                double x = point[1];
                double y = map.getRailHeight(split, i);
                double z = point[0];
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
                maxZ = Math.max(maxZ, z);
            }
        }
        cachedRenderBounds = minX == Double.POSITIVE_INFINITY
            ? new AABB(worldPosition).inflate(1.0D)
            : new AABB(minX - 8.0D, minY - 4.0D, minZ - 8.0D, maxX + 8.0D, maxY + 8.0D, maxZ + 8.0D);
        renderBoundsDirty = false;
        return cachedRenderBounds;
    }

    private SwitchType buildSwitchType() {
        if (railPositions == null || railPositions.length < 4) {
            if (railPositions == null || railPositions.length < 3) {
                return null;
            }
        }
        if (!isSwitchMarkerLayout(railPositions)) {
            return null;
        }
        List<RailPosition> ordered = new ArrayList<>(railPositions.length);
        for (RailPosition railPosition : railPositions) {
            if (railPosition != null) {
                ordered.add(RailPosition.readFromNBT(railPosition.writeToNBT()));
            }
        }
        if (ordered.size() < 3) {
            return null;
        }
        return new RailMaker(ordered).getSwitch();
    }

    private static boolean isSwitchMarkerLayout(RailPosition[] positions) {
        if (positions == null || positions.length < 3 || positions.length > 4) {
            return false;
        }
        int switchCount = 0;
        for (RailPosition position : positions) {
            if (position == null) {
                return false;
            }
            if (position.switchType == 1) {
                switchCount++;
            }
        }
        if (positions.length == 3) {
            return switchCount == 1;
        }
        if (positions.length == 4) {
            return switchCount == 2 || switchCount == 4;
        }
        return false;
    }

    private RailMap[] buildRailMaps() {
        if (railPositions == null || railPositions.length < 2) {
            return new RailMap[0];
        }
        if (switchType != null) {
            RailMapSwitch[] switchMaps = switchType.getAllRailMap();
            RailMap[] result = new RailMap[switchMaps.length];
            System.arraycopy(switchMaps, 0, result, 0, switchMaps.length);
            return result;
        }
        List<RailMap> maps = new ArrayList<>(railPositions.length / 2);
        for (int i = 0; i + 1 < railPositions.length; i += 2) {
            RailPosition start = railPositions[i];
            RailPosition end = railPositions[i + 1];
            if (start != null && end != null) {
                maps.add(new RailMapBasic(start, end));
            }
        }
        return maps.toArray(new RailMap[0]);
    }

    private SwitchLayout detectSwitchLayout() {
        if (railPositions == null) {
            return SwitchLayout.NONE;
        }
        int count = railPositions.length;
        int switchCount = 0;
        for (RailPosition rp : railPositions) {
            if (rp == null) {
                return SwitchLayout.NONE;
            }
            if (rp.switchType == 1) {
                switchCount++;
            }
        }
        if (count == 4 && switchCount == 2) {
            return SwitchLayout.BASIC;
        }
        if (count == 6 && switchCount == 4) {
            return SwitchLayout.SINGLE_CROSS;
        }
        if (count == 8 && switchCount == 8) {
            return hasSameDirectionPair() ? SwitchLayout.SCISSORS : SwitchLayout.DIAMOND;
        }
        if (count == 4 && switchCount == 4) {
            return SwitchLayout.DIAMOND;
        }
        return SwitchLayout.NONE;
    }

    private boolean hasSameDirectionPair() {
        if (railPositions == null) {
            return false;
        }
        for (int i = 0; i < railPositions.length; i++) {
            RailPosition a = railPositions[i];
            if (a == null || a.switchType != 1) {
                continue;
            }
            for (int j = i + 1; j < railPositions.length; j++) {
                RailPosition b = railPositions[j];
                if (b != null && b.switchType == 1 && (a.direction & 7) == (b.direction & 7)) {
                    return true;
                }
            }
        }
        return false;
    }

    private int readSegmentSignal(RailMap map) {
        if (level == null || map == null) {
            return 0;
        }
        int strongest = 0;
        RailPosition start = map.getStartRP();
        RailPosition end = map.getEndRP();
        if (start != null) {
            strongest = Math.max(strongest, readSignalAround(new BlockPos(start.blockX, start.blockY, start.blockZ)));
        }
        if (end != null) {
            strongest = Math.max(strongest, readSignalAround(new BlockPos(end.blockX, end.blockY, end.blockZ)));
        }
        int split = Math.max(8, RailMap.curveSplitForLength(map.getHorizontalPathLength()));
        for (int i = 0; i <= split; i++) {
            double[] point = map.getRailPos(split, i);
            BlockPos samplePos = new BlockPos(
                (int) Math.floor(point[1]),
                (int) Math.floor(map.getRailHeight(split, i)),
                (int) Math.floor(point[0])
            );
            strongest = Math.max(strongest, readSignalAround(samplePos));
            if (strongest > 0) {
                return strongest;
            }
        }
        return strongest;
    }

    private int readSignalAround(BlockPos pos) {
        if (level == null) {
            return 0;
        }
        int strongest = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos sample = pos.offset(dx, dy, dz);
                    strongest = Math.max(strongest, level.getBestNeighborSignal(sample));
                    strongest = Math.max(strongest, level.getDirectSignalTo(sample));
                    for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
                        strongest = Math.max(strongest, level.getSignal(sample, direction));
                    }
                    if (strongest > 0) {
                        return strongest;
                    }
                }
            }
        }
        return strongest;
    }

    private int firstScissorsStraightSegment() {
        RailMap[] maps = getAllRailMaps();
        for (int i = 0; i < maps.length; i++) {
            if (isScissorsStraightSegment(i)) {
                return i;
            }
        }
        return 0;
    }

    private int firstScissorsDiagonalSegment() {
        RailMap[] maps = getAllRailMaps();
        for (int i = 0; i < maps.length; i++) {
            if (!isScissorsStraightSegment(i)) {
                return i;
            }
        }
        return 0;
    }

    private int findPoweredScissorsStraightSegment(int[] segmentSignals) {
        int bestIndex = firstScissorsStraightSegment();
        int bestSignal = 0;
        for (int i = 0; i < segmentSignals.length; i++) {
            if (isScissorsStraightSegment(i) && segmentSignals[i] >= bestSignal) {
                bestSignal = segmentSignals[i];
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private boolean isScissorsStraightSegment(int index) {
        RailMap[] maps = getAllRailMaps();
        if (index < 0 || index >= maps.length) {
            return false;
        }
        RailPosition start = maps[index].getStartRP();
        RailPosition end = maps[index].getEndRP();
        return start != null && end != null && (start.direction & 7) == (end.direction & 7);
    }

    private static boolean sameRailShape(RailMap a, RailMap b) {
        if (a == null || b == null) {
            return false;
        }
        return sameRailEndpoint(a.getStartRP(), b.getStartRP()) && sameRailEndpoint(a.getEndRP(), b.getEndRP())
            || sameRailEndpoint(a.getStartRP(), b.getEndRP()) && sameRailEndpoint(a.getEndRP(), b.getStartRP());
    }

    private static boolean sameRailEndpoint(RailPosition a, RailPosition b) {
        if (a == null || b == null) {
            return false;
        }
        return a.blockX == b.blockX && a.blockY == b.blockY && a.blockZ == b.blockZ;
    }
}
