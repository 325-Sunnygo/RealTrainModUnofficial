package com.portofino.realtrainmodunofficial.client.render;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

/**
 * 道床のベイクドモデルを {@link BallastModel} へ差し替える。
 *
 * <p>{@code ballast.json} が焼かれた結果 (1/16 の平らな板) を「形が出せないときの控え」として
 * 抱えたまま包む。これでレールコアがまだ読めていない一瞬でも、道床が消えたりしない。
 */
@EventBusSubscriber(modid = RealTrainModUnofficial.MODID, value = Dist.CLIENT,
    bus = EventBusSubscriber.Bus.MOD)
public final class BallastModelHook {

    private BallastModelHook() {
    }

    /**
     * 道床を描くブロック。
     *
     * <p>★敷設で実際に置かれるのは {@code jp.ngt.rtm.rail} 側のレールブロック。
     * {@code RailMap.setRail} が LARGE_RAIL_BASE を敷き詰め、コアだけ別ブロックになる。
     * 本家でも {@code BlockLargeRailBase.getRenderType()} = renderIdBlockRail で、
     * コア (BlockLargeRailCore extends BlockLargeRailBase) も同じ道床を描く。
     *
     * <p>坂レール (LARGE_RAIL_SLOPE_BASE) だけは高さをブロックステートに持つ別系統で、
     * 静的モデル (large_rail_slope_h0〜h15) がそのまま道床になっているので触らない。
     */
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
