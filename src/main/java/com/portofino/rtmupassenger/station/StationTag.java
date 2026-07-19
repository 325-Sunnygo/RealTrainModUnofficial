package com.portofino.rtmupassenger.station;

/**
 * 駅のタグ。時刻に応じた「出発需要 (origin)」「到着需要 (dest)」を持ち、
 * 乗客の湧き数と行き先抽選の重みを決める (通勤・通学・買い物・観光のシミュレーション)。
 * <p>例: 住宅街は朝に出発需要が高く (通勤で出る)、夕方に到着需要が高い (帰宅)。
 * オフィス街はその逆。時刻は MC の dayTime から取る (dayTime 0 = 朝 6 時)。
 */
public enum StationTag {
    RESIDENTIAL("住宅街"),
    OFFICE("オフィス街"),
    INDUSTRIAL("工業地帯"),
    EDUCATION("教育機関"),
    COMMERCIAL("商業"),
    TOURIST("観光");

    private final String displayName;

    StationTag(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return this.displayName;
    }

    /** MC の dayTime → 時刻 (0-24)。dayTime 0 = 朝 6:00。 */
    public static float hourOf(long dayTime) {
        return ((dayTime % 24000L) / 1000.0F + 6.0F) % 24.0F;
    }

    /** この駅から出発する需要 (時刻別)。 */
    public float origin(float h) {
        return switch (this) {
            case RESIDENTIAL -> 0.20F + 1.6F * g(h, 7.5F, 1.5F) + 0.3F * g(h, 13.0F, 3.0F);
            case OFFICE -> 0.15F + 1.4F * g(h, 18.0F, 1.5F);
            //工業地帯: 早朝出勤・夕方退勤 (オフィスより 1 時間早い二交代のイメージ)。
            case INDUSTRIAL -> 0.15F + 1.3F * g(h, 17.0F, 1.5F) + 0.4F * g(h, 6.0F, 1.0F);
            case EDUCATION -> 0.10F + 1.2F * g(h, 16.0F, 1.5F);
            case COMMERCIAL -> 0.20F + 0.8F * g(h, 15.0F, 3.0F);
            case TOURIST -> 0.10F + 0.6F * g(h, 16.0F, 3.0F);
        };
    }

    /** この駅へ向かう需要 (時刻別)。行き先抽選の重み。 */
    public float dest(float h) {
        return switch (this) {
            case RESIDENTIAL -> 0.20F + 1.6F * g(h, 18.5F, 2.0F);
            case OFFICE -> 0.15F + 1.6F * g(h, 8.5F, 1.5F);
            //工業地帯: 早朝に出勤者が到着 (オフィスより早い)。
            case INDUSTRIAL -> 0.15F + 1.5F * g(h, 7.0F, 1.0F);
            case EDUCATION -> 0.10F + 1.4F * g(h, 8.0F, 1.0F);
            case COMMERCIAL -> 0.20F + 1.0F * g(h, 14.0F, 3.0F) + 0.4F * g(h, 19.0F, 2.0F);
            case TOURIST -> 0.10F + 0.9F * g(h, 11.0F, 2.5F);
        };
    }

    /** タグビット集合の出発需要 (平均)。タグ無しは弱い一定需要。 */
    public static float originWeight(int bits, float h) {
        float sum = 0.0F;
        int n = 0;
        for (StationTag t : values()) {
            if ((bits & (1 << t.ordinal())) != 0) {
                sum += t.origin(h);
                n++;
            }
        }
        return n == 0 ? 0.35F : sum / n;
    }

    /** タグビット集合の到着需要 (平均)。タグ無しは弱い一定需要。 */
    public static float destWeight(int bits, float h) {
        float sum = 0.0F;
        int n = 0;
        for (StationTag t : values()) {
            if ((bits & (1 << t.ordinal())) != 0) {
                sum += t.dest(h);
                n++;
            }
        }
        return n == 0 ? 0.40F : sum / n;
    }

    /** 山型カーブ (中心 c 時・幅 w 時間のガウス、24 時間ラップ対応)。 */
    private static float g(float h, float c, float w) {
        float d = Math.abs(h - c);
        d = Math.min(d, 24.0F - d);
        return (float) Math.exp(-(d * d) / (2.0F * w * w));
    }
}
