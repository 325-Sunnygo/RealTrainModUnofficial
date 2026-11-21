package jp.kaiz.atsassistmod.ifttt;

/**
 * 本家 jp.ngt.rtm.modelpack.state.DataType 相当 (IFTTT の DataMap 条件/アクション用)。
 * RTMU の DataMap が対応する 4 型のみ (本家の HEX/VEC は RTMU 側に実装がないため除外)。
 */
public enum DataType {
    BOOLEAN("Boolean"),
    INT("Int"),
    DOUBLE("Double"),
    STRING("String");

    public final String key;

    DataType(String key) {
        this.key = key;
    }

    public static DataType byName(String name) {
        try {
            return DataType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return STRING;
        }
    }
}
