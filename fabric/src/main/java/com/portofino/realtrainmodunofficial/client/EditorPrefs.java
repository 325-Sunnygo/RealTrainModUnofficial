package com.portofino.realtrainmodunofficial.client;

import net.neoforged.fml.loading.FMLPaths;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * フィルタごとに最後に使った設定を覚えておく (neo mcte 追加)。
 * 本家も MCTEU も、画面を閉じると設定は初期値へ戻る。
 */
public final class EditorPrefs {

    /** フィルタ名 → (パラメータ名 → 値)。 */
    private static final Map<String, Map<String, String>> VALUES = new LinkedHashMap<>();
    private static boolean loaded;

    private EditorPrefs() {
    }

    private static Path file() {
        return FMLPaths.GAMEDIR.get().resolve("mcte").resolve("prefs.txt");
    }

    public static synchronized String get(String filter, String param, String fallback) {
        load();
        Map<String, String> m = VALUES.get(filter);
        String v = m == null ? null : m.get(param);
        return v == null ? fallback : v;
    }

    public static synchronized void put(String filter, String param, String value) {
        load();
        VALUES.computeIfAbsent(filter, k -> new LinkedHashMap<>()).put(param, value);
    }

    /** 実行のたびに呼ぶ。書き込みに失敗しても黙って続ける。 */
    public static synchronized void save() {
        try {
            Path p = file();
            Files.createDirectories(p.getParent());
            try (BufferedWriter w = Files.newBufferedWriter(p)) {
                for (var e : VALUES.entrySet()) {
                    for (var kv : e.getValue().entrySet()) {
                        // 値に改行やタブが入ることは無い (入力欄が 1 行のため)
                        w.write(e.getKey() + "\t" + kv.getKey() + "\t" + kv.getValue());
                        w.newLine();
                    }
                }
            }
        } catch (Exception ignored) {
            // 保存できなくても動作には影響しない
        }
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            Path p = file();
            if (!Files.isRegularFile(p)) {
                return;
            }
            List<String> lines = Files.readAllLines(p);
            for (String line : lines) {
                String[] a = line.split("\t", 3);
                if (a.length == 3) {
                    VALUES.computeIfAbsent(a[0], k -> new LinkedHashMap<>()).put(a[1], a[2]);
                }
            }
        } catch (Exception ignored) {
            // 壊れていたら読めた分だけで進む
        }
    }
}
