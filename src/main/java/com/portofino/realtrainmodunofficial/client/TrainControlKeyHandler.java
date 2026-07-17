package com.portofino.realtrainmodunofficial.client;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems;
import com.portofino.realtrainmodunofficial.client.sound.LegacyScriptSoundManager;
import com.portofino.realtrainmodunofficial.client.screen.TrainControlScreen;
import com.portofino.realtrainmodunofficial.entity.TrainEntity;
import com.portofino.realtrainmodunofficial.entity.TrainSeatEntity;
import com.portofino.realtrainmodunofficial.network.MountTrainPayload;
import com.portofino.realtrainmodunofficial.network.TrainControlPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = RealTrainModUnofficial.MODID, value = Dist.CLIENT)
public final class TrainControlKeyHandler {
    private static final int HOLD_REPEAT_INITIAL_DELAY_TICKS = 7;
    private static final int HOLD_REPEAT_INTERVAL_TICKS = 2;
    private static boolean doorLeftChordDown;
    private static boolean doorRightChordDown;
    private static boolean shiftWasDown;
    private static int powerHoldTicks = -1;
    private static int brakeHoldTicks = -1;
    //jp.ngt 列車の W/S 長押しノッチ用
    private static int rtmPowerHoldTicks = -1;
    private static int rtmBrakeHoldTicks = -1;

    private TrainControlKeyHandler() {
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return;
        }
        //フリーカメラ中は WASD がカメラ移動に使われるため、運転キー (マスコン/ドア/降車) を無効化
        if (FreeCameraController.isActive()) {
            return;
        }
        // Phase 2: 本家忠実列車 (jp.ngt) — マスコン/降車/ドアのみ先行対応
        if (mc.player.getVehicle() instanceof jp.ngt.rtm.entity.train.EntityTrainBase rtmTrain) {
            handleRtmTrainKeys(mc, rtmTrain, event);
            return;
        }
        // slotPos 座席 (EntityFloor): スニークで降車
        if (mc.player.getVehicle() instanceof jp.ngt.rtm.entity.train.parts.EntityFloor floor) {
            if (TrainControlKeyMappings.matchesSneak(event.getKey(), event.getScanCode())) {
                PacketDistributor.sendToServer(new TrainControlPayload(floor.getId(), "dismount", 0));
            }
            return;
        }
        TrainEntity train = getControlledTrain(mc);
        if (train == null) {
            return;
        }
        if (TrainControlKeyMappings.matchesSneak(event.getKey(), event.getScanCode())) {
            PacketDistributor.sendToServer(new TrainControlPayload(train.getId(), "dismount", 0));
            return;
        }
        if (!train.isLikelyDriverPassenger(mc.player)) {
            return;
        }
        if (TrainControlKeyMappings.OPEN_CONTROL.matches(event.getKey(), event.getScanCode())) {
            mc.setScreen(new TrainControlScreen(train));
            return;
        }
        if (TrainControlKeyMappings.TOGGLE_CAB.matches(event.getKey(), event.getScanCode())) {
            TrainHudOverlay.toggleCabHidden();
            return;
        }
        if (TrainControlKeyMappings.POWER_OFF.matches(event.getKey(), event.getScanCode())) {
            PacketDistributor.sendToServer(new TrainControlPayload(train.getId(), "mascon_power", 0), new CustomPacketPayload[0]);
            powerHoldTicks = 0;
            brakeHoldTicks = -1;
            return;
        }
        if (TrainControlKeyMappings.BRAKE_OFF.matches(event.getKey(), event.getScanCode())) {
            PacketDistributor.sendToServer(new TrainControlPayload(train.getId(), "mascon_brake", 0), new CustomPacketPayload[0]);
            brakeHoldTicks = 0;
            powerHoldTicks = -1;
            return;
        }
        if (TrainControlKeyMappings.NEUTRAL.matches(event.getKey(), event.getScanCode())) {
            PacketDistributor.sendToServer(new TrainControlPayload(train.getId(), "mascon_neutral", 0), new CustomPacketPayload[0]);
        }

        //矢印キー単独でドア開閉 (← = 左ドア, → = 右ドア)
        if (event.getKey() == GLFW.GLFW_KEY_LEFT) {
            PacketDistributor.sendToServer(new TrainControlPayload(train.getId(), "toggle_door_left", 0));
            doorLeftChordDown = true;
            return;
        }
        if (event.getKey() == GLFW.GLFW_KEY_RIGHT) {
            PacketDistributor.sendToServer(new TrainControlPayload(train.getId(), "toggle_door_right", 0));
            doorRightChordDown = true;
        }
    }

    /**
     * 本家 1122: マーカーのアンカー線を右クリックで掴む/確定 (レール形状編集)。
     * 掴み/確定時はブロック・アイテムの右クリックを消費する。
     */
    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT || event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return;
        }
        if (com.portofino.realtrainmodunofficial.client.renderer.MarkerBlockEntityRenderer.onRightClick()) {
            event.setCanceled(true);
        }
    }

    /**
     * 本家: 運転席乗車中にインベントリキー → 運転台 GUI (通常インベントリを差し替え)。
     */
    @SubscribeEvent
    public static void onScreenOpening(net.neoforged.neoforge.client.event.ScreenEvent.Opening event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (!(event.getNewScreen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen)) {
            return;
        }
        if (mc.player.getVehicle() instanceof jp.ngt.rtm.entity.train.EntityTrainBase rtmTrain
                && !rtmTrain.hasSeat(mc.player)) {
            event.setNewScreen(new com.portofino.realtrainmodunofficial.client.screen.RtmTrainControlScreen(rtmTrain));
        }
    }

    /**
     * jp.ngt.rtm.entity.train.EntityTrainBase 用のキー処理 (Phase 2 先行版)。
     */
    private static void handleRtmTrainKeys(Minecraft mc, jp.ngt.rtm.entity.train.EntityTrainBase train,
                                           InputEvent.Key event) {
        int id = train.getId();
        if (TrainControlKeyMappings.matchesSneak(event.getKey(), event.getScanCode())) {
            PacketDistributor.sendToServer(new TrainControlPayload(id, "dismount", 0));
            return;
        }
        //客席 (座席オフセット搭乗) は降車のみ — マスコン/ドア等の運転操作は不可
        if (train.hasSeat(mc.player)) {
            return;
        }
        if (TrainControlKeyMappings.TOGGLE_CAB.matches(event.getKey(), event.getScanCode())) {
            TrainHudOverlay.toggleCabHidden();
            return;
        }
        if (TrainControlKeyMappings.POWER_OFF.matches(event.getKey(), event.getScanCode())) {
            PacketDistributor.sendToServer(new TrainControlPayload(id, "mascon_power", 0));
            return;
        }
        if (TrainControlKeyMappings.BRAKE_OFF.matches(event.getKey(), event.getScanCode())) {
            PacketDistributor.sendToServer(new TrainControlPayload(id, "mascon_brake", 0));
            return;
        }
        if (TrainControlKeyMappings.NEUTRAL.matches(event.getKey(), event.getScanCode())) {
            PacketDistributor.sendToServer(new TrainControlPayload(id, "mascon_neutral", 0));
            return;
        }
        if (TrainControlKeyMappings.PLAY_ANNOUNCEMENT.matches(event.getKey(), event.getScanCode())) {
            PacketDistributor.sendToServer(new TrainControlPayload(id, "play_selected_announcement", 0));
            return;
        }
        if (TrainControlKeyMappings.PLAY_HORN.matches(event.getKey(), event.getScanCode())) {
            PacketDistributor.sendToServer(new TrainControlPayload(id, "play_horn", 0));
            return;
        }
        //矢印キー単独でドア開閉 (← = 左ドア, → = 右ドア)
        if (event.getKey() == GLFW.GLFW_KEY_LEFT) {
            PacketDistributor.sendToServer(new TrainControlPayload(id, "toggle_door_left", 0));
            return;
        }
        if (event.getKey() == GLFW.GLFW_KEY_RIGHT) {
            PacketDistributor.sendToServer(new TrainControlPayload(id, "toggle_door_right", 0));
        }
    }

    //座席乗車中の視点追従 (followSeatRotation) は削除した。
    //本家 (KaizPatchX EntityFloor.updateRiderPosition) は 1.7.10 バニラの
    //「乗員が車両の回転に追従する」挙動をわざわざ打ち消しており、
    //座席の視点は運転席と同じく完全フリーが正 (視点が座った角度に
    //引っ張られてがくがくする副作用もあった)。

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            shiftWasDown = false;
            resetHoldState();
            return;
        }

        if (TrainControlKeyMappings.TOGGLE_RENDER_PROFILER.consumeClick()) {
            ClientRenderProfiler.toggleOverlay();
        }

        if (mc.screen != null) {
            doorLeftChordDown = false;
            doorRightChordDown = false;
            resetHoldState();
            return;
        }

        //フリーカメラ中は運転操作 (ノッチ長押し・ドア・降車) を止める
        if (FreeCameraController.isActive()) {
            doorLeftChordDown = false;
            doorRightChordDown = false;
            resetHoldState();
            rtmPowerHoldTicks = -1;
            rtmBrakeHoldTicks = -1;
            return;
        }

        //jp.ngt 本家忠実列車: ノッチの<b>長押しリピートだけ</b>をここで出す。
        //
        //★1 回の押下で 2 段進んでいた原因:
        //  ここは W/S (バニラの前後移動キー) を直接見て「押した瞬間にも 1 段」送っていた。
        //  一方 onKeyInput → handleRtmTrainKeys も POWER_OFF/BRAKE_OFF の押下で 1 段送る。
        //  既定値が POWER_OFF=S / BRAKE_OFF=W と<b>移動キーと同じ</b>なので、1 回押すと
        //  両方から送られて 2 段進んでいた。
        //
        //  初回の 1 段は onKeyInput が担当し、ここはリピートのみ (shouldSendRepeat) にする。
        //  監視するキーも移動キーではなくキーバインド自身にして、リバインドしてもズレないようにする。
        if (mc.player.getVehicle() instanceof jp.ngt.rtm.entity.train.EntityTrainBase rtmTrain
                && !rtmTrain.hasSeat(mc.player)) {
            boolean powerHeld = TrainControlKeyMappings.POWER_OFF.isDown();
            boolean brakeHeld = TrainControlKeyMappings.BRAKE_OFF.isDown();
            if (powerHeld && !brakeHeld) {
                rtmPowerHoldTicks = Math.max(0, rtmPowerHoldTicks + 1);
                rtmBrakeHoldTicks = -1;
                if (shouldSendRepeat(rtmPowerHoldTicks)) {
                    PacketDistributor.sendToServer(new TrainControlPayload(rtmTrain.getId(), "mascon_power", 0));
                }
            } else if (brakeHeld && !powerHeld) {
                rtmBrakeHoldTicks = Math.max(0, rtmBrakeHoldTicks + 1);
                rtmPowerHoldTicks = -1;
                if (shouldSendRepeat(rtmBrakeHoldTicks)) {
                    PacketDistributor.sendToServer(new TrainControlPayload(rtmTrain.getId(), "mascon_brake", 0));
                }
            } else {
                rtmPowerHoldTicks = -1;
                rtmBrakeHoldTicks = -1;
            }
        } else {
            rtmPowerHoldTicks = -1;
            rtmBrakeHoldTicks = -1;
        }

        TrainEntity train = getControlledTrain(mc);
        if (train == null) {
            shiftWasDown = false;
            doorLeftChordDown = false;
            doorRightChordDown = false;
            resetHoldState();
            return;
        }
        boolean shiftDown = mc.options.keyShift.isDown();
        if (shiftDown && !shiftWasDown) {
            PacketDistributor.sendToServer(new TrainControlPayload(train.getId(), "dismount", 0));
            shiftWasDown = true;
            return;
        }
        shiftWasDown = shiftDown;
        if (!train.isLikelyDriverPassenger(mc.player)) {
            doorLeftChordDown = false;
            doorRightChordDown = false;
            resetHoldState();
            return;
        }
        if (TrainControlKeyMappings.OPEN_CONTROL.consumeClick()) {
            mc.setScreen(new TrainControlScreen(train));
        }
        if (TrainControlKeyMappings.TOGGLE_CAB.consumeClick()) {
            TrainHudOverlay.toggleCabHidden();
        }
        if (TrainControlKeyMappings.PLAY_ANNOUNCEMENT.consumeClick()) {
            PacketDistributor.sendToServer(new TrainControlPayload(train.getId(), "play_selected_announcement", 0));
        }
        if (TrainControlKeyMappings.PLAY_HORN.consumeClick()) {
            PacketDistributor.sendToServer(new TrainControlPayload(train.getId(), "play_horn", 0));
        }
        if (TrainControlKeyMappings.NEUTRAL.consumeClick()) {
            PacketDistributor.sendToServer(new TrainControlPayload(train.getId(), "mascon_neutral", 0), new CustomPacketPayload[0]);
        }

        //矢印キー単独でドア開閉 (← = 左ドア, → = 右ドア)。押しっぱなしで連打しないようラッチ。
        boolean leftArrowDown = GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT) == GLFW.GLFW_PRESS;
        boolean rightArrowDown = GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_RIGHT) == GLFW.GLFW_PRESS;
        if (leftArrowDown) {
            if (!doorLeftChordDown) {
                PacketDistributor.sendToServer(new TrainControlPayload(train.getId(), "toggle_door_left", 0));
                doorLeftChordDown = true;
            }
        } else {
            doorLeftChordDown = false;
        }
        if (rightArrowDown) {
            if (!doorRightChordDown) {
                PacketDistributor.sendToServer(new TrainControlPayload(train.getId(), "toggle_door_right", 0));
                doorRightChordDown = true;
            }
        } else {
            doorRightChordDown = false;
        }

        boolean powerHeld = TrainControlKeyMappings.POWER_OFF.isDown();
        boolean brakeHeld = TrainControlKeyMappings.BRAKE_OFF.isDown();
        if (powerHeld && !brakeHeld) {
            powerHoldTicks = Math.max(0, powerHoldTicks + 1);
            brakeHoldTicks = -1;
            if (shouldSendRepeat(powerHoldTicks)) {
                PacketDistributor.sendToServer(new TrainControlPayload(train.getId(), "mascon_power", 0), new CustomPacketPayload[0]);
            }
        } else if (brakeHeld && !powerHeld) {
            brakeHoldTicks = Math.max(0, brakeHoldTicks + 1);
            powerHoldTicks = -1;
            if (shouldSendRepeat(brakeHoldTicks)) {
                PacketDistributor.sendToServer(new TrainControlPayload(train.getId(), "mascon_brake", 0), new CustomPacketPayload[0]);
            }
        } else {
            resetHoldState();
        }
    }

    @SubscribeEvent
    public static void onUseKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.player.getVehicle() != null) {
            return;
        }
        if (mc.hitResult instanceof EntityHitResult) {
            return;
        }
        ItemStack mainHand = mc.player.getMainHandItem();
        ItemStack offHand = mc.player.getOffhandItem();
        boolean holdingCrowbar = mainHand.is(RealTrainModUnofficialItems.CROWBAR_ITEM.get())
            || offHand.is(RealTrainModUnofficialItems.CROWBAR_ITEM.get());
        if (!holdingCrowbar) {
            return;
        }
        PacketDistributor.sendToServer(MountTrainPayload.INSTANCE);
    }

    private static boolean shouldSendRepeat(int heldTicks) {
        if (heldTicks < HOLD_REPEAT_INITIAL_DELAY_TICKS) {
            return false;
        }
        return (heldTicks - HOLD_REPEAT_INITIAL_DELAY_TICKS) % HOLD_REPEAT_INTERVAL_TICKS == 0;
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

    private static void resetHoldState() {
        //注意: rtmPowerHoldTicks/rtmBrakeHoldTicks はここでリセットしない。
        //このメソッドはレガシー列車が null の tick (= jp.ngt 列車搭乗中) にも
        //毎 tick 呼ばれるため、リセットすると W/S 長押しカウンタが進まなくなる。
        powerHoldTicks = -1;
        brakeHoldTicks = -1;
    }
}
