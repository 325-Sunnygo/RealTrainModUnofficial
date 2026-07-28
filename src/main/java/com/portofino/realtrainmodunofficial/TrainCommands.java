package com.portofino.realtrainmodunofficial;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.portofino.realtrainmodunofficial.entity.TrainBogieEntity;
import com.portofino.realtrainmodunofficial.entity.TrainEntity;
import com.portofino.realtrainmodunofficial.block.RailCollisionBlock;
import com.portofino.realtrainmodunofficial.block.LargeRailCoreBlock;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@EventBusSubscriber(modid = RealTrainModUnofficial.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class TrainCommands {
    private TrainCommands() {
    }

    @SubscribeEvent
    public static void onCommandsRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
            Commands.literal("del")
                .then(Commands.literal("train")
                    .executes(context -> executeDeleteTrain(context.getSource()))
                )
        );

        dispatcher.register(
            Commands.literal("rtm")
                .then(Commands.literal("delAlltrain")
                    .requires(source -> source.hasPermission(2))
                    .executes(context -> executeDeleteTrain(context.getSource()))
                )
                //小文字表記でも効くように (コマンドリテラルは大文字小文字を区別する)
                .then(Commands.literal("delalltrain")
                    .requires(source -> source.hasPermission(2))
                    .executes(context -> executeDeleteTrain(context.getSource()))
                )
                .then(Commands.literal("flyspeed")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("speed", IntegerArgumentType.integer(1, 10))
                        .executes(context -> executeSetFlySpeed(
                            context.getSource(),
                            IntegerArgumentType.getInteger(context, "speed")
                        ))
                    )
                )
                .then(Commands.literal("nashorntest")
                    .requires(source -> source.hasPermission(2))
                    .executes(context -> executeNashornTest(context.getSource()))
                )
                .then(Commands.literal("hidegroup")
                    .executes(context -> executeHideGroup(context.getSource(), null))
                    .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.string())
                        .executes(context -> executeHideGroup(
                            context.getSource(),
                            com.mojang.brigadier.arguments.StringArgumentType.getString(context, "name")
                        ))
                    )
                )
                .then(Commands.literal("serverscript")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.string())
                        .executes(context -> executeServerScriptTest(
                            context.getSource(),
                            com.mojang.brigadier.arguments.StringArgumentType.getString(context, "id")
                        ))
                    )
                )
                //本家 MacroRecorder: 運転操作 (ノッチ/ドア/警笛) をマクロとして録画する。
                //start → 列車を運転 → stop で config/realtrainmodunofficial/macro/日時.txt へ保存。
                //保存したマクロは運転士 (素手右クリック) が再生できる。
                .then(Commands.literal("macro")
                    .then(Commands.literal("start")
                        .executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            if (!jp.ngt.rtm.entity.npc.macro.MacroRecorder.start(player)) {
                                context.getSource().sendFailure(
                                    net.minecraft.network.chat.Component.literal("すでに録画中です"));
                            }
                            return 1;
                        })
                    )
                    .then(Commands.literal("stop")
                        .executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            if (!jp.ngt.rtm.entity.npc.macro.MacroRecorder.stop(player)) {
                                context.getSource().sendFailure(
                                    net.minecraft.network.chat.Component.literal("録画していません (/rtm macro start)"));
                            }
                            return 1;
                        })
                    )
                )
        );

        registerMctrl(dispatcher);
    }

    /**
     * KaizPatchX {@code CommandMCtrl} / {@code ModelCtrl} の移植。
     * <pre>/mctrl &lt;target&gt; notch &lt;-8~5&gt;
     * /mctrl &lt;target&gt; dir &lt;0|1&gt;
     * /mctrl &lt;target&gt; dm &lt;dataName&gt; &lt;value&gt;
     * /mctrl &lt;target&gt; state &lt;TrainStateType&gt; &lt;TrainState&gt;</pre>
     * target = {@code @a}(全列車) / {@code @n}(最寄り) / {@code @r:NN}(半径NN) / {@code @s}(搭乗中) / 列車名。
     */
    private static void registerMctrl(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("mctrl")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("target", StringArgumentType.string())
                    .then(Commands.literal("notch")
                        .then(Commands.argument("value", IntegerArgumentType.integer(-8, 5))
                            .executes(ctx -> mctrlNotch(ctx.getSource(),
                                StringArgumentType.getString(ctx, "target"),
                                IntegerArgumentType.getInteger(ctx, "value")))))
                    .then(Commands.literal("dir")
                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 1))
                            .executes(ctx -> mctrlDir(ctx.getSource(),
                                StringArgumentType.getString(ctx, "target"),
                                IntegerArgumentType.getInteger(ctx, "value")))))
                    .then(Commands.literal("dm")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .then(Commands.argument("value", StringArgumentType.greedyString())
                                .executes(ctx -> mctrlDataMap(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "target"),
                                    StringArgumentType.getString(ctx, "name"),
                                    StringArgumentType.getString(ctx, "value"))))))
                    .then(Commands.literal("state")
                        .then(Commands.argument("type", StringArgumentType.word())
                            .then(Commands.argument("value", StringArgumentType.word())
                                .executes(ctx -> mctrlState(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "target"),
                                    StringArgumentType.getString(ctx, "type"),
                                    StringArgumentType.getString(ctx, "value"))))))
                )
        );
    }

    /** target 文字列 (@a/@n/@r:NN/@s/名前) を解決して対象の本家列車を返す。 */
    private static List<jp.ngt.rtm.entity.train.EntityTrainBase> resolveTrainTargets(
            CommandSourceStack src, String target) {
        ServerLevel level = src.getLevel();
        List<jp.ngt.rtm.entity.train.EntityTrainBase> all = new ArrayList<>();
        for (Entity e : level.getAllEntities()) {
            if (e instanceof jp.ngt.rtm.entity.train.EntityTrainBase t && t.isAlive()) {
                all.add(t);
            }
        }
        String t = target.trim();
        net.minecraft.world.phys.Vec3 pos = src.getPosition();
        if (t.equals("@a")) {
            return all;
        }
        if (t.equals("@s")) {
            Entity sender = src.getEntity();
            if (sender != null && sender.getVehicle() instanceof jp.ngt.rtm.entity.train.EntityTrainBase ride) {
                return List.of(ride);
            }
            return List.of();
        }
        if (t.equals("@n")) {
            jp.ngt.rtm.entity.train.EntityTrainBase nearest = null;
            double best = Double.MAX_VALUE;
            for (var tr : all) {
                double d = tr.distanceToSqr(pos);
                if (d < best) {
                    best = d;
                    nearest = tr;
                }
            }
            return nearest == null ? List.of() : List.of(nearest);
        }
        if (t.startsWith("@r:")) {
            double r;
            try {
                r = Double.parseDouble(t.substring(3));
            } catch (NumberFormatException e) {
                return List.of();
            }
            double rr = r * r;
            List<jp.ngt.rtm.entity.train.EntityTrainBase> out = new ArrayList<>();
            for (var tr : all) {
                if (tr.distanceToSqr(pos) <= rr) {
                    out.add(tr);
                }
            }
            return out;
        }
        //名前一致 (表示名 or カスタム名)
        List<jp.ngt.rtm.entity.train.EntityTrainBase> out = new ArrayList<>();
        for (var tr : all) {
            if (tr.getName().getString().equals(t)
                || (tr.hasCustomName() && tr.getCustomName() != null
                    && tr.getCustomName().getString().equals(t))) {
                out.add(tr);
            }
        }
        return out;
    }

    private static int mctrlNotch(CommandSourceStack src, String target, int value) {
        List<jp.ngt.rtm.entity.train.EntityTrainBase> list = resolveTrainTargets(src, target);
        if (list.isEmpty()) {
            src.sendFailure(Component.literal("対象の列車が見つかりません: " + target));
            return 0;
        }
        list.forEach(tr -> tr.setNotch(value));
        src.sendSuccess(() -> Component.literal(list.size() + " 両のノッチを " + value + " に設定しました。"), true);
        return list.size();
    }

    private static int mctrlDir(CommandSourceStack src, String target, int value) {
        List<jp.ngt.rtm.entity.train.EntityTrainBase> list = resolveTrainTargets(src, target);
        if (list.isEmpty()) {
            src.sendFailure(Component.literal("対象の列車が見つかりません: " + target));
            return 0;
        }
        list.forEach(tr -> tr.setTrainDirection(value));
        src.sendSuccess(() -> Component.literal(list.size() + " 両の進行方向を " + value + " に設定しました。"), true);
        return list.size();
    }

    private static int mctrlDataMap(CommandSourceStack src, String target, String name, String value) {
        List<jp.ngt.rtm.entity.train.EntityTrainBase> list = resolveTrainTargets(src, target);
        if (list.isEmpty()) {
            src.sendFailure(Component.literal("対象の列車が見つかりません: " + target));
            return 0;
        }
        list.forEach(tr -> applyDataMap(tr, name, value));
        src.sendSuccess(() -> Component.literal(list.size() + " 両の DataMap[" + name + "] を " + value + " にしました。"), true);
        return list.size();
    }

    private static int mctrlState(CommandSourceStack src, String target, String typeName, String value) {
        jp.ngt.rtm.entity.train.util.TrainState state;
        jp.ngt.rtm.entity.train.util.TrainState.TrainStateType type;
        try {
            state = jp.ngt.rtm.entity.train.util.TrainState.valueOf(value);
            type = jp.ngt.rtm.entity.train.util.TrainState.TrainStateType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            src.sendFailure(Component.literal(
                "state/type が不正です。例: type=State_Door value=Door_OpenAll"));
            return 0;
        }
        List<jp.ngt.rtm.entity.train.EntityTrainBase> list = resolveTrainTargets(src, target);
        if (list.isEmpty()) {
            src.sendFailure(Component.literal("対象の列車が見つかりません: " + target));
            return 0;
        }
        list.forEach(tr -> tr.setTrainStateData(type.id, state.data));
        src.sendSuccess(() -> Component.literal(list.size() + " 両の " + typeName + " を " + value + " にしました。"), true);
        return list.size();
    }

    /** DataMap の値を型推定して書き込む (本家 ModelCtrl の set(name,value,3) 相当)。 */
    private static void applyDataMap(jp.ngt.rtm.entity.train.EntityTrainBase tr, String name, String value) {
        jp.ngt.rtm.modelpack.state.DataMap dm = tr.getResourceState().getDataMap();
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            dm.setBoolean(name, Boolean.parseBoolean(value), 3);
            return;
        }
        try {
            dm.setInt(name, Integer.parseInt(value), 3);
            return;
        } catch (NumberFormatException ignored) {
        }
        try {
            dm.setDouble(name, Double.parseDouble(value), 3);
            return;
        } catch (NumberFormatException ignored) {
        }
        dm.setString(name, value, 3);
    }

    private static int executeDeleteTrain(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        int removedCount = 0;
        TrainEntity.clearCouplingModes();

        for (ServerLevel level : server.getAllLevels()) {
            removedCount += removeTrainEntities(level);
            removeBogieEntities(level);
            removedCount += removeRtmTrainEntities(level);
            removeRailCollisionBlocks(level);
        }

        int finalRemovedCount = removedCount;
        source.sendSuccess(() -> Component.literal("電車を " + finalRemovedCount + " 両削除しました。残って見える場合はワールドを開き直してください。"), true);
        return removedCount;
    }

    /**
     * Phase 0 スモークテスト: スタンドアロン Nashorn が本家 RTM と同じフラグ
     * ("-doe" "--language=es6" + mozilla_compat.js) で NeoForge/Java21 上で動き、
     * MOD クラス (Packages.jp.ngt / Java.type) を解決できるかを検証する。
     */
    private static int executeNashornTest(CommandSourceStack source) {
        try {
            String script =
                // mozilla_compat: importPackage / importClass が生えていること
                "importPackage(java.util);\n" +
                "var list = new ArrayList(); list.add('a'); list.add('b');\n" +
                // ES6 構文が有効であること (--language=es6)
                "let square = (x) => x * x;\n" +
                "const msg = `es6:${square(4)}`;\n" +
                // MOD クラスローダ経由で jp.ngt.* が見えること
                "var su = Java.type('jp.ngt.ngtlib.io.ScriptUtil');\n" +
                "function result() { return msg + ' list:' + list.size() + ' cls:' + su.class.getSimpleName(); }\n";
            javax.script.ScriptEngine se = jp.ngt.ngtlib.io.ScriptUtil.doScript(script);
            Object result = jp.ngt.ngtlib.io.ScriptUtil.doScriptFunction(se, "result");
            String engineName = se.getFactory().getEngineName() + " " + se.getFactory().getEngineVersion();
            source.sendSuccess(() -> Component.literal(
                "Nashorn OK: engine=[" + engineName + "] result=[" + result + "]"), false);
            return 1;
        } catch (Throwable t) {
            String detail = t.getClass().getSimpleName() + ": " + t.getMessage();
            source.sendFailure(Component.literal("Nashorn NG: " + detail));
            RealTrainModUnofficial.LOGGER.error("Nashorn smoke test failed", t);
            return 0;
        }
    }


    /**
     * 車両のサーバースクリプトを<b>その場で読み込ませて</b>結果を出す。
     *
     * <p>サーバースクリプトは車を置いたときに初めて読まれるので、失敗しても
     * 「本来の機能が使えない」としか分からず、原因の切り分けができなかった。
     * このコマンドなら<b>置かずに</b>読み込みだけ試せる。
     *
     * <p>使い方: {@code /rtm serverscript NGTOBuilder2_Flat}
     */
    private static int executeServerScriptTest(CommandSourceStack source, String id) {
        var definition = com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry.getById(id);
        if (definition == null) {
            source.sendFailure(Component.literal("車両定義が見つかりません: " + id));
            return 0;
        }
        if (!definition.hasServerScript()) {
            source.sendSuccess(() -> Component.literal(
                id + " はサーバースクリプトを持っていません"), false);
            return 1;
        }
        //キャッシュ済みだと 2 回目以降が素通りするので、必ず読み直す
        com.portofino.realtrainmodunofficial.script.CarServerScripts.forget(id);
        var entry = com.portofino.realtrainmodunofficial.script.CarServerScripts.get(definition);
        if (entry == null) {
            source.sendFailure(Component.literal(
                id + " のサーバースクリプト読み込みに失敗しました。原因はログを見てください ("
                + definition.getServerScriptPath() + ")"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
            id + " のサーバースクリプトを読み込めました (" + definition.getServerScriptPath() + ")"), false);
        return 1;
    }


    /**
     * 調査用: モデルのグループを名前で隠す / 戻す。引数なしで一覧と全解除。
     *
     * <p>「変な板が出ている」の類は<b>どの部品かを先に確定させないと直せない</b>。
     * 描画経路を当てずっぽうで変えても当たらず、確認の往復が増えるだけになる。
     *
     * <p>{@code /rtm hidegroup glass} のように使う。隠れれば犯人が確定する。
     * 保存はしないのでワールドを出れば戻る。
     */
    private static int executeHideGroup(CommandSourceStack source, String name) {
        if (name == null || name.isBlank()) {
            var hidden = com.portofino.realtrainmodunofficial.client.DebugHiddenGroups.listHidden();
            if (hidden.isEmpty()) {
                source.sendSuccess(() -> Component.literal(
                    "隠している部品はありません。/rtm hidegroup <名前> で切り替えます"), false);
            } else {
                String list = String.join(", ", hidden);
                com.portofino.realtrainmodunofficial.client.DebugHiddenGroups.clear();
                source.sendSuccess(() -> Component.literal(
                    "隠していた部品を全て戻しました: " + list), false);
            }
            return 1;
        }
        boolean hidden = com.portofino.realtrainmodunofficial.client.DebugHiddenGroups.toggle(name);
        source.sendSuccess(() -> Component.literal(
            "部品 \"" + name + "\" を" + (hidden ? "隠しました" : "表示に戻しました")), false);
        return 1;
    }

    private static int executeSetFlySpeed(CommandSourceStack source, int speed) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        float normalizedSpeed = 0.05F * speed;
        player.getAbilities().setFlyingSpeed(normalizedSpeed);
        player.onUpdateAbilities();
        source.sendSuccess(() -> Component.literal("飛行速度を " + speed + " に設定しました。"), false);
        return speed;
    }

    private static int removeTrainEntities(ServerLevel level) {
        AABB worldAABB = new AABB(-3.0E7D, -2048.0D, -3.0E7D, 3.0E7D, 4096.0D, 3.0E7D);
        List<TrainEntity> trains = new ArrayList<>(level.getEntitiesOfClass(TrainEntity.class, worldAABB, entity -> true));
        try {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof TrainEntity train && !trains.contains(train)) {
                    trains.add(train);
                }
            }
        } catch (Exception ignored) {
        }
        for (TrainEntity train : trains) {
            train.forceDiscardTrain();
        }
        return trains.size();
    }

    private static void removeBogieEntities(ServerLevel level) {
        AABB worldAABB = new AABB(-3.0E7D, -2048.0D, -3.0E7D, 3.0E7D, 4096.0D, 3.0E7D);
        List<TrainBogieEntity> bogies = new ArrayList<>(level.getEntitiesOfClass(TrainBogieEntity.class, worldAABB, entity -> true));
        try {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof TrainBogieEntity bogie && !bogies.contains(bogie)) {
                    bogies.add(bogie);
                }
            }
        } catch (Exception ignored) {
        }
        for (TrainBogieEntity bogie : bogies) {
            bogie.discard();
        }
    }

    /**
     * 本家系の列車 (jp.ngt.rtm.entity.train.EntityTrainBase — 設置される列車はこちら) と
     * その台車・車両パーツを全て削除する。旧 TrainEntity の削除だけでは実車が残る。
     */
    private static int removeRtmTrainEntities(ServerLevel level) {
        List<Entity> targets = new ArrayList<>();
        int trainCount = 0;
        try {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof jp.ngt.rtm.entity.train.EntityTrainBase) {
                    targets.add(entity);
                    trainCount++;
                } else if (entity instanceof jp.ngt.rtm.entity.train.EntityBogie
                        || entity instanceof jp.ngt.rtm.entity.train.parts.EntityVehiclePart) {
                    targets.add(entity);
                }
            }
        } catch (Exception ignored) {
        }
        for (Entity entity : targets) {
            entity.discard();
        }
        return trainCount;
    }

    private static void removeRailCollisionBlocks(ServerLevel level) {
        if (!(level.getChunkSource() instanceof ServerChunkCache cache)) {
            return;
        }

        try {
            java.lang.reflect.Field field = ServerChunkCache.class.getDeclaredField("chunkMap");
            field.setAccessible(true);
            Object chunkMap = field.get(cache);
            java.lang.reflect.Method method = chunkMap.getClass().getMethod("getChunks");
            Iterable<?> chunks = (Iterable<?>) method.invoke(chunkMap);

            for (Object holderObject : chunks) {
                if (!(holderObject instanceof ChunkHolder holder)) {
                    continue;
                }
                Optional<ChunkAccess> optional = Optional.ofNullable(holder.getLatestChunk());
                if (optional.isEmpty() || !(optional.get() instanceof LevelChunk chunk)) {
                    continue;
                }

                List<BlockPos> blockPositions = new ArrayList<>(chunk.getBlockEntities().keySet());
                for (BlockPos pos : blockPositions) {
                    BlockState blockState = chunk.getBlockState(pos);
                    if (blockState.getBlock() instanceof RailCollisionBlock) {
                        level.removeBlock(pos, false);
                    } else if (blockState.getBlock() instanceof LargeRailCoreBlock) {
                        level.removeBlock(pos, false);
                    }
                }
            }
        } catch (ReflectiveOperationException e) {
            // If reflection fails, skip removing block entities rather than crashing.
        }
    }
}
