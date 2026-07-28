package com.portofino.realtrainmodunofficial;

import com.portofino.realtrainmodunofficial.block.BallastBlock;
import com.portofino.realtrainmodunofficial.block.CrossingGateBlock;
import com.portofino.realtrainmodunofficial.block.InstalledObjectBlock;
import com.portofino.realtrainmodunofficial.block.LargeRailCoreBlock;
import com.portofino.realtrainmodunofficial.block.MarkerBlock;
import com.portofino.realtrainmodunofficial.block.RailCollisionBlock;
import com.portofino.realtrainmodunofficial.block.ScriptBlock;
import com.portofino.realtrainmodunofficial.block.SignalRemoteBlock;
import com.portofino.realtrainmodunofficial.block.SignalStateBlock;
import com.portofino.realtrainmodunofficial.block.TrainDetectorBlock;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class RealTrainModUnofficialBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(RealTrainModUnofficial.MODID);

    public static final DeferredBlock<CrossingGateBlock> CROSSING_GATE
        = BLOCKS.register("crossing_gate", () -> new CrossingGateBlock());

    /** 設置済みミニチュア (neo mcte)。中身は MiniatureBlockEntity が丸ごと持つ。 */
    public static final net.neoforged.neoforge.registries.DeferredBlock<com.portofino.realtrainmodunofficial.block.MiniatureBlock> MINIATURE
        = BLOCKS.register("miniature", () -> new com.portofino.realtrainmodunofficial.block.MiniatureBlock());
    public static final DeferredBlock<MarkerBlock> MARKER
        = BLOCKS.register("legacy_marker", () -> new MarkerBlock(false));
    public static final DeferredBlock<MarkerBlock> MARKER_SWITCH
        = BLOCKS.register("legacy_marker_switch", () -> new MarkerBlock(true));

    /** 道床ブロック（レールと独立した物理ブロック） */
    public static final DeferredBlock<BallastBlock> BALLAST
        = BLOCKS.register("legacy_ballast", BallastBlock::new);

    /** レールコアブロック（起点1個のみ、MQOモデル描画を担当） */
    public static final DeferredBlock<LargeRailCoreBlock> LARGE_RAIL_CORE
        = BLOCKS.register("legacy_large_rail_core", () -> new LargeRailCoreBlock());

    /** レール当たり判定ブロック（非表示・薄い） */
    public static final DeferredBlock<RailCollisionBlock> RAIL_COLLISION
        = BLOCKS.register("legacy_rail_collision", () -> new RailCollisionBlock());

    public static final DeferredBlock<InstalledObjectBlock> INSTALLED_OBJECT
        = BLOCKS.register("installed_object", () -> new InstalledObjectBlock());

    /** 本家 electric: 信号変換器 (RSIn/RSOut/Increment/Decrement) */
    public static final DeferredBlock<jp.ngt.rtm.electric.BlockSignalConverter> SIGNAL_CONVERTER
        = BLOCKS.register("signal_converter", () -> new jp.ngt.rtm.electric.BlockSignalConverter(
            net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                .strength(1.5F).sound(net.minecraft.world.level.block.SoundType.STONE)));

    /** SignalControllerMod (masa300) 移植: 信号制御器 (閉塞信号の自動制御) */
    public static final DeferredBlock<jp.masa.signalcontrollermod.SignalController> SIGNAL_CONTROLLER
        = BLOCKS.register("signal_controller", () -> new jp.masa.signalcontrollermod.SignalController(
            net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()));

    public static final DeferredBlock<SignalRemoteBlock> SIGNAL_RECEIVER
        = BLOCKS.register("signal_receiver", () -> new SignalRemoteBlock(SignalRemoteBlock.Mode.RECEIVER));

    public static final DeferredBlock<SignalRemoteBlock> SIGNAL_CHANGER
        = BLOCKS.register("signal_changer", () -> new SignalRemoteBlock(SignalRemoteBlock.Mode.CHANGER));

    public static final DeferredBlock<SignalRemoteBlock> SIGNAL_VALUE_RECEIVER
        = BLOCKS.register("signal_value_receiver", () -> new SignalRemoteBlock(SignalRemoteBlock.Mode.VALUE_INPUT));

    public static final DeferredBlock<TrainDetectorBlock> TRAIN_DETECTOR
        = BLOCKS.register("train_detector", () -> new TrainDetectorBlock());

    public static final DeferredBlock<SignalStateBlock> SIGNAL_STATE
        = BLOCKS.register("signal_state", () -> new SignalStateBlock());

    public static final DeferredBlock<ScriptBlock> SCRIPT_BLOCK
        = BLOCKS.register("script_block", () -> new ScriptBlock());
}
