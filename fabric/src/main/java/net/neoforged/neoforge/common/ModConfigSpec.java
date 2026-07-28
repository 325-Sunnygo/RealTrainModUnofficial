package net.neoforged.neoforge.common;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * シム: MDK テンプレの Config.java を生かすための最小実装。
 * 値は定義時の既定値を返すだけ (ファイル保存なし)。RTMU 本体は独自 properties。
 */
public class ModConfigSpec {

    public static class ConfigValue<T> implements Supplier<T> {
        private final T defaultValue;

        ConfigValue(T defaultValue) {
            this.defaultValue = defaultValue;
        }

        @Override
        public T get() {
            return defaultValue;
        }

        public T getAsInt() {
            return defaultValue;
        }
    }

    public static class BooleanValue extends ConfigValue<Boolean> {
        BooleanValue(Boolean v) {
            super(v);
        }
    }

    public static class IntValue extends ConfigValue<Integer> {
        IntValue(Integer v) {
            super(v);
        }
    }

    public static class Builder {
        public Builder comment(String... comment) {
            return this;
        }

        public Builder push(String path) {
            return this;
        }

        public Builder pop() {
            return this;
        }

        public BooleanValue define(String path, boolean defaultValue) {
            return new BooleanValue(defaultValue);
        }

        public <T> ConfigValue<T> define(String path, T defaultValue) {
            return new ConfigValue<>(defaultValue);
        }

        public IntValue defineInRange(String path, int defaultValue, int min, int max) {
            return new IntValue(defaultValue);
        }

        public <T> ConfigValue<List<? extends T>> defineListAllowEmpty(
                String path, List<? extends T> defaultValue, Predicate<Object> validator) {
            return new ConfigValue<>(defaultValue);
        }

        public <T> ConfigValue<List<? extends T>> defineListAllowEmpty(
                String path, List<? extends T> defaultValue, Supplier<T> newElement, Predicate<Object> validator) {
            return new ConfigValue<>(defaultValue);
        }

        public ModConfigSpec build() {
            return new ModConfigSpec();
        }
    }
}
