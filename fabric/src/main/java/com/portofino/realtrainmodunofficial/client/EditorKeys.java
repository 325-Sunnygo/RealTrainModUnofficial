package com.portofino.realtrainmodunofficial.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.item.EditorItem;
import com.portofino.realtrainmodunofficial.network.EditorPointPayload;
import com.portofino.realtrainmodunofficial.network.RunFilterPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * エディタのキー操作 (neo mcte)。本家 MCTE MCTEKeyHandlerClient の移植。
 * 既定キーは本家と同じにしてある (本家は LWJGL2 のコード指定):
 */
@EventBusSubscriber(modid = RealTrainModUnofficial.MODID, value = Dist.CLIENT)
public final class EditorKeys {

    private static final String CATEGORY = "key.categories.realtrainmodunofficial.mcte";

    public static final KeyMapping MENU = key("menu", GLFW.GLFW_KEY_K);
    public static final KeyMapping MODE = key("mode", GLFW.GLFW_KEY_M);
    public static final KeyMapping DELETE = key("delete", GLFW.GLFW_KEY_DELETE);
    public static final KeyMapping UNDO = key("undo", GLFW.GLFW_KEY_Z);
    public static final KeyMapping CUT = key("cut", GLFW.GLFW_KEY_X);
    public static final KeyMapping COPY = key("copy", GLFW.GLFW_KEY_C);
    public static final KeyMapping PASTE = key("paste", GLFW.GLFW_KEY_V);
    public static final KeyMapping FILL = key("fill", GLFW.GLFW_KEY_B);
    public static final KeyMapping CLEAR = key("clear", GLFW.GLFW_KEY_N);
    /** やり直し。本家に無い追加なので Z の隣に置く。 */
    public static final KeyMapping REDO = key("redo", GLFW.GLFW_KEY_Y);
    /** 1 つ前の選択範囲へ戻す (neo mcte 追加)。 */
    public static final KeyMapping BACK = key("back", GLFW.GLFW_KEY_H);

    /** 視点追従で動かす点。0 = 始点、1 = 終点。本家の editMode 0/1 に対応。 */
    private static int followPoint = -1;
    private static BlockPos lastSent;

    private EditorKeys() {
    }

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.realtrainmodunofficial.mcte." + name,
            InputConstants.Type.KEYSYM, code, CATEGORY);
    }

    @EventBusSubscriber(modid = RealTrainModUnofficial.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(MENU);
            event.register(MODE);
            event.register(DELETE);
            event.register(UNDO);
            event.register(CUT);
            event.register(COPY);
            event.register(PASTE);
            event.register(FILL);
            event.register(CLEAR);
            event.register(REDO);
            event.register(BACK);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null || mc.screen != null) {
            return;
        }
        // エディタを持っているときだけ効かせる (本家と同じ。他の操作を邪魔しない)
        if (!(player.getMainHandItem().getItem() instanceof EditorItem)) {
            followPoint = -1;
            return;
        }

        while (MENU.consumeClick()) {
            // エディタが無ければサーバ側で作らせてから開く
            PacketDistributor.sendToServer(new RunFilterPayload(RunFilterPayload.OPEN, ""));
            com.portofino.realtrainmodunofficial.ClientHooks.openEditorScreen();
        }
        while (MODE.consumeClick()) {
            // -1 (追従なし) → 0 (始点) → 1 (終点) → -1 …
            followPoint = followPoint >= 1 ? -1 : followPoint + 1;
            lastSent = null;
            mc.gui.setOverlayMessage(net.minecraft.network.chat.Component.translatable(
                followPoint < 0 ? "msg.realtrainmodunofficial.editor.mode_off"
                    : followPoint == 0 ? "msg.realtrainmodunofficial.editor.mode_start"
                    : "msg.realtrainmodunofficial.editor.mode_end"), false);
        }
        while (BACK.consumeClick()) {
            if (com.portofino.realtrainmodunofficial.client.ClientSelection.back()) {
                PacketDistributor.sendToServer(new EditorPointPayload(0,
                    com.portofino.realtrainmodunofficial.client.ClientSelection.pos1()));
                PacketDistributor.sendToServer(new EditorPointPayload(1,
                    com.portofino.realtrainmodunofficial.client.ClientSelection.pos2()));
                mc.gui.setOverlayMessage(net.minecraft.network.chat.Component.translatable(
                    "msg.realtrainmodunofficial.editor.back"), false);
            }
        }
        while (REDO.consumeClick()) {
            PacketDistributor.sendToServer(new RunFilterPayload(RunFilterPayload.REDO, ""));
        }
        while (UNDO.consumeClick()) {
            PacketDistributor.sendToServer(new RunFilterPayload(RunFilterPayload.UNDO, ""));
        }
        while (DELETE.consumeClick()) {
            PacketDistributor.sendToServer(new RunFilterPayload("Delete", ""));
        }
        while (CUT.consumeClick()) {
            PacketDistributor.sendToServer(new RunFilterPayload("Cut", ""));
        }
        while (COPY.consumeClick()) {
            PacketDistributor.sendToServer(new RunFilterPayload("Copy", ""));
        }
        while (PASTE.consumeClick()) {
            PacketDistributor.sendToServer(new RunFilterPayload("Paste", ""));
        }
        while (FILL.consumeClick()) {
            PacketDistributor.sendToServer(new RunFilterPayload("Fill", ""));
        }
        while (CLEAR.consumeClick()) {
            // ★実際に解除する。以前はここで追従を切るだけで、キーが効かないように見えていた。
            followPoint = -1;
            lastSent = null;
            PacketDistributor.sendToServer(new RunFilterPayload(RunFilterPayload.CLEAR, ""));
            com.portofino.realtrainmodunofficial.client.ClientSelection.clear();
            com.portofino.realtrainmodunofficial.client.render.SelectionRenderer.forget();
        }

        // 視点追従
        if (followPoint < 0) {
            return;
        }
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        if (pos.equals(lastSent)) {
            return;
        }
        lastSent = pos;
        if (followPoint == 0) {
            com.portofino.realtrainmodunofficial.client.ClientSelection.setStartKeepEnd(pos);
        } else {
            com.portofino.realtrainmodunofficial.client.ClientSelection.setEnd(pos);
        }
        PacketDistributor.sendToServer(new EditorPointPayload(followPoint, pos));
    }

    /**
     * スニーク + ホイールで、見ている面を 1 ブロック伸縮する。
     * 本家 MCTE の EditorTransform 相当。座標欄を打ち直さずに範囲を詰められる。
     */
    @SubscribeEvent
    public static void onScroll(net.neoforged.neoforge.client.event.InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null || mc.screen != null || !player.isShiftKeyDown()) {
            return;
        }
        if (!(player.getMainHandItem().getItem() instanceof EditorItem)) {
            return;
        }
        if (!com.portofino.realtrainmodunofficial.client.ClientSelection.hasEnd()) {
            return;
        }
        net.minecraft.world.phys.AABB selBox = com.portofino.realtrainmodunofficial.client.ClientSelection.box();
        int delta = (int) Math.signum(event.getScrollDeltaY());
        if (delta == 0) {
            return;
        }

        // 視線を選択範囲の箱に当てて、どの面を見ているかを決める
        net.minecraft.world.phys.Vec3 eye = player.getEyePosition(1.0F);
        net.minecraft.world.phys.Vec3 to = eye.add(player.getViewVector(1.0F).scale(128.0D));
        var hit = selBox.clip(eye, to);
        if (hit.isEmpty()) {
            return;
        }
        net.minecraft.core.Direction face = faceOf(selBox, hit.get());
        if (face == null) {
            return;
        }

        // その面を持っている側の点だけを動かす
        BlockPos start = com.portofino.realtrainmodunofficial.client.ClientSelection.pos1();
        BlockPos endPos = com.portofino.realtrainmodunofficial.client.ClientSelection.pos2();
        int axis = face.getAxis().ordinal();
        boolean positive = face.getAxisDirection() == net.minecraft.core.Direction.AxisDirection.POSITIVE;
        boolean startIsFar = component(start, axis) > component(endPos, axis);
        boolean moveStart = positive == startIsFar;
        BlockPos target = moveStart ? start : endPos;
        BlockPos moved = target.relative(face, delta);
        if (moveStart) {
            com.portofino.realtrainmodunofficial.client.ClientSelection.setStartKeepEnd(moved);
        } else {
            com.portofino.realtrainmodunofficial.client.ClientSelection.setEnd(moved);
        }
        PacketDistributor.sendToServer(new EditorPointPayload(moveStart ? 0 : 1, moved));
        event.setCanceled(true);
    }

    private static int component(BlockPos p, int axis) {
        return axis == 0 ? p.getX() : axis == 1 ? p.getY() : p.getZ();
    }

    /** 当たった点がどの面か。 */
    private static net.minecraft.core.Direction faceOf(net.minecraft.world.phys.AABB box,
                                                       net.minecraft.world.phys.Vec3 hit) {
        double eps = 1.0e-3D;
        if (Math.abs(hit.x - box.minX) < eps) return net.minecraft.core.Direction.WEST;
        if (Math.abs(hit.x - box.maxX) < eps) return net.minecraft.core.Direction.EAST;
        if (Math.abs(hit.y - box.minY) < eps) return net.minecraft.core.Direction.DOWN;
        if (Math.abs(hit.y - box.maxY) < eps) return net.minecraft.core.Direction.UP;
        if (Math.abs(hit.z - box.minZ) < eps) return net.minecraft.core.Direction.NORTH;
        if (Math.abs(hit.z - box.maxZ) < eps) return net.minecraft.core.Direction.SOUTH;
        return null;
    }
}
