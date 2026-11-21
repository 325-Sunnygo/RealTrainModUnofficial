package org.webctc.tecon;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import org.webctc.railgroup.RailGroupData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本家 org.webctc.tecon.TeConRuntimeManager の移植 (運行盤てこの実行系)。
 * てこ (TeConLever) の L/R 操作 → Action の operations
 * (Reserve=進路予約 / RedStone=RS出力 / JavaScript=本家同様スタブ) を実行し、
 * 復位 (RETURN) で逆順に戻す。毎 tick 予約状態を監視して失効したてこを落とす。
 *
 * <p>ワイヤ形状 (parts JSON):
 * lever: {type:"TeConLever", id, left/right: {kind:"disabled"|"direct"|"select", action|actions}}
 * action: {routeId?, name, requireNoTrainToCancel, operations:[
 *   {type:"Reserve", chain:{chain:[uuid...], key}, key, reservedRedStonePosSet:[{x,y,z}], lockedRedStonePosSet:[...]},
 *   {type:"RedStone", redStonePosSet:[{x,y,z}]},
 *   {type:"JavaScript", script}]}
 */
public final class TeConRuntimeManager {

    /** teConUuid → (leverId → ActiveTeConAction)。 */
    private static final Map<String, Map<String, ActiveAction>> activeActionMap = new ConcurrentHashMap<>();

    private static MinecraftServer server;

    private TeConRuntimeManager() {
    }

    public static void setServer(MinecraftServer mcServer) {
        server = mcServer;
    }

    public static void clear() {
        activeActionMap.clear();
    }

    private record ActiveAction(String teConUuid, String leverId, String side,
                                String routeId, String actionName) {
    }

    //------------------------------------------------------------ 状態

    /** 本家 getRuntimeState — {leverStates:[{leverId, activeSide, routeId, actionName, cancelable}]}。 */
    public static String runtimeStateJson(JsonObject teCon) {
        String uuid = teCon.get("uuid").getAsString();
        Map<String, ActiveAction> levers = activeActionMap.get(uuid);
        StringBuilder out = new StringBuilder("{\"leverStates\":[");
        if (levers != null) {
            boolean first = true;
            List<ActiveAction> sorted = new ArrayList<>(levers.values());
            sorted.sort((a, b) -> a.leverId().compareTo(b.leverId()));
            for (ActiveAction active : sorted) {
                JsonObject action = findAction(teCon, active.leverId(), active.side(), active.routeId());
                boolean cancelable = action != null && canCancel(action);
                if (!first) {
                    out.append(',');
                }
                first = false;
                out.append("{\"leverId\":\"").append(esc(active.leverId())).append('"')
                        .append(",\"activeSide\":\"").append(active.side()).append('"')
                        .append(",\"routeId\":")
                        .append(active.routeId() == null ? "null" : "\"" + esc(active.routeId()) + "\"")
                        .append(",\"actionName\":\"").append(esc(active.actionName())).append('"')
                        .append(",\"cancelable\":").append(cancelable)
                        .append('}');
            }
        }
        return out.append("]}").toString();
    }

    //------------------------------------------------------------ 操作 (本家 operate)

    /** @return レスポンス JSON {ok, message, runtimeState}。 */
    public static String operate(JsonObject teCon, String leverId, String side,
                                 String routeId, String actionType) {
        JsonObject lever = findLever(teCon, leverId);
        if (lever == null) {
            return response(teCon, false, "Lever not found");
        }
        String uuid = teCon.get("uuid").getAsString();
        Map<String, ActiveAction> activeMap = activeActionMap.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        ActiveAction activeAction = activeMap.get(leverId);

        if ("REQUEST".equals(actionType)) {
            return handleRequest(teCon, lever, side, routeId, activeAction);
        } else if ("RETURN".equals(actionType)) {
            return handleReturn(teCon, lever, side, activeAction);
        }
        return response(teCon, false, "Bad actionType");
    }

    private static String handleRequest(JsonObject teCon, JsonObject lever, String side,
                                        String routeId, ActiveAction activeAction) {
        if (activeAction != null) {
            return response(teCon, false, "Lever already active");
        }
        JsonObject action = findActionInSide(sideConfig(lever, side), routeId);
        if (action == null) {
            return response(teCon, false, "Action not found");
        }
        List<JsonObject> performed = new ArrayList<>();
        try {
            for (JsonElement el : operations(action)) {
                JsonObject operation = el.getAsJsonObject();
                if (!executeOperation(teCon, lever, side, action, operation)) {
                    rollback(teCon, lever, side, action, performed);
                    return response(teCon, false, "Operation failed");
                }
                performed.add(operation);
            }
        } catch (Throwable t) {
            rollback(teCon, lever, side, action, performed);
            return response(teCon, false, "Operation failed");
        }
        String uuid = teCon.get("uuid").getAsString();
        String actionRouteId = optString(action, "routeId");
        activeActionMap.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                .put(str(lever, "id"), new ActiveAction(uuid, str(lever, "id"), side,
                        actionRouteId, optString(action, "name") == null ? "" : optString(action, "name")));
        return response(teCon, true, "OK");
    }

    private static String handleReturn(JsonObject teCon, JsonObject lever, String side,
                                       ActiveAction activeAction) {
        if (activeAction == null || !activeAction.side().equals(side)) {
            return response(teCon, false, "Active side not found");
        }
        JsonObject action = findActionInSide(sideConfig(lever, side), activeAction.routeId());
        if (action == null) {
            return response(teCon, false, "Action not found");
        }
        if (!canCancel(action)) {
            return response(teCon, false, "Action is locked");
        }
        try {
            List<JsonElement> ops = new ArrayList<>();
            operations(action).forEach(ops::add);
            for (int i = ops.size() - 1; i >= 0; i--) {
                executeReverseOperation(teCon, lever, side, action, ops.get(i).getAsJsonObject());
            }
        } catch (Throwable t) {
            return response(teCon, false, "Return failed");
        }
        String uuid = teCon.get("uuid").getAsString();
        Map<String, ActiveAction> levers = activeActionMap.get(uuid);
        if (levers != null) {
            levers.remove(str(lever, "id"));
            if (levers.isEmpty()) {
                activeActionMap.remove(uuid);
            }
        }
        return response(teCon, true, "OK");
    }

    private static void rollback(JsonObject teCon, JsonObject lever, String side,
                                 JsonObject action, List<JsonObject> performed) {
        for (int i = performed.size() - 1; i >= 0; i--) {
            try {
                executeReverseOperation(teCon, lever, side, action, performed.get(i));
            } catch (Throwable ignored) {
            }
        }
    }

    //------------------------------------------------------------ operations

    private static boolean executeOperation(JsonObject teCon, JsonObject lever, String side,
                                            JsonObject action, JsonObject operation) {
        String type = str(operation, "type");
        switch (type) {
            case "Reserve": {
                String[] uuids = chainUuids(operation);
                String key = resolveKey(teCon, lever, side, action, operation);
                boolean ok = RailGroupData.reserve(uuids, key);
                updateReserveRedStone(operation, key);
                return ok;
            }
            case "RedStone":
                setRedStone(posSet(operation, "redStonePosSet"), true);
                return true;
            case "JavaScript":
                //本家もスクリプト実行は無効化して true を返す
                return true;
            default:
                return false;
        }
    }

    private static void executeReverseOperation(JsonObject teCon, JsonObject lever, String side,
                                                JsonObject action, JsonObject operation) {
        String type = str(operation, "type");
        switch (type) {
            case "Reserve": {
                RailGroupData.release(chainUuids(operation), resolveKey(teCon, lever, side, action, operation));
                clearReserveRedStone(operation);
                break;
            }
            case "RedStone":
                setRedStone(posSet(operation, "redStonePosSet"), false);
                break;
            case "JavaScript":
            default:
                break;
        }
    }

    //------------------------------------------------------------ tick (本家 tick)

    public static void tick() {
        activeActionMap.entrySet().removeIf(entry -> {
            JsonObject teCon = TeConData.teConList.get(entry.getKey());
            if (teCon == null) {
                return true;
            }
            entry.getValue().entrySet().removeIf(leverEntry -> {
                ActiveAction active = leverEntry.getValue();
                JsonObject action = findAction(teCon, active.leverId(), active.side(), active.routeId());
                JsonObject lever = findLever(teCon, active.leverId());
                if (action == null || lever == null || !isActionStillActive(teCon, lever, active, action)) {
                    if (action != null) {
                        deactivateRedStone(action);
                    }
                    return true;
                }
                updateActiveReserveRedStone(teCon, lever, active, action);
                return false;
            });
            return entry.getValue().isEmpty();
        });
    }

    private static boolean isActionStillActive(JsonObject teCon, JsonObject lever,
                                               ActiveAction active, JsonObject action) {
        List<JsonObject> reserves = reserveOperations(action);
        if (reserves.isEmpty()) {
            return true;
        }
        for (JsonObject reserve : reserves) {
            String[] uuids = chainUuids(reserve);
            String key = resolveKey(teCon, lever, active.side(), action, reserve);
            if (!(RailGroupData.isLocked(uuids, key) || RailGroupData.isReserved(uuids, key))) {
                return false;
            }
        }
        return true;
    }

    private static void deactivateRedStone(JsonObject action) {
        for (JsonElement el : operations(action)) {
            JsonObject operation = el.getAsJsonObject();
            String type = str(operation, "type");
            if ("Reserve".equals(type)) {
                clearReserveRedStone(operation);
            } else if ("RedStone".equals(type)) {
                setRedStone(posSet(operation, "redStonePosSet"), false);
            }
        }
    }

    private static void updateActiveReserveRedStone(JsonObject teCon, JsonObject lever,
                                                    ActiveAction active, JsonObject action) {
        for (JsonObject reserve : reserveOperations(action)) {
            updateReserveRedStone(reserve, resolveKey(teCon, lever, active.side(), action, reserve));
        }
    }

    private static void updateReserveRedStone(JsonObject operation, String key) {
        String[] uuids = chainUuids(operation);
        boolean hasRailGroups = uuids.length > 0;
        setRedStone(posSet(operation, "reservedRedStonePosSet"),
                hasRailGroups && RailGroupData.isReserved(uuids, key));
        setRedStone(posSet(operation, "lockedRedStonePosSet"),
                hasRailGroups && RailGroupData.isLocked(uuids, key));
    }

    private static void clearReserveRedStone(JsonObject operation) {
        setRedStone(posSet(operation, "reservedRedStonePosSet"), false);
        setRedStone(posSet(operation, "lockedRedStonePosSet"), false);
    }

    /** 本家 canCancel — requireNoTrainToCancel の在線チェック。 */
    private static boolean canCancel(JsonObject action) {
        if (!action.has("requireNoTrainToCancel") || !action.get("requireNoTrainToCancel").getAsBoolean()) {
            return true;
        }
        for (JsonObject reserve : reserveOperations(action)) {
            for (String uuid : chainUuids(reserve)) {
                if (RailGroupData.isTrainOnRail(uuid)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void setRedStone(List<BlockPos> posList, boolean active) {
        if (server == null) {
            return;
        }
        ServerLevel level = server.overworld();
        var block = active ? Blocks.REDSTONE_BLOCK : Blocks.RED_STAINED_GLASS;
        for (BlockPos pos : posList) {
            level.setBlock(pos, block.defaultBlockState(), 3);
        }
    }

    //------------------------------------------------------------ JSON ヘルパー

    private static JsonObject findLever(JsonObject teCon, String leverId) {
        for (JsonElement el : teCon.getAsJsonArray("parts")) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject part = el.getAsJsonObject();
            if ("TeConLever".equals(str(part, "type")) && leverId.equals(str(part, "id"))) {
                return part;
            }
        }
        return null;
    }

    private static JsonObject sideConfig(JsonObject lever, String side) {
        String field = "L".equals(side) ? "left" : "right";
        return lever.has(field) && lever.get(field).isJsonObject()
                ? lever.getAsJsonObject(field) : null;
    }

    /** 本家 findAction — direct は routeId==null のみ、select は routeId 一致。 */
    private static JsonObject findActionInSide(JsonObject config, String routeId) {
        if (config == null) {
            return null;
        }
        String kind = str(config, "kind");
        if ("direct".equals(kind)) {
            return routeId == null && config.has("action") ? config.getAsJsonObject("action") : null;
        }
        if ("select".equals(kind) && config.has("actions")) {
            for (JsonElement el : config.getAsJsonArray("actions")) {
                JsonObject action = el.getAsJsonObject();
                String actionRoute = optString(action, "routeId");
                if (actionRoute != null && actionRoute.equals(routeId)) {
                    return action;
                }
            }
        }
        return null;
    }

    private static JsonObject findAction(JsonObject teCon, String leverId, String side, String routeId) {
        JsonObject lever = findLever(teCon, leverId);
        return lever == null ? null : findActionInSide(sideConfig(lever, side), routeId);
    }

    /** 本家 resolveKey — key 未指定は "teconUuid:leverId:side:routeId"。 */
    private static String resolveKey(JsonObject teCon, JsonObject lever, String side,
                                     JsonObject action, JsonObject operation) {
        String key = optString(operation, "key");
        if (key != null && !key.isBlank()) {
            return key;
        }
        String routeId = optString(action, "routeId");
        return teCon.get("uuid").getAsString() + ":" + str(lever, "id") + ":" + side + ":"
                + (routeId == null ? "" : routeId);
    }

    private static JsonArray operations(JsonObject action) {
        return action.has("operations") && action.get("operations").isJsonArray()
                ? action.getAsJsonArray("operations") : new JsonArray();
    }

    private static List<JsonObject> reserveOperations(JsonObject action) {
        List<JsonObject> out = new ArrayList<>();
        for (JsonElement el : operations(action)) {
            if (el.isJsonObject() && "Reserve".equals(str(el.getAsJsonObject(), "type"))) {
                out.add(el.getAsJsonObject());
            }
        }
        return out;
    }

    private static String[] chainUuids(JsonObject operation) {
        List<String> out = new ArrayList<>();
        if (operation.has("chain") && operation.get("chain").isJsonObject()) {
            JsonObject chain = operation.getAsJsonObject("chain");
            if (chain.has("chain") && chain.get("chain").isJsonArray()) {
                for (JsonElement el : chain.getAsJsonArray("chain")) {
                    out.add(el.getAsString());
                }
            }
        }
        return out.toArray(new String[0]);
    }

    private static List<BlockPos> posSet(JsonObject operation, String field) {
        List<BlockPos> out = new ArrayList<>();
        if (operation.has(field) && operation.get(field).isJsonArray()) {
            for (JsonElement el : operation.getAsJsonArray(field)) {
                JsonObject pos = el.getAsJsonObject();
                out.add(new BlockPos(pos.get("x").getAsInt(), pos.get("y").getAsInt(), pos.get("z").getAsInt()));
            }
        }
        return out;
    }

    private static String str(JsonObject obj, String field) {
        return obj.has(field) && obj.get(field).isJsonPrimitive() ? obj.get(field).getAsString() : "";
    }

    private static String optString(JsonObject obj, String field) {
        return obj.has(field) && obj.get(field).isJsonPrimitive() ? obj.get(field).getAsString() : null;
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String response(JsonObject teCon, boolean ok, String message) {
        return "{\"ok\":" + ok + ",\"message\":\"" + esc(message) + "\",\"runtimeState\":"
                + runtimeStateJson(teCon) + "}";
    }
}
