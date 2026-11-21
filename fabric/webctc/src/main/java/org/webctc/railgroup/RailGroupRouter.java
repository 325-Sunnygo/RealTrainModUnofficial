package org.webctc.railgroup;

import com.sun.net.httpserver.HttpExchange;
import net.minecraft.server.MinecraftServer;
import org.webctc.HttpUtil;
import org.webctc.railgroup.RailGroupData.RailGroup;
import org.webctc.railgroup.RailGroupData.RailGroupFolder;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * 本家 org.webctc.router.api.RailGroupRouter の移植。
 * GET/POST /api/railgroups、GET/POST /api/railgroups/folders、
 * PUT/DELETE /api/railgroups(/folders)/{uuid}、GET /api/railgroups/state?uuids=a,b,c。
 * (本家の WebSocket 購読はポーリング API に置換。)
 */
public final class RailGroupRouter {

    private RailGroupRouter() {
    }

    public static void handle(HttpExchange exchange, String path, MinecraftServer server) throws IOException {
        if (server == null) {
            HttpUtil.sendJson(exchange, 503, "{\"error\":\"server not ready\"}");
            return;
        }
        String method = HttpUtil.method(exchange);
        String rest = path.substring("/api/railgroups".length()).replaceAll("^/+|/+$", "");

        if (rest.isEmpty()) {
            switch (method) {
                case "GET" -> HttpUtil.sendJson(exchange, 200, RailGroupData.railGroupsJson());
                case "POST" -> {
                    RailGroup railGroup = new RailGroup();
                    RailGroupData.railGroupList.add(railGroup);
                    RailGroupData.markDirty();
                    HttpUtil.sendJson(exchange, 200, RailGroupData.toJson(railGroup));
                }
                default -> HttpUtil.sendJson(exchange, 405, "{\"error\":\"method not allowed\"}");
            }
            return;
        }

        if (rest.equals("state")) {
            String uuids = HttpUtil.queryParam(exchange, "uuids");
            List<String> list = uuids == null || uuids.isBlank()
                    ? List.of() : Arrays.asList(uuids.split(","));
            //在線判定はワールドに触るのでサーバースレッドで実行
            String json = server.submit(() -> RailGroupData.stateJson(list)).join();
            HttpUtil.sendJson(exchange, 200, json);
            return;
        }

        if (rest.equals("folders")) {
            switch (method) {
                case "GET" -> HttpUtil.sendJson(exchange, 200, RailGroupData.foldersJson());
                case "POST" -> {
                    RailGroupFolder folder = new RailGroupFolder();
                    RailGroupData.folderList.add(folder);
                    RailGroupData.markDirty();
                    HttpUtil.sendJson(exchange, 200, RailGroupData.toJson(folder));
                }
                default -> HttpUtil.sendJson(exchange, 405, "{\"error\":\"method not allowed\"}");
            }
            return;
        }

        if (rest.startsWith("folders/")) {
            String uuid = rest.substring("folders/".length());
            RailGroupFolder folder = RailGroupData.findFolder(uuid);
            if (folder == null) {
                HttpUtil.sendJson(exchange, 404, "{\"error\":\"not found\"}");
                return;
            }
            switch (method) {
                case "PUT" -> {
                    RailGroupFolder updated = RailGroupData.parseFolder(HttpUtil.readBody(exchange));
                    if (updated == null) {
                        HttpUtil.sendJson(exchange, 400, "{\"error\":\"bad body\"}");
                        return;
                    }
                    folder.name = updated.name;
                    folder.parentUuid = updated.parentUuid;
                    RailGroupData.markDirty();
                    HttpUtil.sendJson(exchange, 200, RailGroupData.toJson(folder));
                }
                case "DELETE" -> {
                    //本家: 子フォルダ/所属 RailGroup は親フォルダへ付け替え
                    String parentUuid = folder.parentUuid;
                    RailGroupData.folderList.stream()
                            .filter(f -> folder.uuid.equals(f.parentUuid))
                            .forEach(f -> f.parentUuid = parentUuid);
                    RailGroupData.railGroupList.stream()
                            .filter(rg -> folder.uuid.equals(rg.folderUuid))
                            .forEach(rg -> rg.folderUuid = parentUuid);
                    RailGroupData.folderList.remove(folder);
                    RailGroupData.markDirty();
                    HttpUtil.sendJson(exchange, 200, "{\"ok\":true}");
                }
                default -> HttpUtil.sendJson(exchange, 405, "{\"error\":\"method not allowed\"}");
            }
            return;
        }

        ///api/railgroups/{uuid}
        RailGroup railGroup = RailGroupData.findRailGroup(rest);
        if (railGroup == null) {
            HttpUtil.sendJson(exchange, 404, "{\"error\":\"not found\"}");
            return;
        }
        switch (method) {
            case "GET" -> HttpUtil.sendJson(exchange, 200, RailGroupData.toJson(railGroup));
            case "PUT" -> {
                RailGroup updated = RailGroupData.parseRailGroup(HttpUtil.readBody(exchange));
                if (updated == null) {
                    HttpUtil.sendJson(exchange, 400, "{\"error\":\"bad body\"}");
                    return;
                }
                railGroup.updateBy(updated);
                RailGroupData.markDirty();
                HttpUtil.sendJson(exchange, 200, RailGroupData.toJson(railGroup));
            }
            case "DELETE" -> {
                RailGroupData.railGroupList.remove(railGroup);
                RailGroupData.markDirty();
                HttpUtil.sendJson(exchange, 200, "{\"ok\":true}");
            }
            default -> HttpUtil.sendJson(exchange, 405, "{\"error\":\"method not allowed\"}");
        }
    }
}
