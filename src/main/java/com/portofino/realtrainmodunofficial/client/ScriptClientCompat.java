package com.portofino.realtrainmodunofficial.client;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** 1.12.2 RTM スクリプト(SuperRailBuilder3 の render スクリプト… */
public final class ScriptClientCompat {

    /** 現在レンダリング中の partialTick。 */
    public static volatile float currentRenderPartialTick = 1.0F;

    /** エンティティのレンダー補間位置(PoseStack 原点と一致)。 */
    public double renderPosX(Object e) { return renderPos(e, 0); }
    public double renderPosY(Object e) { return renderPos(e, 1); }
    public double renderPosZ(Object e) { return renderPos(e, 2); }

    private double renderPos(Object e, int axis) {
        if (!(e instanceof net.minecraft.world.entity.Entity ent)) {
            return 0.0D;
        }
        try {
            float pt = currentRenderPartialTick;
            return switch (axis) {
                case 0 -> net.minecraft.util.Mth.lerp(pt, ent.xOld, ent.getX());
                case 1 -> net.minecraft.util.Mth.lerp(pt, ent.yOld, ent.getY());
                default -> net.minecraft.util.Mth.lerp(pt, ent.zOld, ent.getZ());
            };
        } catch (Throwable t) {
            return axis == 0 ? ent.getX() : (axis == 1 ? ent.getY() : ent.getZ());
        }
    }

    /** クライアントのローカルプレイヤー(なければ null)。 */
    public Object getPlayer() {
        try {
            return Minecraft.getInstance().player;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 現在開いている画面(GUI)。開いていなければ null。 */
    public Object getCurrentScreen() {
        try {
            return Minecraft.getInstance().screen;
        } catch (Throwable t) {
            return null;
        }
    }

    /** クライアントの世界(なければ null)。 */
    public Object getLevel() {
        try {
            return Minecraft.getInstance().level;
        } catch (Throwable t) {
            return null;
        }
    }

    /** プレイヤー視線のレイキャスト(SRB の BlockUtil.getMOPFromPlayer 相… */
    public RaycastResult raycast(double distance) {
        try {
            Minecraft mc = Minecraft.getInstance();
            net.minecraft.world.entity.player.Player p = mc.player;
            if (p == null || mc.level == null) {
                return null;
            }
            // 本家NGT(BlockUtil.getMOPFromPlayer)準拠: partialTick=…
            net.minecraft.world.phys.Vec3 eye = p.getEyePosition(1.0F);
            net.minecraft.world.phys.Vec3 look = p.getViewVector(1.0F);
            net.minecraft.world.phys.Vec3 end = eye.add(look.x * distance, look.y * distance, look.z * distance);
            net.minecraft.world.phys.BlockHitResult hit = mc.level.clip(new net.minecraft.world.level.ClipContext(
                eye, end, net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE, p));
            if (hit != null && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                net.minecraft.world.phys.Vec3 loc = hit.getLocation();
                net.minecraft.core.BlockPos bp = hit.getBlockPos();
                return new RaycastResult(true, loc.x, loc.y, loc.z, bp.getX(), bp.getY(), bp.getZ());
            }
            // ブロックに当たらなかった(空を見た等)場合は null。
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** レイキャスト結果ホルダー(JS から hitVec/BlockPos を取り出す)。 */
    public static final class RaycastResult {
        private final boolean hit;
        private final double hitX, hitY, hitZ;
        private final int blockX, blockY, blockZ;
        RaycastResult(boolean hit, double hitX, double hitY, double hitZ, int blockX, int blockY, int blockZ) {
            this.hit = hit; this.hitX = hitX; this.hitY = hitY; this.hitZ = hitZ;
            this.blockX = blockX; this.blockY = blockY; this.blockZ = blockZ;
        }
        public boolean isHit() { return hit; }
        public double getHitX() { return hitX; }
        public double getHitY() { return hitY; }
        public double getHitZ() { return hitZ; }
        public int getBlockX() { return blockX; }
        public int getBlockY() { return blockY; }
        public int getBlockZ() { return blockZ; }
    }

    /** LWJGL2 Mouse.isButtonDown 相当(0=左,1=右,2=中)。 */
    public boolean isMouseDown(int button) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getWindow() == null) {
                return false;
            }
            return GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), button) == GLFW.GLFW_PRESS;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 現在の言語コード(例 "en_us")。RTM の getLanguageManager 系チェー… */
    public String getLanguageCode() {
        try {
            String code = Minecraft.getInstance().getLanguageManager().getSelected();
            return code == null ? "en_us" : code.toLowerCase(java.util.Locale.ROOT);
        } catch (Throwable t) {
            return "en_us";
        }
    }
}
