package net.neoforged.neoforge.network;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** シム: NeoForge の送信ヘルパを Fabric Networking API へ写像。 */
public final class PacketDistributor {
    private PacketDistributor() {
    }

    /** C2S 送信 (クライアント側から呼ぶ)。クライアント専用クラスは Bridge に隔離。 */
    public static void sendToServer(CustomPacketPayload payload, CustomPacketPayload... extra) {
        ClientSendBridge.send(payload);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    public static void sendToAllPlayers(CustomPacketPayload payload) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        for (ServerPlayer p : PlayerLookup.all(server)) {
            ServerPlayNetworking.send(p, payload);
        }
    }

    public static void sendToPlayersTrackingEntity(Entity entity, CustomPacketPayload payload) {
        for (ServerPlayer p : PlayerLookup.tracking(entity)) {
            ServerPlayNetworking.send(p, payload);
        }
    }

    public static void sendToPlayersTrackingEntityAndSelf(Entity entity, CustomPacketPayload payload) {
        for (ServerPlayer p : PlayerLookup.tracking(entity)) {
            ServerPlayNetworking.send(p, payload);
        }
        if (entity instanceof ServerPlayer self) {
            ServerPlayNetworking.send(self, payload);
        }
    }

    public static void sendToPlayersTrackingChunk(ServerLevel level, ChunkPos pos, CustomPacketPayload payload) {
        for (ServerPlayer p : PlayerLookup.tracking(level, pos)) {
            ServerPlayNetworking.send(p, payload);
        }
    }
}
