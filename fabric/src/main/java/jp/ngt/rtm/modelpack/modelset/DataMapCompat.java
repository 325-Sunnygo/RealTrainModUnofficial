package jp.ngt.rtm.modelpack.modelset;

import java.util.HashMap;
import java.util.Map;

/**
 * 本家 ResourceState.getDataMap() 相当 (スクリプトが値を出し入れする箱)。
 * レールなど「保存する必要がない」対象向けの、その場かぎりの実装。
 */
public class DataMapCompat {
    private final Map<String, Object> values = new HashMap<>();

    public boolean contains(String key) {
        return this.values.containsKey(key);
    }

    public int getInt(String key) {
        Object v = this.values.get(key);
        return v instanceof Number n ? n.intValue() : 0;
    }

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
