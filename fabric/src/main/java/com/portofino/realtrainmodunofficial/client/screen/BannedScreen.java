package com.portofino.realtrainmodunofficial.client.screen;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * BAN 画面。BAN されたユーザーが RTMU 入りで起動すると必ずこの画面になり、先へ進めない。
 * (BAN リストは RTMU 公式サイトの ban.txt — {@link com.portofino.realtrainmodunofficial.online.RtmuOnlineServices})
 *
 * <p>背景は {@code textures/gui/ban_background.png} (草原の壁紙) を画面いっぱいに敷き、
 * 描画時に少しぼかす (ずらし重ね描き)。テクスチャが無い場合は暗色にフォールバック。
 */
public final class BannedScreen extends Screen {

    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "textures/gui/ban_background.png");

    //背景テクスチャの有無とサイズ (初回だけ調べてキャッシュ)
    private static Boolean backgroundExists;
    private static int texW = 1920;
    private static int texH = 1080;

    public BannedScreen() {
        super(Component.literal("RTMU BAN"));
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("ゲームを終了"), b -> this.minecraft.stop())
                .bounds(this.width / 2 - 75, this.height / 2 + 40, 150, 20).build());
    }

    private static boolean backgroundAvailable() {
        if (backgroundExists == null) {
            try {
                var res = Minecraft.getInstance().getResourceManager().getResource(BACKGROUND);
                if (res.isPresent()) {
                    //実サイズを読む (どの解像度の画像を置いてもアスペクト比を保てるように)
                    try (var in = res.get().open(); NativeImage img = NativeImage.read(in)) {
                        texW = img.getWidth();
                        texH = img.getHeight();
                    }
                    backgroundExists = true;
                } else {
                    backgroundExists = false;
                }
            } catch (Exception e) {
                backgroundExists = false;
            }
        }
        return backgroundExists;
    }

    /** ブラー無効化 (Screen.render が内部で呼ぶため、既定実装だと文字までぼやける)。 */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!backgroundAvailable()) {
            g.fill(0, 0, this.width, this.height, 0xFF100000);
            return;
        }
        //画面を覆うようにアスペクト比を保って拡大 (cover)
        float scale = Math.max((float) this.width / texW, (float) this.height / texH);
        int w = (int) Math.ceil(texW * scale);
        int h = (int) Math.ceil(texH * scale);
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        g.blit(BACKGROUND, x, y, w, h, 0.0F, 0.0F, texW, texH, texW, texH);

        //少しぼかす: 上下左右斜めへ 2px ずらして半透明で重ね描き (簡易ボックスブラー)。
        RenderSystem.enableBlend();
        final int[][] offsets = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 2}, {-2, -2}, {2, -2}, {-2, 2}};
        g.setColor(1.0F, 1.0F, 1.0F, 0.15F);
        for (int[] o : offsets) {
            g.blit(BACKGROUND, x + o[0], y + o[1], w, h, 0.0F, 0.0F, texW, texH, texW, texH);
        }
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();

        //文字の可読性のため、うっすら暗くする
        g.fill(0, 0, this.width, this.height, 0x50000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        //タイトルは 2 倍サイズで目立たせる
        g.pose().pushPose();
        g.pose().translate(this.width / 2.0F, this.height / 2.0F - 40, 0);
        g.pose().scale(2.0F, 2.0F, 1.0F);
        String msg = "あなたはRTMUをBANされています！";
        g.drawString(this.font, msg, -this.font.width(msg) / 2, 0, 0xFF4444, true);
        g.pose().popPose();

        String sub = "この MOD の利用は制限されています。心当たりがない場合は配布元へお問い合わせください。";
        g.drawCenteredString(this.font, sub, this.width / 2, this.height / 2 - 8, 0xF0F0F0);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void onClose() {
        //閉じさせない (OnlineClientHooks が再度この画面へ強制する)。
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
