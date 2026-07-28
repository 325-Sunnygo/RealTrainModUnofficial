package com.portofino.realtrainmodunofficial.client;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

/**
 * フリーカメラモード (RTMU オリジナル)。
 * 列車の運転席・客席に座っている状態でフリーカメラキー (既定 V、MC のコントロール設定で変更可)
 * を押すと「幽体離脱」してカメラがその場から切り離され、クリエイティブ飛行と同じ操作で
 * 自由に飛べる (WASD=水平移動 / スペース=上昇 / スニーク=下降 / ダッシュキー=高速)。
 */
@EventBusSubscriber(modid = RealTrainModUnofficial.MODID, value = Dist.CLIENT)
public final class FreeCameraController {

    private static final float BASE_SPEED = 0.7F;
    private static final float SPRINT_MULT = 3.0F;
    /** マウス視点の速さ。バニラ (Entity.turn) は 0.15。フリーカメラは少し速め。 */
    private static final double LOOK_SPEED = 0.22D;

    private static boolean active;
    private static Vec3 pos = Vec3.ZERO;
    private static Vec3 prevPos = Vec3.ZERO;
    private static CameraType savedCameraType;
    // カメラ独自の向き (体は回さず、マウスはここへ振り向ける = 視点追従オフ)
    private static float camYaw;
    private static float camPitch;

    private FreeCameraController() {
    }

    public static boolean isActive() {
        return active;
    }

    public static float getYaw() {
        return camYaw;
    }

    public static float getPitch() {
        return camPitch;
    }

    /**
     * MouseHandlerMixin から: マウスの視点デルタをカメラの向きへ加える
     * (体は回さない)。係数 0.15 は Entity.turn と同じ。
     */
    public static void addLook(double dyaw, double dpitch) {
        camYaw += (float) (dyaw * LOOK_SPEED);
        camPitch += (float) (dpitch * LOOK_SPEED);
        if (camPitch > 90.0F) {
            camPitch = 90.0F;
        } else if (camPitch < -90.0F) {
            camPitch = -90.0F;
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        if (!TrainControlKeyMappings.FREE_CAMERA.matches(event.getKey(), event.getScanCode())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return;
        }
        // 起動は運転席・客席に座っている時のみ。解除はフリーカメラ中ならいつでも。
        if (!active && !isRidingTrain(mc)) {
            return;
        }
        toggle(mc);
    }

    private static boolean isRidingTrain(Minecraft mc) {
        Entity vehicle = mc.player.getVehicle();
        return vehicle instanceof jp.ngt.rtm.entity.vehicle.EntityVehicleBase<?>
                || vehicle instanceof jp.ngt.rtm.entity.train.parts.EntityFloor
                || vehicle instanceof com.portofino.realtrainmodunofficial.entity.TrainEntity
                || vehicle instanceof com.portofino.realtrainmodunofficial.entity.TrainSeatEntity;
    }

    private static void toggle(Minecraft mc) {
        if (!active) {
            active = true;
            FreeCameraState.active = true;
            pos = mc.player.getEyePosition();
            prevPos = pos;
            camYaw = mc.player.getYRot();
            camPitch = mc.player.getXRot();
            savedCameraType = mc.options.getCameraType();
            // 三人称扱いにして自分 (と列車) が描画されるようにする。位置と向きは毎フレーム上書きする。
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else {
            active = false;
            FreeCameraState.active = false;
            if (savedCameraType != null) {
                mc.options.setCameraType(savedCameraType);
                savedCameraType = null;
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!active) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        // 降車・ログアウトしたら自動解除
        if (mc.player == null || mc.player.getVehicle() == null) {
            if (mc.player != null) {
                toggle(mc);
            } else {
                active = false;
                FreeCameraState.active = false;
                savedCameraType = null;
            }
            return;
        }
        prevPos = pos;
        if (mc.screen != null) {
            return;
        }
        // WASD + スペース/スニークで移動 (カメラ自身の向き基準)
        float yawRad = (float) Math.toRadians(camYaw);
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);
        double dx = 0.0D, dy = 0.0D, dz = 0.0D;
        if (mc.options.keyUp.isDown()) {
            dx += fx;
            dz += fz;
        }
        if (mc.options.keyDown.isDown()) {
            dx -= fx;
            dz -= fz;
        }
        if (mc.options.keyLeft.isDown()) {
            dx += fz;
            dz -= fx;
        }
        if (mc.options.keyRight.isDown()) {
            dx -= fz;
            dz += fx;
        }
        if (mc.options.keyJump.isDown()) {
            dy += 1.0D;
        }
        if (mc.options.keyShift.isDown()) {
            dy -= 1.0D;
        }
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 1.0E-4D) {
            float speed = BASE_SPEED * (mc.options.keySprint.isDown() ? SPRINT_MULT : 1.0F);
            pos = pos.add(dx / len * speed, dy / len * speed, dz / len * speed);
        }
    }

    /**
     * フリーカメラ中は移動 (WASD/スペース/スニーク/マウス視点) 以外の操作を全て無効化する。
     * 攻撃・使用・アイテムピック (左/右/中クリック) を握りつぶす。
     */
    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (active) {
            event.setCanceled(true);
        }
    }

    /** 右クリック等のマウスボタン操作を無効化 (ブロック設置・使用・攻撃)。 */
    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (active) {
            event.setCanceled(true);
        }
    }

    /**
     * フリーカメラ中は E キー等でインベントリを開かせない (ユーザー報告: フリーカメラなのに
     * E でインベントリが開いてしまう)。飛び回っている最中に画面が開くと操作が奪われ没入も切れるため。
     * ポーズメニュー (Esc) は脱出用に通す。
     */
    @SubscribeEvent
    public static void onScreenOpening(net.neoforged.neoforge.client.event.ScreenEvent.Opening event) {
        if (!active) {
            return;
        }
        net.minecraft.client.gui.screens.Screen s = event.getNewScreen();
        if (s instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen
                || s instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen) {
            event.setCanceled(true);
        }
    }

    /**
     * Camera.setup 末尾で CameraMixin が呼ぶ、補間済みカメラ位置。
     * ComputeCameraAngles イベントは position 確定より前に発火し上書きされてしまうため、
     * 位置の上書きは setup の TAIL (mixin) で行う。null ならフリーカメラ非アクティブ。
     */
    public static Vec3 getInterpolatedPosition(float partialTick) {
        if (!active) {
            return null;
        }
        return new Vec3(
                prevPos.x + (pos.x - prevPos.x) * partialTick,
                prevPos.y + (pos.y - prevPos.y) * partialTick,
                prevPos.z + (pos.z - prevPos.z) * partialTick);
    }
}
