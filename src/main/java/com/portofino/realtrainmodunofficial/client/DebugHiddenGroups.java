package com.portofino.realtrainmodunofficial.client;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** モデルのグループを名前で一時的に隠す (調査用)。 */
public final class DebugHiddenGroups {

    private static final Set<String> HIDDEN = ConcurrentHashMap.newKeySet();

    private DebugHiddenGroups() {
    }

    
    public static boolean toggle(String groupName) {
        String key = normalize(groupName);
        if (key.isEmpty()) {
            return false;
        }
        if (HIDDEN.remove(key)) {
            return false;
        }
        HIDDEN.add(key);
        return true;
    }

    public static void clear() {
        HIDDEN.clear();
    }

    public static Set<String> listHidden() {
        return Set.copyOf(HIDDEN);
    }

    /** 隠す指定があるか。1 つも指定が無ければ即 false (通常時の負荷をゼロにする)。 */
    public static boolean isHidden(String groupName) {
        if (HIDDEN.isEmpty() || groupName == null) {
            return false;
        }
        return HIDDEN.contains(normalize(groupName));
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }
}
