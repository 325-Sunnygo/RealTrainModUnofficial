package net.neoforged.neoforge.network.registration;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C 受信登録 (クライアント専用クラスをここへ隔離)。
 * 専用サーバーではこのクラスをロードしないこと (PayloadRegistrar が dist で分岐)。
 */
final class ClientPayloadBridge {
    private ClientPayloadBridge() {
    }

    static <T extends CustomPacketPayload> void registerClientReceiver(
            CustomPacketPayload.Type<T> type, PayloadRegistrar.PlayPayloadHandler<T> handler) {
        ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) ->
            handler.handle(payload, new IPayloadContext() {
                @Override
                public net.minecraft.world.entity.player.Player player() {
                    return context.player();
                }

                @Override
                public void enqueueWork(Runnable work) {
                    context.client().execute(work);
                }

                @Override
                public void reply(CustomPacketPayload reply) {
                    context.responseSender().sendPacket(reply);
                }
            }));
    }
}
