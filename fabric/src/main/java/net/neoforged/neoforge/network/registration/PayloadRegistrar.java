package net.neoforged.neoforge.network.registration;

import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * シム: NeoForge の playToClient/playToServer を Fabric Networking API v1 へ写像。
 * - 型登録: PayloadTypeRegistry.playS2C()/playC2S()
 * - 受信: ServerPlayNetworking (C2S)。S2C 受信はクライアント専用クラスのため
 *   ClientPayloadBridge (client パッケージ) 経由で登録し、専用サーバーでの
 *   クラスロードを避ける。
 */
public class PayloadRegistrar {

    @FunctionalInterface
    public interface PlayPayloadHandler<T extends CustomPacketPayload> {
        void handle(T payload, IPayloadContext context);
    }

    public PayloadRegistrar versioned(String version) {
        return this;
    }

    public PayloadRegistrar optional() {
        return this;
    }

    public <T extends CustomPacketPayload> PayloadRegistrar playToClient(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            PlayPayloadHandler<T> handler) {
        PayloadTypeRegistry.playS2C().register(type, codec);
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            ClientPayloadBridge.registerClientReceiver(type, handler);
        }
        return this;
    }

    public <T extends CustomPacketPayload> PayloadRegistrar playToServer(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            PlayPayloadHandler<T> handler) {
        PayloadTypeRegistry.playC2S().register(type, codec);
        ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) ->
            handler.handle(payload, new IPayloadContext() {
                @Override
                public net.minecraft.world.entity.player.Player player() {
                    return context.player();
                }

                @Override
                public void enqueueWork(Runnable work) {
                    context.server().execute(work);
                }

                @Override
                public void reply(CustomPacketPayload reply) {
                    context.responseSender().sendPacket(reply);
                }
            }));
        return this;
    }

    public <T extends CustomPacketPayload> PayloadRegistrar playBidirectional(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            PlayPayloadHandler<T> handler) {
        playToClient(type, codec, handler);
        //playToClient で S2C 登録済み。C2S 側の型登録と受信を追加
        PayloadTypeRegistry.playC2S().register(type, codec);
        ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) ->
            handler.handle(payload, new IPayloadContext() {
                @Override
                public net.minecraft.world.entity.player.Player player() {
                    return context.player();
                }

                @Override
                public void enqueueWork(Runnable work) {
                    context.server().execute(work);
                }

                @Override
                public void reply(CustomPacketPayload reply) {
                    context.responseSender().sendPacket(reply);
                }
            }));
        return this;
    }
}
