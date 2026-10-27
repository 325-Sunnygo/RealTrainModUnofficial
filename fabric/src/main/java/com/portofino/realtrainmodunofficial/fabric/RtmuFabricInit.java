package com.portofino.realtrainmodunofficial.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.ShimEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Fabric の共通エントリポイント。
 * RTMU 本体は NeoForge の形 (@Mod コンストラクタ + イベントバス) のまま動かし、
 * net.neoforged.* は自前シムで受ける。
 */
public class RtmuFabricInit implements ModInitializer {

    /** NeoForge の「MOD バス」に相当するもの。本体コンストラクタへ渡す。 */
    public static final ShimEventBus MOD_BUS = new ShimEventBus();

    private static com.portofino.realtrainmodunofficial.RealTrainModUnofficial instance;

    @Override
    public void onInitialize() {
        ModContainer container = new ModContainer();

        // ★本体の生成 = レジストリ登録。NeoForge では @Mod のコンストラクタが担っていた所。
        // DeferredRegister のシムは register の時点で即座にバニラへ入れるので、
        // ここを通せば登録が済む。
        Dist dist = net.fabricmc.loader.api.FabricLoader.getInstance().getEnvironmentType()
            == net.fabricmc.api.EnvType.CLIENT ? Dist.CLIENT : Dist.DEDICATED_SERVER;
        instance = new com.portofino.realtrainmodunofficial.RealTrainModUnofficial(
            MOD_BUS, container, dist);

        // @EventBusSubscriber 相当。NeoForge はアノテーション走査で拾うが Fabric には無いので明示。
        RtmuSubscribers.registerCommon();

        // --- MOD バス: 起動時に 1 回流すもの ---
        MOD_BUS.post(new RegisterTicketControllersEvent());
        MOD_BUS.post(new RegisterPayloadHandlersEvent());
        MOD_BUS.post(new FMLCommonSetupEvent());

        // ★エンティティ属性。
        // こちらは集めた結果を Fabric のレジストリへ流す。
        var attrEvent = new net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent();
        MOD_BUS.post(attrEvent);
        attrEvent.getAttributes().forEach((type, supplier) ->
            net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry
                .register(type, supplier));

        // --- GAME バス: Fabric のライフサイクルから流す ---
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            // NeoForge の ServerLifecycleHooks.getCurrentServer を成立させる
            ServerLifecycleHooks.setCurrentServer(server);
            NeoForge.EVENT_BUS.post(new ServerStartingEvent(server));
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
            NeoForge.EVENT_BUS.post(new ServerStartedEvent(server)));
        ServerLifecycleEvents.SERVER_STOPPED.register(server ->
            ServerLifecycleHooks.setCurrentServer(null));

        ServerTickEvents.END_SERVER_TICK.register(server ->
            NeoForge.EVENT_BUS.post(new ServerTickEvent.Post(server)));

        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) ->
            NeoForge.EVENT_BUS.post(new RegisterCommandsEvent(dispatcher, registry,
                env == net.minecraft.commands.Commands.CommandSelection.DEDICATED
                    ? net.minecraft.commands.Commands.CommandSelection.DEDICATED
                    : net.minecraft.commands.Commands.CommandSelection.ALL)));

        // ★NeoForge の BlockEntity#onLoad をここで再現する。
        // バニラにこのフックは無く、レール・設置物は onLoad で自分を静的一覧へ登録している。
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents.BLOCK_ENTITY_LOAD
            .register((be, level) -> {
                if (be instanceof jp.ngt.mccompat.LoadAwareBlockEntity aware) {
                    aware.onLoad();
                }
            });

        // 同じ理由でエンティティ側の onAddedToLevel も NeoForge 拡張。
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD
            .register((entity, level) -> {
                if (entity instanceof jp.ngt.rtm.entity.train.parts.EntityVehiclePart part) {
                    part.onAddedToLevel();
                }
            });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(handler.getPlayer())));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(handler.getPlayer())));

        com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.info(
            "[RTMU/Fabric] 共通初期化が完了しました");
    }

    public static com.portofino.realtrainmodunofficial.RealTrainModUnofficial instance() {
        return instance;
    }
}
