package com.portofino.realtrainmodunofficial.client;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems;
import com.portofino.realtrainmodunofficial.client.screen.TrainControlScreen;
import com.portofino.realtrainmodunofficial.entity.TrainEntity;
import com.portofino.realtrainmodunofficial.entity.TrainSeatEntity;
import com.portofino.realtrainmodunofficial.network.MountTrainPayload;
import com.portofino.realtrainmodunofficial.network.TrainControlPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** 本家 RTMKeyHandlerClient と同じキー操作。 */
@EventBusSubscriber(modid = RealTrainModUnofficial.MODID, value = Dist.CLIENT)
public final class TrainControlKeyHandler {
    private static final int HOLD_REPEAT_INITIAL_DELAY_TICKS = 7;
    private static final int HOLD_REPEAT_INTERVAL_TICKS = 2;
    private static boolean doorLeftChordDown;
    private static boolean doorRightChordDown;
    private static boolean shiftWasDown;
    private static int powerHoldTicks = -1;
    private static int brakeHoldTicks = -1;
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
        if (FreeCameraController.isActive()) {
            return;
        }
        // 自動車: インベントリキーで車両 GUI (バニラのインベントリは消費して開かない)
        if (mc.player.getVehicle() instanceof com.portofino.realtrainmodunofficial.entity.CarEntity car
                && TrainControlKeyMappings.matchesInventory(mc, event.getKey(), event.getScanCode())) {
            mc.options.keyInventory.consumeClick();
            mc.setScreen(new com.portofino.realtrainmodunofficial.client.screen.CarControlScreen(car));
            return;
        }
        // 本家と同じ: W/S の押下直後に 1 段動かす (長押しリピートは tick 側)。
        if (mc.player.getVehicle() instanceof jp.ngt.rtm.entity.train.EntityTrainBase rtmTrainPress
                && !rtmTrainPress.hasSeat(mc.player)) {
            if (mc.options.keyDown.matches(event.getKey(), event.getScanCode())) {
                PacketDistributor.sendToServer(new TrainControlPayload(rtmTrainPress.getId(), "mascon_power", 0));
                rtmPowerHoldTicks = 0;
                return;
            }
            if (mc.options.keyUp.matches(event.getKey(), event.getScanCode())) {
                PacketDistributor.sendToServer(new TrainControlPayload(rtmTrainPress.getId(), "mascon_brake", 0));
                rtmBrakeHoldTicks = 0;
                return;
            }
        }
        TrainEntity pressTrain = getControlledTrain(mc);
        if (pressTrain != null && pressTrain.isLikelyDriverPassenger(mc.player)) {
            if (mc.options.keyDown.matches(event.getKey(), event.getScanCode())) {
                PacketDistributor.sendToServer(new TrainControlPayload(pressTrain.getId(), "mascon_power", 0));
                powerHoldTicks = 0;
                return;
            }
            if (mc.options.keyUp.matches(event.getKey(), event.getScanCode())) {
                PacketDistributor.sendToServer(new TrainControlPayload(pressTrain.getId(), "mascon_brake", 0));
                brakeHoldTicks = 0;
                return;
            }
        }
        // ドアは Space + ←/→ (本家と同じチャord)
        if (mc.options.keyJump.isDown()) {
            TrainEntity train = getControlledTrain(mc);
            if (train != null && train.isLikelyDriverPassenger(mc.player)) {
                if (TrainControlKeyMappings.KEY_CHIME_NEXT.matches(event.getKey(), event.getScanCode())) {
                    PacketDistributor.sendToServer(new TrainControlPayload(train.getId(), "toggle_door_right", 0));
                    doorRightChordDown = true;
                    return;
                }
                if (TrainControlKeyMappings.KEY_CHIME_PREV.matches(event.getKey(), event.getScanCode())) {
                    PacketDistributor.sendToServer(new TrainControlPayload(train.getId(), "toggle_door_left", 0));
                    doorLeftChordDown = true;
                    return;
                }
            }
            return;
        }
    }

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
            return;
        }
        if (com.portofino.realtrainmodunofficial.client.ActionPartsPicker.onRightClick(mc)) {
            event.setCanceled(true);
        }
    }

    /** 運転席乗車中にインベントリキー → 運転台 GUI (バニラのインベントリを差し替え)。 */
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
        } else if (getControlledTrain(mc) != null) {
            event.setNewScreen(new TrainControlScreen(getControlledTrain(mc)));
        }
    }

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

        if (FreeCameraController.isActive()) {
            doorLeftChordDown = false;
            doorRightChordDown = false;
            resetHoldState();
            rtmPowerHoldTicks = -1;
            rtmBrakeHoldTicks = -1;
            return;
        }

        // jp.ngt 本家忠実列車: W/S 長押しでマスコン連打
        if (mc.player.getVehicle() instanceof jp.ngt.rtm.entity.train.EntityTrainBase rtmTrain
                && !rtmTrain.hasSeat(mc.player)) {
            tickMasconHold(mc, rtmTrain.getId());
            tickRtmTrainKeys(mc, rtmTrain.getId());
            return;
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

        tickMasconHold(mc, train.getId());
        tickTrainKeys(train.getId());
    }

    /** 本家 RTM: W=ブレーキ方向(ノッチ減)、S=力行方向(ノッチ増)。 */
    private static void tickMasconHold(Minecraft mc, int trainId) {
        boolean wHeld = mc.options.keyUp.isDown();
        boolean sHeld = mc.options.keyDown.isDown();
        if (wHeld && !sHeld) {
            if (brakeHoldTicks < 0) {
                // 押下直後は onKeyInput で送信済み。カウンタだけ進める。
                brakeHoldTicks = 0;
                powerHoldTicks = -1;
            }
            brakeHoldTicks++;
            if (brakeHoldTicks >= HOLD_REPEAT_INITIAL_DELAY_TICKS
                    && (brakeHoldTicks - HOLD_REPEAT_INITIAL_DELAY_TICKS) % HOLD_REPEAT_INTERVAL_TICKS == 0) {
                PacketDistributor.sendToServer(new TrainControlPayload(trainId, "mascon_brake", 0));
            }
        } else if (sHeld && !wHeld) {
            if (powerHoldTicks < 0) {
                powerHoldTicks = 0;
                brakeHoldTicks = -1;
            }
            powerHoldTicks++;
            if (powerHoldTicks >= HOLD_REPEAT_INITIAL_DELAY_TICKS
                    && (powerHoldTicks - HOLD_REPEAT_INITIAL_DELAY_TICKS) % HOLD_REPEAT_INTERVAL_TICKS == 0) {
                PacketDistributor.sendToServer(new TrainControlPayload(trainId, "mascon_power", 0));
            }
        } else {
            powerHoldTicks = -1;
            brakeHoldTicks = -1;
        }
    }

    /** 本家 RTMKeyHandlerClient.keyDown 相当 (1 回押しのキー)。 */
    private static void tickTrainKeys(int trainId) {
        sendIfPressed(TrainControlKeyMappings.KEY_HORN, trainId, "play_horn");
        sendIfPressed(TrainControlKeyMappings.KEY_CHIME, trainId, "play_selected_announcement");
        sendIfPressed(TrainControlKeyMappings.KEY_ATS, trainId, "ats");
        sendIfPressed(TrainControlKeyMappings.KEY_EB, trainId, "eb");
        sendIfPressed(TrainControlKeyMappings.KEY_CHIME_NEXT, trainId, "next_sound");
        sendIfPressed(TrainControlKeyMappings.KEY_CHIME_PREV, trainId, "prev_sound");
        sendIfPressed(TrainControlKeyMappings.KEY_REVERSER_FORWARD, trainId, "reverser_up");
        sendIfPressed(TrainControlKeyMappings.KEY_REVERSER_BACK, trainId, "reverser_down");
        sendIfPressed(TrainControlKeyMappings.KEY_PANTOGRAPH, trainId, "toggle_pantograph");
        sendIfPressed(TrainControlKeyMappings.KEY_DEST_NEXT, trainId, "next_destination");
        sendIfPressed(TrainControlKeyMappings.KEY_DEST_PREV, trainId, "prev_destination");
        if (TrainControlKeyMappings.TOGGLE_CAB.consumeClick()) {
            TrainHudOverlay.toggleCabHidden();
        }
    }

    private static void tickRtmTrainKeys(Minecraft mc, int trainId) {
        if (mc.options.keyShift.isDown()) {
            PacketDistributor.sendToServer(new TrainControlPayload(trainId, "dismount", 0));
            return;
        }
        sendIfPressed(TrainControlKeyMappings.KEY_HORN, trainId, "play_horn");
        sendIfPressed(TrainControlKeyMappings.KEY_CHIME, trainId, "play_selected_announcement");
        sendIfPressed(TrainControlKeyMappings.KEY_ATS, trainId, "ats");
        sendIfPressed(TrainControlKeyMappings.KEY_EB, trainId, "eb");
        sendIfPressed(TrainControlKeyMappings.KEY_CHIME_NEXT, trainId, "next_sound");
        sendIfPressed(TrainControlKeyMappings.KEY_CHIME_PREV, trainId, "prev_sound");
        sendIfPressed(TrainControlKeyMappings.KEY_REVERSER_FORWARD, trainId, "reverser_up");
        sendIfPressed(TrainControlKeyMappings.KEY_REVERSER_BACK, trainId, "reverser_down");
        sendIfPressed(TrainControlKeyMappings.KEY_PANTOGRAPH, trainId, "toggle_pantograph");
        sendIfPressed(TrainControlKeyMappings.KEY_DEST_NEXT, trainId, "next_destination");
        sendIfPressed(TrainControlKeyMappings.KEY_DEST_PREV, trainId, "prev_destination");
        if (TrainControlKeyMappings.TOGGLE_CAB.consumeClick()) {
            TrainHudOverlay.toggleCabHidden();
        }
    }

    private static void sendIfPressed(KeyMapping key, int trainId, String action) {
        if (key.consumeClick()) {
            PacketDistributor.sendToServer(new TrainControlPayload(trainId, action, 0));
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
        powerHoldTicks = -1;
        brakeHoldTicks = -1;
    }
}
