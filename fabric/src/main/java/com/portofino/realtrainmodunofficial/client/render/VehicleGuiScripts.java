package com.portofino.realtrainmodunofficial.client.render;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.client.model.MqoModelLoader;
import com.portofino.realtrainmodunofficial.script.PackScriptSource;
import com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition;
import com.portofino.realtrainmodunofficial.vehicle.VehiclePackLoader;
import jp.ngt.ngtlib.io.ScriptUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import javax.script.ScriptEngine;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本家 ModelConfig.guiScriptPath (KaizPatchX ModelSetBase.guiSE) の移植。
 * 車両パックの運転台 GUI をスクリプトで描く。毎フレーム scripts/gui/*.js の
 * renderGui(entity, gui) を呼ぶ。guiScriptPath が無い車両は既定の cab を描く。
 */
public final class VehicleGuiScripts {

    private static final Map<String, ScriptEngine> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();

    private VehicleGuiScripts() {
    }

    /** guiScriptPath の engine。未設定/失敗なら null (= 既定 cab を描く)。 */
    public static ScriptEngine get(VehicleDefinition def) {
        if (def == null || !def.hasGuiScript()) {
            return null;
        }
        if (FAILED.contains(def.getId())) {
            return null;
        }
        return CACHE.computeIfAbsent(def.getId(), id -> create(def));
    }

    private static ScriptEngine create(VehicleDefinition def) {
        try {
            String source = VehiclePackLoader.readScriptContent(def, def.getGuiScriptPath());
            if (source == null || source.isBlank()) {
                RealTrainModUnofficial.LOGGER.warn("Vehicle gui script not readable for {} ({})",
                        def.getId(), def.getGuiScriptPath());
                FAILED.add(def.getId());
                return null;
            }
            source = PackScriptSource.prepare(source, def.getGuiScriptPath());
            // 本家と同じく Nashorn。プリリュードは 1.7.10 クラス名ブリッジ用。
            ScriptEngine se = ScriptUtil.doScript(PackScriptSource.PRELUDE + source);
            RealTrainModUnofficial.LOGGER.info("[RTMU] vehicle gui script init: {} ({})",
                    def.getId(), def.getGuiScriptPath());
            return se;
        } catch (Throwable t) {
            RealTrainModUnofficial.LOGGER.warn("Failed to init vehicle gui script for {}", def.getId(), t);
            FAILED.add(def.getId());
            return null;
        }
    }

    /** 診断コマンド用: 読み込みキャッシュを捨てる。 */
    public static void forget(String id) {
        if (id != null) {
            CACHE.remove(id);
            FAILED.remove(id);
        }
    }

    /**
     * スクリプトへ渡す gui オブジェクト。本家では GuiIngameCustom (GuiScreen) が
     * そのまま渡されていたため、スクリプトが使う描画 API をここで提供する。
     * 実描画は GuiGraphics 経由。
     */
    public static final class Gui {
        private final GuiGraphics graphics;
        private final Font font;
        /** スクリプトが gui.width / gui.height を直接読むことがある。 */
        public final int width;
        public final int height;
        /** 本家 Gui.zLevel。 */
        public final float zLevel = 0.0F;
        /** 本家 GuiScreen.mc (1.21 の Minecraft)。 */
        public final Object mc = net.minecraft.client.Minecraft.getInstance();
        /** 本家 GuiScreen.fontRendererObj。 */
        public final FontRendererCompat fontRendererObj;
        private ResourceLocation texture;

        public Gui(GuiGraphics graphics, Font font, int width, int height, ResourceLocation texture) {
            this.graphics = graphics;
            this.font = font;
            this.width = width;
            this.height = height;
            this.texture = texture;
            this.fontRendererObj = new FontRendererCompat(graphics, font);
        }

        public int getWidth() {
            return this.width;
        }

        public int getHeight() {
            return this.height;
        }

        /** 本家 GuiScreen.bindTexture / 1.7.10 TextureManager。 */
        public void bindTexture(ResourceLocation rl) {
            if (rl != null) {
                this.texture = rl;
            }
        }

        /**
         * 本家 (1.12 系) GuiScreen.drawRectangle(x, y, u, v, w, h, pxl)。
         * pxl = テクスチャ実寸 (通常 512)。現在 bind 中のテクスチャを切り出して描く。
         */
        public void drawRectangle(int x, int y, int u, int v, int w, int h, int pxl) {
            if (this.texture == null || w <= 0 || h <= 0) {
                return;
            }
            int tex = pxl > 0 ? pxl : 512;
            this.graphics.blit(this.texture, x, y, (float) u, (float) v, w, h, tex, tex);
        }

        /** 1.7.10 drawTexturedModalRect。テクスチャ実寸は 256 想定。 */
        public void drawTexturedModalRect(int x, int y, int u, int v, int w, int h) {
            this.drawRectangle(x, y, u, v, w, h, 256);
        }

        public void drawString(String s, int x, int y, int color) {
            this.graphics.drawString(this.font, s == null ? "" : s, x, y, color, true);
        }

        public void drawCenteredString(String s, int x, int y, int color) {
            this.graphics.drawCenteredString(this.font, s == null ? "" : s, x, y, color);
        }

        /** 本家 GuiScreen.drawRect。 */
        public void drawRect(int x1, int y1, int x2, int y2, int color) {
            this.graphics.fill(x1, y1, x2, y2, color);
        }

        public int getStringWidth(String s) {
            return this.font.width(s == null ? "" : s);
        }

        public Font getFontRenderer() {
            return this.font;
        }

        // ---- 本家 GuiScreen/Gui の主要メソッド (gui スクリプト互換) ----

        /** 本家 GuiScreen.drawHorizontalLine。 */
        public void drawHorizontalLine(int x1, int x2, int y, int color) {
            this.graphics.hLine(Math.min(x1, x2), Math.max(x1, x2), y, color);
        }

        /** 本家 GuiScreen.drawVerticalLine。 */
        public void drawVerticalLine(int x, int y1, int y2, int color) {
            this.graphics.vLine(x, Math.min(y1, y2), Math.max(y1, y2), color);
        }

        /** 本家 GuiScreen.drawGradientRect。 */
        public void drawGradientRect(int x1, int y1, int x2, int y2, int color1, int color2) {
            this.graphics.fillGradient(x1, y1, x2, y2, color1, color2);
        }

        /** 本家 Gui.drawModalRectWithCustomSizedTexture。 */
        public void drawModalRectWithCustomSizedTexture(int x, int y, float u, float v,
                                                        int w, int h, float tw, float th) {
            if (this.texture == null || w <= 0 || h <= 0) {
                return;
            }
            this.graphics.blit(this.texture, x, y, u, v, w, h, (int) tw, (int) th);
        }

        /** 本家 GuiScreen.drawTexturedModalRect(float,float,int,int,int,int)。 */
        public void drawTexturedModalRect(float x, float y, int u, int v, int w, int h) {
            this.drawTexturedModalRect((int) x, (int) y, u, v, w, h);
        }

        /** 本家 GuiScreen.mc 相当 (限定的)。 */
        public Object getMinecraft() {
            return net.minecraft.client.Minecraft.getInstance();
        }

        /** 本家 GuiScreen.fontRendererObj 相当の別名。 */
        public Font getFontRendererObj() {
            return this.font;
        }

        /** パックテクスチャを動的登録して bind する (スクリプトがパスで指定する用)。 */
        public void bindTexture(String path) {
            ResourceLocation rl = jp.ngt.ngtlib.io.NGTFileLoader.resolvePackTexture(path);
            if (rl != null) {
                this.texture = rl;
            }
        }

        // ---- 1.7.10 GuiScreen/Gui の MCP 名を再現するブリッジ (面を揃える) ----

        /** 本家 Gui.drawTexturedModalRect(int,int,int,int,int,int,float zLevel)。 */
        public void drawTexturedModalRect(int x, int y, int u, int v, int w, int h, float zLevel) {
            this.drawTexturedModalRect(x, y, u, v, w, h);
        }

        /** 本家 Gui.drawTexturedModalRect(float,float,int,int,int,int,float)。 */
        public void drawTexturedModalRect(float x, float y, int u, int v, int w, int h, float zLevel) {
            this.drawTexturedModalRect((int) x, (int) y, u, v, w, h);
        }

        /** 本家 Gui.drawModalRectWithCustomSizedTexture (float 版)。 */
        public void drawModalRectWithCustomSizedTexture(float x, float y, float u, float v,
                                                        float w, float h, float tw, float th) {
            this.drawModalRectWithCustomSizedTexture((int) x, (int) y, u, v, (int) w, (int) h, tw, th);
        }

        /** 本家 GuiScreen.drawString(FontRenderer, String, int, int, int)。 */
        public void drawString(Object fontRenderer, String s, int x, int y, int color) {
            this.graphics.drawString(this.font, s == null ? "" : s, x, y, color, false);
        }

        /** 本家 GuiScreen.drawString(FontRenderer, String, int, int, int, boolean shadow)。 */
        public void drawString(Object fontRenderer, String s, int x, int y, int color, boolean shadow) {
            this.graphics.drawString(this.font, s == null ? "" : s, x, y, color, shadow);
        }

        /** 本家 Gui.drawCenteredString(FontRenderer, String, int, int, int)。 */
        public void drawCenteredString(Object fontRenderer, String s, int x, int y, int color) {
            this.graphics.drawCenteredString(this.font, s == null ? "" : s, x, y, color);
        }

        /** 本家 Gui.drawHorizontalLine(int x1, int x2, int y) (1.7.10 は色無し)。 */
        public void drawHorizontalLine(int x1, int x2, int y) {
            this.drawHorizontalLine(x1, x2, y, -16777216);
        }

        /** 本家 Gui.drawVerticalLine(int x, int y1, int y2)。 */
        public void drawVerticalLine(int x, int y1, int y2) {
            this.drawVerticalLine(x, y1, y2, -16777216);
        }

        /** 本家 FontRenderer 相当のファサード (fontRendererObj)。 */
        public static final class FontRendererCompat {
            public static final int FONT_HEIGHT = 9;
            private final GuiGraphics graphics;
            private final Font font;

            FontRendererCompat(GuiGraphics graphics, Font font) {
                this.graphics = graphics;
                this.font = font;
            }

            public int getStringWidth(String s) {
                return this.font.width(s == null ? "" : s);
            }

            public int drawString(String s, int x, int y, int color) {
                this.graphics.drawString(this.font, s == null ? "" : s, x, y, color, false);
                return x + this.getStringWidth(s);
            }

            public int drawStringWithShadow(String s, int x, int y, int color) {
                this.graphics.drawString(this.font, s == null ? "" : s, x, y, color, true);
                return x + this.getStringWidth(s);
            }

            public void drawSplitString(String s, int x, int y, int wrapWidth, int color) {
                // 1.21 の Font.split は FormattedText を要求する。改行を含む場合のみ分割して描く。
                String text = s == null ? "" : s;
                if (text.indexOf('\n') < 0) {
                    this.graphics.drawString(this.font, text, x, y, color, false);
                    return;
                }
                for (String line : text.split("\n", -1)) {
                    this.graphics.drawString(this.font, line, x, y, color, false);
                    y += FONT_HEIGHT;
                }
            }
        }
    }

    /** guiTexture を解決する (空なら null を返し、呼び出し側が既定 cab を使う)。 */
    public static ResourceLocation resolveGuiTexture(VehicleDefinition def) {
        if (def == null || def.getGuiTexture() == null || def.getGuiTexture().isBlank()) {
            return null;
        }
        try {
            return MqoModelLoader.resolvePackTexture(def.getPackName(), def.getGuiTexture());
        } catch (Exception e) {
            return null;
        }
    }
}
