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

    /** この車両が「乗車中で ActionParts を持つ」= ピッキング描画すべきか。 */
    public static boolean shouldCapture(Object entity, ActionPartsHost host) {
        if (entity == null || host == null) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || riddenVehicle() != entity) {
            return false;
        }
        List<Parts> targets = host.getTargetsList();
        if (targets == null) {
            return false;
        }
        for (Parts p : targets) {
            if (p instanceof ActionParts) {
                return true;
            }
        }
        return false;
    }

    /**
     * 色ピッキング FBO の結果 (パーツ ID) を反映する。
     * ID は ActionParts.id (1 始まり)。0 以下は「当たり無し」。
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
        ctx.host.setHoveredParts(entity, hit);
        hoveredEntity = entity;
        hoveredId = id;
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
        Object vehicle = riddenVehicle();
        if (vehicle == null) {
            return false;
        }
        Context ctx = CONTEXTS.get(vehicle);
        if (ctx == null) {
            return false;
        }
        ActionParts hit = currentHit(ctx);
        if (hit == null) {
            return false;
        }
        activeEntity = vehicle;
        activeParts = hit;
        dragStart = hit.behavior == ActionType.DRAG_Y ? mc.mouseHandler.ypos() : mc.mouseHandler.xpos();
        if (hit.behavior == ActionType.TOGGLE) {
            dragging = false;
            dispatch(ctx.host, "onRightClick", vehicle, hit);
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
        if (!rightDown || !dragging || riddenVehicle() != activeEntity) {
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
