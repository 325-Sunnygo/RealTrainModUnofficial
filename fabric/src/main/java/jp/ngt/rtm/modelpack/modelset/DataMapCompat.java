package jp.ngt.rtm.modelpack.modelset;

import java.util.HashMap;
import java.util.Map;

/** 本家 ResourceState.getDataMap 相当 (スクリプトが値を出し入れする箱)。 */
public class DataMapCompat {
    private final Map<String, Object> values = new HashMap<>();

    public boolean contains(String key) {
        return this.values.containsKey(key);
    }

    public int getInt(String key) {
        Object v = this.values.get(key);
        return v instanceof Number n ? n.intValue() : 0;
    }

    /** ★GraalJS 互換: スクリプトは boolean 値を setInt で入れることがある。 */
    public void setInt(String key, boolean value, int flag) { this.setBoolean(key, value, flag); }

    /** ★GraalJS 互換。 */
    public void setBoolean(String key, int value, int flag) { this.setBoolean(key, value != 0, flag); }

    /** ★GraalJS 互換。 */
    public void setDouble(String key, boolean value, int flag) { this.setBoolean(key, value, flag); }

    /** ★GraalJS 互換。 */
    public void setDouble(String key, int value, int flag) { this.setFloat(key, (float) value, flag); }

    /** ★GraalJS 互換。 */
    public void setFloat(String key, int value, int flag) { this.setFloat(key, (float) value, flag); }

    /** ★GraalJS 互換。 */
    public void setInt(String key, int value, boolean flag) { this.setInt(key, value, flag ? 1 : 0); }

    /** ★GraalJS 互換。 */
    public void setBoolean(String key, boolean value, boolean flag) { this.setBoolean(key, value, flag ? 1 : 0); }

    /** ★GraalJS 互換。 */
    public void setString(String key, String value, boolean flag) { this.setString(key, value, flag ? 1 : 0); }

    /** ★GraalJS 互換。 */
    public void setFloat(String key, double value, boolean flag) { this.setFloat(key, (float) value, flag); }

    public void setInt(String key, int value, int flag) {
        this.values.put(key, value);
    }

    public void setInt(String key, int value) {
        this.values.put(key, value);
    }

    public float getFloat(String key) {
        Object v = this.values.get(key);
        return v instanceof Number n ? n.floatValue() : 0.0F;
    }

    public void setFloat(String key, float value, int flag) {
        this.values.put(key, value);
    }

    public boolean getBoolean(String key) {
        Object v = this.values.get(key);
        return v instanceof Boolean b && b;
    }

    public void setBoolean(String key, boolean value, int flag) {
        this.values.put(key, value);
    }





    public String getString(String key) {
        Object v = this.values.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    public void setString(String key, String value, int flag) {
        this.values.put(key, value);
    }
}
