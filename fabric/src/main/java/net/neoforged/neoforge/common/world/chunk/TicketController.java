package net.neoforged.neoforge.common.world.chunk;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.UUID;

/**
 * シム: NeoForge のチケット式強制ロードをバニラの setChunkForced で近似する。
 * 所有者別のチケット管理は持たない。
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

    public TicketController(ResourceLocation id) {
    }

    public TicketController(ResourceLocation id, LoadingValidationCallback callback) {
    }

    public boolean forceChunk(ServerLevel level, Entity owner, int chunkX, int chunkZ,
                              boolean add, boolean ticking) {
        level.setChunkForced(chunkX, chunkZ, add);
        return true;
    }
}
