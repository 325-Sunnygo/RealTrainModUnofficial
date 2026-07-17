package com.myname.legacyloader.bridge.fml;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 1.7.10 の {@code cpw.mods.fml.relauncher.ReflectionHelper} の実装ブリッジ。
 * ClassTransformer が旧クラス参照をこのクラスへ差し替える (Mappings 参照)。
 *
 * <p>スタブ生成に任せると {@code getPrivateValue} 等のメソッドが存在せず
 * {@code NoSuchMethodError} でクラッシュする (Bamboo 等が使用) ため、実装を持つ。
 * 原典と同じく、フィールドが見つからない/アクセスできない場合は
 * {@link UnableToAccessFieldException} を投げる (呼び出し側の catch はこれを期待している)。
 */
public final class LegacyReflectionHelper {

    private LegacyReflectionHelper() {
    }

    @SuppressWarnings("unchecked")
    public static <T, E> T getPrivateValue(Class<? super E> classToAccess, E instance, int fieldIndex) {
        try {
            Field f = classToAccess.getDeclaredFields()[fieldIndex];
            f.setAccessible(true);
            return (T) f.get(instance);
        } catch (Exception e) {
            throw new UnableToAccessFieldException(new String[]{"#" + fieldIndex}, e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T, E> T getPrivateValue(Class<? super E> classToAccess, E instance, String... fieldNames) {
        try {
            return (T) findField(classToAccess, fieldNames).get(instance);
        } catch (Exception e) {
            throw new UnableToAccessFieldException(fieldNames, e);
        }
    }

    public static <T, E> void setPrivateValue(Class<? super T> classToAccess, T instance, E value, int fieldIndex) {
        try {
            Field f = classToAccess.getDeclaredFields()[fieldIndex];
            f.setAccessible(true);
            f.set(instance, value);
        } catch (Exception e) {
            throw new UnableToAccessFieldException(new String[]{"#" + fieldIndex}, e);
        }
    }

    public static <T, E> void setPrivateValue(Class<? super T> classToAccess, T instance, E value, String... fieldNames) {
        try {
            findField(classToAccess, fieldNames).set(instance, value);
        } catch (Exception e) {
            throw new UnableToAccessFieldException(fieldNames, e);
        }
    }

    public static Field findField(Class<?> clazz, String... fieldNames) {
        Exception failure = null;
        for (String name : fieldNames) {
            if (name == null) {
                continue;
            }
            try {
                Field f = clazz.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (Exception e) {
                failure = e;
            }
        }
        throw new UnableToFindFieldException(fieldNames, failure);
    }

    public static Method findMethod(Class<?> clazz, Object instance, String[] methodNames, Class<?>... methodTypes) {
        Exception failure = null;
        for (String name : methodNames) {
            if (name == null) {
                continue;
            }
            try {
                Method m = clazz.getDeclaredMethod(name, methodTypes);
                m.setAccessible(true);
                return m;
            } catch (Exception e) {
                failure = e;
            }
        }
        throw new UnableToFindMethodException(methodNames, failure);
    }

    //--- 原典の例外クラス群 (RuntimeException=unchecked。catch 型が Throwable のサブクラスであること) ---

    public static class UnableToAccessFieldException extends RuntimeException {
        public String[] fieldNameList;

        public UnableToAccessFieldException(String[] fieldNames, Throwable cause) {
            super(cause);
            this.fieldNameList = fieldNames;
        }
    }

    public static class UnableToFindFieldException extends RuntimeException {
        public String[] fieldNameList;

        public UnableToFindFieldException(String[] fieldNames, Throwable cause) {
            super(cause);
            this.fieldNameList = fieldNames;
        }
    }

    public static class UnableToFindMethodException extends RuntimeException {
        public String[] methodNameList;

        public UnableToFindMethodException(String[] methodNames, Throwable cause) {
            super(cause);
            this.methodNameList = methodNames;
        }
    }
}
