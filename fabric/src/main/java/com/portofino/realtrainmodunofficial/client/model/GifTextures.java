package com.portofino.realtrainmodunofficial.client.model;

import com.mojang.blaze3d.platform.NativeImage;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.w3c.dom.Node;

/**
 * 方向幕 (rollsign) / 種別幕などのテクスチャに <b>アニメーション GIF</b> を直接使えるようにする。
 *
 * <p>Minecraft の {@link NativeImage#read} は PNG しか読めないので、GIF は ImageIO で
 * フレーム分解 (部分フレーム・透過・disposal を合成して各フレームをフル画像に coalesce) し、
 * {@link DynamicTexture} 1 枚に毎 tick 現在フレームを貼り替えて再生する。
 *
 * <p>{@link MqoModelLoader#resolvePackTexture} が拡張子 .gif を見てここへ委譲する。
 * フレーム送りは {@link #tick()} を毎クライアント tick で呼ぶ。
 */
public final class GifTextures {

    /** テクスチャの中身を開くための供給子 (パック zip / フォルダから)。 */
    public interface StreamOpener {
        InputStream open() throws Exception;
    }

    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();
    private static final List<Entry> ANIMATED = new CopyOnWriteArrayList<>();

    private GifTextures() {
    }

    private static final class Entry {
        ResourceLocation location;
        DynamicTexture texture;
        NativeImage[] frames;
        int[] cumulativeMs;   //各フレーム終了時刻の累積 (ms)
        int totalMs;
        long startMillis;
        int shownFrame = -1;
    }

    /**
     * .gif パックテクスチャを (必要なら) 登録し、その ResourceLocation を返す。
     * デコードに失敗したら null (呼び出し側は静止テクスチャ経路へフォールバックする)。
     */
    public static ResourceLocation resolve(String cacheKey, StreamOpener opener) {
        Entry existing = CACHE.get(cacheKey);
        if (existing != null) {
            return existing.location;
        }
        try {
            Entry entry = decode(cacheKey, opener);
            if (entry == null) {
                return null;
            }
            CACHE.put(cacheKey, entry);
            if (entry.frames.length > 1) {
                ANIMATED.add(entry);
            }
            return entry.location;
        } catch (Throwable t) {
            RealTrainModUnofficial.LOGGER.warn("Failed to load GIF texture {}: {}", cacheKey, t.toString());
            return null;
        }
    }

    /** 毎クライアント tick: 経過時間から現在フレームを求めて貼り替える。 */
    public static void tick() {
        if (ANIMATED.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Entry e : ANIMATED) {
            if (e.totalMs <= 0 || e.frames.length <= 1) {
                continue;
            }
            int t = (int) ((now - e.startMillis) % e.totalMs);
            int frame = frameForTime(e.cumulativeMs, t);
            if (frame != e.shownFrame) {
                e.shownFrame = frame;
                upload(e, frame);
            }
        }
    }

    private static int frameForTime(int[] cumulative, int t) {
        for (int i = 0; i < cumulative.length; i++) {
            if (t < cumulative[i]) {
                return i;
            }
        }
        return cumulative.length - 1;
    }

    private static void upload(Entry e, int frame) {
        try {
            NativeImage dst = e.texture.getPixels();
            if (dst == null) {
                return;
            }
            dst.copyFrom(e.frames[frame]);
            e.texture.upload();
        } catch (Throwable ignored) {
            //貼り替え失敗で描画を巻き込まない
        }
    }

    private static Entry decode(String cacheKey, StreamOpener opener) throws Exception {
        List<BufferedImage> coalesced = new ArrayList<>();
        List<Integer> delaysMs = new ArrayList<>();
        try (InputStream in = opener.open();
             ImageInputStream iis = ImageIO.createImageInputStream(in)) {
            if (iis == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, false);
                int count = reader.getNumImages(true);
                if (count <= 0) {
                    return null;
                }
                int[] screen = logicalScreenSize(reader, count);
                int w = screen[0];
                int h = screen[1];
                BufferedImage canvas = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                for (int i = 0; i < count; i++) {
                    BufferedImage frame = reader.read(i);
                    IIOMetadata meta = reader.getImageMetadata(i);
                    FrameMeta fm = frameMeta(meta);
                    BufferedImage before = fm.disposal == 3 ? deepCopy(canvas) : null;
                    Graphics2D g = canvas.createGraphics();
                    g.drawImage(frame, fm.x, fm.y, null);
                    g.dispose();
                    coalesced.add(deepCopy(canvas));
                    delaysMs.add(Math.max(20, fm.delayMs)); //0 は多くのビューアで最低速度に丸める
                    //disposal: 2=背景で塗り潰す, 3=直前に戻す, それ以外=そのまま
                    if (fm.disposal == 2) {
                        clearRegion(canvas, fm.x, fm.y, frame.getWidth(), frame.getHeight());
                    } else if (fm.disposal == 3 && before != null) {
                        canvas = before;
                    }
                }
            } finally {
                reader.dispose();
            }
        }
        if (coalesced.isEmpty()) {
            return null;
        }

        Entry entry = new Entry();
        entry.frames = new NativeImage[coalesced.size()];
        entry.cumulativeMs = new int[coalesced.size()];
        int acc = 0;
        for (int i = 0; i < coalesced.size(); i++) {
            entry.frames[i] = toNativeImage(coalesced.get(i));
            acc += delaysMs.get(i);
            entry.cumulativeMs[i] = acc;
        }
        entry.totalMs = acc;
        entry.startMillis = System.currentTimeMillis();

        NativeImage first = new NativeImage(entry.frames[0].getWidth(), entry.frames[0].getHeight(), false);
        first.copyFrom(entry.frames[0]);
        entry.texture = new DynamicTexture(first);
        entry.location = ResourceLocation.fromNamespaceAndPath(
                RealTrainModUnofficial.MODID, "gif/" + safeKey(cacheKey));
        Minecraft.getInstance().getTextureManager().register(entry.location, entry.texture);
        entry.shownFrame = 0;
        return entry;
    }

    private static int[] logicalScreenSize(ImageReader reader, int frameCount) {
        try {
            IIOMetadata sm = reader.getStreamMetadata();
            if (sm != null) {
                IIOMetadataNode root = (IIOMetadataNode) sm.getAsTree(sm.getNativeMetadataFormatName());
                Node lsd = child(root, "LogicalScreenDescriptor");
                if (lsd != null) {
                    int w = attrInt(lsd, "logicalScreenWidth", 0);
                    int h = attrInt(lsd, "logicalScreenHeight", 0);
                    if (w > 0 && h > 0) {
                        return new int[]{w, h};
                    }
                }
            }
        } catch (Exception ignored) {
        }
        //フォールバック: 全フレームの (x+幅, y+高さ) の最大
        int w = 1;
        int h = 1;
        try {
            for (int i = 0; i < frameCount; i++) {
                w = Math.max(w, reader.getWidth(i));
                h = Math.max(h, reader.getHeight(i));
            }
        } catch (Exception ignored) {
        }
        return new int[]{w, h};
    }

    private static final class FrameMeta {
        int x;
        int y;
        int delayMs;
        int disposal; //0/1=none, 2=restoreToBackgroundColor, 3=restoreToPrevious
    }

    private static FrameMeta frameMeta(IIOMetadata meta) {
        FrameMeta fm = new FrameMeta();
        try {
            IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree(meta.getNativeMetadataFormatName());
            Node desc = child(root, "ImageDescriptor");
            if (desc != null) {
                fm.x = attrInt(desc, "imageLeftPosition", 0);
                fm.y = attrInt(desc, "imageTopPosition", 0);
            }
            Node gce = child(root, "GraphicControlExtension");
            if (gce != null) {
                fm.delayMs = attrInt(gce, "delayTime", 0) * 10; //センチ秒 → ミリ秒
                String disposal = attrStr(gce, "disposalMethod");
                fm.disposal = switch (disposal == null ? "" : disposal) {
                    case "restoreToBackgroundColor" -> 2;
                    case "restoreToPrevious" -> 3;
                    default -> 0;
                };
            }
        } catch (Exception ignored) {
        }
        return fm;
    }

    private static Node child(Node parent, String name) {
        if (parent == null) {
            return null;
        }
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (name.equals(n.getNodeName())) {
                return n;
            }
        }
        return null;
    }

    private static int attrInt(Node node, String attr, int def) {
        String s = attrStr(node, attr);
        if (s == null || s.isEmpty()) {
            return def;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String attrStr(Node node, String attr) {
        if (node.getAttributes() == null) {
            return null;
        }
        Node a = node.getAttributes().getNamedItem(attr);
        return a == null ? null : a.getNodeValue();
    }

    private static BufferedImage deepCopy(BufferedImage src) {
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return copy;
    }

    private static void clearRegion(BufferedImage img, int x, int y, int w, int h) {
        for (int yy = y; yy < y + h && yy < img.getHeight(); yy++) {
            for (int xx = x; xx < x + w && xx < img.getWidth(); xx++) {
                if (xx >= 0 && yy >= 0) {
                    img.setRGB(xx, yy, 0);
                }
            }
        }
    }

    /** BufferedImage(ARGB) → NativeImage(RGBA)。int の R と B を入れ替える。 */
    private static NativeImage toNativeImage(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        NativeImage out = new NativeImage(NativeImage.Format.RGBA, w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >>> 16) & 0xFF;
                int gg = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                //NativeImage の RGBA int は 0xAABBGGRR (little-endian の R,G,B,A)
                int abgr = (a << 24) | (b << 16) | (gg << 8) | r;
                out.setPixelRGBA(x, y, abgr);
            }
        }
        return out;
    }

    private static String safeKey(String cacheKey) {
        //ResourceLocation のパスは [a-z0-9/._-] のみ。ハッシュにして安全化。
        return Integer.toHexString(cacheKey.hashCode()) + "_" + (cacheKey.length() & 0xFFFF);
    }
}
