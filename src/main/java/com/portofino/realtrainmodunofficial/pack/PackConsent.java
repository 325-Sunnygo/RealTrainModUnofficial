package com.portofino.realtrainmodunofficial.pack;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.neoforged.fml.loading.FMLPaths;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * パック同意 (README 初回同意) の管理。
 * パック zip の中に README (readme.txt / お読みください.txt 等) が入っている場合、
 * その起動構成でそのパックを初めて入れたときにタイトル画面で README を表示し、
 * 「同意する / 同意しない」を選ばせる。
 */
public final class PackConsent {

    private PackConsent() {
    }

    public enum State { AGREED, DECLINED }

    /** 1 つの未決パック (README 表示待ち)。 */
    public record Pending(String fileName, Path path, String readme) {
    }

    // ファイル名 → 決定。決定済みのみ保持。
    private static final Map<String, State> DECISIONS = new ConcurrentHashMap<>();
    // ファイル名 → 未決パック (README あり・未決定)。タイトル画面で表示する。
    private static final Map<String, Pending> PENDING = new ConcurrentHashMap<>();
    // README を持たないと判明したパック (同一セッションで複数ローダーから何度も zip を開かないため)。
    private static final java.util.Set<String> NO_README = ConcurrentHashMap.newKeySet();
    private static volatile boolean loaded;

    private static Path storePath() {
        return FMLPaths.GAMEDIR.get()
                .resolve("config").resolve("realtrainmodunofficial").resolve("pack_consent.txt");
    }

    /** 保存ファイルから決定を読み込む (初回のみ)。 */
    public static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path p = storePath();
        try {
            if (Files.isRegularFile(p)) {
                for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                    String s = line.trim();
                    if (s.isEmpty() || s.startsWith("#")) {
                        continue;
                    }
                    int tab = s.indexOf('\t');
                    if (tab <= 0) {
                        continue;
                    }
                    String state = s.substring(0, tab).trim().toUpperCase(Locale.ROOT);
                    String name = s.substring(tab + 1).trim();
                    if (name.isEmpty()) {
                        continue;
                    }
                    if ("AGREED".equals(state)) {
                        DECISIONS.put(name, State.AGREED);
                    }
                    // DECLINED は読み込まない: 「同意しない」は永続しない仕様
                    // (同意するまで毎起動で同意画面を出す)。旧形式の DECLINED 行は無視され、
                    // 次回 save で消える。
                }
            }
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("[PackConsent] 同意ファイルの読み込みに失敗: {}", e.toString());
        }
    }

    private static synchronized void save() {
        Path p = storePath();
        try {
            Files.createDirectories(p.getParent());
            List<String> lines = new ArrayList<>();
            lines.add("# RTMU pack consent — 1 line per agreed pack. Delete a line to be asked again.");
            // AGREED のみ永続化。「同意しない」は保存せず、次回起動時にまた同意画面を出す。
            DECISIONS.forEach((name, state) -> {
                if (state == State.AGREED) {
                    lines.add(state.name() + "\t" + name);
                }
            });
            Files.write(p, lines, StandardCharsets.UTF_8);
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("[PackConsent] 同意ファイルの保存に失敗: {}", e.toString());
        }
    }

    /**
     * このパック zip をロードしてよいか。
     * 同意済み / README 無し → true
     * 同意しない → false
     * 未決 (README あり・未決定) → false を返し、タイトル画面表示用に retain する
     */
    public static boolean isAllowed(Path zip) {
        if (zip == null) {
            return true;
        }
        // 専用サーバーにはタイトル画面 (README 同意 UI) が無いため、同意ゲートを適用すると
        // README 付きパックが永久に「未決=false」となりロードされない。
        // 車両/レール/サウンド等のパックが欠け、クライアントで選んだ列車が設置時に
        // VehicleRegistry.getById=null → 既定 (先頭の ELECTRIC =5000番台Tc) にフォールバック
        // する (= 「専用サーバーだと全列車がになる。自動車 (同梱) は正常」の真因。
        if (net.neoforged.fml.loading.FMLEnvironment.dist != net.neoforged.api.distmarker.Dist.CLIENT) {
            return true;
        }
        // ★MOD 自身の規約は出さない。
        // README 判定が "license" にも一致するため、パックの規約と同じ画面に出てしまっていた。
        // ★同梱パック (rtm_default_assets/ の等) は除外しない。
        if (isOwnModJar(zip)) {
            return true;
        }
        ensureLoaded();
        String name = zip.getFileName().toString();
        State decided = DECISIONS.get(name);
        if (decided == State.AGREED) {
            return true;
        }
        if (decided == State.DECLINED) {
            return false;
        }
        if (NO_README.contains(name)) {
            return true;
        }
        // 未決: README があるか調べる。無ければ同意不要でロード。
        String readme = readReadme(zip);
        if (readme == null) {
            NO_README.add(name);
            return true;
        }
        PENDING.putIfAbsent(name, new Pending(name, zip, readme));
        return false;
    }


    /** この MOD 自身の jar か。 */
    private static boolean isOwnModJar(Path zip) {
        try {
            return com.portofino.realtrainmodunofficial.BundledPackStore.isOwnModJar(zip);
        } catch (Exception e) {
            // 判定できないときは同意を求める側に倒す (規約を勝手に飛ばさない)
            return false;
        }
    }

    /** タイトル画面表示待ちの未決パック一覧。 */
    public static List<Pending> getPending() {
        return new ArrayList<>(PENDING.values());
    }

    public static boolean hasPending() {
        return !PENDING.isEmpty();
    }

    /**
     * 同意/不同意を記録する。未決一覧からは外す。
     * 同意はファイルへ保存され以後表示しない。
     */
    public static void decide(String fileName, boolean agreed) {
        if (fileName == null || fileName.isEmpty()) {
            return;
        }
        DECISIONS.put(fileName, agreed ? State.AGREED : State.DECLINED);
        PENDING.remove(fileName);
        save();
    }

    /** zip 内の README 系ファイルの本文。無ければ null。 */
    private static String readReadme(Path zip) {
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            ZipEntry best = null;
            var entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e.isDirectory()) {
                    continue;
                }
                if (!isReadmeName(e.getName())) {
                    continue;
                }
                // ルート直下 (階層が浅い) の README を優先する。
                if (best == null || depth(e.getName()) < depth(best.getName())) {
                    best = e;
                }
            }
            if (best == null) {
                return null;
            }
            try (InputStream in = zf.getInputStream(best)) {
                byte[] bytes = in.readAllBytes();
                String text = sanitize(decodeText(bytes));
                return text.isBlank() ? null : text;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static int depth(String name) {
        int d = 0;
        for (int i = 0; i < name.length(); i++) {
            if (name.charAt(i) == '/') {
                d++;
            }
        }
        return d;
    }

    private static boolean isReadmeName(String entryName) {
        String n = entryName.substring(entryName.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        if (!(n.endsWith(".txt") || n.endsWith(".md") || n.indexOf('.') < 0)) {
            // 拡張子なし (README) も許可。それ以外の拡張子 (.png 等) は除外。
            if (!n.equals("readme")) {
                return false;
            }
        }
        // 英語系
        if (n.contains("readme") || n.startsWith("license") || n.contains("terms")) {
            return true;
        }
        // 日本語系 (ファイル名が日本語のことが多い)
        String raw = entryName.substring(entryName.lastIndexOf('/') + 1);
        return raw.contains("お読み") || raw.contains("よんで") || raw.contains("読んで")
                || raw.contains("説明") || raw.contains("利用規約") || raw.contains("規約")
                || raw.contains("はじめに") || raw.contains("注意");
    }

    /**
     * README を画面表示用に整える。テキストファイルと同じ見た目にする。
     * BOM / ゼロ幅文字を除去 (Minecraft が "ZWNBSP" 等の箱で表示するのを防ぐ)
     * 改行を LF に正規化 (CRLF/CR の \r が "CR" の箱で表示されるのを防ぐ)
     * タブ・改行以外の制御文字を除去
     */
    private static String sanitize(String s) {
        if (s == null) {
            return "";
        }
        // 改行を LF に統一 (\r を消す)。CRLF/CR の \r が "CR" の箱で表示されるのを防ぐ。
        s = s.replace("\r\n", "\n").replace("\r", "\n");
        // タブは "HT" の箱で表示されるのでスペースへ置き換える。
        s = s.replace("\t", "    ");
        // 改行以外の制御文字、BOM(U+FEFF)、ゼロ幅文字(U+200B〜U+200F)を除去。
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n') {
                sb.append(c);
            } else if (c < 0x20) {
                continue; //制御文字
            } else if (c == '﻿' || (c >= '​' && c <= '‏')) {
                continue; //BOM・ゼロ幅文字
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * README を UTF-8 → MS932(CP932) → Shift_JIS の順に厳格デコードする共通デコーダに委譲する。
     * 旧実装は Shift_JIS しか試さず、① / ㈱ / 号 のような「JDK の Shift_JIS には無く CP932 にだけある」
     * 文字を含む README (ATS 系パックに多い) だと 1 文字でも失敗した瞬間に全文が UTF-8 素通し = 文字化け
     * していた。MS932 を挟む com.portofino.realtrainmodunofficial.util.PackTextDecoder に一本化して解消。
     */
    private static String decodeText(byte[] bytes) {
        return com.portofino.realtrainmodunofficial.util.PackTextDecoder.decodeText(bytes);
    }
}
