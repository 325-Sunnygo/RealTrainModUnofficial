package jp.ngt.ngtlib.io;

import java.util.ArrayList;
import java.util.List;

/**
 * 本家 NGTLib jp.ngt.ngtlib.io.NGTText の移植 (スクリプトが触る範囲)。
 * パックのスクリプトは Packages.jp.ngt.ngtlib.io.NGTText で直接参照してくる。
 */
public final class NGTText {

    private NGTText() {
    }

    public static List<String> readText(Object resource) {
        return readTextLines(resource);
    }

    /**
     * パック内アセット (ResourceLocation / パス文字列) をテキストとして 1 行ずつ読む。
     * スクリプトの自前 include (eval(append(readText(getResource(path)))) 等) が使う。
     */
    public static List<String> readTextLines(Object resource) {
        List<String> lines = new ArrayList<>();
        try (java.io.InputStream in = NGTFileLoader.getInputStream(resource)) {
            if (in == null) {
                return lines;
            }
            String text = decodeText(in.readAllBytes());
            // 本家 readTextList: indention=true で読んだ結果を行へ分解する (改行種別は問わない)。
            for (String line : text.split(LINE_SEPARATOR_PATTERN, -1)) {
                lines.add(line);
            }
        } catch (Exception ignored) {
            // 読めない場合は空 (呼び出し側は include をスキップして続行)
        }
        return lines;
    }

    /**
     * 本家 jp.kaiz.kaizpatch.util.MCFileUtil.readText 完全移植。
     * 既定文字コードで読み、置換文字 (U+FFFD) が出たときだけ MS932 (Shift_JIS) で読み直す。
     * 1.7.10 時代のパックは Shift_JIS の .js を持つものが多く、
     * これをやらないと日本語コメント/文字列が壊れて構文エラーになる。
     */
    public static String decodeText(byte[] bytes) {
        String utf8 = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        if (utf8.indexOf('\ufffd') >= 0) {
            try {
                return new String(bytes, java.nio.charset.Charset.forName("MS932"));
            } catch (Exception ignored) {
                return utf8;
            }
        }
        return utf8;
    }

    private static final String LINE_SEPARATOR_PATTERN = "\r\n|[\n\r\u2028\u2029\u0085]";

    public static String loadText(Object resource) {
        return "";
    }

    public static String createText(Object... args) {
        return "";
    }

    public static void writeText(Object... args) {
    }

    public static void appendText(Object... args) {
    }

    public static String applyTextStyles(Object... args) {
        return args != null && args.length > 0 ? String.valueOf(args[0]) : "";
    }

    /** 本家append: 行リストを連結。 */
    public static String append(java.util.List<String> list, boolean indention) {
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            sb.append(s);
            if (indention) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * 本家 NGTText.getText(ResourceLocation, boolean) 完全移植。
     * 全文を読み、改行を indention=true なら \n、false なら除去して連結する。
     * (本家 ModelPackManager.loadScript は indention=true で読み、//include を行単位で置換する。
     *  indention=false のときは改行が消え、"//" 以降がコメントとして残りを飲み込む。)
     */
    public static String getText(Object resource, boolean indention) {
        try (java.io.InputStream in = NGTFileLoader.getInputStream(resource)) {
            if (in == null) {
                return "";
            }
            return decodeText(in.readAllBytes())
                    .replaceAll(LINE_SEPARATOR_PATTERN, indention ? "\n" : "");
        } catch (Exception ignored) {
            return "";
        }
    }

    /** 本家readTextL: InputStreamから行リスト。 */
    public static java.util.List<String> readTextL(java.io.InputStream is, String encoding) {
        java.util.List<String> list = new java.util.ArrayList<>();
        try {
            java.nio.charset.Charset cs = (encoding == null || encoding.isEmpty())
                ? java.nio.charset.StandardCharsets.UTF_8 : java.nio.charset.Charset.forName(encoding);
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, cs));
            String line;
            while ((line = reader.readLine()) != null) {
                list.add(line);
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    /** 本家readCSV。 */
    public static String[][] readCSV(java.io.File file, String encoding) {
        try (java.io.InputStream in = new java.io.FileInputStream(file)) {
            java.util.List<String> texts = readTextL(in, encoding);
            String[][] out = new String[texts.size()][];
            for (int i = 0; i < texts.size(); i++) {
                out[i] = texts.get(i).split(",");
            }
            return out;
        } catch (Exception e) {
            return new String[0][];
        }
    }

    /** 本家writeToText: ファイルへ行を書き出す。 */
    public static boolean writeToText(java.io.File file, String... texts) {
        try (java.io.PrintWriter pw = new java.io.PrintWriter(
                new java.io.OutputStreamWriter(new java.io.FileOutputStream(file), java.nio.charset.StandardCharsets.UTF_8))) {
            for (String t : texts) {
                pw.println(t);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}
