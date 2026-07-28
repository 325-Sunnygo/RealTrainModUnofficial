package com.portofino.realtrainmodunofficial.client;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.entity.TrainEntity;
import com.portofino.realtrainmodunofficial.entity.TrainSeatEntity;
import com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition;
import com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = RealTrainModUnofficial.MODID, value = Dist.CLIENT)
public final class TrainHudOverlay {
    private static final ResourceLocation CAB_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "textures/gui/rtm_cab.png");
    private static final int TEX_SIZE = 512;
    private static final int CAB_W = 416;
    private static final int CAB_H = 48;
    private static boolean cabHidden;

    private TrainHudOverlay() {
    }

    public static void toggleCabHidden() {
        cabHidden = !cabHidden;
    }

    /**
     * HUD が旧 TrainEntity / 新 jp.ngt EntityTrainBase の両方を描けるようにする共通ビュー。
     * doorsClosed = 編成の全車両のドアが閉まっている (戸閉め灯用)。
     */
    /**
     * @param brakeCount    ブレーキ圧計 (黒針) の生値。本家 GuiIngameCustom は brakeCount*3 を 432 で割る
     * @param brakeAirCount 空気残量計 (赤針) の生値。本家は MAX_AIR_COUNT で割る
     */
    private record HudData(int notch, float speed, int maxBrakeNotch, String modelId, boolean doorsClosed,
                           int brakeCount, int brakeAirCount) {
    }

    private static HudData getHudData(Minecraft mc) {
        TrainEntity train = getControlledTrain(mc);
        if (train != null && train.isDriverPassenger(mc.player)) {
            // 旧 TrainEntity はドア状態を持たないため戸閉め灯は常時点灯 (閉扱い)。
            // 旧実装は圧力を持たないのでノッチから概算する。
            int maxBrake = Math.max(1, train.getMaxBrakeNotch());
            int approxBrake = Math.round(144.0F * Math.max(0, -train.getNotch()) / maxBrake);
            return new HudData(train.getNotch(), train.getSpeed(), train.getMaxBrakeNotch(),
                train.getVehicleId(), true, approxBrake,
                jp.ngt.rtm.entity.train.EntityTrainBase.MAX_AIR_COUNT);
        }
        // Phase 2: 本家忠実列車 — 運転士のみ表示 (客席 = 座席オフセット搭乗は非表示)
        if (mc.player.getVehicle() instanceof jp.ngt.rtm.entity.train.EntityTrainBase rtmTrain
                && !rtmTrain.hasSeat(mc.player)) {
            return new HudData(
                rtmTrain.getNotch(),
                rtmTrain.getSpeed(),
                rtmTrain.getConfig().deccelerations.length - 1,
                rtmTrain.getModelName(),
                computeDoorsClosed(rtmTrain),
                rtmTrain.brakeCount,
                rtmTrain.brakeAirCount);
        }
        return null;
    }

    /**
     * ドアが閉じているか (戸閉め灯用)。State_Door (ドア開閉コマンド) で判定する。
     * ドアのアニメ値 (doorMoveL/R) は非運転車両だとクライアントで更新されないことがあり、
     * 「閉めても暗いまま・降りると直る」の原因になっていた。
     */
    private static boolean computeDoorsClosed(jp.ngt.rtm.entity.train.EntityTrainBase train) {
        return train.getTrainStateData(
            jp.ngt.rtm.entity.train.util.TrainState.TrainStateType.State_Door.id) == 0;
    }

    @SubscribeEvent
    public static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.options.hideGui) {
            return;
        }
        if (getHudData(mc) == null) {
            return;
        }
        // フリーカメラ中はキャブを隠すので、経験値バーの抑止もしない (通常表示に戻す)。
        if (cabHidden || FreeCameraController.isActive()) {
            return;
        }

        ResourceLocation layer = event.getName();
        if (VanillaGuiLayers.EXPERIENCE_BAR.equals(layer)
            || VanillaGuiLayers.EXPERIENCE_LEVEL.equals(layer)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.options.hideGui) {
            return;
        }
        // フリーカメラ中は運転UI (キャブ・速度計) を隠す (ユーザー要望)。カメラは車外を飛んでおり
        // 運転していないため。
        if (FreeCameraController.isActive()) {
            return;
        }
        HudData data = getHudData(mc);
        if (data == null) {
            return;
        }

        GuiGraphics g = event.getGuiGraphics();
        Font font = mc.font;
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        VehicleDefinition def = VehicleRegistry.getById(data.modelId());
        boolean showCabOverlay = def == null || !def.isNotDisplayCab();

        if (!cabHidden && showCabOverlay) {
            renderDefaultRtmCab(g, font, data, def, screenW, screenH);
        }
    }

    private static void renderDefaultRtmCab(GuiGraphics graphics, Font font, HudData train,
                                            VehicleDefinition def, int screenW, int screenH) {
        float scale = Math.min(1.0F, screenW / (float) CAB_W);
        int x = Math.round((screenW - CAB_W * scale) * 0.5F);
        int y = Math.round(screenH - CAB_H * scale);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        pose.scale(scale, scale, 1.0F);
        graphics.blit(CAB_TEXTURE, 0, 0, 0.0F, 0.0F, CAB_W, CAB_H, TEX_SIZE, TEX_SIZE);
        // 圧力計は本家 GuiIngameCustom と同じ 2 針。ノッチ段数ではなく実際の圧力を出す。
        // 赤 (UV 32) = 空気残量  240 * brakeAirCount / MAX_AIR_COUNT
        // 黒 (UV  0) = ブレーキ圧 240 * (brakeCount*3) / 432
        drawMeter(graphics, 32, 19, 32, 32, 48, 240.0F * getAirRatio(train));
        drawMeter(graphics, 32, 19, 32, 0, 48, 240.0F * getBrakePressureRatio(train));
        drawMeter(graphics, 72, 19, 32, 64, 48, getSpeedNeedleRotation(train, def));
        drawLever(graphics, 104, 29, train);
        drawWatch(graphics);
        // 速度は中央揃えで描く。
        // (ユーザー報告「2桁いくと横にズレる」)。中心 x を固定すれば桁数が変わっても中央に揃う。
        String kmhText = Integer.toString(getSpeedKmh(train));
        graphics.drawString(font, kmhText, 73 - font.width(kmhText) / 2, 39, 0x00FF00, false);
        // ブレーキ段数表示 (B1-B8)。本家同様ノッチ番号をそのまま出す。x は元の 30 に戻し、y だけ 39。
        graphics.drawString(font, Integer.toString(Math.max(0, -train.notch())), 30, 39, 0x00FF00, false);
        graphics.drawString(font, Integer.toString(getWorldTime()), 338, 8, 0x00FF00, false);
        graphics.drawString(font, getClockText(), 338, 18, 0x00FF00, false);
        drawDoorLamp(graphics, font, train.doorsClosed());
        pose.popPose();
    }

    /**
     * 戸閉め灯: 編成の全ドアが閉まっていると緑に点灯、1 両でも開いていると消灯 (暗い赤)。
     * 実車の戸閉め灯 (ドアが全部閉じて発車できる状態を運転士に知らせる灯) と同じ。
     */
    private static void drawDoorLamp(GuiGraphics graphics, Font font, boolean closed) {
        // マスコン (レバー x=104) のすぐ右。
        int lx = 124;
        int ly = 10;
        int size = 11;
        // 枠 (暗い縁取り) → 本体。
        graphics.fill(lx - 1, ly - 1, lx + size + 1, ly + size + 1, 0xFF101010);
        int body = closed ? 0xFF20E040 : 0xFF3A1010;      //点灯=緑 / 消灯=暗い赤
        graphics.fill(lx, ly, lx + size, ly + size, body);
        if (closed) {
            // 点灯時のハイライト (光っている感じ)。
            graphics.fill(lx + 1, ly + 1, lx + 5, ly + 4, 0xFF90FFB0);
            graphics.fill(lx + size - 4, ly + size - 4, lx + size - 1, ly + size - 1, 0xFF10A030);
        }
        // ラベル「戸閉」 (ランプの上)。緑=点灯時 / 灰=消灯時。
        graphics.drawString(font, "戸閉", lx - 3, ly - 9, closed ? 0x40FF60 : 0x808080, false);
    }

    private static TrainEntity getControlledTrain(Minecraft mc) {
        if (mc.player == null) {
            return null;
        }
        if (mc.player.getVehicle() instanceof TrainEntity train) {
            return train;
        }
        if (mc.player.getVehicle() instanceof TrainSeatEntity seat) {
            return seat.getTrain();
        }
        return null;
    }

    private static void drawLever(GuiGraphics graphics, int x, int y, HudData train) {
        int notch = train.notch();
        // rtm_cab.png のマスコン目盛りは中立(y28)から 3px 等間隔で並ぶ(実測):
        // EB(-8)=y4(赤), B7=y7 ... B1=y25, N=y28, P1=y31 ... P5=y43。
        float offset = 3.0F * notch;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y + offset, 0.0F);
        graphics.blit(CAB_TEXTURE, -4, -2, 0.0F, 80.0F, 8, 3, TEX_SIZE, TEX_SIZE);
        pose.popPose();
    }

    private static void drawWatch(GuiGraphics graphics) {
        int startX = 320;
        int startY = 32;
        int t0 = getWorldTime();
        int hour12 = (t0 / 1000 + 6) % 12;
        drawMeter(graphics, startX, startY, 32, 96, 48, 360.0F * hour12 / 12.0F + 135.0F);
        int minute = (int) ((t0 % 1000) * 0.06F);
        drawMeter(graphics, startX, startY, 32, 128, 48, 360.0F * minute / 60.0F + 135.0F);
    }

    private static void drawMeter(GuiGraphics graphics, int x, int y, int size, int u, int v, float rotation) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        pose.mulPose(Axis.ZP.rotationDegrees(rotation));
        int offset = -(size / 2);
        graphics.blit(CAB_TEXTURE, offset, offset, u, v, size, size, TEX_SIZE, TEX_SIZE);
        pose.popPose();
    }

    private static int getSpeedKmh(HudData train) {
        return Math.round(Math.abs(train.speed()) * 72.0F);
    }

    private static float getSpeedNeedleRotation(HudData train, VehicleDefinition def) {
        float max = 120.0F;
        if (def != null && !def.getNotchMaxSpeeds().isEmpty()) {
            for (Float speed : def.getNotchMaxSpeeds()) {
                if (speed != null) {
                    max = Math.max(max, speed);
                }
            }
        }
        return Math.min(270.0F, 270.0F * getSpeedKmh(train) / Math.max(1.0F, max));
    }

    /** 空気残量計 (赤針)。本家: brakeAirCount / MAX_AIR_COUNT。 */
    private static float getAirRatio(HudData train) {
        float max = jp.ngt.rtm.entity.train.EntityTrainBase.MAX_AIR_COUNT;
        return Math.min(1.0F, Math.max(0.0F, train.brakeAirCount() / max));
    }

    /** ブレーキ圧計 (黒針)。本家: (brakeCount * 3) / 432。 */
    private static float getBrakePressureRatio(HudData train) {
        return Math.min(1.0F, Math.max(0.0F, train.brakeCount() * 3.0F / 432.0F));
    }

    private static int getWorldTime() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : (int) (mc.level.getDayTime() % 24000L);
    }

    private static String getClockText() {
        int t0 = getWorldTime();
        int hour = (t0 / 1000 + 6) % 24;
        int minute = (int) ((t0 % 1000) * 0.06F);
        return hour + ":" + minute;
    }
}
