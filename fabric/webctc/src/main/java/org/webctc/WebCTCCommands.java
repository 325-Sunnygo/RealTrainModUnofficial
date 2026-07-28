package org.webctc;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.webctc.railgroup.RailGroupData;

/**
 * 本家 WebCTC のコマンド移植。
 * /webctc waypoint create &lt;identifier&gt; &lt;displayName&gt; — 現在地にウェイポイント作成
 * /webctc waypoint delete &lt;identifier&gt;
 * /webctc auth — アカウント連携 URL (ワンタイムキー付き) をチャットに表示
 * /webctc swagger — 本家は Swagger UI。RTMU 版はダッシュボード URL を表示
 * /railgroup setsignal &lt;UUID&gt; &lt;SignalLevel&gt; — RailGroup 配下レールの信号設定
 */
public final class WebCTCCommands {

    private static final Gson GSON = new Gson();

    private WebCTCCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        var dispatcher = event.getDispatcher();

        dispatcher.register(
            Commands.literal("webctc")
                .then(Commands.literal("waypoint")
                    .then(Commands.literal("create")
                        .then(Commands.argument("identifier", StringArgumentType.word())
                            .then(Commands.argument("displayName", StringArgumentType.greedyString())
                                .executes(context -> createWaypoint(context.getSource(),
                                        StringArgumentType.getString(context, "identifier"),
                                        StringArgumentType.getString(context, "displayName"))))))
                    .then(Commands.literal("delete")
                        .then(Commands.argument("identifier", StringArgumentType.word())
                            .executes(context -> deleteWaypoint(context.getSource(),
                                    StringArgumentType.getString(context, "identifier"))))))
                .then(Commands.literal("auth")
                    .executes(context -> auth(context.getSource())))
                .then(Commands.literal("swagger")
                    .executes(context -> {
                        sendUrl(context.getSource(), origin() + "/");
                        return 1;
                    }))
        );

        dispatcher.register(
            Commands.literal("railgroup")
                .then(Commands.literal("setsignal")
                    .then(Commands.argument("uuid", StringArgumentType.word())
                        .then(Commands.argument("signal", IntegerArgumentType.integer(0, 7))
                            .executes(context -> {
                                String uuid = StringArgumentType.getString(context, "uuid");
                                int signal = IntegerArgumentType.getInteger(context, "signal");
                                if (RailGroupData.findRailGroup(uuid) == null) {
                                    context.getSource().sendFailure(
                                            Component.literal("[RailGroup] uuid not found: " + uuid));
                                    return 0;
                                }
                                RailGroupData.setSignal(uuid, signal);
                                context.getSource().sendSuccess(() -> Component.literal(
                                        "[RailGroup] Set signal: " + signal + " to uuid: " + uuid), false);
                                return 1;
                            })))));
    }

    //------------------------------------------------------------ waypoint (本家 CommandWebCTC)

    private static int createWaypoint(CommandSourceStack source, String identifier, String displayName) {
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("[WebCTC] Sorry! This command can only be executed by player."));
            return 0;
        }
        var level = source.getServer().overworld();
        WebCTCSavedData data = WebCTCSavedData.get(level);
        JsonArray waypoints = JsonParser.parseString(data.get("waypoints")).getAsJsonArray();
        //同 identifier は上書き (本家は map なので同キー上書き)
        JsonArray next = new JsonArray();
        for (var el : waypoints) {
            if (!(el.isJsonObject() && identifier.equals(str(el.getAsJsonObject(), "identifier")))) {
                next.add(el);
            }
        }
        JsonObject wp = new JsonObject();
        wp.addProperty("identifier", identifier);
        wp.addProperty("displayName", displayName);
        JsonObject pos = new JsonObject();
        pos.addProperty("x", player.getX());
        pos.addProperty("y", player.getY());
        pos.addProperty("z", player.getZ());
        wp.add("pos", pos);
        next.add(wp);
        data.set("waypoints", GSON.toJson(next));
        source.sendSuccess(() -> Component.literal("Waypoint created."), false);
        return 1;
    }

    private static int deleteWaypoint(CommandSourceStack source, String identifier) {
        var level = source.getServer().overworld();
        WebCTCSavedData data = WebCTCSavedData.get(level);
        JsonArray waypoints = JsonParser.parseString(data.get("waypoints")).getAsJsonArray();
        JsonArray next = new JsonArray();
        boolean found = false;
        for (var el : waypoints) {
            if (el.isJsonObject() && identifier.equals(str(el.getAsJsonObject(), "identifier"))) {
                found = true;
            } else {
                next.add(el);
            }
        }
        if (found) {
            data.set("waypoints", GSON.toJson(next));
            source.sendSuccess(() -> Component.literal("Waypoint deleted."), false);
            return 1;
        }
        source.sendFailure(Component.literal("Waypoint not found."));
        return 0;
    }

    private static String str(JsonObject obj, String field) {
        return obj.has(field) && obj.get(field).isJsonPrimitive() ? obj.get(field).getAsString() : "";
    }

    //------------------------------------------------------------ auth (本家 /webctc auth)

    private static int auth(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("[WebCTC] Sorry! This command can only be executed by player."));
            return 0;
        }
        String key = PlayerSessionManager.createSession(player);
        sendUrl(source, origin() + "/auth/mc-session-login?key=" + key);
        return 1;
    }

    private static void sendUrl(CommandSourceStack source, String url) {
        Component link = Component.literal(url).setStyle(Style.EMPTY
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url)));
        source.sendSuccess(() -> Component.literal("URL: ").append(link), false);
    }

    /** アクセス URL (本家と同じ config/webctc.cfg の access url / access port から組み立て)。 */
    private static String origin() {
        return WebCTCConfig.origin();
    }
}
