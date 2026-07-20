package com.portofino.realtrainmodunofficial.online;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * RTMU のオンライン連携 (クライアント専用・起動時に1回、バックグラウンドで実行)。
 *
 * <ul>
 *   <li><b>アップデート通知:</b> GitHub の最新リリースと現在のバージョンを比較し、
 *       古ければタイトル画面にお知らせを出す ({@link OnlineClientHooks})。</li>
 *   <li><b>BAN:</b> RTMU 公式サイト (借りているサーバー) の {@code ban.txt} を取得し、
 *       自分の Minecraft ユーザー名が載っていたら BAN 画面から先へ進めなくする。
 *       リストはサーバー側のテキストを編集するだけで追加/削除できる (mod の更新不要)。</li>
 * </ul>
 *
 * <p>どちらも失敗時は安全側 (通知なし / BAN なし) に倒す。サーバーが落ちていても
 * ゲームは普通に遊べる。
 */
public final class RtmuOnlineServices {

    private RtmuOnlineServices() {
    }

    /** GitHub リポジトリ (最新リリースの取得先)。 */
    private static final String GITHUB_LATEST_API =
            "https://api.github.com/repos/325-Sunnygo/RealTrainModUnofficial/releases/latest";

    /**
     * BAN リストの URL。RTMU 公式サイト (rtmu.net) 上の ban.txt。
     * 1 行に 1 ユーザー名 (大文字小文字は無視、# で始まる行はコメント)。
     * サーバー上のテキストを編集するだけで BAN の追加/削除ができる (mod 更新不要)。
     */
    private static final String BAN_LIST_URL = "https://rtmu.net/ban.txt";

    //--- 結果 (バックグラウンドスレッドが書き、描画/イベントスレッドが読む) ---
    private static volatile String updateLatestVersion; //新しいバージョンがある時のみ非null
    private static volatile String updateUrl = "https://github.com/325-Sunnygo/RealTrainModUnofficial/releases";
    private static volatile boolean banned;
    private static volatile boolean started;

    /** クライアントセットアップから 1 回呼ぶ。ネットワークはバックグラウンドで行う。 */
    public static void init() {
        if (started) {
            return;
        }
        started = true;
        Thread t = new Thread(() -> {
            checkUpdate();
            checkBan();
        }, "RTMU-Online-Check");
        t.setDaemon(true);
        t.start();
    }

    /** 新しいバージョンがあるか。あればバージョン文字列、なければ null。 */
    public static String getUpdateLatestVersion() {
        return updateLatestVersion;
    }

    public static String getUpdateUrl() {
        return updateUrl;
    }

    /** この Minecraft ユーザーが BAN されているか。 */
    public static boolean isBanned() {
        return banned;
    }

    //------------------------------------------------------------------
    private static HttpClient newClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    private static void checkUpdate() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(GITHUB_LATEST_API))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "RealTrainModUnofficial-UpdateCheck")
                    .GET().build();
            HttpResponse<String> res = newClient().send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                return;
            }
            JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
            String tag = json.has("tag_name") ? json.get("tag_name").getAsString() : "";
            if (json.has("html_url")) {
                updateUrl = json.get("html_url").getAsString();
            }
            String latest = normalizeVersion(tag);
            String current = normalizeVersion(currentModVersion());
            if (!latest.isEmpty() && !current.isEmpty() && compareVersions(latest, current) > 0) {
                updateLatestVersion = latest;
            } else {
            }
        } catch (Exception e) {
        }
    }

    private static String currentModVersion() {
        try {
            return ModList.get().getModContainerById(RealTrainModUnofficial.MODID)
                    .map(c -> c.getModInfo().getVersion().toString()).orElse("");
        } catch (Exception e) {
            return "";
        }
    }

    /** 先頭の v や空白を除き、数字とドット以外で打ち切る ("v1.0.7-beta" → "1.0.7")。 */
    private static String normalizeVersion(String v) {
        if (v == null) {
            return "";
        }
        String s = v.trim();
        if (s.startsWith("v") || s.startsWith("V")) {
            s = s.substring(1);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= '0' && c <= '9') || c == '.') {
                sb.append(c);
            } else {
                break;
            }
        }
        return sb.toString();
    }

    /** "1.0.7" 形式の数値セグメント比較。a>b なら正。 */
    private static int compareVersions(String a, String b) {
        String[] as = a.split("\\.");
        String[] bs = b.split("\\.");
        int n = Math.max(as.length, bs.length);
        for (int i = 0; i < n; i++) {
            int ai = i < as.length ? parseIntSafe(as[i]) : 0;
            int bi = i < bs.length ? parseIntSafe(bs[i]) : 0;
            if (ai != bi) {
                return Integer.compare(ai, bi);
            }
        }
        return 0;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    //------------------------------------------------------------------
    private static void checkBan() {
        //URL が未設定 (プレースホルダのまま) なら何もしない。
        if (BAN_LIST_URL.contains("YOUR-SERVER")) {
            return;
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(BAN_LIST_URL))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "RealTrainModUnofficial-BanCheck")
                    .GET().build();
            HttpResponse<String> res = newClient().send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                return;
            }
            Set<String> names = new HashSet<>();
            for (String line : res.body().split("\n")) {
                String s = line.trim();
                if (s.isEmpty() || s.startsWith("#")) {
                    continue;
                }
                names.add(s.toLowerCase(Locale.ROOT));
            }
            String me = Minecraft.getInstance().getUser().getName();
            if (me != null && names.contains(me.toLowerCase(Locale.ROOT))) {
                banned = true;
                RealTrainModUnofficial.LOGGER.warn("[Online] user is banned from RTMU");
            }
        } catch (Exception e) {
            //サーバーが落ちている/オフライン → 安全側 (BAN しない)。
        }
    }
}
