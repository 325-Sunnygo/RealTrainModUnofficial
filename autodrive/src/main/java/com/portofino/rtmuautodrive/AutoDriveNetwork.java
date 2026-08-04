package com.portofino.rtmuautodrive;

import com.portofino.realtrainmodunofficial.formation.FormationSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;

/**
 * 通信。NeoForge の形 (CustomPacketPayload + PayloadRegistrar) で書いてあり、
 * Fabric では RTMU 本体が持っているシムが同じ形で受ける。
 */
public final class AutoDriveNetwork {

    private AutoDriveNetwork() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("rtmuautodrive", path);
    }

    // ---- C2S: スポナーの名前を保存 ----

    public record SetName(BlockPos pos, String name) implements CustomPacketPayload {
        public static final Type<SetName> TYPE = new Type<>(id("set_name"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetName> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SetName::pos,
                ByteBufCodecs.stringUtf8(64), SetName::name,
                SetName::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ---- C2S: 一覧をくれ ----

    public record RequestList() implements CustomPacketPayload {
        public static final Type<RequestList> TYPE = new Type<>(id("request_list"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestList> CODEC =
                StreamCodec.unit(new RequestList());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ---- S2C: 一覧 ----

    /** 1 台ぶん。ready = 編成アイテムが入っていて近くにレールがある。 */
    public record Entry(BlockPos pos, String name, boolean ready) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Entry::pos,
                ByteBufCodecs.stringUtf8(64), Entry::name,
                ByteBufCodecs.BOOL, Entry::ready,
                Entry::new);
    }

    public record ListPayload(List<Entry> entries) implements CustomPacketPayload {
        public static final Type<ListPayload> TYPE = new Type<>(id("list"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ListPayload> CODEC = StreamCodec.composite(
                Entry.CODEC.apply(ByteBufCodecs.list(256)), ListPayload::entries,
                ListPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ---- C2S: 発車 ----

    public record Launch(BlockPos pos) implements CustomPacketPayload {
        public static final Type<Launch> TYPE = new Type<>(id("launch"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Launch> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Launch::pos,
                Launch::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }


    // ---- S2C: 駅列車ブロックの名前を付ける画面を開く ----

    public record OpenStationName(BlockPos pos, String name) implements CustomPacketPayload {
        public static final Type<OpenStationName> TYPE = new Type<>(id("open_station_name"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenStationName> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, OpenStationName::pos,
                ByteBufCodecs.stringUtf8(64), OpenStationName::name,
                OpenStationName::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ---- C2S: 駅名を保存 ----

    public record SetStationName(BlockPos pos, String name) implements CustomPacketPayload {
        public static final Type<SetStationName> TYPE = new Type<>(id("set_station_name"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetStationName> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SetStationName::pos,
                ByteBufCodecs.stringUtf8(64), SetStationName::name,
                SetStationName::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ---- 詳細設定 (停車/通過) ----

    /** 経路上の駅 1 つ。stop = 停車する / door = 0:両側 1:左 2:右。 */
    public record RouteEntry(BlockPos pos, String name, boolean stop, int door) {
        public static final StreamCodec<RegistryFriendlyByteBuf, RouteEntry> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, RouteEntry::pos,
                ByteBufCodecs.stringUtf8(64), RouteEntry::name,
                ByteBufCodecs.BOOL, RouteEntry::stop,
                ByteBufCodecs.VAR_INT, RouteEntry::door,
                RouteEntry::new);
    }

    public record RequestRoute(BlockPos dispatcher) implements CustomPacketPayload {
        public static final Type<RequestRoute> TYPE = new Type<>(id("request_route"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestRoute> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, RequestRoute::dispatcher,
                RequestRoute::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RoutePayload(BlockPos dispatcher, List<RouteEntry> entries) implements CustomPacketPayload {
        public static final Type<RoutePayload> TYPE = new Type<>(id("route"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RoutePayload> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, RoutePayload::dispatcher,
                RouteEntry.CODEC.apply(ByteBufCodecs.list(256)), RoutePayload::entries,
                RoutePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SetStop(BlockPos dispatcher, BlockPos station, boolean stop, int door)
            implements CustomPacketPayload {
        public static final Type<SetStop> TYPE = new Type<>(id("set_stop"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetStop> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SetStop::dispatcher,
                BlockPos.STREAM_CODEC, SetStop::station,
                ByteBufCodecs.BOOL, SetStop::stop,
                ByteBufCodecs.VAR_INT, SetStop::door,
                SetStop::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** C2S: 方向幕番号と停車時間。 */
    public record SetConfig(BlockPos pos, int rollsign, int dwell) implements CustomPacketPayload {
        public static final Type<SetConfig> TYPE = new Type<>(id("set_config"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetConfig> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SetConfig::pos,
                ByteBufCodecs.VAR_INT, SetConfig::rollsign,
                ByteBufCodecs.VAR_INT, SetConfig::dwell,
                SetConfig::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ---- 登録 ----

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(SetName.TYPE, SetName.CODEC, AutoDriveNetwork::handleSetName);
        registrar.playToServer(SetConfig.TYPE, SetConfig.CODEC, AutoDriveNetwork::handleSetConfig);
        registrar.playToServer(RequestList.TYPE, RequestList.CODEC, AutoDriveNetwork::handleRequestList);
        registrar.playToServer(Launch.TYPE, Launch.CODEC, AutoDriveNetwork::handleLaunch);
        registrar.playToClient(ListPayload.TYPE, ListPayload.CODEC, AutoDriveNetwork::handleList);
        registrar.playToClient(OpenStationName.TYPE, OpenStationName.CODEC, AutoDriveNetwork::handleOpenStationName);
        registrar.playToServer(SetStationName.TYPE, SetStationName.CODEC, AutoDriveNetwork::handleSetStationName);
        registrar.playToServer(RequestRoute.TYPE, RequestRoute.CODEC, AutoDriveNetwork::handleRequestRoute);
        registrar.playToServer(SetStop.TYPE, SetStop.CODEC, AutoDriveNetwork::handleSetStop);
        registrar.playToClient(RoutePayload.TYPE, RoutePayload.CODEC, AutoDriveNetwork::handleRoute);
    }

    private static void handleOpenStationName(OpenStationName payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientBridge.openStationNameScreen(payload.pos(), payload.name()));
    }

    private static void handleSetStationName(SetStationName payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player == null || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (player.distanceToSqr(payload.pos().getCenter()) > 64.0D) {
                return;
            }
            if (level.getBlockEntity(payload.pos()) instanceof StationStopBlockEntity be) {
                be.setStationName(payload.name());
            }
        });
    }

    private static void handleRequestRoute(RequestRoute payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ServerLevel level = player.serverLevel();
            if (!(level.getBlockEntity(payload.dispatcher()) instanceof TrainDispatcherBlockEntity be)) {
                return;
            }
            List<RouteEntry> entries = new ArrayList<>();
            for (RailRoute.Stop stop : be.route(level)) {
                entries.add(new RouteEntry(stop.pos(), stop.name(),
                        be.isStopping(stop.pos()), be.doorSide(stop.pos())));
            }
            ClientBridge.sendRoute(player, new RoutePayload(payload.dispatcher(), entries));
        });
    }

    private static void handleSetStop(SetStop payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player == null || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (player.distanceToSqr(payload.dispatcher().getCenter()) > 64.0D) {
                return;
            }
            if (level.getBlockEntity(payload.dispatcher()) instanceof TrainDispatcherBlockEntity be) {
                be.setStopping(payload.station(), payload.stop());
                be.setDoorSide(payload.station(), payload.door());
            }
        });
    }

    private static void handleRoute(RoutePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientBridge.openRoute(payload.dispatcher(), payload.entries()));
    }

private static void handleSetConfig(SetConfig payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player == null || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (player.distanceToSqr(payload.pos().getCenter()) > 64.0D) {
                return;
            }
            if (level.getBlockEntity(payload.pos()) instanceof TrainDispatcherBlockEntity be) {
                be.setConfig(payload.rollsign(), payload.dwell());
            }
        });
    }

    private static void handleSetName(SetName payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player == null || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (player.distanceToSqr(payload.pos().getCenter()) > 64.0D) {
                return;
            }
            if (level.getBlockEntity(payload.pos()) instanceof TrainDispatcherBlockEntity be) {
                be.setDispatcherName(payload.name());
            }
        });
    }

    private static void handleRequestList(RequestList payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ServerLevel level = player.serverLevel();
            List<Entry> entries = new ArrayList<>();
            DispatcherRegistry.get(level).all().forEach((pos, name) -> {
                boolean ready = level.getBlockEntity(pos) instanceof TrainDispatcherBlockEntity be && be.canLaunch();
                String label = name == null || name.isBlank()
                        ? pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
                        : name;
                entries.add(new Entry(pos, label, ready));
            });
            ClientBridge.sendList(player, new ListPayload(entries));
        });
    }

    private static void handleLaunch(Launch payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player == null || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (!(level.getBlockEntity(payload.pos()) instanceof TrainDispatcherBlockEntity be)) {
                player.displayClientMessage(Component.translatable("message.rtmuautodrive.not_loaded"), true);
                return;
            }
            FormationSpawner.Result result = be.launch(level);
            player.displayClientMessage(Component.translatable(switch (result) {
                case OK -> "message.rtmuautodrive.launched";
                case EMPTY -> "message.rtmuautodrive.no_formation";
                case NO_RAIL -> "message.rtmuautodrive.no_rail_near";
                case NOT_ENOUGH_RAIL -> "message.rtmuautodrive.not_enough_rail";
                case OCCUPIED -> "message.rtmuautodrive.occupied";
            }), true);
        });
    }

    private static void handleList(ListPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientBridge.openList(payload.entries()));
    }
}
