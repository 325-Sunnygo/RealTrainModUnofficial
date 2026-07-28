package net.minecraftforge.fml.common;

/**
 * 旧 Forge (1.7.10/1.12.2) の {@code net.minecraftforge.fml.common.Loader} スクリプト互換シム。
 * <p>
 * rtm-ts 系マルチターゲットパック (SR1-200 等) の mc1122 ターゲットが
 * {@code Packages.net.minecraftforge.fml.common.Loader.isModLoaded("fix-rtm")} を
 * <b>クラス本体で即時実行</b>する。RTMU の include 解決は prepend 方式で全ターゲットの
 * IIFE が無条件に走るため、このクラスが無いと ClassNotFoundException で
 * <b>スクリプト全体の eval が死に</b>、プレーン描画へフォールバックしていた
 * (SR1: パンタ下げても上がったまま・マスコン等の運転台パーツ全描画)。
 * <p>
 * 本家 Loader はシングルトン + 大量の FML API を持つが、パックスクリプトが使うのは
 * 事実上 {@code isModLoaded} だけなので最小実装とする。
 */
public final class Loader {

    private static final Loader INSTANCE = new Loader();

    private Loader() {
    }

    /** 本家: {@code Loader.instance()} — シングルトン取得。 */
    public static Loader instance() {
        return INSTANCE;
    }

    /**
     * 本家: 指定 modid がロード済みか。NeoForge の ModList へ委譲する。
     * fixrtm ("fix-rtm") の検出イディオムに使われる — RTMU 環境では存在しないので false。
     */
    public static boolean isModLoaded(String modid) {
        if (modid == null || modid.isBlank()) {
            return false;
        }
        try {
            return net.neoforged.fml.ModList.get() != null
                    && net.neoforged.fml.ModList.get().isLoaded(modid);
        } catch (Throwable t) {
            //ModList 未初期化タイミング等は「入っていない」扱い (スクリプトを落とさない)
            return false;
        }
    }
}
