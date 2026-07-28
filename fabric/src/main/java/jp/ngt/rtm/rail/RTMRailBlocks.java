package jp.ngt.rtm.rail;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 本家 jp.ngt.rtm.RTMRail のブロック登録 (1.21 DeferredRegister 版)。
 * メインクラスから REGISTER.register(modBus) される。
 */
public final class RTMRailBlocks {
    public static final DeferredRegister.Blocks REGISTER = DeferredRegister.createBlocks(RealTrainModUnofficial.MODID);

    //本家: largeRailBase0 (rtm:LRBase)
    public static final DeferredHolder<Block, BlockLargeRailBase> LARGE_RAIL_BASE =
            REGISTER.register("large_rail_base", () -> new BlockLargeRailBase(2, BlockBehaviour.Properties.of()));
    //本家: largeRailCore0 (rtm:LRCore)
    public static final DeferredHolder<Block, BlockLargeRailCore> LARGE_RAIL_NORMAL_CORE =
            REGISTER.register("large_rail_normal_core", () -> new BlockLargeRailCore(2, BlockBehaviour.Properties.of()));
    //本家: largeRailSwitchBase0 (rtm:LRSBase)
    public static final DeferredHolder<Block, BlockLargeRailSwitchBase> LARGE_RAIL_SWITCH_BASE =
            REGISTER.register("large_rail_switch_base", () -> new BlockLargeRailSwitchBase(2, BlockBehaviour.Properties.of()));
    //本家: largeRailSwitchCore0 (rtm:LRSCore)
    public static final DeferredHolder<Block, BlockLargeRailSwitchCore> LARGE_RAIL_SWITCH_CORE =
            REGISTER.register("large_rail_switch_core", () -> new BlockLargeRailSwitchCore(2, BlockBehaviour.Properties.of()));
    //本家: largeRailSlopeBase0
    public static final DeferredHolder<Block, BlockLargeRailSlopeBase> LARGE_RAIL_SLOPE_BASE =
            REGISTER.register("large_rail_slope_base", () -> new BlockLargeRailSlopeBase(2, BlockBehaviour.Properties.of()));
    //本家: largeRailSlopeCore0
    public static final DeferredHolder<Block, BlockLargeRailSlopeCore> LARGE_RAIL_SLOPE_CORE =
            REGISTER.register("large_rail_slope_core", () -> new BlockLargeRailSlopeCore(2, BlockBehaviour.Properties.of()));
    //本家: TURNTABLE_CORE (rtm:turntable_core)
    public static final DeferredHolder<Block, BlockTurntableCore> TURNTABLE_CORE =
            REGISTER.register("turntable_core", () -> new BlockTurntableCore(2, BlockBehaviour.Properties.of()));

    //本家: RTMBlock.marker (markerType=0)
    public static final DeferredHolder<Block, BlockMarker> MARKER =
            REGISTER.register("marker", () -> new BlockMarker(0, BlockBehaviour.Properties.of()));
    //本家: RTMBlock.markerSwitch (markerType=1)
    public static final DeferredHolder<Block, BlockMarker> MARKER_SWITCH =
            REGISTER.register("marker_switch", () -> new BlockMarker(1, BlockBehaviour.Properties.of()));

    private RTMRailBlocks() {
    }
}
