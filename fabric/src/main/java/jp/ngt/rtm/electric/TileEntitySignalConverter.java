package jp.ngt.rtm.electric;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 信号変換機。本家 {@code jp.ngt.rtm.electric.TileEntitySignalConverter} と
 * その 5 つの内部クラス (RSIn / RSOut / Increment / Decrement / Wireless) の移植。
 *
 * <p>本家は種類ごとに別クラスだが、1.21 の BlockEntity は 1 種類にまとめて
 * BlockState の {@code type} で分岐する (本家のメタと同じ 0〜4)。
 * 分岐の中身・NBT のキー ({@code comparatorIndex} / {@code signal_0} / {@code signal_1})・
 * 値の意味は本家そのまま。
 *
 * <p>★本家は「配線網が {@link #getElectricity()} を読みに来る」<b>プル型</b>だが、
 * RTMU の {@link WireManager} は信号が変わった側から流す<b>プッシュ型</b>。
 * そのため RSIn と無線受信は、値が変わった時にこちらから {@code propagate} する。
 * 流れる値そのものは本家と同じ。
 */
public class TileEntitySignalConverter extends BlockEntity implements IProvideElectricity {

    /** 本家 comparator。RSOut の比較演算子。 */
    private ComparatorType comparator = ComparatorType.EQUAL;
    /** 本家 signalOnTrue: RS_ON のとき流す値 / RSOut の閾値 / 無線のチャンネル。 */
    private int signalOnTrue;
    /** 本家 signalOnFalse: RS_OFF のとき流す値 / 無線のチャンク読み込み範囲。 */
    private int signalOnFalse;
    /**
     * 本家 signal: 変換器が今持っている値 (RSOut では比較結果の 0/1)。
     * ★本家は<b>保存しない</b> (writeToNBT に無い) のでこちらも保存しない。
     */
    private int signal;

    /** RSIn がレッドストーンの変化を拾うための前回値。 */
    private int prevInputSignal = Integer.MIN_VALUE;

    public TileEntitySignalConverter(BlockPos pos, BlockState state) {
        super(com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlockEntities.SIGNAL_CONVERTER.get(),
            pos, state);
    }

    public SignalConverterType getConverterType() {
        return SignalConverterType.getType(this.getBlockState().getValue(BlockSignalConverter.TYPE));
    }

    // ───── 本家 readFromNBT / writeToNBT ─────

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider provider) {
        super.loadAdditional(nbt, provider);
        //本家のキー。旧 RTMU のキーも読めるようにしておく (既存ワールド互換)
        int index = nbt.contains("comparatorIndex") ? nbt.getInt("comparatorIndex") : nbt.getInt("comparator");
        int i0 = nbt.contains("signal_0") ? nbt.getInt("signal_0")
            : (nbt.contains("signalOnTrue") ? nbt.getInt("signalOnTrue") : 15);
        int i1 = nbt.contains("signal_1") ? nbt.getInt("signal_1") : nbt.getInt("signalOnFalse");
        this.comparator = ComparatorType.getType(index);
        this.signalOnTrue = i0;
        this.signalOnFalse = i1;
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider provider) {
        super.saveAdditional(nbt, provider);
        nbt.putInt("comparatorIndex", this.comparator.id);
        nbt.putInt("signal_0", this.signalOnTrue);
        nbt.putInt("signal_1", this.signalOnFalse);
    }

    /**
     * ★本家 {@code markDirty} は {@code sendPacket()} でクライアントへも配る。
     * これが無いと設定画面を開き直したときにクライアントが既定値 (15 / 0) を出すので、
     * 「完了を押したのに保存されていない」ように見える。
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        return this.saveWithoutMetadata(provider);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ───── 本家 getComparator / getSignalLevel / setSignalProp ─────

    public ComparatorType getComparator() {
        return this.comparator;
    }

    /** 本家 getSignalLevel: {RS_ON のとき, RS_OFF のとき}。 */
    public int[] getSignalLevel() {
        return new int[]{this.signalOnTrue, this.signalOnFalse};
    }

    /** 本家 setSignalProp。 */
    public void setSignalProp(int par1, int par2, ComparatorType par3) {
        this.signalOnTrue = par1;
        this.signalOnFalse = par2;
        this.comparator = par3;
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            if (this.getConverterType() == SignalConverterType.Wireless) {
                this.registerWireless();   //本家 updateAntennaList (チャンネル変更で入り直す)
            }
            //本家 markDirty → sendPacket 相当 (クライアントへ同期)
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
            //設定が変わったら今の値で流し直す (本家はプル型なので次の読み取りで反映される)
            this.prevInputSignal = Integer.MIN_VALUE;
        }
    }

    /** GUI パケット用 (比較演算子は id)。 */
    public void setSignalProp(int onTrue, int onFalse, int comparatorId) {
        this.setSignalProp(onTrue, onFalse, ComparatorType.getType(comparatorId));
    }

    // ───── 本家 getElectricity / setElectricity (種類ごと) ─────

    /** 本家 各 TileEntitySC_*.getElectricity。 */
    @Override
    public int getElectricity() {
        return switch (this.getConverterType()) {
            //本家 TileEntitySC_RSIn: どこかの面がレッドストーンで押されていれば signalOnTrue
            case RSIn -> this.isPoweredBySide() ? this.signalOnTrue : this.signalOnFalse;
            //本家 TileEntitySC_RSOut: 配線へは何も出さない
            case RSOut -> 0;
            default -> this.signal;
        };
    }

    /** 本家 TileEntitySC_RSIn.getElectricity の {@code world.isSidePowered} 相当。 */
    private boolean isPoweredBySide() {
        if (this.level == null) {
            return false;
        }
        for (Direction side : Direction.values()) {
            if (this.level.getSignal(this.worldPosition.relative(side), side) > 0) {
                return true;
            }
        }
        return false;
    }

    /** 本家 各 TileEntitySC_*.setElectricity。 */
    @Override
    public void setElectricity(int x, int y, int z, int level) {
        switch (this.getConverterType()) {
            case RSIn -> {
                //本家 TileEntitySC_RSIn.setElectricity は空 (入力専用)
            }
            case RSOut -> {
                //本家 TileEntitySC_RSOut: 閾値 (signalOnTrue) と比べて 0/1
                int i0 = switch (this.comparator) {
                    case EQUAL -> level == this.signalOnTrue ? 1 : 0;
                    case GREATER_EQUAL -> level >= this.signalOnTrue ? 1 : 0;
                    case GREATER_THAN -> level > this.signalOnTrue ? 1 : 0;
                    case LESS_EQUAL -> level <= this.signalOnTrue ? 1 : 0;
                    case LESS_THAN -> level < this.signalOnTrue ? 1 : 0;
                    case NOT_EQUAL -> level != this.signalOnTrue ? 1 : 0;
                };
                if (i0 != this.signal) {
                    this.signal = i0;
                    if (this.level != null && !this.level.isClientSide()) {
                        //本家 world.notifyNeighborsOfStateChange
                        this.level.updateNeighborsAt(this.worldPosition, this.getBlockState().getBlock());
                    }
                }
            }
            //本家 TileEntitySC_Increment: 0 より大きければ +1、0 なら 0 (上限クランプ無し)
            case Increment -> this.signal = level > 0 ? level + 1 : 0;
            //本家 TileEntitySC_Decrement: 1 より大きければ -1、それ以外はそのまま (1 は 1 のまま)
            case Decrement -> this.signal = level > 1 ? level - 1 : level;
            case Wireless -> {
                //本家 TileEntitySC_Wireless.setElectricity: 同じチャンネルの全機へ配る
                this.signal = level;
                if (this.level != null && !this.level.isClientSide()) {
                    broadcastWireless(this, level);
                }
            }
        }
    }

    /** 本家 getRSOutput: この変換器がレッドストーンへ出す強さ。 */
    public int getRSOutput() {
        //本家: RSOut 以外は 0
        return this.getConverterType() == SignalConverterType.RSOut && this.signal == 1 ? 15 : 0;
    }

    /**
     * RSIn の監視。
     * ★本家はプル型なので tick を持たないが、RTMU の配線はプッシュ型なので
     * 「レッドストーンが変わったら流し直す」役をここでやる。流す値は本家と同じ。
     */
    public static void tick(Level level, BlockPos pos, BlockState state, TileEntitySignalConverter be) {
        if (level.isClientSide()) {
            return;
        }
        if (be.getConverterType() != SignalConverterType.RSIn) {
            return;
        }
        int out = be.getElectricity();
        if (out != be.prevInputSignal) {
            be.prevInputSignal = out;
            WireManager.propagate(level, pos, out);
        }
    }

    // ───── 本家 TileEntitySC_Wireless ─────

    /** 本家 ADAPTER_MAP。key は 次元 + チャンネル。 */
    private static final Map<String, Set<TileEntitySignalConverter>> WIRELESS_MAP = new ConcurrentHashMap<>();
    /** RTMU はプッシュ型なので、配り直しが無限に回らないようにする。 */
    private static boolean broadcasting;
    private String wirelessKey;

    /** 本家 getChannel: チャンネルは signalOnTrue (GUI の上段)。 */
    public int getChannel() {
        return this.signalOnTrue;
    }

    /** 本家 getChunkLoadRange: signalOnFalse (GUI の下段)。★チャンク常時読み込みは未実装。 */
    public int getChunkLoadRange() {
        return this.signalOnFalse;
    }

    public boolean isChunkLoaderEnable() {
        return this.getChunkLoadRange() > 0;
    }

    private String wirelessMapKey() {
        if (this.level == null) {
            return null;
        }
        return this.level.dimension().location() + "#" + this.getChannel();
    }

    private void registerWireless() {
        String key = this.wirelessMapKey();
        if (key == null) {
            return;
        }
        if (this.wirelessKey != null && !this.wirelessKey.equals(key)) {
            Set<TileEntitySignalConverter> old = WIRELESS_MAP.get(this.wirelessKey);
            if (old != null) {
                old.remove(this);
            }
        }
        WIRELESS_MAP.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(this);
        this.wirelessKey = key;
    }

    private void unregisterWireless() {
        if (this.wirelessKey != null) {
            Set<TileEntitySignalConverter> set = WIRELESS_MAP.get(this.wirelessKey);
            if (set != null) {
                set.remove(this);
            }
            this.wirelessKey = null;
        }
    }

    /** 本家 setElectricity → 各機の setWirelessSignal。 */
    private static void broadcastWireless(TileEntitySignalConverter sender, int level) {
        if (broadcasting) {
            return;
        }
        Set<TileEntitySignalConverter> set = WIRELESS_MAP.get(sender.wirelessMapKey());
        if (set == null) {
            return;
        }
        broadcasting = true;
        try {
            for (TileEntitySignalConverter receiver : set) {
                if (receiver == sender || receiver.isRemoved() || receiver.level == null) {
                    continue;
                }
                receiver.signal = level;   //本家 setWirelessSignal
                //本家はここで終わり (受け手の getElectricity を配線が読む)。
                //RTMU はプッシュ型なので受け手側の配線へ流す。
                WireManager.propagate(receiver.level, receiver.worldPosition, level);
            }
        } finally {
            broadcasting = false;
        }
    }

    //★onLoad は NeoForge 拡張でバニラ (Fabric) に無い。両ローダーにある clearRemoved で登録する
    @Override
    public void clearRemoved() {
        super.clearRemoved();
        if (this.level != null && !this.level.isClientSide()
                && this.getConverterType() == SignalConverterType.Wireless) {
            this.registerWireless();
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        this.unregisterWireless();
    }

    /** 本家 TileEntitySignalConverter.ComparatorType。 */
    public enum ComparatorType {
        EQUAL(0, "=="),
        GREATER_THAN(1, ">"),
        GREATER_EQUAL(2, ">="),
        LESS_THAN(3, "<"),
        LESS_EQUAL(4, "<="),
        NOT_EQUAL(5, "!=");

        public final byte id;
        public final String operator;

        ComparatorType(int par1, String par2) {
            this.id = (byte) par1;
            this.operator = par2;
        }

        public static ComparatorType getType(int par1) {
            for (ComparatorType type : values()) {
                if (type.id == par1) {
                    return type;
                }
            }
            return EQUAL;
        }
    }
}
