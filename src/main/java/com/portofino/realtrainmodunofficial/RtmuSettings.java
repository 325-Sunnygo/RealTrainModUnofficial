package com.portofino.realtrainmodunofficial;

import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RTMU クライアント設定 (ポーズメニューの「RTMU設定」から変更)。
 * <ul>
 *   <li>autoCant: カーブに自動でカントを付けるか (ON/OFF)</li>
 *   <li>autoHeightLevel: レール敷設時の自動高さ調整レベル (1〜5、レンチ高さ単位=1/16ブロック)</li>
 * </ul>
 * クライアントの値はファイルへ永続化し、サーバーへは {@code RtmuSettingsPayload} で同期する
 * (レール生成はサーバー側で行うため、敷設プレイヤーの設定をサーバーが参照する)。
 */
public final class RtmuSettings {

    // ---- クライアント自身の設定 ----
    public static boolean autoCant = false;
    /** 1〜5。レール敷設時に付ける高さ (height byte)。0=無効相当だが最低1。 */
    public static int autoHeightLevel = 1;

    // ---- サーバー側: プレイヤー別に同期された設定 ----
    private static final Map<UUID, Boolean> SERVER_AUTO_CANT = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> SERVER_AUTO_HEIGHT = new ConcurrentHashMap<>();

    private static final Path FILE = FMLPaths.CONFIGDIR.get()
            .resolve("realtrainmodunofficial").resolve("rtmu_settings.properties");

    private RtmuSettings() {
    }

    public static int clampLevel(int level) {
        return Math.max(1, Math.min(5, level));
    }

    // ===== クライアント: 永続化 =====

    public static void load() {
        try {
            if (!Files.exists(FILE)) {
                return;
            }
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(FILE)) {
                p.load(in);
            }
            autoCant = Boolean.parseBoolean(p.getProperty("autoCant", "false"));
            autoHeightLevel = clampLevel(parseInt(p.getProperty("autoHeightLevel", "1"), 1));
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("RTMU: failed to load settings", e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Properties p = new Properties();
            p.setProperty("autoCant", Boolean.toString(autoCant));
            p.setProperty("autoHeightLevel", Integer.toString(autoHeightLevel));
            try (OutputStream out = Files.newOutputStream(FILE)) {
                p.store(out, "RTMU client settings");
            }
        } catch (IOException e) {
            RealTrainModUnofficial.LOGGER.warn("RTMU: failed to save settings", e);
        }
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    // ===== サーバー: プレイヤー別の同期値 =====

    public static void setServerValues(UUID player, boolean autoCant, int autoHeightLevel) {
        SERVER_AUTO_CANT.put(player, autoCant);
        SERVER_AUTO_HEIGHT.put(player, clampLevel(autoHeightLevel));
    }

    public static boolean serverAutoCant(UUID player) {
        return player != null && SERVER_AUTO_CANT.getOrDefault(player, Boolean.FALSE);
    }

    /** 0 = 自動高さ無効 (プレイヤー未同期)。1〜5 = レベル。 */
    public static int serverAutoHeightLevel(UUID player) {
        return player == null ? 0 : SERVER_AUTO_HEIGHT.getOrDefault(player, 0);
    }
}
