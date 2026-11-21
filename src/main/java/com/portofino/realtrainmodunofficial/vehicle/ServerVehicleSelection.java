package com.portofino.realtrainmodunofficial.vehicle;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** サーバー側で「各プレイヤーが最後に選択した車両 ID」を覚える保険。 */
public final class ServerVehicleSelection {

    private static final Map<UUID, String> LAST = new ConcurrentHashMap<>();

    private ServerVehicleSelection() {
    }

    public static void set(UUID player, String modelId) {
        if (player == null) {
            return;
        }
        if (modelId == null || modelId.isBlank()) {
            LAST.remove(player);
        } else {
            LAST.put(player, modelId);
        }
    }

    /** 直近の選択 ID (無ければ null)。 */
    public static String get(UUID player) {
        return player == null ? null : LAST.get(player);
    }
}
