package org.webctc.tecon;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.server.MinecraftServer;
import org.webctc.HttpUtil;

import java.io.IOException;

/**
 * 本家 org.webctc.router.api.TeConRouter の移植。
 * GET/POST /api/tecons、GET/PUT/DELETE /api/tecons/{uuid}、
 * GET /api/tecons/{uuid}/runtime、POST /api/tecons/{uuid}/operate。
 */
public final class TeConRouter {

    private TeConRouter() {
    }

    public static void handle(HttpExchange exchange, String path, MinecraftServer server) throws IOException {
        if (server == null) {
            HttpUtil.sendJson(exchange, 503, "{\"error\":\"server not ready\"}");
            return;
        }
        String method = HttpUtil.method(exchange);
        String rest = path.substring("/api/tecons".length()).replaceAll("^/+|/+$", "");

        if (rest.isEmpty()) {
            switch (method) {
                case "GET" -> HttpUtil.sendJson(exchange, 200, TeConData.listJson());
                case "POST" -> {
                    JsonObject teCon = TeConData.create();
                    TeConData.markDirty();
                    HttpUtil.sendJson(exchange, 200, TeConData.toJson(teCon));
                }
                default -> HttpUtil.sendJson(exchange, 405, "{\"error\":\"method not allowed\"}");
            }
            return;
        }

        String uuid = rest.contains("/") ? rest.substring(0, rest.indexOf('/')) : rest;
        String sub = rest.contains("/") ? rest.substring(rest.indexOf('/') + 1) : "";
        JsonObject teCon = TeConData.teConList.get(uuid);
        if (teCon == null) {
            HttpUtil.sendJson(exchange, 404, "{\"error\":\"not found\"}");
            return;
        }

        if (sub.equals("runtime")) {
            HttpUtil.sendJson(exchange, 200, TeConRuntimeManager.runtimeStateJson(teCon));
            return;
        }

        if (sub.equals("operate")) {
            if (!"POST".equals(method)) {
                HttpUtil.sendJson(exchange, 405, "{\"error\":\"method not allowed\"}");
                return;
            }
            JsonObject request = HttpUtil.readJsonObject(exchange);
            if (request == null || !request.has("leverId") || !request.has("side")
                    || !request.has("actionType")) {
                HttpUtil.sendJson(exchange, 400, "{\"error\":\"bad body\"}");
                return;
            }
            String leverId = request.get("leverId").getAsString();
            String side = request.get("side").getAsString();
            String routeId = request.has("routeId") && !request.get("routeId").isJsonNull()
                    ? request.get("routeId").getAsString() : null;
            String actionType = request.get("actionType").getAsString();
            //予約/RS 出力はワールドに触るのでサーバースレッドで実行 (本家はサーバー内 Ktor)
            String response = server.submit(() ->
                    TeConRuntimeManager.operate(teCon, leverId, side, routeId, actionType)).join();
            boolean ok = response.startsWith("{\"ok\":true");
            HttpUtil.sendJson(exchange, ok ? 200 : 409, response);
            return;
        }

        switch (method) {
            case "GET" -> HttpUtil.sendJson(exchange, 200, TeConData.toJson(teCon));
            case "PUT" -> {
                JsonObject updated = HttpUtil.readJsonObject(exchange);
                if (updated == null) {
                    HttpUtil.sendJson(exchange, 400, "{\"error\":\"bad body\"}");
                    return;
                }
                TeConData.updateBy(teCon, updated);
                TeConData.markDirty();
                HttpUtil.sendJson(exchange, 200, TeConData.toJson(teCon));
            }
            case "DELETE" -> {
                TeConData.delete(uuid);
                TeConData.markDirty();
                HttpUtil.sendJson(exchange, 200, "{\"ok\":true}");
            }
            default -> HttpUtil.sendJson(exchange, 405, "{\"error\":\"method not allowed\"}");
        }
    }
}
