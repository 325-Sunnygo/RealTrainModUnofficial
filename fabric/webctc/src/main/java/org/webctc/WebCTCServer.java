package org.webctc;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import jp.ngt.rtm.rail.util.RailMap;
import jp.ngt.rtm.rail.util.RailPosition;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * 本家 WebCTC (org.webctc / Ktor 実装) の HTTP サーバー部の 1.21.1 移植。
 * API のデータ形状は本家 common/types (LargeRailData / FormationData / TrainData / SignalData) に合わせる。
 * フロントは本家 MapView の忠実レプリカ (assets/webctc/html/index.html)。
 */
public final class WebCTCServer {
    private static HttpServer httpServer;
    private static MinecraftServer minecraftServer;

    private WebCTCServer() {
    }

    public static void onServerStarted(ServerStartedEvent event) {
        minecraftServer = event.getServer();
        stop();
        try {
            int port = WebCTCConfig.getPortNumber();
            httpServer = HttpServer.create(new InetSocketAddress(port), 0);
            httpServer.createContext("/", WebCTCServer::handle);
            httpServer.setExecutor(Executors.newCachedThreadPool(r -> {
                Thread thread = new Thread(r, "WebCTC");
                thread.setDaemon(true);
                return thread;
            }));
            httpServer.start();
            WebCTCCore.LOGGER.info("WebCTC server started on http://127.0.0.1:{}/ (access: {})",
                    port, WebCTCConfig.origin());
        } catch (IOException e) {
            WebCTCCore.LOGGER.warn("Failed to start WebCTC server", e);
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        stop();
        minecraftServer = null;
    }

    private static void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
    }

    private static void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if ("/api/trains".equals(path)) {
            send(exchange, 200, "application/json", trainsJson());
        } else if (path.startsWith("/api/trains/") && path.endsWith("/notch")) {
            setTrainNotch(exchange, path);
        } else if (path.startsWith("/api/trains/") && path.endsWith("/state")) {
            setTrainState(exchange, path);
        } else if ("/api/rails".equals(path)) {
            send(exchange, 200, "application/json", railsJson());
        } else if ("/api/formations".equals(path)) {
            send(exchange, 200, "application/json", formationsJson());
        } else if ("/api/signals".equals(path)) {
            send(exchange, 200, "application/json", signalsJson());
        } else if (path.startsWith("/api/signals/")) {
            setSignalAspect(exchange, path);
        } else if ("/api/waypoints".equals(path)) {
            storedJson(exchange, "waypoints");
        } else if (path.startsWith("/api/railgroups")) {
            org.webctc.railgroup.RailGroupRouter.handle(exchange, path, minecraftServer);
        } else if (path.startsWith("/api/tecons")) {
            org.webctc.tecon.TeConRouter.handle(exchange, path, minecraftServer);
        } else if (path.startsWith("/api")) {
            send(exchange, 404, "application/json", "{\"error\":\"not found\"}");
        } else if (path.startsWith("/auth/")) {
            handleAuth(exchange, path);
        } else {
            sendStatic(exchange, path);
        }
    }

    //------------------------------------------------------------ 認証 (本家 AuthRouter)

    /**
     * /auth/mc-session-login?key= — /webctc auth で発行したワンタイムキーを消費して
     * セッション Cookie を発行 (本家 mc-session-login)。
     * /auth/profile — ログイン中プレイヤーの {id, uuid} (本家 PlayerPrincipal)。
     * /auth/logout — セッション破棄。
     */
    private static void handleAuth(HttpExchange exchange, String path) throws IOException {
        if (path.equals("/auth/mc-session-login")) {
            String key = HttpUtil.queryParam(exchange, "key");
            String token = key == null ? null : PlayerSessionManager.useKey(key);
            if (token == null) {
                send(exchange, 401, "text/plain; charset=utf-8", "Login failed");
                return;
            }
            exchange.getResponseHeaders().add("Set-Cookie",
                    "WEBCTC_SESSION=" + token + "; Path=/; HttpOnly; SameSite=Lax");
            exchange.getResponseHeaders().set("Location", "/p/account");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
            return;
        }
        if (path.equals("/auth/profile")) {
            PlayerSessionManager.PlayerData data = sessionOf(exchange);
            if (data == null) {
                send(exchange, 401, "application/json", "{\"error\":\"unauthorized\"}");
                return;
            }
            send(exchange, 200, "application/json",
                    "{\"id\":\"" + escape(data.name()) + "\",\"uuid\":\"" + data.uuid() + "\"}");
            return;
        }
        if (path.equals("/auth/logout")) {
            PlayerSessionManager.logout(sessionToken(exchange));
            exchange.getResponseHeaders().add("Set-Cookie",
                    "WEBCTC_SESSION=; Path=/; Max-Age=0");
            exchange.getResponseHeaders().set("Location", "/");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
            return;
        }
        send(exchange, 404, "text/plain; charset=utf-8", "not found");
    }

    private static String sessionToken(HttpExchange exchange) {
        var cookies = exchange.getRequestHeaders().get("Cookie");
        if (cookies == null) {
            return null;
        }
        for (String header : cookies) {
            for (String cookie : header.split(";")) {
                String[] pair = cookie.trim().split("=", 2);
                if (pair.length == 2 && pair[0].equals("WEBCTC_SESSION")) {
                    return pair[1];
                }
            }
        }
        return null;
    }

    private static PlayerSessionManager.PlayerData sessionOf(HttpExchange exchange) {
        return PlayerSessionManager.getSession(sessionToken(exchange));
    }

    //------------------------------------------------------------ trains / formations

    private static List<EntityTrainBase> allTrains() {
        //旧 TrainEntity ではなく本家系 EntityTrainBase を列挙する
        //(設置される列車は jp.ngt.rtm.entity.train.EntityTrain)
        return StreamSupport.stream(minecraftServer.overworld().getEntities().getAll().spliterator(), false)
            .filter(EntityTrainBase.class::isInstance)
            .map(EntityTrainBase.class::cast)
            .collect(Collectors.toList());
    }

    private static String trainsJson() {
        if (minecraftServer == null || minecraftServer.overworld() == null) {
            return "[]";
        }
        return allTrains().stream()
            .map(WebCTCServer::trainJson)
            .collect(Collectors.joining(",", "[", "]"));
    }

    /** 本家 TrainData 形状。 */
    private static String trainJson(EntityTrainBase train) {
        StringBuilder states = new StringBuilder("[");
        for (int i = 0; i < 16; i++) {
            if (i > 0) {
                states.append(',');
            }
            states.append(train.getTrainStateData(i));
        }
        states.append(']');
        long formationId = train.getFormation() != null ? train.getFormation().id : 0L;
        String driver = driverName(train);
        var dataMap = train.getResourceState().getDataMap();
        return "{"
            + "\"formation\":" + formationId
            + ",\"id\":" + train.getId()
            + ",\"speed\":" + train.getSpeed()
            + ",\"notch\":" + train.getNotch()
            + ",\"modelName\":\"" + escape(train.getModelName()) + "\""
            + ",\"isControlCar\":" + train.isControlCar()
            + ",\"signal\":" + train.getSignal()
            + ",\"driver\":\"" + escape(driver) + "\""
            + ",\"passengers\":[]"
            + ",\"pos\":{\"x\":" + train.getX() + ",\"y\":" + train.getY() + ",\"z\":" + train.getZ() + "}"
            + ",\"trainStateData\":" + states
            + ",\"name\":\"no_name\""
            + ",\"customButton\":[]"
            + ",\"dataMap\":{"
            + "\"ATSAssist_CurrentTP\":\"" + escape(dataMap.getString("ATSAssist_CurrentTP")) + "\""
            + ",\"ATSAssist_SpeedLimit\":\"" + escape(dataMap.getString("ATSAssist_SpeedLimit")) + "\""
            + "}"
            + "}";
    }

    private static String driverName(EntityTrainBase train) {
        var first = train.getFirstPassenger();
        if (first instanceof net.minecraft.world.entity.player.Player player) {
            return player.getGameProfile().getName();
        }
        if (first instanceof jp.ngt.rtm.entity.npc.EntityMotorman) {
            return "Motorman";
        }
        return "";
    }

    /** 本家 FormationData 形状 (制御車を持つ編成のみ)。 */
    private static String formationsJson() {
        if (minecraftServer == null || minecraftServer.overworld() == null) {
            return "[]";
        }
        return allTrains().stream()
            .filter(EntityTrainBase::isControlCar)
            .filter(t -> t.getFormation() != null)
            .map(t -> "{"
                + "\"id\":" + t.getFormation().id
                + ",\"entities\":[]"
                + ",\"controlCar\":" + trainJson(t)
                + ",\"driver\":\"" + escape(driverName(t)) + "\""
                + ",\"direction\":" + t.getTrainDirection()
                + ",\"speed\":" + t.getSpeed()
                + ",\"currentRailMap\":null"
                + "}")
            .collect(Collectors.joining(",", "[", "]"));
    }

    //------------------------------------------------------------ rails / signals

    /**
     * 本家 LargeRailData 形状。RailMap ごとに始点/終点の RailPosition を返す
     * (本家フロントは直線で描画する)。
     */
    private static String railsJson() {
        if (minecraftServer == null || minecraftServer.overworld() == null) {
            return "[]";
        }
        StringBuilder out = new StringBuilder("[");
        boolean firstCore = true;
        for (TileEntityLargeRailCore core : TileEntityLargeRailCore.getLoadedCores(minecraftServer.overworld())) {
            try {
                RailMap[] maps = core.getAllRailMaps();
                if (maps == null || maps.length == 0) {
                    continue;
                }
                StringBuilder railMaps = new StringBuilder("[");
                boolean firstMap = true;
                for (RailMap map : maps) {
                    if (map == null || map.getStartRP() == null || map.getEndRP() == null) {
                        continue;
                    }
                    if (!firstMap) {
                        railMaps.append(',');
                    }
                    firstMap = false;
                    railMaps.append("{\"startRP\":").append(rpJson(map.getStartRP()))
                        .append(",\"endRP\":").append(rpJson(map.getEndRP()))
                        .append(",\"length\":").append(round1(map.getLength()))
                        .append(",\"isNotActive\":false")
                        .append('}');
                }
                railMaps.append(']');
                if (firstMap) {
                    continue;
                }
                var pos = core.getBlockPos();
                if (!firstCore) {
                    out.append(',');
                }
                firstCore = false;
                out.append("{\"pos\":{\"x\":").append(pos.getX())
                    .append(",\"y\":").append(pos.getY())
                    .append(",\"z\":").append(pos.getZ())
                    .append("},\"isTrainOnRail\":").append(core.isTrainOnRail())
                    .append(",\"railMaps\":").append(railMaps)
                    .append(",\"turning\":false}");
            } catch (Exception ignored) {
                //個々のレールの失敗は無視
            }
        }
        return out.append(']').toString();
    }

    private static String rpJson(RailPosition rp) {
        return "{\"posX\":" + round1(rp.posX) + ",\"posY\":" + rp.blockY + ",\"posZ\":" + round1(rp.posZ) + "}";
    }

    private static double round1(double v) {
        return Math.round(v * 10.0D) / 10.0D;
    }

    /**
     * 本家 SignalData 形状 (signalLevel = RTM の信号レベル 0-7)。
     * チャンネル登録の有無に関わらず、ロード済みの信号機 (InstalledObjectBlockEntity の
     * SIGNAL カテゴリ) を全て返す (本家は TileEntitySignal のキャッシュを全件返していた)。
     */
    private static String signalsJson() {
        if (minecraftServer == null || minecraftServer.overworld() == null) {
            return "[]";
        }
        try {
            return com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity
                .getLoadedObjects(minecraftServer.overworld()).stream()
                .filter(com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity::isSignal)
                .map(signal -> {
                    var pos = signal.getBlockPos();
                    int rotation = Math.floorMod(Math.round(signal.getYaw()), 360);
                    return "{\"pos\":{\"x\":" + pos.getX()
                        + ",\"y\":" + pos.getY()
                        + ",\"z\":" + pos.getZ() + "}"
                        + ",\"rotation\":" + rotation
                        + ",\"signalLevel\":" + Math.max(0, signal.getSignal())
                        + ",\"blockDirection\":0"
                        + ",\"modelName\":\"\""
                        + ",\"channel\":" + signal.getSignalChannel()
                        + "}";
                })
                .collect(Collectors.joining(",", "[", "]"));
        } catch (Exception e) {
            return "[]";
        }
    }

    /** 信号現示の手動設定: POST /api/signals/&lt;channel&gt; {"aspect": 0-6}。 */
    private static void setSignalAspect(HttpExchange exchange, String path) throws IOException {
        if (minecraftServer == null || minecraftServer.overworld() == null) {
            send(exchange, 503, "application/json", "{\"error\":\"server not ready\"}");
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod()) && !"PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "application/json", "{\"error\":\"method not allowed\"}");
            return;
        }
        String[] parts = path.split("/");
        int channel;
        try {
            channel = Integer.parseInt(parts[3]);
        } catch (Exception e) {
            send(exchange, 400, "application/json", "{\"error\":\"bad channel\"}");
            return;
        }
        int aspectId = parseBodyInt(exchange, "aspect", -1);
        if (aspectId < 0 || aspectId > 6) {
            send(exchange, 400, "application/json", "{\"error\":\"bad aspect\"}");
            return;
        }
        var aspect = com.portofino.realtrainmodunofficial.signal.SignalAspect.byId(aspectId);
        minecraftServer.execute(() -> {
            var data = com.portofino.realtrainmodunofficial.signal.SignalNetworkSavedData.get(minecraftServer.overworld());
            data.setAspect(minecraftServer, channel, aspect);
        });
        send(exchange, 200, "application/json", "{\"ok\":true}");
    }

    //------------------------------------------------------------ train control

    private static void setTrainNotch(HttpExchange exchange, String path) throws IOException {
        EntityTrainBase train = trainFromPath(path);
        if (train == null) {
            send(exchange, 404, "application/json", "{\"error\":\"train not found\"}");
            return;
        }
        int notch = parseBodyInt(exchange, "notch", 0);
        minecraftServer.execute(() -> train.setNotch(notch));
        send(exchange, 200, "application/json", trainJson(train));
    }

    private static void setTrainState(HttpExchange exchange, String path) throws IOException {
        EntityTrainBase train = trainFromPath(path);
        if (train == null) {
            send(exchange, 404, "application/json", "{\"error\":\"train not found\"}");
            return;
        }
        String body = readBody(exchange);
        int state = parseJsonInt(body, "state", -1);
        int value = parseJsonInt(body, "value", 0);
        minecraftServer.execute(() -> {
            if (state >= 0 && state < 16) {
                train.setTrainStateData(state, (byte) value);
            }
        });
        send(exchange, 200, "application/json", trainJson(train));
    }

    private static EntityTrainBase trainFromPath(String path) {
        if (minecraftServer == null || minecraftServer.overworld() == null) {
            return null;
        }
        String[] parts = path.split("/");
        if (parts.length < 4) {
            return null;
        }
        int id;
        try {
            id = Integer.parseInt(parts[3]);
        } catch (NumberFormatException e) {
            return null;
        }
        return allTrains().stream()
            .filter(train -> train.getId() == id)
            .findFirst()
            .orElse(null);
    }

    //------------------------------------------------------------ storage (waypoints / railgroups / tecons)

    private static void storedJson(HttpExchange exchange, String key) throws IOException {
        if (minecraftServer == null || minecraftServer.overworld() == null) {
            send(exchange, 503, "application/json", "{\"error\":\"server not ready\"}");
            return;
        }
        WebCTCSavedData data = WebCTCSavedData.get(minecraftServer.overworld());
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 200, "application/json", data.get(key));
            return;
        }
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod()) || "PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
            data.set(key, readBody(exchange));
            send(exchange, 200, "application/json", data.get(key));
            return;
        }
        send(exchange, 405, "application/json", "{\"error\":\"method not allowed\"}");
    }

    //------------------------------------------------------------ static / helpers

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        sendBytes(exchange, status, contentType, bytes);
    }

    private static void sendStatic(HttpExchange exchange, String path) throws IOException {
        String safePath = path == null || path.equals("/") || path.isBlank() ? "/index.html" : path;
        if (safePath.contains("..")) {
            send(exchange, 400, "text/plain; charset=utf-8", "bad path");
            return;
        }
        //本家 SPA のページルーティング: railgroup 管理と運行盤は専用ページ
        String normalized = safePath.replaceAll("/+$", "");
        if (normalized.equals("/p/railgroup") || normalized.equals("/p/railgroups")) {
            safePath = "/railgroup.html";
        } else if (normalized.equals("/p/tecons") || normalized.startsWith("/p/tecons/")) {
            safePath = "/tecon.html";
        } else if (normalized.equals("/p/account")) {
            safePath = "/account.html";
        }
        String resourcePath = "/assets/webctc/html" + safePath;
        try (InputStream input = WebCTCServer.class.getResourceAsStream(resourcePath)) {
            if (input != null) {
                sendBytes(exchange, 200, contentType(safePath), input.readAllBytes());
                return;
            }
        }
        //SPA ルーティング: 拡張子なしは index.html へ
        try (InputStream input = WebCTCServer.class.getResourceAsStream("/assets/webctc/html/index.html")) {
            if (input != null) {
                sendBytes(exchange, 200, "text/html; charset=utf-8", input.readAllBytes());
                return;
            }
        }
        send(exchange, 404, "text/plain; charset=utf-8", "not found");
    }

    private static void sendBytes(HttpExchange exchange, int status, String contentType, byte[] bytes) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static String contentType(String path) {
        if (path.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        if (path.endsWith(".json")) return "application/json; charset=utf-8";
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream input = exchange.getRequestBody()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int parseBodyInt(HttpExchange exchange, String key, int fallback) throws IOException {
        return parseJsonInt(readBody(exchange), key, fallback);
    }

    private static int parseJsonInt(String body, String key, int fallback) {
        return (int) parseJsonDouble(body, key, fallback);
    }

    private static double parseJsonDouble(String body, String key, double fallback) {
        String needle = "\"" + key + "\"";
        int index = body.indexOf(needle);
        if (index < 0) {
            return fallback;
        }
        int colon = body.indexOf(':', index + needle.length());
        if (colon < 0) {
            return fallback;
        }
        int end = colon + 1;
        while (end < body.length() && "-0123456789.".indexOf(body.charAt(end)) < 0) {
            end++;
        }
        int start = end;
        while (end < body.length() && "-0123456789.".indexOf(body.charAt(end)) >= 0) {
            end++;
        }
        try {
            return Double.parseDouble(body.substring(start, end));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}
