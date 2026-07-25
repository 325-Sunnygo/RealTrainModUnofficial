package jp.ngt.ngtlib.renderer.model;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * MQO (Metasequoia) テキストの<b>唯一の読み取り口</b>。
 * <p>
 * 以前は同じ書式を 2 箇所で別々に解析していた ({@link ModelLoader} のグラフ生成と、
 * 描画用バッチを組む {@code MqoModelLoader})。片方だけ直すと、スクリプトが見るモデルと
 * 実際に描かれるモデルが食い違う (グループ名・面数・マテリアル番号のずれ) ため、
 * 書式の解釈はここに一本化する。
 * <p>
 * ここは<b>解釈だけ</b>を行い、座標のスケールや面の頂点順序といった「使う側の都合」は
 * {@link Handler} の実装に委ねる。両者の既存の振る舞いを変えずに読み取りだけ共有するため。
 */
public final class MqoReader {
    private static final Pattern OBJECT_NAME = Pattern.compile("Object\\s+\"([^\"]*)\"");
    private static final Pattern TEX = Pattern.compile("tex\\(\"([^\"]*)\"\\)");
    private static final Pattern COL = Pattern.compile("col\\(([-0-9.]+)\\s+([-0-9.]+)\\s+([-0-9.]+)\\s+([-0-9.]+)\\)");
    private static final Pattern V = Pattern.compile("V\\(([^)]*)\\)");
    private static final Pattern M = Pattern.compile("M\\(([^)]*)\\)");
    private static final Pattern UV = Pattern.compile("UV\\(([^)]*)\\)");

    private MqoReader() {
    }

    /** 読み取り結果の受け口。必要なものだけ実装すればよい。 */
    public interface Handler {
        /** Material チャンクの 1 行。{@code index} は面の {@code M(n)} が指す番号。 */
        default void material(int index, String name, String texPath, float r, float g, float b, float a) {
        }

        /** Object チャンクの開始。facet / mirror は既定値へ戻っている。 */
        default void objectStart(String name) {
        }

        /** スムージング角 (Object 内 {@code facet})。 */
        default void facet(float angle) {
        }

        /** ミラー軸 (Object 内 {@code mirror_axis})。1=X, 2=Y, 4=Z のビットマスク。 */
        default void mirrorAxis(int axis) {
        }

        /** vertex チャンクの開始。ここまでに溜めた頂点は捨てる。 */
        default void verticesStart() {
        }

        /**
         * 頂点 1 個。<b>MQO の生の単位</b> (スケール前)。
         * z を持たない 2 成分表記の場合 {@code z} は 0。
         */
        default void vertex(float x, float y, float z) {
        }

        /**
         * 面 1 枚。{@code vertexIndices} は直前の {@code vertex} チャンク内の番号、
         * 順序も MQO の記述どおり。{@code uvs} は {@code count * 2} 個、無ければ null。
         */
        default void face(int count, int materialId, int[] vertexIndices, float[] uvs) {
        }
    }

    /**
     * .mqoz (zip 内に .mqo) にも対応してテキストを取り出す。
     * 文字コードは Shift_JIS 前提 (MQO の既定)。
     */
    public static String extractText(byte[] bytes, String name) throws IOException {
        if (bytes == null) {
            return null;
        }
        if (bytes.length > 4 && bytes[0] == 'P' && bytes[1] == 'K') {
            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (entry.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".mqo")) {
                        return new String(zip.readAllBytes(), charset());
                    }
                }
            }
            return null;
        }
        return new String(bytes, charset());
    }

    private static Charset charset() {
        try {
            return Charset.forName("Shift_JIS");
        } catch (Exception e) {
            return Charset.defaultCharset();
        }
    }

    /** MQO テキストを 1 回走査して {@link Handler} へ流す。 */
    public static void read(String text, Handler handler) {
        if (text == null || handler == null) {
            return;
        }
        //0=外, 1=vertex, 2=face, 3=Material, 4=読み飛ばす (BVertex 等の未対応チャンク)
        int chunk = 0;
        int materialIndex = 0;
        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("//")) {
                continue;
            }
            if (line.equals("{")) {
                continue;
            }
            if (line.startsWith("}")) {
                chunk = 0;
                continue;
            }
            if (chunk != 0) {
                switch (chunk) {
                    case 1 -> readVertex(line, handler);
                    case 2 -> readFace(line, handler);
                    case 3 -> materialIndex = readMaterial(line, materialIndex, handler);
                    default -> { }
                }
                continue;
            }
            if (line.startsWith("Material ")) {
                chunk = 3;
                materialIndex = 0;
                continue;
            }
            if (line.startsWith("vertex ")) {
                chunk = 1;
                handler.verticesStart();
                continue;
            }
            if (line.startsWith("BVertex")) {
                //バイナリ頂点は未対応。チャンクごと読み飛ばす (面は出ないので描画されない)
                chunk = 4;
                continue;
            }
            if (line.startsWith("face ")) {
                chunk = 2;
                continue;
            }
            Matcher om = OBJECT_NAME.matcher(line);
            if (om.find()) {
                handler.objectStart(om.group(1));
                continue;
            }
            if (line.startsWith("facet ")) {
                String[] p = line.split("\\s+");
                if (p.length > 1) {
                    try {
                        handler.facet(Float.parseFloat(p[1]));
                    } catch (NumberFormatException ignored) {
                    }
                }
                continue;
            }
            if (line.startsWith("mirror_axis ")) {
                String[] p = line.split("\\s+");
                if (p.length > 1) {
                    try {
                        handler.mirrorAxis(Integer.parseInt(p[1]));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
    }

    private static void readVertex(String line, Handler handler) {
        String[] t = line.split("\\s+");
        try {
            if (t.length == 2) {
                handler.vertex(Float.parseFloat(t[0]), Float.parseFloat(t[1]), 0.0F);
            } else if (t.length >= 3) {
                handler.vertex(Float.parseFloat(t[0]), Float.parseFloat(t[1]), Float.parseFloat(t[2]));
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private static void readFace(String line, Handler handler) {
        int sp = line.indexOf(' ');
        if (sp <= 0) {
            return;
        }
        int count;
        try {
            count = Integer.parseInt(line.substring(0, sp).trim());
        } catch (NumberFormatException e) {
            return;
        }
        if (count < 3) {
            return;
        }
        Matcher vm = V.matcher(line);
        if (!vm.find()) {
            return;
        }
        String[] vt = vm.group(1).trim().split("\\s+");
        if (vt.length < count) {
            return;
        }
        int[] indices = new int[count];
        try {
            for (int i = 0; i < count; i++) {
                indices[i] = Integer.parseInt(vt[i]);
            }
        } catch (NumberFormatException e) {
            return;
        }
        int materialId = 0;
        Matcher mm = M.matcher(line);
        if (mm.find()) {
            try {
                materialId = Integer.parseInt(mm.group(1).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        float[] uvs = null;
        Matcher um = UV.matcher(line);
        if (um.find()) {
            String[] ut = um.group(1).trim().split("\\s+");
            if (ut.length >= count * 2) {
                uvs = new float[count * 2];
                try {
                    for (int i = 0; i < count * 2; i++) {
                        uvs[i] = Float.parseFloat(ut[i]);
                    }
                } catch (NumberFormatException e) {
                    uvs = null;
                }
            }
        }
        handler.face(count, materialId, indices, uvs);
    }

    private static int readMaterial(String line, int index, Handler handler) {
        String[] tok = line.split("\\s+");
        if (tok.length == 0) {
            return index;
        }
        String name = tok[0].replace("\"", "");
        if (name.isBlank()) {
            return index;
        }
        Matcher tm = TEX.matcher(line);
        String texPath = tm.find() ? tm.group(1) : null;
        float r = 1.0F, g = 1.0F, b = 1.0F, a = 1.0F;
        Matcher cm = COL.matcher(line);
        if (cm.find()) {
            try {
                r = Float.parseFloat(cm.group(1));
                g = Float.parseFloat(cm.group(2));
                b = Float.parseFloat(cm.group(3));
                a = Float.parseFloat(cm.group(4));
            } catch (NumberFormatException ignored) {
            }
        }
        handler.material(index, name, texPath, r, g, b, a);
        return index + 1;
    }
}
