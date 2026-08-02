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
 * autoCant: カーブに自動でカントを付けるか (ON/OFF)
 * autoHeightLevel: レール敷設時の自動高さ調整レベル (1〜5、レンチ高さ単位=1/16ブロック)
 */
public final class RtmuSettings {

    // ---- クライアント自身の設定 ----
    public static boolean autoCant = false;
    /**
     * レール敷設時の自動高さ。0 = OFF、1〜16 = 有効で、実際のレール高さ byte = level-1 (0〜15、1/16ブロック単位)。
     * これで 9,10 など任意の高さを指定できる (以前は 5 段階固定だった)。
     */
    public static int autoHeightLevel = 1;
    /** レール描画距離 (ブロック)。既定 128。長いほど遠くのレールが描画されるが負荷は上がる。 */
    public static int railRenderDistance = 128;

    // ---- 軽量化 (すべて opt-in。既定値は「見た目が変わらない」側) ----
    /**
     * 車両描画距離 (ブロック)。0 = 無制限 (バニラどおり)。
     * 車両は丸ごと描画をスキップする。
     */
    public static int vehicleRenderDistance = 0;



    // ---- 表示 (見た目だけの設定。読み込み自体は変えない) ----
    /**
     * MOD に同梱している既定のモデル (車両・照明など) をモデル選択画面から隠すか。
     *
     * <p><b>読み込みは止めない。</b> 既に設置されている物や、パックがこれらを参照している場合に
     * 壊れてしまうため、隠すのは<b>選択画面の一覧だけ</b>にする。
     */
    public static boolean hideBundledModels = false;

    /** その pack 名が MOD 同梱の物か (選択画面で隠す判定)。 */
    public static boolean isBundledPack(String packName) {
        if (packName == null || packName.isBlank()) {
            return false;
        }
        String n = packName.trim();
        return n.equalsIgnoreCase(RealTrainModUnofficial.MODID)
            || BundledPackStore.isBundledPackName(n);
    }

    // ---- カメラ (撮り鉄カメラの装着レンズ。クライアントのみ・所持アイテムで切替) ----

    // ---- 乗客シミュレーション ----
    /**
     * ワールド全体に湧く乗客 NPC の最大数。0〜100、および 101 = 無制限。既定 30。
     * サーバー側の値なので、設定画面での変更は  でサーバーへ送る
     * (ワールド全体で 1 つの値。最後に送ったプレイヤーの値が反映される)。
     */
    public static final int MAX_PASSENGERS_UNLIMITED = 101;
    public static int maxPassengers = 30;
    /** サーバー側で保持する現在の乗客上限 (全プレイヤー共通の 1 値)。 */
    private static volatile int SERVER_MAX_PASSENGERS = 30;

    // ---- サーバー側: プレイヤー別に同期された設定 ----
    private static final Map<UUID, Boolean> SERVER_AUTO_CANT = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> SERVER_AUTO_HEIGHT = new ConcurrentHashMap<>();

    private static final Path FILE = FMLPaths.CONFIGDIR.get()
            .resolve("realtrainmodunofficial").resolve("rtmu_settings.properties");

    private RtmuSettings() {
    }

    /** 0 = OFF、1〜16 = 有効 (高さ 0〜15)。 */
    public static int clampLevel(int level) {
        return Math.max(0, Math.min(16, level));
    }

    /** レール描画距離を 64〜512 に丸める。 */
    public static int clampRailRenderDistance(int d) {
        return Math.max(64, Math.min(512, d));
    }

    /** 乗客上限を 0〜101 (101=無制限) に丸める。 */
    public static int clampMaxPassengers(int v) {
        return Math.max(0, Math.min(MAX_PASSENGERS_UNLIMITED, v));
    }

    /** サーバー側: 実効の乗客上限 (無制限なら Integer.MAX_VALUE)。StationBlockEntity が読む。 */
    public static int serverMaxPassengers() {
        int v = SERVER_MAX_PASSENGERS;
        return v >= MAX_PASSENGERS_UNLIMITED ? Integer.MAX_VALUE : v;
    }

    public static void setServerMaxPassengers(int v) {
        SERVER_MAX_PASSENGERS = clampMaxPassengers(v);
    }

    /** 車両描画距離。0 = 無制限、それ以外は 32〜256 に丸める。 */
    public static int clampVehicleRenderDistance(int d) {
        if (d <= 0) {
            return 0;
        }
        return Math.max(32, Math.min(256, d));
    }

    /**
     * 車両描画距離を超えているか (カメラ座標との距離で判定)。
     * 0 = 無制限なら常に false。全車両レンダラーの shouldRender から呼ぶ。
     */
    public static boolean beyondVehicleRenderDistance(double ex, double ey, double ez,
                                                      double camX, double camY, double camZ) {
        int limit = vehicleRenderDistance;
        if (limit <= 0) {
            return false;
        }
        double dx = ex - camX;
        double dy = ey - camY;
        double dz = ez - camZ;
        return dx * dx + dy * dy + dz * dz > (double) limit * (double) limit;
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
            railRenderDistance = clampRailRenderDistance(parseInt(p.getProperty("railRenderDistance", "128"), 128));
            vehicleRenderDistance = clampVehicleRenderDistance(parseInt(p.getProperty("vehicleRenderDistance", "0"), 0));
            maxPassengers = clampMaxPassengers(parseInt(p.getProperty("maxPassengers", "30"), 30));
            hideBundledModels = Boolean.parseBoolean(p.getProperty("hideBundledModels", "false"));
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
            p.setProperty("railRenderDistance", Integer.toString(railRenderDistance));
            p.setProperty("vehicleRenderDistance", Integer.toString(vehicleRenderDistance));
            p.setProperty("maxPassengers", Integer.toString(maxPassengers));
            p.setProperty("hideBundledModels", Boolean.toString(hideBundledModels));
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
