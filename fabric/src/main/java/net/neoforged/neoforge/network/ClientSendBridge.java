package net.neoforged.neoforge.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C2S 送信のクライアント専用ブリッジ (専用サーバーでのクラスロード回避)。 */
final class ClientSendBridge {
    private ClientSendBridge() {
    }

    static void send(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }
}
