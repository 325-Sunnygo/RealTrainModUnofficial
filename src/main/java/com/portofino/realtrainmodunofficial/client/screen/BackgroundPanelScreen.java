package com.portofino.realtrainmodunofficial.client.screen;

import com.mojang.blaze3d.platform.NativeImage;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.blockentity.BackgroundPanelBlockEntity;
import com.portofino.realtrainmodunofficial.client.render.BackgroundTextures;
import com.portofino.realtrainmodunofficial.network.BackgroundPanelPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 背景パネルの設定画面。
 *
 * <p><b>その場でファイルを選ぶ</b> (OS のファイル選択ダイアログ)。
 * 専用フォルダは作らない。選んだ画像はここで縮小して<b>ブロックへ埋め込む</b>ので、
 * あとでファイルを消してもワールドから消えないし、マルチでも相手に見える。
 */
public class BackgroundPanelScreen extends Screen {

    /** 埋め込む前に縮める上限 (長辺・画素)。背景としてはこれで足りる。 */
    private static final int MAX_SIDE = 1024;

    private final BlockPos pos;
    private byte[] image;
    private String imageName;
    private float scale;
    private float offsetY;
    /** 選択ダイアログを出している間は二重に開かない。 */
    private volatile boolean choosing;
    private String message = "";

    public BackgroundPanelScreen(BlockPos pos, byte[] image, String imageName,
                                 float scale, float offsetY) {
        super(Component.literal("背景パネル"));
        this.pos = pos;
        this.image = image == null ? new byte[0] : image;
        this.imageName = imageName == null ? "" : imageName;
        this.scale = scale;
        this.offsetY = offsetY;
    }

    private EditBox scaleBox;
    private EditBox offsetBox;

    @Override
    protected void init() {
        int hw = this.width / 2;

        addRenderableWidget(Button.builder(Component.literal("画像を選ぶ..."), b -> choose())
            .bounds(hw - 155, this.height - 112, 200, 20).build());
        addRenderableWidget(Button.builder(Component.literal("画像を外す"), b -> {
            image = new byte[0];
            imageName = "";
            message = "";
        }).bounds(hw + 55, this.height - 112, 100, 20).build());

        scaleBox = new EditBox(this.font, hw - 155, this.height - 84, 150, 20, Component.empty());
        scaleBox.setValue(trim(scale));
        scaleBox.setHint(Component.literal("幅 (ブロック)"));
        addRenderableWidget(scaleBox);

        offsetBox = new EditBox(this.font, hw + 5, this.height - 84, 150, 20, Component.empty());
        offsetBox.setValue(trim(offsetY));
        offsetBox.setHint(Component.literal("高さのずらし"));
        addRenderableWidget(offsetBox);

        addRenderableWidget(Button.builder(Component.literal("保存"), b -> save())
            .bounds(hw - 155, this.height - 28, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
            .bounds(hw + 5, this.height - 28, 150, 20).build());
    }

    /**
     * OS のファイル選択ダイアログを出す。
     *
     * <p>★描画スレッドで開かないこと。ダイアログが閉じるまでゲームが固まる。
     * 別スレッドで開き、選ばれてから本スレッドへ戻す。
     */
    private void choose() {
        if (choosing) {
            return;
        }
        choosing = true;
        message = "選択中...";
        Thread t = new Thread(() -> {
            String picked = null;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(4);
                filters.put(stack.UTF8("*.png"));
                filters.put(stack.UTF8("*.jpg"));
                filters.put(stack.UTF8("*.jpeg"));
                filters.put(stack.UTF8("*.bmp"));
                filters.flip();
                picked = TinyFileDialogs.tinyfd_openFileDialog(
                    "背景にする画像を選ぶ", "", filters, "画像 (png / jpg / bmp)", false);
            } catch (Throwable e) {
                RealTrainModUnofficial.LOGGER.warn("[背景パネル] ファイル選択に失敗", e);
            }
            String result = picked;
            Minecraft.getInstance().execute(() -> {
                choosing = false;
                if (result == null) {
                    message = "";
                    return;
                }
                load(result);
            });
        }, "rtmu-background-file-dialog");
        t.setDaemon(true);
        t.start();
    }

    /** 選ばれた画像を読み、埋め込める大きさまで縮めて持つ。 */
    private void load(String path) {
        try {
            Path file = Path.of(path);
            byte[] raw = Files.readAllBytes(file);
            NativeImage src = NativeImage.read(new java.io.ByteArrayInputStream(raw));
            byte[] encoded = shrinkToFit(src);
            src.close();
            if (encoded == null) {
                message = "この画像は大きすぎます";
                return;
            }
            image = encoded;
            imageName = file.getFileName().toString();
            message = String.format("読み込みました (%.0f KB)", encoded.length / 1024.0);
        } catch (Throwable e) {
            message = "この画像は読めません";
            RealTrainModUnofficial.LOGGER.warn("[背景パネル] 画像を読めません: {} ({})", path, e.toString());
        }
    }

    /**
     * 長辺 {@value #MAX_SIDE} 画素以内、かつ埋め込み上限以内になるまで半分ずつ縮める。
     * <p>写真をそのまま入れると同期に載らないため。背景なのでこの解像度で足りる。
     */
    private static byte[] shrinkToFit(NativeImage src) throws Exception {
        int w = src.getWidth();
        int h = src.getHeight();
        int side = Math.max(w, h);
        int div = 1;
        while (side / div > MAX_SIDE) {
            div *= 2;
        }
        for (int attempt = 0; attempt < 6; attempt++, div *= 2) {
            int tw = Math.max(1, w / div);
            int th = Math.max(1, h / div);
            NativeImage dst = new NativeImage(tw, th, false);
            try {
                for (int y = 0; y < th; y++) {
                    for (int x = 0; x < tw; x++) {
                        //単純な間引き。背景なので画質より確実さを取る
                        dst.setPixelRGBA(x, y, src.getPixelRGBA(
                            Math.min(w - 1, x * div), Math.min(h - 1, y * div)));
                    }
                }
                byte[] out = dst.asByteArray();
                if (out.length <= BackgroundPanelBlockEntity.MAX_IMAGE_BYTES) {
                    return out;
                }
            } finally {
                dst.close();
            }
        }
        return null;
    }

    private float readScale() {
        try {
            return Math.max(BackgroundPanelBlockEntity.MIN_SCALE,
                Math.min(BackgroundPanelBlockEntity.MAX_SCALE,
                    Float.parseFloat(scaleBox.getValue().trim())));
        } catch (Exception e) {
            return scale;
        }
    }

    private float readOffset() {
        try {
            return Math.max(-64.0F, Math.min(64.0F, Float.parseFloat(offsetBox.getValue().trim())));
        } catch (Exception e) {
            return offsetY;
        }
    }

    private void save() {
        PacketDistributor.sendToServer(
            new BackgroundPanelPayload(pos, image, imageName, readScale(), readOffset()));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFFFFF);
        String current = image.length == 0
            ? "画像なし (紫と黒の格子が出ます)"
            : (imageName.isEmpty() ? "画像あり" : imageName)
                + String.format(" (%.0f KB)", image.length / 1024.0);
        g.drawCenteredString(this.font, Component.literal(current), this.width / 2, 28, 0xC0C0C0);
        if (!message.isEmpty()) {
            g.drawCenteredString(this.font, Component.literal(message), this.width / 2, 40, 0xFFD070);
        }

        //保存前に見た目を確かめられるよう、選んだ画像を大きめに出す
        BackgroundTextures.Loaded l = BackgroundTextures.get(image);
        if (l != null) {
            int maxW = Math.min(320, this.width - 40);
            int maxH = this.height - 190;
            if (maxH > 20) {
                float aspect = (float) l.height() / (float) l.width();
                int w = maxW;
                int h = Math.max(1, (int) (w * aspect));
                if (h > maxH) {
                    h = maxH;
                    w = Math.max(1, (int) (h / aspect));
                }
                int x = (this.width - w) / 2;
                g.blit(l.location(), x, 56, 0, 0.0F, 0.0F, w, h, w, h);
            }
        }
    }

    private static String trim(float v) {
        return v == Math.rint(v) ? String.valueOf((int) v) : String.valueOf(v);
    }

    /** 1.21 のメニューぼかしを無効化 (本家 1.7.10 の GUI にぼかしは無い)。 */
    @Override
    protected void renderBlurredBackground(float partialTick) {
    }
}
