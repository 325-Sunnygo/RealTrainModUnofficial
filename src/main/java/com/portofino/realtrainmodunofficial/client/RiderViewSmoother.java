package com.portofino.realtrainmodunofficial.client;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * カーブでの視点追従をフレーム単位で滑らかにするクライアント専用の補正。
 * EntityVehicleBase.rotateRiders は、車体が 1 tick で回ったぶん (dYaw/dPitch) を乗客の
 * yaw にその tick で全量・瞬時に加える (yRot も yRotO も同時に動かす)。
 */
@EventBusSubscriber(modid = RealTrainModUnofficial.MODID, value = Dist.CLIENT)
public final class RiderViewSmoother {

    // rotateRiders が毎 tick 記録する、その tick の車体回転量 (ローカルプレイヤー搭乗時のみ)。
    private static float dYaw;
    private static float dPitch;

    private RiderViewSmoother() {
    }

    /**
     * EntityVehicleBase.rotateRiders から、ローカルプレイヤーを乗せている車両が毎 tick 呼ぶ。
     * 直進 (dYaw=0) でも呼ぶこと (前カーブの値を残さないため)。
     */
    public static void record(float tickYaw, float tickPitch) {
        dYaw = tickYaw;
        dPitch = tickPitch;
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || FreeCameraController.isActive()) {
            return;
        }
        // RTM 系の車両/座席に乗っている時だけ補正。乗っていなければ残り値を消してリターン。
        if (!isRidingRtm(mc.player.getVehicle())) {
            dYaw = 0.0F;
            dPitch = 0.0F;
            return;
        }
        if (dYaw == 0.0F && dPitch == 0.0F) {
            return;
        }
        float notElapsed = 1.0F - (float) event.getPartialTick();
        // rotateRiders は yaw を -dYaw している。まだ経過していないぶんを戻す = +dYaw*(1-pt)。
        event.setYaw(event.getYaw() + dYaw * notElapsed);
        event.setPitch(event.getPitch() + dPitch * notElapsed);
    }

    private static boolean isRidingRtm(net.minecraft.world.entity.Entity vehicle) {
        return vehicle instanceof jp.ngt.rtm.entity.vehicle.EntityVehicleBase<?>
            || vehicle instanceof jp.ngt.rtm.entity.train.parts.EntityFloor
            || vehicle instanceof com.portofino.realtrainmodunofficial.entity.TrainEntity
            || vehicle instanceof com.portofino.realtrainmodunofficial.entity.TrainSeatEntity;
    }
}
