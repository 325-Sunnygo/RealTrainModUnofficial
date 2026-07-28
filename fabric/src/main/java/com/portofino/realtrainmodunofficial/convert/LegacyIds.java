package com.portofino.realtrainmodunofficial.convert;

import java.util.Locale;
import java.util.Map;

/**
 * 旧 RTM の タイルエンティティ / エンティティ の登録名を、変換の種類に振り分ける。
 *
 * <p>保存される id はバージョンで書式が違う:
 * <ul>
 *   <li>1.7.10: タイルエンティティ = {@code "TERailCore"}、エンティティ = {@code "RTM.E.ElectricCar"}</li>
 *   <li>1.12.2: 名前空間付き ({@code "minecraft:TERailCore"} / {@code "rtm:electric_car"})</li>
 * </ul>
 * どちらでも通るように「名前空間を落として小文字化」した形で突き合わせる。
 */
public final class LegacyIds {

    public enum Kind {
        /** 通常レール (2 点)。 */
        RAIL,
        /** 分岐・クロス (3 点以上)。 */
        RAIL_SWITCH,
        /** 転車台。 */
        TURNTABLE,
        /** モデル付き設置物 (信号・踏切・照明・看板・碍子・架線 …)。 */
        INSTALLED_OBJECT,
        /** 列車。 */
        TRAIN,
        /** 自動車・船・飛行機などの車両。 */
        VEHICLE,
        /**
         * 本家では<b>エンティティ</b>だが、RTMU では<b>設置物 (ブロック)</b> になっているもの。
         * 車止め・列車検知器・ATC。
         */
        ENTITY_OBJECT
    }

    /** タイルエンティティ登録名 → 種類。RTM が実際に {@code registerTileEntity} した名前。 */
    private static final Map<String, Kind> TILES = Map.ofEntries(
            //--- レール ---
            Map.entry("terailcore", Kind.RAIL),
            Map.entry("terailswitchcore", Kind.RAIL_SWITCH),
            Map.entry("teturntablecore", Kind.TURNTABLE),
            //レールの土台/マーカーはコアから作り直すので拾わない (terailbase / terailswitchbase / temarker)

            //--- モデル付き設置物 (すべて ResourceState を持つ) ---
            Map.entry("tesignal", Kind.INSTALLED_OBJECT),
            Map.entry("tecrossinggate", Kind.INSTALLED_OBJECT),
            Map.entry("tepoint", Kind.INSTALLED_OBJECT),
            Map.entry("telight", Kind.INSTALLED_OBJECT),
            Map.entry("fluorescent", Kind.INSTALLED_OBJECT),
            Map.entry("tesignboard", Kind.INSTALLED_OBJECT),
            Map.entry("teticketvendor", Kind.INSTALLED_OBJECT),
            Map.entry("teturnstile", Kind.INSTALLED_OBJECT),
            Map.entry("tespeaker", Kind.INSTALLED_OBJECT),
            Map.entry("teinsulator", Kind.INSTALLED_OBJECT),
            Map.entry("teconnectorin", Kind.INSTALLED_OBJECT),
            Map.entry("teconnectorout", Kind.INSTALLED_OBJECT),
            Map.entry("terailroadsign", Kind.INSTALLED_OBJECT),
            Map.entry("tepole", Kind.INSTALLED_OBJECT),
            Map.entry("tedecoration", Kind.INSTALLED_OBJECT),
            Map.entry("teplantornament", Kind.INSTALLED_OBJECT),
            Map.entry("temechanism", Kind.INSTALLED_OBJECT),
            Map.entry("tepipe", Kind.INSTALLED_OBJECT),
            Map.entry("temirror", Kind.INSTALLED_OBJECT),
            Map.entry("teeffect", Kind.INSTALLED_OBJECT)
    );

    /** エンティティ登録名 → 種類。1.12.2 の登録名と 1.7.10 の旧名の両方を入れる。 */
    private static final Map<String, Kind> ENTITIES = Map.ofEntries(
            Map.entry("electric_car", Kind.TRAIN),
            Map.entry("rtm.e.electriccar", Kind.TRAIN),
            Map.entry("diesel_car", Kind.TRAIN),
            Map.entry("rtm.e.dieselcar", Kind.TRAIN),
            Map.entry("freight_car", Kind.TRAIN),
            Map.entry("rtm.e.freightcar", Kind.TRAIN),
            Map.entry("tanker", Kind.TRAIN),
            Map.entry("rtm.e.tanker", Kind.TRAIN),
            Map.entry("test_car", Kind.TRAIN),
            Map.entry("rtm.e.traintest", Kind.TRAIN),

            Map.entry("car", Kind.VEHICLE),
            Map.entry("rtm.e.car", Kind.VEHICLE),

            //本家はエンティティ、RTMU は設置物 (ブロック)
            Map.entry("bumping_post", Kind.ENTITY_OBJECT),
            Map.entry("rtm.e.bumpingpost", Kind.ENTITY_OBJECT),
            Map.entry("train_detector", Kind.ENTITY_OBJECT),
            Map.entry("rtm.e.traindetector", Kind.ENTITY_OBJECT),
            Map.entry("atc", Kind.ENTITY_OBJECT),
            Map.entry("rtm.e.atc", Kind.ENTITY_OBJECT)
            //台車 (bogie) と床 (floor) は列車から作り直されるので拾わない
    );

    private LegacyIds() {
    }

    /** 名前空間を落として小文字化する。 */
    public static String normalize(String id) {
        if (id == null || id.isBlank()) {
            return "";
        }
        String s = id.trim();
        int colon = s.indexOf(':');
        if (colon >= 0) {
            s = s.substring(colon + 1);
        }
        return s.toLowerCase(Locale.ROOT);
    }

    public static Kind tileKind(String normalizedId) {
        return TILES.get(normalizedId);
    }

    public static Kind entityKind(String normalizedId) {
        return ENTITIES.get(normalizedId);
    }

    /**
     * RTM 由来っぽい id か (未対応のものをログに出すための判定)。
     * バニラのチェストや看板まで報告すると埋もれるので、それらは除く。
     */
    public static boolean looksLikeRtm(String normalizedId) {
        return normalizedId.startsWith("te") || normalizedId.startsWith("rtm.") || normalizedId.startsWith("ngt");
    }
}
