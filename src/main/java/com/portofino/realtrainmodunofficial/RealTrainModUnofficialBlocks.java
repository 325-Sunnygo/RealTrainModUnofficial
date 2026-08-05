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
    /** 背景パネル: 模型やジオラマの背景に写真を立てる。 */
    public static final DeferredBlock<com.portofino.realtrainmodunofficial.block.BackgroundPanelBlock> BACKGROUND_PANEL
        = BLOCKS.register("background_panel",
            () -> new com.portofino.realtrainmodunofficial.block.BackgroundPanelBlock());
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
    // ───── 本家 RTM_工業 タブのブロック (RTMBlock.init と同じ並び) ─────

    /** 本家 steel_material: ただの鋼のブロック。 */
    public static final DeferredBlock<net.minecraft.world.level.block.Block> STEEL_MATERIAL
        = BLOCKS.register("steel_material", () -> new net.minecraft.world.level.block.Block(
            net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                .strength(2.0F, 10.0F)
                .sound(net.minecraft.world.level.block.SoundType.METAL)
                .requiresCorrectToolForDrops()));

    /** 本家 slot: 液体の吸込口。 */
    public static final DeferredBlock<jp.ngt.rtm.block.BlockSlot> SLOT
        = BLOCKS.register("slot", jp.ngt.rtm.block.BlockSlot::new);

    /** 本家 fire_brick: 耐火レンガ。 */
    public static final DeferredBlock<jp.ngt.rtm.block.BlockFireBrick> FIRE_BRICK
        = BLOCKS.register("fire_brick", () -> new jp.ngt.rtm.block.BlockFireBrick(false));

    /** 本家 hot_stove_brick: 熱風炉レンガ。 */
    public static final DeferredBlock<jp.ngt.rtm.block.BlockFireBrick> HOT_STOVE_BRICK
        = BLOCKS.register("hot_stove_brick", () -> new jp.ngt.rtm.block.BlockFireBrick(true));

    /**
     * 本家 steel_slab: 固まった鋼材。金ノコで鋼インゴットにする。
     * ★本家もクリエイティブタブには出さない (溶けた金属が冷えて生まれる)。
     */
    public static final DeferredBlock<jp.ngt.rtm.block.BlockMetalSlab> STEEL_SLAB
        = BLOCKS.register("steel_slab", jp.ngt.rtm.block.BlockMetalSlab::new);
    /** 本家 iron_pillar: 鉄柱。 */
    public static final DeferredBlock<jp.ngt.rtm.block.BlockIronPillar> IRON_PILLAR
        = BLOCKS.register("iron_pillar", jp.ngt.rtm.block.BlockIronPillar::new);
    /** 本家 station_core: 駅コア (駅名を持つ)。 */
    public static final DeferredBlock<jp.ngt.rtm.block.BlockStation> STATION_CORE
        = BLOCKS.register("station_core", jp.ngt.rtm.block.BlockStation::new);
    /** 本家 train_workbench メタ 0: RTM 専用作業台。 */
    public static final DeferredBlock<jp.ngt.rtm.block.BlockTrainWorkBench> TRAIN_WORKBENCH
        = BLOCKS.register("train_workbench", () -> new jp.ngt.rtm.block.BlockTrainWorkBench(false));

    /** 本家 train_workbench メタ 1: レール用作業台。 */
    public static final DeferredBlock<jp.ngt.rtm.block.BlockTrainWorkBench> RAIL_WORKBENCH
        = BLOCKS.register("rail_workbench", () -> new jp.ngt.rtm.block.BlockTrainWorkBench(true));
    /** 本家 moving_machine: 移動装置。 */
    public static final DeferredBlock<jp.ngt.rtm.block.BlockMovingMachine> MOVING_MACHINE
        = BLOCKS.register("moving_machine", () -> new jp.ngt.rtm.block.BlockMovingMachine(false));

    /** 本家 moving_machine メタ 1: 乗り物生成器。 */
    public static final DeferredBlock<jp.ngt.rtm.block.BlockMovingMachine> VEHICLE_GENERATOR
        = BLOCKS.register("vehicle_generator", () -> new jp.ngt.rtm.block.BlockMovingMachine(true));

    /** 本家 RTMFluid pig_iron_l: 溶けた銑鉄 (液体ブロック)。 */
    public static final DeferredBlock<jp.ngt.rtm.block.BlockMeltedMetal> PIG_IRON_L
        = BLOCKS.register("pig_iron_l", () -> new jp.ngt.rtm.block.BlockMeltedMetal(false));
    /** 本家 RTMFluid steel_l: 溶けた鋼鉄 (固まると steel_slab)。 */
    public static final DeferredBlock<jp.ngt.rtm.block.BlockMeltedMetal> STEEL_L
        = BLOCKS.register("steel_l", () -> new jp.ngt.rtm.block.BlockMeltedMetal(true));
    /** 本家 RTMFluid furnace_fire: 炉の火。 */
    public static final DeferredBlock<jp.ngt.rtm.block.BlockFurnaceFire> FURNACE_FIRE
        = BLOCKS.register("furnace_fire", () -> new jp.ngt.rtm.block.BlockFurnaceFire(true));
    /** 本家 RTMFluid exhaust_gas: 排ガス。熱風炉レンガが熱を吸う。 */
    public static final DeferredBlock<jp.ngt.rtm.block.BlockFurnaceFire> EXHAUST_GAS
        = BLOCKS.register("exhaust_gas", () -> new jp.ngt.rtm.block.BlockFurnaceFire(false));

    /** 本家 decoration: 装飾ブロック (BER 描画・フルキューブ)。 */
    public static final DeferredBlock<jp.ngt.rtm.block.BlockDecoration> DECORATION
        = BLOCKS.register("decoration", () -> new jp.ngt.rtm.block.BlockDecoration(
            net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                .mapColor(net.minecraft.world.level.material.MapColor.STONE)
                .strength(1.0F)));
    /** 本家 brick_slab: ハーフ耐火レンガ。 */
    public static final DeferredBlock<net.minecraft.world.level.block.SlabBlock> BRICK_SLAB
        = BLOCKS.register("brick_slab", () -> new net.minecraft.world.level.block.SlabBlock(
            net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                .mapColor(net.minecraft.world.level.material.MapColor.COLOR_RED)
                .strength(2.0F, 10.0F)
                .sound(net.minecraft.world.level.block.SoundType.STONE)
                .requiresCorrectToolForDrops()));
}