package com.portofino.realtrainmodunofficial.client.camera;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * カメラモードのフック一式。
 * 焦点距離に応じた FOV (望遠)
 * 手とバニラ HUD を隠す (ファインダーに余計なものを出さない)
 * ワールド描画後・GUI 描画前に ボケ / 流し撮り を掛け、そこで撮影する
 * ファインダー (グリッド / アスペクトガイド / 水平器 / 設定表示) を描く
 */
@EventBusSubscriber(modid = RealTrainModUnofficial.MODID, value = Dist.CLIENT)
public final class CameraClientEvents {

    private static final int COL_TEXT = 0xFFEEEEEE;
    private static final int COL_ACCENT = 0xFF7CFF7C;
    private static final int COL_LINE = 0x60FFFFFF;
    private static final int COL_GUIDE = 0xB0000000;
    private static final int COL_FOCUS = 0xFF7CFF7C;

    private CameraClientEvents() {
    }

    // ---- 入力 ----

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        // 方向幕/種別幕のアニメーション GIF のフレーム送り (毎 tick)
        com.portofino.realtrainmodunofficial.client.model.GifTextures.tick();
        // カメラを持っていない / 死んだ / ワールドを出た → 自動で閉じる
        RtmCamera cam = RtmCamera.INSTANCE;
        if (cam.isActive() && (mc.level == null || mc.player == null || !holdingCamera(mc))) {
            cam.close();
        }
        cam.tick(mc);
    }

    private static boolean holdingCamera(Minecraft mc) {
        return mc.player != null && (
            mc.player.getMainHandItem().getItem() instanceof com.portofino.realtrainmodunofficial.item.CameraItem
                || mc.player.getOffhandItem().getItem() instanceof com.portofino.realtrainmodunofficial.item.CameraItem);
    }

    /** ホイールでズーム。実機のズームリングのつもり。 */
    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        RtmCamera cam = RtmCamera.INSTANCE;
        if (!cam.isActive() || Minecraft.getInstance().screen != null) {
            return;
        }
        double dy = event.getScrollDeltaY();
        if (dy != 0.0D) {
            cam.state().zoom((float) dy * 3.0F);
            event.setCanceled(true);
        }
    }

    // ---- 画角 ----

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        RtmCamera cam = RtmCamera.INSTANCE;
        if (cam.isActive()) {
            event.setFOV(cam.computeFov(event.getFOV()));
        }
    }

    // ---- 隠す ----

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (RtmCamera.INSTANCE.isActive()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        // ファインダー中はバニラ HUD (ホットバー/体力/十字/エフェクト) を全部消す。
        // チャットだけは残す (撮影完了メッセージを出すため)。
        if (!RtmCamera.INSTANCE.isActive()) {
            return;
        }
        if (net.neoforged.neoforge.client.gui.VanillaGuiLayers.CHAT.equals(event.getName())) {
            return;
        }
        event.setCanceled(true);
    }

    // ---- ポストエフェクト + 撮影 ----

    /**
     * GUI を描く直前。この時点でメインターゲットにはワールドだけが入っている。
     * ここでボケと流し撮りを掛け、そのうえで撮影する (ファインダーの線が写真に写らない)。
     */
    @SubscribeEvent
    public static void onRenderGuiPre(RenderGuiEvent.Pre event) {
        RtmCamera cam = RtmCamera.INSTANCE;
        if (!cam.isActive()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        CameraPostProcessor.process(mc, cam.state(), cam.getCurrentFocus());
        cam.captureIfPending(mc);
    }

    // ---- ファインダー ----

    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        RtmCamera cam = RtmCamera.INSTANCE;
        if (!cam.isActive()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth();
        int h = g.guiHeight();

        // アスペクトガイド (その外側を黒く落とす) を先に出し、その内側にグリッドを引く
        int[] frame = {0, 0, w, h};
        CameraState.AspectGuide guide = cam.state().getAspectGuide();
        if (guide != CameraState.AspectGuide.OFF && guide.ratio > 0.0F) {
            frame = aspectFrame(w, h, guide.ratio);
            drawLetterbox(g, w, h, frame);
        }

        drawGrid(g, cam.state().getGrid(), frame);
        drawFocusMark(g, frame, cam);
        if (cam.state().isLevelShown()) {
            drawLevel(g, mc, frame);
        }
        drawInfo(g, mc.font, cam, frame);

        // 撮影フラッシュ
        float flash = cam.getFlash();
        if (flash > 0.0F) {
            int a = (int) (flash * 200.0F) << 24;
            g.fill(0, 0, w, h, a | 0x00FFFFFF);
        }
    }

    /** 指定アスペクト比で内接する矩形 {x0, y0, x1, y1} */
    private static int[] aspectFrame(int w, int h, float ratio) {
        float current = (float) w / (float) h;
        if (current > ratio) {
            int fw = Math.round(h * ratio);
            int x0 = (w - fw) / 2;
            return new int[]{x0, 0, x0 + fw, h};
        }
        int fh = Math.round(w / ratio);
        int y0 = (h - fh) / 2;
        return new int[]{0, y0, w, y0 + fh};
    }

    private static void drawLetterbox(GuiGraphics g, int w, int h, int[] f) {
        if (f[0] > 0) {
            g.fill(0, 0, f[0], h, COL_GUIDE);
            g.fill(f[2], 0, w, h, COL_GUIDE);
        }
        if (f[1] > 0) {
            g.fill(0, 0, w, f[1], COL_GUIDE);
            g.fill(0, f[3], w, h, COL_GUIDE);
        }
    }

    private static void drawGrid(GuiGraphics g, CameraState.Grid grid, int[] f) {
        int x0 = f[0];
        int y0 = f[1];
        int x1 = f[2];
        int y1 = f[3];
        int fw = x1 - x0;
        int fh = y1 - y0;
        switch (grid) {
            case OFF -> {
            }
            case THIRDS -> {
                for (int i = 1; i <= 2; i++) {
                    int x = x0 + fw * i / 3;
                    int y = y0 + fh * i / 3;
                    g.fill(x, y0, x + 1, y1, COL_LINE);
                    g.fill(x0, y, x1, y + 1, COL_LINE);
                }
            }
            case SQUARE -> {
                for (int i = 1; i <= 5; i++) {
                    int x = x0 + fw * i / 6;
                    g.fill(x, y0, x + 1, y1, COL_LINE);
                }
                for (int i = 1; i <= 3; i++) {
                    int y = y0 + fh * i / 4;
                    g.fill(x0, y, x1, y + 1, COL_LINE);
                }
            }
            case GOLDEN -> {
                // 対角線 (被写体を対角に乗せる構図)
                drawLine(g, x0, y0, x1, y1);
                drawLine(g, x1, y0, x0, y1);
            }
        }
    }

    /** GuiGraphics に斜線は無いので、細かい矩形で近似する。 */
    private static void drawLine(GuiGraphics g, int ax, int ay, int bx, int by) {
        int steps = Math.max(Math.abs(bx - ax), Math.abs(by - ay));
        if (steps <= 0) {
            return;
        }
        for (int i = 0; i <= steps; i += 2) {
            int x = ax + (bx - ax) * i / steps;
            int y = ay + (by - ay) * i / steps;
            g.fill(x, y, x + 1, y + 1, COL_LINE);
        }
    }

    /** 中央の AF フレーム。AF-C で列車を掴んでいるときは緑にする。 */
    private static void drawFocusMark(GuiGraphics g, int[] f, RtmCamera cam) {
        int cx = (f[0] + f[2]) / 2;
        int cy = (f[1] + f[3]) / 2;
        int r = 9;
        boolean locked = cam.state().getFocusMode() == CameraState.FocusMode.AF_C
            && cam.getFocusTarget().endsWith("●");
        int col = locked ? COL_FOCUS : 0xC0FFFFFF;
        // 四隅のカギ括弧
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                int px = cx + sx * r;
                int py = cy + sy * r;
                g.fill(px - (sx > 0 ? 4 : 0), py, px + (sx > 0 ? 0 : 4), py + 1, col);
                g.fill(px, py - (sy > 0 ? 4 : 0), px + 1, py + (sy > 0 ? 0 : 4), col);
            }
        }
    }

    /** 水平器。カメラの roll(=ピッチのねじれ) は無いので、ピッチのズレを出す。 */
    private static void drawLevel(GuiGraphics g, Minecraft mc, int[] f) {
        if (mc.player == null) {
            return;
        }
        int cx = (f[0] + f[2]) / 2;
        int cy = (f[1] + f[3]) / 2;
        float pitch = mc.player.getXRot();
        // 水平からのズレを ±30度で ±60px にマップ
        int off = (int) (Mth.clamp(pitch, -30.0F, 30.0F) * 2.0F);
        boolean levelOk = Math.abs(pitch) < 0.75F;
        int col = levelOk ? COL_FOCUS : 0x90FFFFFF;
        // 目盛り (真の水平)
        g.fill(cx - 70, cy - 1, cx - 30, cy + 1, 0x50FFFFFF);
        g.fill(cx + 30, cy - 1, cx + 70, cy + 1, 0x50FFFFFF);
        // 現在の傾き
        g.fill(cx - 70, cy + off - 1, cx - 30, cy + off + 1, col);
        g.fill(cx + 30, cy + off - 1, cx + 70, cy + off + 1, col);
    }

    private static void drawInfo(GuiGraphics g, Font font, RtmCamera cam, int[] f) {
        CameraState s = cam.state();
        int x = f[0] + 8;
        int y = f[1] + 8;
        int line = 11;

        // 焦点距離 / F値 / シャッター (実機の表示に寄せる)
        String main = String.format("§f%dmm  §fF%s  §f1/%d",
            Math.round(s.getFocalMm()), fmtF(s.getFStop()), s.getShutterDenominator());
        g.drawString(font, main, x, y, COL_TEXT, true);
        y += line + 2;

        // 2 行目: ピント
        String focus = String.format("§7%s §f%.1fm", s.getFocusMode().label, s.getFocusDistance());
        if (!cam.getFocusTarget().isEmpty()) {
            focus += " §a" + cam.getFocusTarget();
        }
        g.drawString(font, focus, x, y, COL_ACCENT, true);
        y += line;

        // 3 行目: 流し撮りが効いているときだけ出す
        if (s.getMotionBlend() > 0.01F) {
            g.drawString(font, "§e流し撮り", x, y, COL_TEXT, true);
            y += line;
        }
        if (com.portofino.realtrainmodunofficial.client.ShaderCompat.isShaderPackInUse()) {
            g.drawString(font, "§cシェーダーパック使用中: ボケ/流し撮りは無効", x, y, COL_TEXT, true);
            y += line;
        }

        // 操作ヒント (右下、控えめに)
        String[] hints = {
            "Z/X ズーム (ホイールも)",
            "F/G 絞り",
            "C/V シャッター",
            "M フォーカスモード" + (s.getFocusMode() == CameraState.FocusMode.MF ? " / B,N ピント送り" : ""),
            "レンズ交換: レンズを持って右クリック",
            "H グリッド  J アスペクト  K 水平器",
            "Enter 撮影  右クリック 終了",
        };
        int hy = f[3] - 8 - hints.length * line;
        for (String hint : hints) {
            int hw = font.width(hint);
            g.drawString(font, hint, f[2] - 8 - hw, hy, 0xFF9A9A9A, true);
            hy += line;
        }
    }

    private static String fmtF(float v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(v);
    }
}
