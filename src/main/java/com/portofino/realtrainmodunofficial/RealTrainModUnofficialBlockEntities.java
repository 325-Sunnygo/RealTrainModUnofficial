package com.portofino.realtrainmodunofficial;

import com.portofino.realtrainmodunofficial.blockentity.LargeRailCoreBlockEntity;
import com.portofino.realtrainmodunofficial.blockentity.MarkerBlockEntity;
import com.portofino.realtrainmodunofficial.blockentity.RailCollisionBlockEntity;
import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.blockentity.ScriptBlockEntity;
import com.portofino.realtrainmodunofficial.blockentity.SignalRemoteBlockEntity;
import com.portofino.realtrainmodunofficial.blockentity.SignalStateBlockEntity;
import com.portofino.realtrainmodunofficial.blockentity.TrainDetectorBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class RealTrainModUnofficialBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
        DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, RealTrainModUnofficial.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MarkerBlockEntity>> MARKER =
        BLOCK_ENTITY_TYPES.register("legacy_marker", () -> BlockEntityType.Builder.of(MarkerBlockEntity::new,
            RealTrainModUnofficialBlocks.MARKER.get(), RealTrainModUnofficialBlocks.MARKER_SWITCH.get()).build(null));

    /** 設置済みミニチュア (neo mcte)。 */
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,
        net.minecraft.world.level.block.entity.BlockEntityType<com.portofino.realtrainmodunofficial.blockentity.MiniatureBlockEntity>> MINIATURE =
        BLOCK_ENTITY_TYPES.register("miniature", () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(
            com.portofino.realtrainmodunofficial.blockentity.MiniatureBlockEntity::new,
            RealTrainModUnofficialBlocks.MINIATURE.get()).build(null));

    /** 背景パネル。 */
    public static final DeferredHolder<BlockEntityType<?>,
        BlockEntityType<com.portofino.realtrainmodunofficial.blockentity.BackgroundPanelBlockEntity>> BACKGROUND_PANEL =
        BLOCK_ENTITY_TYPES.register("background_panel", () -> BlockEntityType.Builder.of(
            com.portofino.realtrainmodunofficial.blockentity.BackgroundPanelBlockEntity::new,
            RealTrainModUnofficialBlocks.BACKGROUND_PANEL.get()).build(null));

    /** レールコア: 起点ブロック1個。道床とは無関係。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LargeRailCoreBlockEntity>> LARGE_RAIL_CORE =
        BLOCK_ENTITY_TYPES.register("legacy_large_rail_core", () -> BlockEntityType.Builder.of(LargeRailCoreBlockEntity::new,
            RealTrainModUnofficialBlocks.LARGE_RAIL_CORE.get()).build(null));

    /** レール当たり判定ブロック: レールコア削除に追従する。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RailCollisionBlockEntity>> RAIL_COLLISION =
        BLOCK_ENTITY_TYPES.register("legacy_rail_collision", () -> BlockEntityType.Builder.of(RailCollisionBlockEntity::new,
            RealTrainModUnofficialBlocks.RAIL_COLLISION.get()).build(null));

    /** 道床ブロック: 対応レールコア位置を保持し、壊すとレールも撤去・列車設置検出にも使う。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.portofino.realtrainmodunofficial.blockentity.BallastBlockEntity>> BALLAST =
        BLOCK_ENTITY_TYPES.register("legacy_ballast", () -> BlockEntityType.Builder.of(
            com.portofino.realtrainmodunofficial.blockentity.BallastBlockEntity::new,
            RealTrainModUnofficialBlocks.BALLAST.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InstalledObjectBlockEntity>> INSTALLED_OBJECT =
        BLOCK_ENTITY_TYPES.register("installed_object", () -> BlockEntityType.Builder.of(InstalledObjectBlockEntity::new,
            RealTrainModUnofficialBlocks.INSTALLED_OBJECT.get()).build(null));

    /** 本家 decoration: 装飾ブロック */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<jp.ngt.rtm.block.tileentity.TileEntityDecoration>> DECORATION =
        BLOCK_ENTITY_TYPES.register("decoration", () -> BlockEntityType.Builder.of(
            jp.ngt.rtm.block.tileentity.TileEntityDecoration::new,
            RealTrainModUnofficialBlocks.DECORATION.get()).build(null));

    /** 本家 electric: 信号変換器 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<jp.ngt.rtm.electric.TileEntitySignalConverter>> SIGNAL_CONVERTER =
        BLOCK_ENTITY_TYPES.register("signal_converter", () -> BlockEntityType.Builder.of(
            jp.ngt.rtm.electric.TileEntitySignalConverter::new,
            RealTrainModUnofficialBlocks.SIGNAL_CONVERTER.get()).build(null));

    /** SignalControllerMod (masa300) 移植: 信号制御器 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<jp.masa.signalcontrollermod.TileEntitySignalController>> SIGNAL_CONTROLLER =
        BLOCK_ENTITY_TYPES.register("signal_controller", () -> BlockEntityType.Builder.of(
            jp.masa.signalcontrollermod.TileEntitySignalController::new,
            RealTrainModUnofficialBlocks.SIGNAL_CONTROLLER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SignalRemoteBlockEntity>> SIGNAL_REMOTE =
        BLOCK_ENTITY_TYPES.register("signal_remote", () -> BlockEntityType.Builder.of(SignalRemoteBlockEntity::new,
            RealTrainModUnofficialBlocks.SIGNAL_RECEIVER.get(),
            RealTrainModUnofficialBlocks.SIGNAL_CHANGER.get(),
            RealTrainModUnofficialBlocks.SIGNAL_VALUE_RECEIVER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TrainDetectorBlockEntity>> TRAIN_DETECTOR =
        BLOCK_ENTITY_TYPES.register("train_detector", () -> BlockEntityType.Builder.of(TrainDetectorBlockEntity::new,
            RealTrainModUnofficialBlocks.TRAIN_DETECTOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SignalStateBlockEntity>> SIGNAL_STATE =
        BLOCK_ENTITY_TYPES.register("signal_state", () -> BlockEntityType.Builder.of(SignalStateBlockEntity::new,
            RealTrainModUnofficialBlocks.SIGNAL_STATE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ScriptBlockEntity>> SCRIPT_BLOCK =
        BLOCK_ENTITY_TYPES.register("script_block", () -> BlockEntityType.Builder.of(ScriptBlockEntity::new,
            RealTrainModUnofficialBlocks.SCRIPT_BLOCK.get()).build(null));
    /** 本家 TileEntitySlot (液体の吸込口)。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<jp.ngt.rtm.block.tileentity.TileEntitySlot>> SLOT =
        BLOCK_ENTITY_TYPES.register("slot", () -> BlockEntityType.Builder.of(
            jp.ngt.rtm.block.tileentity.TileEntitySlot::new,
            RealTrainModUnofficialBlocks.SLOT.get()).build(null));
    /** 本家 TileEntityStation (駅コア)。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<jp.ngt.rtm.block.tileentity.TileEntityStation>> STATION =
        BLOCK_ENTITY_TYPES.register("station", () -> BlockEntityType.Builder.of(
            jp.ngt.rtm.block.tileentity.TileEntityStation::new,
            RealTrainModUnofficialBlocks.STATION_CORE.get()).build(null));
    /** 本家 TileEntityMovingMachine (移動装置)。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<jp.ngt.rtm.block.tileentity.TileEntityMovingMachine>> MOVING_MACHINE =
        BLOCK_ENTITY_TYPES.register("moving_machine", () -> BlockEntityType.Builder.of(
            jp.ngt.rtm.block.tileentity.TileEntityMovingMachine::new,
            RealTrainModUnofficialBlocks.MOVING_MACHINE.get(),
            RealTrainModUnofficialBlocks.VEHICLE_GENERATOR.get()).build(null));
}