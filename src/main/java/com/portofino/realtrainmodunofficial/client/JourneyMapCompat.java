package com.portofino.realtrainmodunofficial.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * JourneyMap のミニマップを<b>選択画面を開いている間だけ一時的に無効化</b>するソフト依存ヘルパー。
 * <p>
 * JourneyMap のミニマップは {@code RenderGuiEvent.Pre} で自前のリスナーが無条件に描画しており、
 * {@code hideGui} も NeoForge のイベントキャンセルも無視するため、RTMU 側からは
 * {@code MiniMapProperties.enabled} (ミニマップのオン/オフ) をリフレクションで一時的に false に
 * するしか消す方法が無い。JourneyMap 未導入時や API 変更時は静かに無効化する (try/catch)。
 */
public final class JourneyMapCompat {
    private static Boolean savedEnabled;
    private static boolean unavailable;

    private JourneyMapCompat() {
    }

    /** suppress=true でミニマップを一時オフ、false で元に戻す。 */
    public static void setSuppressed(boolean suppress) {
        if (unavailable) {
            return;
        }
        try {
            Class<?> clientClass = Class.forName("journeymap.client.JourneymapClient");
            Object instance = clientClass.getMethod("getInstance").invoke(null);
            Object props = clientClass.getMethod("getActiveMiniMapProperties").invoke(instance);
            if (props == null) {
                return;
            }
            Field enabledField = props.getClass().getField("enabled");
            Object boolField = enabledField.get(props);
            Method get = boolField.getClass().getMethod("get");
            Method set = findSetter(boolField.getClass());
            if (set == null) {
                unavailable = true;
                return;
            }
            if (suppress) {
                if (savedEnabled == null) {
                    savedEnabled = (Boolean) get.invoke(boolField);
                }
                set.invoke(boolField, Boolean.FALSE);
            } else if (savedEnabled != null) {
                set.invoke(boolField, savedEnabled);
                savedEnabled = null;
            }
        } catch (ClassNotFoundException notInstalled) {
            unavailable = true;
        } catch (Throwable ignored) {
            // API 変更等。無害に諦める。
        }
    }

    private static Method findSetter(Class<?> boolFieldClass) {
        for (Method m : boolFieldClass.getMethods()) {
            if (m.getName().equals("set") && m.getParameterCount() == 1) {
                return m;
            }
        }
        return null;
    }
}
