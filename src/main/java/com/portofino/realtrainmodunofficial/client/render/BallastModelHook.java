package com.portofino.realtrainmodunofficial.client.render;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

/** 道床のベイクドモデルを BallastModel へ差し替える。 */
@EventBusSubscriber(modid = RealTrainModUnofficial.MODID, value = Dist.CLIENT,
    bus = EventBusSubscriber.Bus.MOD)
public final class BallastModelHook {

    private BallastModelHook() {
    }

    /** 道床を描くブロック。 ★敷設で実際に置かれるのは jp.ngt.rtm.rail 側のレールブロッ… */
    private static java.util.List<net.minecraft.world.level.block.Block> targets() {
        return java.util.List.of(
            jp.ngt.rtm.rail.RTMRailBlocks.LARGE_RAIL_BASE.get(),
            jp.ngt.rtm.rail.RTMRailBlocks.LARGE_RAIL_NORMAL_CORE.get(),
            jp.ngt.rtm.rail.RTMRailBlocks.LARGE_RAIL_SWITCH_BASE.get(),
            jp.ngt.rtm.rail.RTMRailBlocks.LARGE_RAIL_SWITCH_CORE.get(),
            jp.ngt.rtm.rail.RTMRailBlocks.LARGE_RAIL_SLOPE_CORE.get());
    }

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        int replaced = 0;
        for (var target : targets()) {
            for (BlockState state : target.getStateDefinition().getPossibleStates()) {
                ModelResourceLocation key = net.minecraft.client.renderer.block.BlockModelShaper
                    .stateToModelLocation(state);
                BakedModel original = event.getModels().get(key);
                if (original == null || original instanceof BallastModel) {
                    continue;
                }
                event.getModels().put(key, new BallastModel(original));
                replaced++;
            }
        }
        if (replaced == 0) {
            RealTrainModUnofficial.LOGGER.warn(
                "道床のモデルを差し替えられませんでした (道床が出ません)");
        }
    }
}
