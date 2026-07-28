package com.portofino.realtrainmodunofficial.vehicle;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * サーバー側で「各プレイヤーが最後に選択した車両 ID」を覚える保険。
 *
 * <p><b>症状</b>: 専用サーバー (特に Youer/Mohist などの Bukkit 系ハイブリッド) では、
 * アイテムのカスタムデータコンポーネント ({@code SELECTED_MODEL_ID}) が Bukkit の
 * ItemStack 変換で失われたり、クリエイティブのスロット同期で上書きされたりして、
 * 設置時 ({@code TrainItem.useOn}) に {@code stack.get(SELECTED_MODEL_ID)} が null になり、
 * どの車両を選んでも {@link VehicleRegistry} の先頭車両 (223 系 5000 番台 Tc) に
 * フォールバックしていた。
 *
 * <p><b>対策</b>: 選択パケット ({@code SelectModelPayload}) を受けたときに、ここへ
 * プレイヤー UUID → 選択 ID を控える。設置側はコンポーネントが null のときここから拾う。
 * アイテム NBT/コンポーネントの同期・保持に依存しないので、Bukkit 系でも選択が効く。
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
