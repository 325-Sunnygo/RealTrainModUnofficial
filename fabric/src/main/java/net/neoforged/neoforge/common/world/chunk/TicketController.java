package net.neoforged.neoforge.common.world.chunk;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.UUID;

/**
 * Fabric では所有エンティティごとにバニラの region ticket を保持する。
 * /forceload の永続設定を変更せず、複数車両が同じチャンクを共有できる。
 */
public class TicketController {

    /** ワールドロード時に残っているチケットを検分するコールバック。 */
    @FunctionalInterface
    public interface LoadingValidationCallback {
        void validateTickets(ServerLevel level, TicketHelper helper);
    }

    /** 残チケットの照会・削除。バニラに相当物が無いので空で応じる。 */
    public static final class TicketHelper {
        public Map<UUID, Object> getEntityTickets() {
            return Map.of();
        }

        public void removeAllTickets(UUID owner) {
        }
    }

    private final net.minecraft.server.level.TicketType<UUID> type;

    public TicketController(ResourceLocation id) {
        this.type = net.minecraft.server.level.TicketType.create(id.toString(), UUID::compareTo);
    }

    public TicketController(ResourceLocation id, LoadingValidationCallback callback) {
        this(id);
    }

    public boolean forceChunk(ServerLevel level, Entity owner, int chunkX, int chunkZ,
                              boolean add, boolean ticking) {
        var chunk = new net.minecraft.world.level.ChunkPos(chunkX, chunkZ);
        int radius = ticking ? 2 : 0;
        if (add) level.getChunkSource().addRegionTicket(type, chunk, radius, owner.getUUID());
        else level.getChunkSource().removeRegionTicket(type, chunk, radius, owner.getUUID());
        return true;
    }
}
