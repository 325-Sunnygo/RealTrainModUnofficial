package com.portofino.realtrainmodunofficial.client.render;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.resources.model.ModelResourceLocation;

/**
 * 道床のベイクドモデルを {@link BallastFabricModel} へ差し替える (Fabric 版)。
 *
 * <p>NeoForge 版と役目は同じ。焼かれた {@code ballast.json} (1/16 の平らな板) を
 * 「形が出せないときの控え」として包む。
 */
public final class BallastModelHook {

    private BallastModelHook() {
    }

    public static void register() {
        RealTrainModUnofficial.LOGGER.info("[道床] モデル差し替えを登録します (FRAPI={})",
            BallastFabricModel.available());
        if (!BallastFabricModel.available()) {
            RealTrainModUnofficial.LOGGER.warn(
                "FRAPI が無いので道床が平らなままになります");
            return;
        }
        ModelLoadingPlugin.register(ctx -> ctx.modifyModelAfterBake().register((model, context) -> {
            if (model == null || model instanceof BallastFabricModel) {
                return model;
            }
            if (!isBallast(context.topLevelId())) {
                return model;
            }
            RealTrainModUnofficial.LOGGER.info("[道床] モデルを差し替えました: {}", context.topLevelId());
            return new BallastFabricModel(model);
        }));
    }

    /**
     * 道床を描くブロック。
     *
     * <p>★敷設で実際に置かれるのは {@code jp.ngt.rtm.rail} 側のレールブロック。
     * {@code RailMap.setRail} が large_rail_base を敷き詰め、コアだけ別ブロックになる。
     * 本家でも {@code BlockLargeRailBase.getRenderType()} = renderIdBlockRail で、
     * コア (BlockLargeRailCore extends BlockLargeRailBase) も同じ道床を描く。
     *
     * <p>坂レール (large_rail_slope_base) だけは高さをブロックステートに持つ別系統で、
     * 静的モデル (large_rail_slope_h0〜h15) がそのまま道床になっているので触らない。
     */
    private static final java.util.Set<String> TARGET_PATHS = java.util.Set.of(
        "large_rail_base",
        "large_rail_normal_core",
        "large_rail_switch_base",
        "large_rail_switch_core",
        "large_rail_slope_core");

    private static boolean isBallast(ModelResourceLocation id) {
        if (id == null) {
            return false;
        }
        return id.id().getNamespace().equals(RealTrainModUnofficial.MODID)
            && TARGET_PATHS.contains(id.id().getPath());
    }
}
