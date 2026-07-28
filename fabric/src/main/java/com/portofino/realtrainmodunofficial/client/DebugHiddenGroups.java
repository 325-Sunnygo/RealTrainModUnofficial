package com.portofino.realtrainmodunofficial.client;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>モデルのグループを名前で一時的に隠す</b> (調査用)。
 *
 * <p>「変な板が出ている」「この部品だけ描かれない」といった報告は、
 * <b>どの部品なのかを先に確定させないと直しようがない</b>。当てずっぽうで
 * 描画経路を変えても当たらず、ユーザーに何度も確認させることになる。
 *
 * <p>{@code /rtm hidegroup <名前>} で切り替える。隠れれば犯人が確定する。
 * 原因が分かるまでの回避策としても使える。
 *
 * <p>クライアント側だけの一時設定で、保存しない (ワールドを出ると戻る)。
 */
public final class DebugHiddenGroups {

    private static final Set<String> HIDDEN = ConcurrentHashMap.newKeySet();

    private DebugHiddenGroups() {
    }

    /** @return true = 以後隠す / false = 表示に戻した */
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

    /** 隠す指定があるか。<b>1 つも指定が無ければ即 false</b> (通常時の負荷をゼロにする)。 */
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
