package org.webctc;

import net.neoforged.fml.loading.FMLPaths;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 本家 org.webctc.WebCTCConfig の移植。config/webctc.cfg で設定する (項目は本家と同じ):
 * <pre>
 *   port number = 8080     … WebCTC が使うポート番号
 *   access url  =          … 例 http(s)://example.com。空なら自動 (グローバルIP / 127.0.0.1)
 *   access port = 0        … 0 なら port number をそのまま表示、それ以外はこの番号
 * </pre>
 * access url / access port は /webctc auth 等でチャットに表示するリンクの組み立てに使う。
 * (旧 -Dwebctc.port / -Dwebctc.url は指定されていれば設定より優先。)
 */
public final class WebCTCConfig {

    private static final String FILE_NAME = "webctc.cfg";

    public static int portNumber = 8080;
    public static String accessUrl = "";
    public static int accessPort = 0;

    /** 専用サーバーかどうか (サーバー起動時に設定)。シングル/LAN ホストなら localhost を使う。 */
    private static boolean dedicatedServer;

    private static String detectedIp;
    private static boolean loaded;

    public static void setDedicatedServer(boolean dedicated) {
        dedicatedServer = dedicated;
    }

    private WebCTCConfig() {
    }

    /** config/webctc.cfg を読む (無ければ雛形を生成)。 */
    public static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path file = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
        try {
            if (Files.exists(file)) {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    int eq = trimmed.indexOf('=');
                    if (eq < 0) {
                        continue;
                    }
                    String key = trimmed.substring(0, eq).trim();
                    String value = trimmed.substring(eq + 1).trim();
                    switch (key) {
                        case "port number" -> portNumber = parseInt(value, 8080);
                        case "access url" -> accessUrl = value;
                        case "access port" -> accessPort = parseInt(value, 0);
                        default -> {
                        }
                    }
                }
            } else {
                List<String> lines = new ArrayList<>();
                lines.add("# WebCTC 設定 (本家 WebCTCConfig と同じ項目)");
                lines.add("");
                lines.add("# Port number used by WebCTC");
                lines.add("port number = 8080");
                lines.add("");
                lines.add("# eg. http(s)://example.com. if null, display your machine GLOBAL IP address.");
                lines.add("access url = ");
                lines.add("");
                lines.add("# if 0, display webctc port number, else display this number");
                lines.add("access port = 0");
                Files.write(file, lines);
            }
        } catch (IOException e) {
            WebCTCCore.LOGGER.warn("Failed to load {}", FILE_NAME, e);
        }
        //旧 system property は指定されていれば優先 (後方互換)
        Integer sysPort = Integer.getInteger("webctc.port");
        if (sysPort != null) {
            portNumber = sysPort;
        }
        String sysUrl = System.getProperty("webctc.url");
        if (sysUrl != null && !sysUrl.isBlank()) {
            accessUrl = sysUrl;
        }
    }

    /** サーバーの待受ポート。 */
    public static int getPortNumber() {
        load();
        return portNumber;
    }

    /**
     * チャットに表示するリンクの origin:
     * accessUrl が空なら自動 — シングル/LAN ホストは localhost (ブラウザは同じ PC)、
     * 専用サーバーはグローバル IP (取得失敗時 127.0.0.1)。
     * ポートは accessPort (0 なら portNumber)、80/443 は省略。
     */
    public static String origin() {
        load();
        String base = accessUrl;
        if (base == null || base.isBlank()) {
            base = dedicatedServer ? "http://" + detectGlobalIp() : "http://localhost";
        }
        boolean isHttps = base.startsWith("https://");
        int port = accessPort == 0 ? portNumber : accessPort;
        if ((port == 80 && !isHttps) || (port == 443 && isHttps)) {
            return base;
        }
        return base + ":" + port;
    }

    /** 本家 init: checkip.amazonaws.com でグローバル IP を引く (失敗したら 127.0.0.1)。 */
    private static synchronized String detectGlobalIp() {
        if (detectedIp != null) {
            return detectedIp;
        }
        try {
            URLConnection connection = URI.create("http://checkip.amazonaws.com").toURL().openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                detectedIp = line == null || line.isBlank() ? "127.0.0.1" : line.trim();
            }
        } catch (Exception e) {
            detectedIp = "127.0.0.1";
        }
        return detectedIp;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
