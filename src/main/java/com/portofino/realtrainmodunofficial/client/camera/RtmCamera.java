package com.portofino.realtrainmodunofficial.client.camera;

import com.mojang.blaze3d.platform.NativeImage;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 撮り鉄用カメラ本体。本家 jp.ngt.rtm.gui.camera.Camera の作り直し。
 * 本家は「ズーム倍率・感度・深度バッファのフォーカス値」を直接いじる作りだったが、
 * ここは実際のカメラと同じ操作系にしてある:
 */
public final class RtmCamera {

    public static final RtmCamera INSTANCE = new RtmCamera();

    private static final DateTimeFormatter FILE_TIME =
        DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss");
    /** AF-C が列車を探す最大距離 (m)。望遠で撮るので長めに取る。 */
    private static final double AF_C_RANGE = 256.0D;

    private final CameraState state = new CameraState();
    private boolean active;
    /** 撮影フラッシュの残り (フレーム)。0 で消える。 */
    private int flash;
    /** AF-C が掴んでいる列車。見失うまで追い続ける。 */
    private Entity trackedTrain;
    /** ピントが合っている距離 (m)。AF はここへ滑らかに寄せる。 */
    private float currentFocus = 20.0F;
    /** 直近のファインダー表示用。AF が何に合ったか。 */
    private String focusTarget = "";

    private RtmCamera() {
    }

    public CameraState state() {
        return state;
    }

    public boolean isActive() {
        return active;
    }

    public float getFlash() {
        return flash <= 0 ? 0.0F : (float) flash / 6.0F;
    }

    public String getFocusTarget() {
        return focusTarget;
    }

    /** 実際にピントが合っている距離 (m)。DOF シェーダーに渡す。 */
    public float getCurrentFocus() {
        return currentFocus;
    }

    public void toggle() {
        if (active) {
            close();
        } else {
            active = true;
            trackedTrain = null;
        }
    }

    private void message(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) {
            mc.gui.setOverlayMessage(net.minecraft.network.chat.Component.literal(msg), false);
        }
    }

    public void close() {
        active = false;
        trackedTrain = null;
        flash = 0;
        CameraPostProcessor.reset();
    }

    /**
     * 本家 Camera.getFov 相当。焦点距離から実際の FOV を出す。
     * 単純に fov/zoom にすると望遠側で画角が不自然になるので、本家と同じく
     * 「イメージセンサー上の像高が zoom 倍になる」= tan(fov/2) を割る、で計算する。
     */
    public double computeFov(double baseFov) {
        double half = Math.toRadians(baseFov * 0.5D);
        double t = Math.tan(half) / state.getZoomScale();
        return Math.toDegrees(Math.atan(t) * 2.0D);
    }

    /** マウス感度をズームに合わせて落とす。800mm で等倍のままだと狙いが定まらない。 */
    public double getMouseSensitivityScale() {
        return 1.0D / Math.max(1.0D, Math.sqrt(state.getZoomScale()));
    }

    // ---- 毎 tick ----

    public void tick(Minecraft mc) {
        if (flash > 0) {
            --flash;
        }
        if (!active || mc.level == null || mc.player == null) {
            return;
        }
        // GUI を開いている間は操作しない (チャット等)
        if (mc.screen != null) {
            return;
        }
        handleKeys(mc);
        updateFocus(mc);
    }

    private void handleKeys(Minecraft mc) {
        if (CameraKeyMappings.ZOOM_IN.isDown()) {
            state.zoom(1.0F);
        }
        if (CameraKeyMappings.ZOOM_OUT.isDown()) {
            state.zoom(-1.0F);
        }
        if (state.getFocusMode() == CameraState.FocusMode.MF) {
            if (CameraKeyMappings.FOCUS_FAR.isDown()) {
                state.stepFocus(1.0F);
            }
            if (CameraKeyMappings.FOCUS_NEAR.isDown()) {
                state.stepFocus(-1.0F);
            }
        }
        // 押しっぱなしで連続変化させたくないもの (段階的な設定) は consumeClick
        while (CameraKeyMappings.APERTURE_OPEN.consumeClick()) {
            state.stepAperture(-1);
        }
        while (CameraKeyMappings.APERTURE_CLOSE.consumeClick()) {
            state.stepAperture(1);
        }
        while (CameraKeyMappings.SHUTTER_FASTER.consumeClick()) {
            state.stepShutter(-1);
        }
        while (CameraKeyMappings.SHUTTER_SLOWER.consumeClick()) {
            state.stepShutter(1);
        }
        while (CameraKeyMappings.FOCUS_MODE.consumeClick()) {
            state.cycleFocusMode();
            trackedTrain = null;
        }
        while (CameraKeyMappings.CYCLE_GRID.consumeClick()) {
            state.cycleGrid();
        }
        while (CameraKeyMappings.CYCLE_ASPECT.consumeClick()) {
            state.cycleAspectGuide();
        }
        while (CameraKeyMappings.TOGGLE_LEVEL.consumeClick()) {
            state.toggleLevel();
        }
        while (CameraKeyMappings.SHOOT.consumeClick()) {
            shoot(mc);
        }
    }

    // ---- ピント ----

    private void updateFocus(Minecraft mc) {
        float target = switch (state.getFocusMode()) {
            case AF_S -> autofocusCenter(mc);
            case AF_C -> autofocusTrain(mc);
            case MF -> {
                focusTarget = "";
                yield state.getFocusDistance();
            }
        };
        if (state.getFocusMode() != CameraState.FocusMode.MF) {
            state.setFocusDistance(target);
        }
        // 実機のように少し遅れて合焦する (パッと切り替わると不自然、AF-C の追従感も出る)
        currentFocus = Mth.lerp(0.35F, currentFocus, state.getFocusDistance());
    }

    /** AF-S: 画面中央にあるもの (ブロック / エンティティ) までの距離。 */
    private float autofocusCenter(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) {
            return state.getFocusDistance();
        }
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        double range = AF_C_RANGE;
        Vec3 end = eye.add(look.scale(range));

        double best = range;
        String label = "";

        HitResult block = mc.level == null ? null : mc.level.clip(
            new net.minecraft.world.level.ClipContext(eye, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        if (block != null && block.getType() == HitResult.Type.BLOCK) {
            best = eye.distanceTo(((BlockHitResult) block).getLocation());
            label = "地形";
        }

        EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
            player, eye, end, new AABB(eye, end).inflate(1.0D),
            e -> e != player && e.isPickable(), best * best);
        if (hit != null) {
            double d = eye.distanceTo(hit.getLocation());
            if (d < best) {
                best = d;
                label = displayName(hit.getEntity());
            }
        }

        focusTarget = label;
        return (float) Mth.clamp(best, CameraState.MIN_FOCUS_M, CameraState.MAX_FOCUS_M);
    }

    /**
     * AF-C: 画角の中に入っている一番近い列車を掴んで追い続ける。
     * 走ってくる列車を望遠で抜くときに、いちいちピントを送らなくて済むようにするための機能。
     */
    private float autofocusTrain(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return state.getFocusDistance();
        }
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);

        // 掴んでいる列車がまだ生きていて画角の中にいるなら、それを追い続ける
        if (trackedTrain != null) {
            if (!trackedTrain.isAlive() || !inFrame(eye, look, trackedTrain)) {
                trackedTrain = null;
            }
        }
        if (trackedTrain == null) {
            trackedTrain = findNearestTrain(mc, eye, look);
        }
        if (trackedTrain == null) {
            focusTarget = "追尾なし";
            // 列車がいなければ中央 AF にフォールバック (背景に合わせておく)
            return autofocusCenterQuiet(mc);
        }
        focusTarget = displayName(trackedTrain) + " ●";
        double d = eye.distanceTo(trackedTrain.position().add(0.0D, trackedTrain.getBbHeight() * 0.5D, 0.0D));
        return (float) Mth.clamp(d, CameraState.MIN_FOCUS_M, CameraState.MAX_FOCUS_M);
    }

    private float autofocusCenterQuiet(Minecraft mc) {
        String saved = focusTarget;
        float f = autofocusCenter(mc);
        focusTarget = saved;
        return f;
    }

    private Entity findNearestTrain(Minecraft mc, Vec3 eye, Vec3 look) {
        AABB box = new AABB(eye, eye).inflate(AF_C_RANGE);
        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : mc.level.getEntities((Entity) null, box, RtmCamera::isTrain)) {
            if (!inFrame(eye, look, e)) {
                continue;
            }
            double d = eye.distanceToSqr(e.position());
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    private static boolean isTrain(Entity e) {
        return e instanceof jp.ngt.rtm.entity.train.EntityTrainBase
            || e instanceof com.portofino.realtrainmodunofficial.entity.TrainEntity;
    }

    /** 画角 (ズームを考慮した半頂角) の中に入っているか。 */
    private boolean inFrame(Vec3 eye, Vec3 look, Entity e) {
        Vec3 to = e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D).subtract(eye);
        double len = to.length();
        if (len < 0.01D || len > AF_C_RANGE) {
            return false;
        }
        double cos = to.scale(1.0D / len).dot(look);
        // ズームするほど画角が狭くなる = 追尾対象も絞られる
        double halfFovRad = Math.toRadians(Math.max(2.0D, 70.0D / state.getZoomScale()) * 0.5D);
        // 少し余裕を持たせる (画角ぎりぎりで見失うのを防ぐ)
        return cos > Math.cos(halfFovRad * 1.6D);
    }

    private static String displayName(Entity e) {
        if (e instanceof jp.ngt.rtm.entity.train.EntityTrainBase train) {
            String name = train.getModelName();
            return name == null || name.isBlank() ? "列車" : name;
        }
        if (e instanceof com.portofino.realtrainmodunofficial.entity.TrainEntity train) {
            String name = train.getVehicleId();
            if (name != null && !name.isBlank()) {
                int i = name.lastIndexOf(':');
                return i >= 0 ? name.substring(i + 1) : name;
            }
            return "列車";
        }
        return e.getDisplayName().getString();
    }

    // ---- 撮影 ----

    /** 撮影予約。実際の取り込みは GUI を描く直前 (#captureIfPending) に行う。 */
    private boolean pendingShot;

    private void shoot(Minecraft mc) {
        if (pendingShot) {
            return;
        }
        pendingShot = true;
        mc.level.playSound(mc.player, mc.player.blockPosition(),
            SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 0.6F, 1.8F);
    }

    /**
     * 画面の取り込み。GUI を描く直前に呼ぶこと。
     * バニラの F2 は GUI ごと写るが、それだとファインダーのグリッドや設定表示が
     * 写真に焼き込まれてしまう。
     */
    public void captureIfPending(Minecraft mc) {
        if (!pendingShot) {
            return;
        }
        pendingShot = false;
        flash = 6;
        try {
            NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget());
            String name = LocalDateTime.now().format(FILE_TIME) + "_"
                + state.describe().replace('/', '-').replace(' ', '_') + ".png";
            Util.ioPool().execute(() -> {
                try {
                    Path dir = mc.gameDirectory.toPath().resolve("screenshots").resolve("rtm_camera");
                    Files.createDirectories(dir);
                    Path file = dir.resolve(name);
                    image.writeToFile(file);
                    mc.execute(() -> {
                        if (mc.gui != null) {
                            mc.gui.getChat().addMessage(
                                Component.literal("§a● 撮影 §7" + file.getFileName()));
                        }
                    });
                } catch (Exception e) {
                    RealTrainModUnofficial.LOGGER.warn("Camera screenshot failed", e);
                } finally {
                    image.close();
                }
            });
        } catch (Throwable t) {
            RealTrainModUnofficial.LOGGER.warn("Camera screenshot failed", t);
        }
    }
}
