package jp.ngt.rtm.rail;

import jp.ngt.rtm.rail.util.MarkerState;
import jp.ngt.rtm.rail.util.RailMaker;
import jp.ngt.rtm.rail.util.RailMap;
import jp.ngt.rtm.rail.util.RailMapBasic;
import jp.ngt.rtm.rail.util.RailPosition;
import jp.ngt.rtm.rail.util.RailProperty;
import jp.ngt.rtm.rail.util.SwitchType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * 本家 jp.ngt.rtm.rail.TileEntityMarker (KaizPatchX) の忠実移植。
 * NBT: RP / MarkerState (本家キーのまま)。
 */
// ★jp.ngt.mccompat.LoadAwareBlockEntity を必ず付けること。
// バニラの BlockEntity に onLoad は無く、Fabric 側はこの印が付いた物だけへ
// BLOCK_ENTITY_LOAD から onLoad を呼んでいる。
public class TileEntityMarker extends BlockEntity
        implements jp.ngt.mccompat.LoadAwareBlockEntity {
    private RailPosition rp;

    public boolean displayDistance = true;//同期必要なし
    /** 0:なし, 1:グリッド, 2:ベジェ */
    private byte displayMode;
    private TileEntityMarker coreMarker;
    public int editMode;

    // 本家: レンチのアンカー移動モード (レール形状編集、同期不要)
    public boolean followMouseMoving;
    public net.minecraft.world.entity.player.Player followingPlayer;

    public int startX, startY = -1, startZ;

    private List<int[]> markerPosList = new ArrayList<>();

    private RailMap[] railMaps;

    /** {{x,y,z}} */
    private List<int[]> grid;

    public float startPlayerPitch;
    public float startPlayerYaw;
    public byte startMarkerHeight;

    private int markerState;

    public boolean fitNeighbor = true;

    public TileEntityMarker(BlockPos pos, BlockState state) {
        this(RTMRailBlockEntities.MARKER.get(), pos, state);
    }

    public TileEntityMarker(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.markerState = MarkerState.DISTANCE.set(this.markerState, true);
        this.markerState = MarkerState.GRID.set(this.markerState, false);
        this.markerState = MarkerState.LINE1.set(this.markerState, false);
        this.markerState = MarkerState.LINE2.set(this.markerState, false);
        this.markerState = MarkerState.ANCHOR21.set(this.markerState, false);
        this.markerState = MarkerState.FIT_NEIGHBOR.set(this.markerState, true);
    }

    /**
     * クライアントでアンカー編集中のマーカー (レンダラーが設定)。
     * 編集中は同期パケットの RP で上書きしない (編集値が巻き戻るため)。サーバーでは常に null。
     */
    public static TileEntityMarker clientEditingMarker;

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);

        if (nbt.contains("RP") && this != clientEditingMarker) {
            this.rp = RailPosition.readFromNBT(nbt.getCompound("RP"));
        }
        this.markerState = nbt.getInt("MarkerState");
        // コアマーカー座標 (クライアントの getCoreMarker 解決に必須)
        if (nbt.contains("StartY")) {
            this.startX = nbt.getInt("StartX");
            this.startY = nbt.getInt("StartY");
            this.startZ = nbt.getInt("StartZ");
        }

        // 1.21: PacketMarker 代替 (同期タグ経由の markerPosList)
        if (nbt.contains("MarkerPosList")) {
            ListTag list = nbt.getList("MarkerPosList", Tag.TAG_INT_ARRAY);
            List<int[]> posList = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                posList.add(((net.minecraft.nbt.IntArrayTag) list.get(i)).getAsIntArray());
            }
            this.markerPosList = posList;
            this.railMaps = null;
            this.grid = null;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.saveAdditional(nbt, registries);

        if (this.rp != null) {
            nbt.put("RP", this.rp.writeToNBT());
        }
        nbt.putInt("MarkerState", this.markerState);
        nbt.putInt("StartX", this.startX);
        nbt.putInt("StartY", this.startY);
        nbt.putInt("StartZ", this.startZ);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag nbt = this.saveWithoutMetadata(registries);
        ListTag list = new ListTag();
        for (int[] pos : this.markerPosList) {
            list.add(new net.minecraft.nbt.IntArrayTag(pos));
        }
        nbt.put("MarkerPosList", list);
        return nbt;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ★@Override を付けないこと: これは NeoForge が足したメソッドで、バニラには無い
    public void onLoad() {
        // super.onLoad; — バニラの BlockEntity には onLoad が無い
        MarkerManager.add(this);
    }

    @Override
    public void setRemoved() {
        MarkerManager.remove(this);
        super.setRemoved();
    }

    /** 本家 updateEntity 相当 (rp の遅延生成)。BlockMarker の ticker から呼ばれる。 */
    public void tick() {
        if (this.rp == null && this.level != null) {
            BlockState state = this.getBlockState();
            if (state.getBlock() instanceof BlockMarker markerBlock) {
                byte dir = BlockMarker.getMarkerDir(state);
                byte type = (byte) (markerBlock.markerType == 1 ? 1 : 0);
                this.rp = new RailPosition(this.worldPosition.getX(), this.worldPosition.getY(), this.worldPosition.getZ(), dir, type);
            }
        }
    }

    public void updateStartPos() {
        if (this.level == null) {
            return;
        }
        if (this.startY != -1) {
            BlockEntity tileEntity = this.level.getBlockEntity(new BlockPos(this.startX, this.startY, this.startZ));
            if (!(tileEntity instanceof TileEntityMarker)) {
                this.startY = -1;
            }
        } else {
            BlockState state = this.getBlockState();
            if (state.getBlock() instanceof BlockMarker markerBlock) {
                markerBlock.makeRailMap(this, this.worldPosition.getX(), this.worldPosition.getY(), this.worldPosition.getZ(), null);
            }
        }
    }

    public RailPosition getMarkerRP() {
        if (this.rp == null) {
            this.tick();
        }
        return this.rp;
    }

    public void setMarkerRP(RailPosition par1) {
        this.rp = par1;
    }

    private RailPosition getMarkerRP(int[] pos) {
        return this.getMarkerRP(pos[0], pos[1], pos[2]);
    }

    private RailPosition getMarkerRP(int x, int y, int z) {
        BlockEntity tile = this.level.getBlockEntity(new BlockPos(x, y, z));
        if (tile instanceof TileEntityMarker) {
            return ((TileEntityMarker) tile).getMarkerRP();
        }
        return null;
    }

    public byte getDisplayMode() {
        return this.displayMode;
    }

    public void changeDisplayMode() {
        if (this.level != null && !this.level.isClientSide) {
            this.setDisplayMode((byte) ((this.displayMode + 1) % 3));
        }
    }

    public void setDisplayMode(byte par1) {
        this.displayMode = par1;
    }

    public byte increaseHeight() {
        this.getMarkerRP().setHeight((byte) ((this.rp.height + 1) % 16));
        return this.rp.height;
    }

    public List<int[]> getGrid() {
        if (this.grid == null && this.railMaps == null && !this.markerPosList.isEmpty() && this.level != null && this.level.isClientSide) {
            // 同期タグ受信後の遅延再構築
            this.rebuildRailMaps();
        }
        return this.grid;
    }

    public RailMap[] getRailMaps() {
        if (this.railMaps == null && !this.markerPosList.isEmpty() && this.level != null) {
            this.rebuildRailMaps();
        }
        return this.railMaps;
    }

    private void rebuildRailMaps() {
        List<int[]> list = this.markerPosList;
        if (list.size() == 2) {
            RailPosition rp0 = this.getMarkerRP(list.get(0));
            RailPosition rp1 = this.getMarkerRP(list.get(1));
            if (rp0 != null && rp1 != null) {
                this.railMaps = new RailMap[]{new RailMapBasic(rp0, rp1, RailMapBasic.fixRTMRailMapVersionCurrent)};
            }
        } else if (list.size() >= 3) {
            List<RailPosition> list2 = new ArrayList<>();
            for (int[] ia : list) {
                RailPosition rp0 = this.getMarkerRP(ia[0], ia[1], ia[2]);
                if (rp0 != null) {
                    list2.add(rp0);
                }
            }
            RailMaker rm = new RailMaker(this.level, list2, RailMapBasic.fixRTMRailMapVersionCurrent);
            SwitchType sw = rm.getSwitch();
            if (sw != null) {
                this.railMaps = sw.getAllRailMap();
            }
        }
        if (this.railMaps != null && this.level.isClientSide) {
            this.createGrids();
        }
    }

    public void onChangeRailShape() {
        TileEntityMarker core = this.getCoreMarker();
        if (core != null) {
            core.updateRailMap();
        } else if (!this.markerPosList.isEmpty()) {
            // クライアントで core 未解決でも自身が RailMap を持っていれば再構築できる
            this.updateRailMap();
        }
    }

    public void updateRailMap() {
        // 自身のリストを渡すとイテレート中の差し替えで CME になるため防御コピー
        this.setMarkersPos(new ArrayList<>(this.markerPosList));
    }

    /** マーカーのグリッド表示用RailMap生成 */
    public void setMarkersPos(List<int[]> list) {
        // loadAdditional (同期パケット) が markerPosList を差し替えても
        // イテレーションが壊れないよう防御コピーで処理する
        list = new ArrayList<>(list);
        if (list.size() == 2) {
            RailPosition rp0 = this.getMarkerRP(list.get(0));
            RailPosition rp1 = this.getMarkerRP(list.get(1));
            if (rp0 != null && rp1 != null) {
                RailMap rm = new RailMapBasic(rp0, rp1, RailMapBasic.fixRTMRailMapVersionCurrent);
                this.railMaps = new RailMap[]{rm};
            }
        } else {
            List<RailPosition> list2 = new ArrayList<>();
            for (int[] ia : list) {
                RailPosition rp0 = this.getMarkerRP(ia[0], ia[1], ia[2]);
                if (rp0 != null) {
                    list2.add(rp0);
                }
            }
            RailMaker rm = new RailMaker(this.level, list2, RailMapBasic.fixRTMRailMapVersionCurrent);
            SwitchType sw = rm.getSwitch();
            if (sw != null) {
                this.railMaps = sw.getAllRailMap();
            }
        }
        if (this.railMaps == null) {
            return;
        }
        this.markerPosList = list;
        if (this.level.isClientSide) {
            this.createGrids();
        }
        for (int[] pos : list) {
            BlockEntity tile = this.level.getBlockEntity(new BlockPos(pos[0], pos[1], pos[2]));
            if (tile instanceof TileEntityMarker) {
                TileEntityMarker marker = (TileEntityMarker) tile;
                marker.setStartPos(this.worldPosition.getX(), this.worldPosition.getY(), this.worldPosition.getZ());
                marker.railMaps = this.railMaps;//RailMap同期
                marker.markerPosList = list;
            }
        }

        if (!this.level.isClientSide) {
            // 本家 PacketMarker 代替: フル NBT 同期 (markerPosList 含む)
            for (int[] pos : list) {
                BlockPos bp = new BlockPos(pos[0], pos[1], pos[2]);
                BlockState st = this.level.getBlockState(bp);
                this.level.sendBlockUpdated(bp, st, st, 3);
            }
            BlockState st = this.getBlockState();
            this.level.sendBlockUpdated(this.worldPosition, st, st, 3);
        }
    }

    private void setStartPos(int x, int y, int z) {
        this.startX = x;
        this.startY = y;
        this.startZ = z;

        if (!(this.worldPosition.getX() == x && this.worldPosition.getY() == y && this.worldPosition.getZ() == z)) {
            this.markerPosList.clear();
            this.railMaps = null;
            this.grid = null;
        }
    }

    private void createGrids() {
        this.grid = new ArrayList<>();
        for (RailMap rm : this.railMaps) {
            this.grid.addAll(rm.getRailBlockList(RailProperty.getDefaultProperty()));
        }
    }

    public boolean isCoreMarker() {
        return (this.startX == this.worldPosition.getX() && this.startY == this.worldPosition.getY() && this.startZ == this.worldPosition.getZ());
    }

    public TileEntityMarker getCoreMarker() {
        if (this.startY < 0) {
            return null;
        }

        if (this.coreMarker == null || this.coreMarker.worldPosition.getX() != this.startX
                || this.coreMarker.worldPosition.getY() != this.startY || this.coreMarker.worldPosition.getZ() != this.startZ) {
            this.coreMarker = null;
            BlockEntity tile = this.level.getBlockEntity(new BlockPos(this.startX, this.startY, this.startZ));
            if (tile instanceof TileEntityMarker) {
                this.coreMarker = (TileEntityMarker) tile;
            }
        }
        return this.coreMarker;
    }

    public RailPosition[] getAllRP() {
        // GuiRailMarker用
        if (this.markerPosList.isEmpty()) {
            return new RailPosition[]{this.getMarkerRP()};
        }

        List<RailPosition> list2 = new ArrayList<>();
        for (int[] ia : this.markerPosList) {
            RailPosition rp0 = this.getMarkerRP(ia[0], ia[1], ia[2]);
            if (rp0 != null) {
                list2.add(rp0);
            }
        }
        return list2.toArray(new RailPosition[0]);
    }

    public List<int[]> getMarkerPosList() {
        return this.markerPosList;
    }

    public double getDistanceFrom(double x, double y, double z) {
        double dx = this.worldPosition.getX() + 0.5D - x;
        double dy = this.worldPosition.getY() + 0.5D - y;
        double dz = this.worldPosition.getZ() + 0.5D - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public void updateMarkerRM(Player player) {
        BlockState state = this.getBlockState();
        if (state.getBlock() instanceof BlockMarker markerBlock) {
            markerBlock.makeRailMap(this, this.worldPosition.getX(), this.worldPosition.getY(), this.worldPosition.getZ(), player);
        }
    }

    public int getMarkerState() {
        return markerState;
    }

    public void setMarkerState(int markerState) {
        this.markerState = markerState;
    }

    public boolean getState(MarkerState state) {
        return state.get(this.markerState);
    }

    public void flipState(MarkerState state) {
        boolean data = state.get(this.markerState);
        setState(state, !data);
    }

    public void setState(MarkerState state, boolean data) {
        this.updateStartPos();
        if (!isCoreMarker()) {
            TileEntityMarker marker = this.getCoreMarker();
            if (marker != null) {
                marker.setState(state, data);
            } else if (state == MarkerState.DISTANCE) {
                this.markerState = state.set(this.markerState, data);
            }
        } else {
            this.markerState = state.set(this.markerState, data);
            this.markerPosList.stream()
                    .map(pos -> this.level.getBlockEntity(new BlockPos(pos[0], pos[1], pos[2])))
                    .filter(TileEntityMarker.class::isInstance).map(TileEntityMarker.class::cast)
                    .forEach(marker -> marker.markerState = this.markerState);
        }
    }

    public String getStateString(MarkerState state) {
        boolean data = state.get(this.markerState);
        return String.format("%s : %s", state.toString(), data ? "ON" : "OFF");
    }
}
