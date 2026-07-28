package jp.ngt.rtm.electric;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 本家 jp.ngt.rtm.electric.TileEntitySignalConverter の移植 (簡略版)。
 * RSIn: レッドストーン入力→配線網 / RSOut: 配線網→レッドストーン出力 /
 * Increment・Decrement: 通過する信号レベルを ±1。
 */
public class TileEntitySignalConverter extends BlockEntity implements IProvideElectricity {
    private int electricity;
    private int prevInputSignal = -1;

    public TileEntitySignalConverter(BlockPos pos, BlockState state) {
        super(com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlockEntities.SIGNAL_CONVERTER.get(), pos, state);
    }

    public SignalConverterType getConverterType() {
        return SignalConverterType.getType(this.getBlockState().getValue(BlockSignalConverter.TYPE));
    }

    @Override
    public int getElectricity() {
        return this.electricity;
    }

    @Override
    public void setElectricity(int x, int y, int z, int level) {
        if (this.electricity != level) {
            this.electricity = level;
            this.setChanged();
            if (this.level != null && !this.level.isClientSide) {
                if (this.getConverterType() == SignalConverterType.RSOut) {
                    // レッドストーン出力の更新
                    this.level.updateNeighborsAt(this.worldPosition, this.getBlockState().getBlock());
                }
            }
        }
    }

    /** RSIn: レッドストーン入力を監視して配線網へ伝播 */
    public static void tick(Level level, BlockPos pos, BlockState state, TileEntitySignalConverter be) {
        if (level.isClientSide) {
            return;
        }
        if (be.getConverterType() == SignalConverterType.RSIn) {
            int signal = level.getBestNeighborSignal(pos);
            if (signal != be.prevInputSignal) {
                be.prevInputSignal = signal;
                be.electricity = signal;
                be.setChanged();
                WireManager.propagate(level, pos, signal);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider provider) {
        super.saveAdditional(nbt, provider);
        nbt.putInt("electricity", this.electricity);
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider provider) {
        super.loadAdditional(nbt, provider);
        this.electricity = nbt.getInt("electricity");
    }

    /** 本家 signalOnTrue/signalOnFalse: 入力が有/無のときに出す信号レベル。 */
    public int signalOnTrue = 15;
    public int signalOnFalse;
    /** 本家 signal: 変換器の種類ごとのフラグ用 (RS 出力の可否など)。 */
    public int signal;
    /** 本家 operator/comparator: 比較器モードの演算子と閾値。 */
    public int operator;
    public int comparator;
    /** 本家 id: 変換器のチャンネル番号。 */
    public int id;

    /** 本家 getSignalLevel: {入力有りのとき, 入力無しのとき}。 */
    public int[] getSignalLevel() {
        return new int[]{this.signalOnTrue, this.signalOnFalse};
    }

    public void setSignalLevel(int onTrue, int onFalse) {
        this.signalOnTrue = onTrue;
        this.signalOnFalse = onFalse;
        this.setChanged();
    }

    /** 本家 getChannel: 同じチャンネル同士だけが繋がる。 */
    public int getChannel() {
        return this.id;
    }

    public void setChannel(int channel) {
        this.id = channel;
        this.setChanged();
    }

    /** 本家 getRSOutput: この変換器がレッドストーンへ出す強度。 */
    public int getRSOutput() {
        return this.signal == 1 ? 15 : 0;
    }

    /** 本家 getChunkLoadRange: チャンクローダーとして保持する範囲。 */
    public int getChunkLoadRange() {
        return 0;
    }

    public boolean isChunkLoaderEnable() {
        return false;
    }

}
