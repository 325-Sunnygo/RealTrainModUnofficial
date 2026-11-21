package com.portofino.realtrainmodunofficial.vehicle;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * サーバー側で「各プレイヤーが最後に選択した車両 ID」を覚える保険。
 * 症状: 専用サーバー (特に Youer/Mohist などの Bukkit 系ハイブリッド) では、
 * アイテムのカスタムデータコンポーネント (SELECTED_MODEL_ID) が Bukkit の
 * ItemStack 変換で失われたり、クリエイティブのスロット同期で上書きされたりして、
 * 設置時 (TrainItem.useOn) に stack.get(SELECTED_MODEL_ID) が null になり、
 * どの車両を選んでも VehicleRegistry の先頭車両 5000 番台 Tc) に
 * フォールバックしていた。
 */
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
