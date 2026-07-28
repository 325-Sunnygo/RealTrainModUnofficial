package jp.ngt.mcte.editor.filter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * フィルタのパラメータ一式 (neo mcte)。本家 MCTE Config + CfgParameter の移植。
 * フィルタは initConfig で「どんなパラメータを持つか」を宣言するだけでよく、
 * 入力 UI はこの宣言から自動で組まれる。
 */
public class FilterConfig {

    public enum Type { INT, FLOAT, BOOLEAN, STRING, ENUM }

    /** 1 個のパラメータ。 */
    public static final class Parameter {
        public final String name;
        public final Type type;
        public final double min;
        public final double max;
        /** ENUM のときの選択肢。 */
        public final List<String> choices;
        private Object value;

        private Parameter(String name, Type type, Object value, double min, double max, List<String> choices) {
            this.name = name;
            this.type = type;
            this.value = value;
            this.min = min;
            this.max = max;
            this.choices = choices;
        }

        public Object raw() {
            return value;
        }

        /** 文字列から設定する (UI からの入力口)。範囲外は丸める。 */
        public void set(String s) {
            try {
                switch (type) {
                    case INT -> value = (int) clamp(Integer.parseInt(s.trim()));
                    case FLOAT -> value = (float) clamp(Float.parseFloat(s.trim()));
                    case BOOLEAN -> value = Boolean.parseBoolean(s.trim());
                    case ENUM -> {
                        String t = s.trim();
                        if (choices != null && choices.contains(t)) {
                            value = t;
                        }
                    }
                    default -> value = s;
                }
            } catch (Exception ignored) {
                // 不正な入力は無視して前の値を残す (本家も入力途中で壊れないようにしている)
            }
        }

        private double clamp(double v) {
            return Math.max(min, Math.min(max, v));
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    private final Map<String, Parameter> params = new LinkedHashMap<>();

    public FilterConfig addInt(String name, int def, int min, int max) {
        params.put(name, new Parameter(name, Type.INT, def, min, max, null));
        return this;
    }

    public FilterConfig addFloat(String name, float def, float min, float max) {
        params.put(name, new Parameter(name, Type.FLOAT, def, min, max, null));
        return this;
    }

    public FilterConfig addBoolean(String name, boolean def) {
        params.put(name, new Parameter(name, Type.BOOLEAN, def, 0, 1, null));
        return this;
    }

    public FilterConfig addString(String name, String def) {
        params.put(name, new Parameter(name, Type.STRING, def, 0, 0, null));
        return this;
    }

    public FilterConfig addEnum(String name, String def, List<String> choices) {
        params.put(name, new Parameter(name, Type.ENUM, def, 0, 0, new ArrayList<>(choices)));
        return this;
    }

    public List<Parameter> parameters() {
        return new ArrayList<>(params.values());
    }

    public Parameter get(String name) {
        return params.get(name);
    }

    public int getInt(String name) {
        Parameter p = params.get(name);
        return p != null && p.value instanceof Integer i ? i : 0;
    }

    public float getFloat(String name) {
        Parameter p = params.get(name);
        return p != null && p.value instanceof Float f ? f : 0.0F;
    }

    public boolean getBoolean(String name) {
        Parameter p = params.get(name);
        return p != null && p.value instanceof Boolean b && b;
    }

    public String getString(String name) {
        Parameter p = params.get(name);
        return p == null || p.value == null ? "" : p.value.toString();
    }
}
