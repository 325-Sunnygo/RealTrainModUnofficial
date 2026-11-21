package jp.kaiz.atsassistmod.utils;

/** 本家 jp.kaiz.atsassistmod.utils.KaizUtils の必要部分の移植。 */
public final class KaizUtils {

    private KaizUtils() {
    }

    /** enum を次の値へ巡回させる (本家 getNextEnum)。 */
    public static <T extends Enum<T>> T getNextEnum(T e) {
        T[] values = e.getDeclaringClass().getEnumConstants();
        return values[(e.ordinal() + 1) % values.length];
    }
}
