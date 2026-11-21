package com.portofino.realtrainmodunofficial.client;

import net.neoforged.fml.ModList;

/**
 * Sodium / Embeddium 連携。
 *
 * <p>RTMU の車両描画は高速化のため「モデルごとの焼き込み VBO 経路」を既定で使う
 * (MqoModelLoader の entityVbo)。これは Sodium の有無に依存せず常に有効で、
 * Sodium 併用時もそのまま機能する (Sodium はチャンク/標準エンティティパイプラインを
 * 高速化するが、カスタムエンティティレンダラー上の独自描画には効かないため)。
 *
 * <p>ここでは検出と設定の報告だけを行い、Sodium 特有の描画二重化などは
 * 各レンダラー側でこの検出結果を使ってガードする。
 */
public final class SodiumCompat {
    private static volatile Boolean present;
    private static volatile boolean reported;

    private SodiumCompat() {
    }

    /** Sodium / Embeddium (Sodium 互換フォーク) が入っているか。 */
    public static boolean isSodiumPresent() {
        Boolean p = present;
        if (p == null) {
            boolean found = false;
            try {
                found = ModList.get() != null
                    && (ModList.get().isLoaded("sodium") || ModList.get().isLoaded("embeddium"));
            } catch (Throwable ignored) {
                found = false;
            }
            present = p = found;
        }
        return p;
    }

    /** 起動時に 1 回だけ状態を報告する (F8/描画統計と併せて見る用)。 */
    public static void report() {
        if (reported) {
            return;
        }
        reported = true;
        if (isSodiumPresent()) {
            com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.info(
                "[RTMU] Sodium detected: baked VBO model path stays enabled (fastest under both)");
        }
    }
}
