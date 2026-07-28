package jp.ngt.rtm.rail;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * jp.ngt.rtm.rail の BlockEntityType 登録 (1.21)。
 * メインクラスから REGISTER.register(modBus) される。
 */
public final class RTMRailBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> REGISTER =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, RealTrainModUnofficial.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileEntityLargeRailBase>> LARGE_RAIL_BASE =
            REGISTER.register("large_rail_base", () -> BlockEntityType.Builder.of(TileEntityLargeRailBase::new,
                    RTMRailBlocks.LARGE_RAIL_BASE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileEntityLargeRailNormalCore>> LARGE_RAIL_NORMAL_CORE =
            REGISTER.register("large_rail_normal_core", () -> BlockEntityType.Builder.of(TileEntityLargeRailNormalCore::new,
                    RTMRailBlocks.LARGE_RAIL_NORMAL_CORE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileEntityLargeRailSwitchBase>> LARGE_RAIL_SWITCH_BASE =
            REGISTER.register("large_rail_switch_base", () -> BlockEntityType.Builder.of(TileEntityLargeRailSwitchBase::new,
                    RTMRailBlocks.LARGE_RAIL_SWITCH_BASE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileEntityLargeRailSwitchCore>> LARGE_RAIL_SWITCH_CORE =
            REGISTER.register("large_rail_switch_core", () -> BlockEntityType.Builder.of(TileEntityLargeRailSwitchCore::new,
                    RTMRailBlocks.LARGE_RAIL_SWITCH_CORE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileEntityLargeRailSlopeBase>> LARGE_RAIL_SLOPE_BASE =
            REGISTER.register("large_rail_slope_base", () -> BlockEntityType.Builder.of(TileEntityLargeRailSlopeBase::new,
                    RTMRailBlocks.LARGE_RAIL_SLOPE_BASE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileEntityLargeRailSlopeCore>> LARGE_RAIL_SLOPE_CORE =
            REGISTER.register("large_rail_slope_core", () -> BlockEntityType.Builder.of(TileEntityLargeRailSlopeCore::new,
                    RTMRailBlocks.LARGE_RAIL_SLOPE_CORE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileEntityTurnTableCore>> TURNTABLE_CORE =
            REGISTER.register("turntable_core", () -> BlockEntityType.Builder.of(TileEntityTurnTableCore::new,
                    RTMRailBlocks.TURNTABLE_CORE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileEntityMarker>> MARKER =
            REGISTER.register("marker", () -> BlockEntityType.Builder.of(TileEntityMarker::new,
                    RTMRailBlocks.MARKER.get(), RTMRailBlocks.MARKER_SWITCH.get()).build(null));

    private RTMRailBlockEntities() {
    }
}
