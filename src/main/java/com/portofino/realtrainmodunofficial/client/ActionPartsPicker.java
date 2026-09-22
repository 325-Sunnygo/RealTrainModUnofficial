package com.portofino.realtrainmodunofficial.client;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import jp.ngt.ngtlib.io.ScriptUtil;
import jp.ngt.rtm.render.ActionParts;
import jp.ngt.rtm.render.ActionType;
import jp.ngt.rtm.render.Parts;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import javax.script.ScriptEngine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 本家 PartsRenderer の ActionParts 対話 (PICK パス + onRightClick / onRightDrag) の移植。
 *
 * <p>当たりパーツの判定は {@link com.portofino.realtrainmodunofficial.client.render.ActionPartsPickBuffer}
 * (色ピッキング FBO) が行う。本家は GL_SELECT を使っていたが core profile で削除されているため、
 * ActionParts を ID 色で描いて画面中央ピクセルを読む方式で再現している。
 */
@EventBusSubscriber(modid = RealTrainModUnofficial.MODID, value = Dist.CLIENT)
public final class ActionPartsPicker {

    private static final class Context {
        final ActionPartsHost host;
        final List<ActionParts> parts;

        Context(ActionPartsHost host, List<ActionParts> parts) {
            this.host = host;
            this.parts = parts;
        }
    }

    private static final Map<Object, Context> CONTEXTS =
        Collections.synchronizedMap(new WeakHashMap<>());

    private static Object hoveredEntity;
    private static int hoveredId = -1;

    private static Object activeEntity;
    private static ActionParts activeParts;
    private static boolean dragging;
    private static double dragStart;

    /** このフレームで最も手前の候補 (設置物は複数が同時に候補になるため距離で選ぶ)。 */
    private static Object bestEntity;
    private static int bestId = -1;
    private static double bestDistSq = Double.MAX_VALUE;

    /** 設置物 (エンティティ/ブロック) を候補にする距離。 */
    private static final double MAX_PICK_DISTANCE = 8.0D;

    /**
     * フレーム先頭で候補とホバーを捨てる。
     * 以降の描画で各オブジェクトが {@link #setHoveredId} を呼び、最も近いものが採用される。
     * クリックはフレームとフレームの間に届くので、直前フレームの確定結果が使われる。
     */
    @SubscribeEvent
    public static void onRenderFramePre(net.neoforged.neoforge.client.event.RenderFrameEvent.Pre event) {
        bestEntity = null;
        bestId = -1;
        bestDistSq = Double.MAX_VALUE;
        hoveredEntity = null;
        hoveredId = -1;
    }

    private ActionPartsPicker() {
    }

    /**
     * プレイヤーが実際に乗っている車両実体 (座席に乗っていればその列車)。
     */
    private static Object riddenVehicle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return null;
        }
        Object v = mc.player.getVehicle();
        if (v instanceof com.portofino.realtrainmodunofficial.entity.TrainSeatEntity seat) {
            return seat.getTrain();
        }
        return v;
    }

    /** 描画時に呼ぶ: ActionParts 一覧を記録する (ID 解決に使う)。 */
    public static void record(Object entity, Matrix4f ignoredLocalToCamera, ActionPartsHost host) {
        if (entity == null || host == null) {
            return;
        }
        List<Parts> targets = host.getTargetsList();
        if (targets == null || targets.isEmpty()) {
            CONTEXTS.remove(entity);
            return;
        }
        List<ActionParts> parts = new ArrayList<>(targets.size());
        for (Parts p : targets) {
            if (p instanceof ActionParts ap) {
                parts.add(ap);
            }
        }
        if (parts.isEmpty()) {
            CONTEXTS.remove(entity);
            return;
        }
        CONTEXTS.put(entity, new Context(host, parts));
    }

    /**
     * このオブジェクトの ActionParts を色ピッキングすべきか。
     *
     * <p>本家 {@code PartsRenderer} は「描画中のオブジェクト」すべてで ActionParts を拾う
     * (GL_SELECT で最も手前のパーツを選ぶ)。RTMU は色ピッキング FBO のコストがあるため、
     * 乗車中の車両は従来どおり、設置物 (ATC/列車検知器/車止め/踏切など) は
     * <b>近く前方にあるものだけ</b>を候補にする。
     */
    public static boolean shouldCapture(Object entity, ActionPartsHost host) {
        if (entity == null || host == null) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        List<Parts> targets = host.getTargetsList();
        if (targets == null) {
            return false;
        }
        boolean hasActionParts = false;
        for (Parts p : targets) {
            if (p instanceof ActionParts) {
                hasActionParts = true;
                break;
            }
        }
        if (!hasActionParts) {
            return false;
        }
        Object ridden = riddenVehicle();
        if (ridden == entity) {
            return true;
        }
        // 乗車中は運転台だけを対象にする (従来どおりの挙動を変えない)。
        if (ridden != null) {
            return false;
        }
        // ★設置物: 近く前方のものだけ候補にする。
        if (entity instanceof jp.ngt.rtm.entity.EntityInstalledObject
                || entity instanceof com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity) {
            return isNearAndInFront(mc, entity);
        }
        return false;
    }

    /** 記録済みの host を使う版 (設置物ブロックの描画キャッシュ経路から呼ぶ)。 */
    public static boolean shouldCapture(Object entity) {
        Context ctx = CONTEXTS.get(entity);
        return ctx != null && shouldCapture(entity, ctx.host);
    }

    /** 記録済みの host を返す (色ピッキングの開始に使う)。 */
    public static ActionPartsHost hostOf(Object entity) {
        Context ctx = CONTEXTS.get(entity);
        return ctx == null ? null : ctx.host;
    }

    /** プレイヤーの近く前方 (約 60 度以内) にあるか。 */
    private static boolean isNearAndInFront(Minecraft mc, Object entity) {
        net.minecraft.world.phys.Vec3 target = centerOf(entity);
        if (target == null || mc.player == null) {
            return false;
        }
        net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition();
        net.minecraft.world.phys.Vec3 to = target.subtract(eye);
        double dist = to.length();
        if (dist > MAX_PICK_DISTANCE) {
            return false;
        }
        if (dist < 1.0E-4D) {
            return true;
        }
        return to.scale(1.0D / dist).dot(mc.player.getViewVector(1.0F)) > 0.5D;
    }

    /** エンティティ/ブロックの中心座標 (無ければ null)。 */
    private static net.minecraft.world.phys.Vec3 centerOf(Object entity) {
        if (entity instanceof net.minecraft.world.entity.Entity e) {
            return new net.minecraft.world.phys.Vec3(
                e.getX(), e.getY() + e.getBbHeight() * 0.5D, e.getZ());
        }
        if (entity instanceof net.minecraft.world.level.block.entity.BlockEntity be) {
            net.minecraft.core.BlockPos p = be.getBlockPos();
            return new net.minecraft.world.phys.Vec3(p.getX() + 0.5D, p.getY() + 0.5D, p.getZ() + 0.5D);
        }
        return null;
    }

    /** プレイヤーの目から候補中心までの距離の二乗。 */
    private static double candidateDistanceSq(Object entity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return Double.MAX_VALUE;
        }
        net.minecraft.world.phys.Vec3 target = centerOf(entity);
        if (target == null) {
            return Double.MAX_VALUE;
        }
        return mc.player.getEyePosition().distanceToSqr(target);
    }

    /**
     * 色ピッキング FBO の結果 (パーツ ID) を反映する。
     * ID は ActionParts.id (1 始まり)。0 以下は「当たり無し」。
     *
     * <p>設置物は複数が同時に候補になるので、フレーム内で<b>最も近いもの</b>を採用する
     * (本家 GL_SELECT の「最も手前のパーツを選ぶ」に相当)。
     */
    public static void setHoveredId(Object entity, int id) {
        Context ctx = CONTEXTS.get(entity);
        if (ctx == null) {
            return;
        }
        ActionParts hit = null;
        if (id > 0) {
            for (ActionParts ap : ctx.parts) {
                if (ap.id == id) {
                    hit = ap;
                    break;
                }
            }
        }
        if (hit == null) {
            // 当たり無し: これが現在の最有力なら取り下げる。
            if (bestEntity == entity) {
                bestEntity = null;
                bestId = -1;
                bestDistSq = Double.MAX_VALUE;
                ctx.host.setHoveredParts(entity, null);
                hoveredEntity = null;
                hoveredId = -1;
            }
            return;
        }
        double distSq = candidateDistanceSq(entity);
        if (distSq > bestDistSq) {
            // 既にもっと手前の候補が確定している。
            ctx.host.setHoveredParts(entity, null);
            return;
        }
        if (bestEntity != null && bestEntity != entity) {
            Context prev = CONTEXTS.get(bestEntity);
            if (prev != null) {
                prev.host.setHoveredParts(bestEntity, null);
            }
        }
        bestEntity = entity;
        bestId = id;
        bestDistSq = distSq;
        hoveredEntity = entity;
        hoveredId = id;
        ctx.host.setHoveredParts(entity, hit);
    }

    /** 直近の色ピッキングで当たった ActionParts (無ければ null)。 */
    private static ActionParts currentHit(Context ctx) {
        if (hoveredEntity == null || hoveredId <= 0) {
            return null;
        }
        for (ActionParts ap : ctx.parts) {
            if (ap.id == hoveredId) {
                return ap;
            }
        }
        return null;
    }

    /** 右クリック時。当たったパーツへ onRightClick を送り、消費したら true。 */
    public static boolean onRightClick(Minecraft mc) {
        if (mc.player == null) {
            return false;
        }
        // 乗車中は運転台、非乗車時は設置物。どちらも「直前フレームで当たったパーツ」を使う。
        Object target = hoveredEntity;
        if (target == null) {
            return false;
        }
        Context ctx = CONTEXTS.get(target);
        if (ctx == null) {
            return false;
        }
        ActionParts hit = currentHit(ctx);
        if (hit == null) {
            return false;
        }
        activeEntity = target;
        activeParts = hit;
        dragStart = hit.behavior == ActionType.DRAG_Y ? mc.mouseHandler.ypos() : mc.mouseHandler.xpos();
        if (hit.behavior == ActionType.TOGGLE) {
            dragging = false;
            dispatch(ctx.host, "onRightClick", target, hit);
        } else {
            // DRAG_X / DRAG_Y: 本家は押下時に onRightClick を呼ばない (onRightDrag のみ)。
            dragging = true;
        }
        return true;
    }

    /** 毎クライアント tick: 押下中のドラッグを onRightDrag として送る。 */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (activeParts == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            clear();
            return;
        }
        long window = mc.getWindow().getWindow();
        boolean rightDown =
            GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        if (!rightDown || !dragging || activeEntity == null || CONTEXTS.get(activeEntity) == null) {
            clear();
            return;
        }
        double x = mc.mouseHandler.xpos();
        double y = mc.mouseHandler.ypos();
        int move = activeParts.behavior == ActionType.DRAG_Y ? (int) (y - dragStart) : (int) (x - dragStart);
        if (move == 0) {
            return;
        }
        Context ctx = CONTEXTS.get(activeEntity);
        if (ctx == null) {
            clear();
            return;
        }
        dispatch(ctx.host, "onRightDrag", activeEntity, activeParts, move);
    }

    private static void clear() {
        activeEntity = null;
        activeParts = null;
        dragging = false;
    }

    private static void dispatch(ActionPartsHost host, String func, Object... args) {
        ScriptEngine se = host.getScript();
        if (se == null) {
            return;
        }
        try {
            ScriptUtil.doScriptFunction(se, func, args);
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            if (!(cause instanceof NoSuchMethodException)) {
                RealTrainModUnofficial.LOGGER.debug("[RTMU] ActionParts {} failed: {}", func, cause.toString());
            }
        }
    }
}
