package jp.masa.signalcontrollermod;

import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * SignalControllerMod (作者: masa300, https://github.com/masa300/SignalControllerMod)
 * の TileEntitySignalController 1.21.1 移植。制御ロジック・NBT キーは原作のまま。
 * 信号機の読み書きは当 MOD の信号 (InstalledObjectBlockEntity, SIGNAL カテゴリ) に接続。
 *
 * 動作 (原作準拠):
 *  - nextSignal (次閉塞の信号) の現示レベル最大値を取得 (last なら 1=停止扱い)
 *  - signalType.upSignalLevel で 1 段進めた現示を計算 (repeat は 3-4 現示を素通し)
 *  - レッドストーン入力があれば強制的に 1 (停止現示)
 *  - above (直上探索) と displayPos の信号機へ現示を設定
 */
public class TileEntitySignalController extends BlockEntity {
    private SignalType signalType = SignalType.signal3;
    private List<BlockPos> nextSignal = new ArrayList<>();
    private List<BlockPos> displayPos = new ArrayList<>();
    private boolean above;
    private boolean last;
    private boolean repeat;
    private boolean reducedSpeed;

    public TileEntitySignalController(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public TileEntitySignalController(BlockPos pos, BlockState state) {
        this(com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlockEntities.SIGNAL_CONTROLLER.get(), pos, state);
    }

    /** 信号状態の毎5秒フルダンプ + RS変化ログ (NGTLog.debug は INFO 出力)。ログを埋め尽くすので既定オフ。 */
    private static final boolean DEBUG_LOG = false;
    private long lastDebugLog;
    private boolean prevRSPowered;

    /**
     * 原作 updateEntity (Server Only)
     */
    public void tick() {
        Level world = this.getLevel();
        if (world == null || world.isClientSide) {
            return;
        }
        boolean debug = false;
        if (DEBUG_LOG) {
            long now = System.currentTimeMillis();
            if (now - lastDebugLog > 5000L) {
                lastDebugLog = now;
                debug = true;
            }
        }
        int MAXSIGNALLEVEL = 6;
        List<Integer> nextSignalList = new ArrayList<>();
        for (BlockPos pos : this.nextSignal) {
            Integer level = this.getSignal(world, pos);
            if (level != null) {
                nextSignalList.add(level);
            }
        }
        int nextSignalLevel = (this.last) ? 1 : nextSignalList.stream().mapToInt(v -> v).max().orElse(0);

        // RS入力(停止現示)。getBestNeighborSignal に加え、直接通電 (hasNeighborSignal /
        // 強い動力で通電されたブロック経由) も見る — 原作 isBlockIndirectlyGettingPowered 相当
        boolean isRSPowered = world.getBestNeighborSignal(this.worldPosition) > 0
                || world.hasNeighborSignal(this.worldPosition)
                || world.getDirectSignalTo(this.worldPosition) > 0;
        if (isRSPowered != this.prevRSPowered) {
            this.prevRSPowered = isRSPowered;
            if (DEBUG_LOG) {
                jp.ngt.ngtlib.io.NGTLog.debug("[SignalController] %s RS changed -> %s",
                        this.worldPosition.toShortString(), String.valueOf(isRSPowered));
            }
        }

        //表示する信号機の制御 (変化したときだけ変更して負荷を減らす — 原作コメント)
        int signalLevel = (this.repeat && (3 <= nextSignalLevel && nextSignalLevel <= 4))
                ? nextSignalLevel : this.signalType.upSignalLevel(nextSignalLevel);
        if (signalLevel > MAXSIGNALLEVEL) signalLevel = MAXSIGNALLEVEL;
        if (isRSPowered) signalLevel = 1;

        if (debug) {
            StringBuilder disp = new StringBuilder();
            for (BlockPos p : this.displayPos) {
                Integer cur = this.getSignal(world, p);
                disp.append(p.toShortString()).append('=').append(cur == null ? "非信号!" : cur).append(' ');
            }
            jp.ngt.ngtlib.io.NGTLog.debug("[SignalController] %s rs=%s next=%d level=%d above=%s last=%s type=%s disp=[%s]",
                    this.worldPosition.toShortString(), String.valueOf(isRSPowered), nextSignalLevel, signalLevel,
                    String.valueOf(this.above), String.valueOf(this.last), this.signalType, disp.toString().trim());
        }

        if (this.above) {
            BlockPos abovePos = this.searchSignalAbove(world);
            if (abovePos != null) {
                Integer current = this.getSignal(world, abovePos);
                if (current != null && current != signalLevel) {
                    this.setSignal(world, abovePos, signalLevel);
                }
            }
        }

        for (BlockPos pos : this.displayPos) {
            if (!(pos.getX() == 0 && pos.getY() == 0 && pos.getZ() == 0)) {
                Integer current = this.getSignal(world, pos);
                if (current != null && current != signalLevel) {
                    this.setSignal(world, pos, signalLevel);
                }
            }
        }
    }

    /**
     * 当 MOD の信号機 (SIGNAL カテゴリ) から現示レベルを取得。信号機以外は null。
     * 信号機の見た目 (現示) は electricity でなく SignalAspect が持つため、
     * RTM レベル互換の legacy 値 (1=停止..6=高速進行) で読み書きする。
     */
    private Integer getSignal(Level world, BlockPos pos) {
        BlockEntity tile = world.getBlockEntity(pos);
        if (tile instanceof InstalledObjectBlockEntity be && be.getCategory() == InstalledObjectCategory.SIGNAL) {
            return be.getLegacySignalState();
        }
        return null;
    }

    private void setSignal(Level world, BlockPos pos, int level) {
        BlockEntity tile = world.getBlockEntity(pos);
        if (tile instanceof InstalledObjectBlockEntity be && be.getCategory() == InstalledObjectCategory.SIGNAL) {
            //本家 TileEntitySignal.setElectricity 同様 — 信号機は電気レベル=現示 (BE 側でミラー)
            be.setElectricity(level);
        }
    }

    /**
     * 原作 searchSignalAboveY: 直上 32 ブロックから信号機を探す
     */
    private BlockPos searchSignalAbove(Level world) {
        int searchMaxCount = 32;
        for (int i = 1; i <= searchMaxCount; i++) {
            BlockPos pos = this.worldPosition.above(i);
            BlockEntity tile = world.getBlockEntity(pos);
            if (tile instanceof InstalledObjectBlockEntity be && be.getCategory() == InstalledObjectCategory.SIGNAL) {
                return pos;
            }
        }
        return null;
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);
        this.signalType = SignalType.getType(nbt.getString("signalType"));
        this.last = nbt.getBoolean("last");
        this.repeat = nbt.getBoolean("repeat");
        this.reducedSpeed = nbt.getBoolean("reducedSpeed");
        this.nextSignal.clear();
        this.displayPos.clear();
        ListTag nextSignalList = nbt.getList("nextSignalList", Tag.TAG_COMPOUND);
        for (int i = 0; i < nextSignalList.size(); i++) {
            CompoundTag tag = nextSignalList.getCompound(i);
            this.nextSignal.add(new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z")));
        }
        ListTag displayPosList = nbt.getList("displayPosList", Tag.TAG_COMPOUND);
        for (int i = 0; i < displayPosList.size(); i++) {
            CompoundTag tag = displayPosList.getCompound(i);
            this.displayPos.add(new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z")));
        }
        this.above = nbt.getBoolean("above");
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.saveAdditional(nbt, registries);
        nbt.putString("signalType", this.signalType.toString());
        nbt.putBoolean("last", this.last);
        nbt.putBoolean("repeat", this.repeat);
        nbt.putBoolean("reducedSpeed", this.reducedSpeed);
        ListTag nextSignalList = new ListTag();
        this.nextSignal.forEach(pos -> nextSignalList.add(writePos(pos)));
        nbt.put("nextSignalList", nextSignalList);
        ListTag displayPosList = new ListTag();
        this.displayPos.forEach(pos -> displayPosList.add(writePos(pos)));
        nbt.put("displayPosList", displayPosList);
        nbt.putBoolean("above", this.above);
    }

    private static CompoundTag writePos(BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("X", pos.getX());
        tag.putInt("Y", pos.getY());
        tag.putInt("Z", pos.getZ());
        return tag;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public void syncToClient() {
        if (this.level != null && !this.level.isClientSide) {
            this.setChanged();
            BlockState st = this.getBlockState();
            this.level.sendBlockUpdated(this.worldPosition, st, st, 3);
        }
    }

    // ===== 原作の accessor / リスト操作 =====

    public SignalType getSignalType() {
        return (this.signalType == null) ? this.signalType = SignalType.signal3 : this.signalType;
    }

    public void setSignalType(SignalType signalType) {
        this.signalType = signalType;
    }

    public List<BlockPos> getNextSignal() {
        return nextSignal;
    }

    public boolean addNextSignal(BlockPos nextSignalPos) {
        for (BlockPos pos : this.nextSignal) {
            if (pos.equals(nextSignalPos)) {
                return false;
            }
        }
        this.nextSignal.removeIf(p -> p.getX() == 0 && p.getY() == 0 && p.getZ() == 0);
        this.nextSignal.add(nextSignalPos);
        return true;
    }

    public List<BlockPos> getDisplayPos() {
        return displayPos;
    }

    public boolean addDisplayPos(BlockPos displayPosNew) {
        for (BlockPos pos : this.displayPos) {
            if (pos.equals(displayPosNew)) {
                return false;
            }
        }
        this.displayPos.removeIf(p -> p.getX() == 0 && p.getY() == 0 && p.getZ() == 0);
        this.displayPos.add(displayPosNew);
        return true;
    }

    public boolean isLast() {
        return last;
    }

    public void setLast(boolean last) {
        this.last = last;
    }

    public boolean isRepeat() {
        return repeat;
    }

    public void setRepeat(boolean repeat) {
        this.repeat = repeat;
    }

    public boolean isReducedSpeed() {
        return reducedSpeed;
    }

    public void setReducedSpeed(boolean reducedSpeed) {
        this.reducedSpeed = reducedSpeed;
    }

    public boolean isAbove() {
        return above;
    }

    public void setAbove(boolean above) {
        this.above = above;
    }
}
