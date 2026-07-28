package com.portofino.realtrainmodunofficial.pack;

import com.portofino.realtrainmodunofficial.rail.RailDefinition;
import com.portofino.realtrainmodunofficial.rail.RailPackLoader;
import com.portofino.realtrainmodunofficial.rail.RailRegistry;
import com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition;
import com.portofino.realtrainmodunofficial.vehicle.VehiclePackLoader;
import com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 前提パックの不足チェック。
 * <p>
 * 分割配布のパックは、車両やレールの定義だけを持ち、実体のスクリプトは前提パック側に置く
 * ことがある (例: 車両パックが定義、描画スクリプトはベースの共通パック)。前提パックを
 * 入れ忘れるとスクリプトが解決できず、RTMU はこれまで警告だけ出してスクリプト無しで
 * 描いていた。結果、前面ガラスやドアが崩れた車両が「一応動いてしまう」ため、
 * 何が足りないのか分からないまま遊ぶことになっていた。
 * <p>
 * 本家 RTM は読み込み中の例外をそのまま {@code CrashReport} にして起動を止めるが、
 * ここでは<b>止めずに読み込みは通す</b>。足りないものは覚えておき、タイトル画面で
 * README 同意画面を全部さばいた後に警告画面 (OK ボタン) で知らせる。
 */
public final class PackPrerequisiteCheck {

    /** 警告画面に並べる最大件数 (同じ前提パック不足で何百件も出るため)。 */
    private static final int MAX_LISTED = 40;

    /** 解決できなかった参照。タイトル画面の警告で読む。 */
    private static final List<String> MISSING = new ArrayList<>();

    private PackPrerequisiteCheck() {
    }

    /** 不足があるか (タイトル画面で警告を出すかの判定)。 */
    public static synchronized boolean hasMissing() {
        return !MISSING.isEmpty();
    }

    /** 不足の一覧 (表示用に上限で切ってある)。 */
    public static synchronized List<String> getMissing() {
        return Collections.unmodifiableList(new ArrayList<>(MISSING));
    }

    /**
     * 全パックのロード後に呼ぶ。解決できない前提を集めておく (ここでは止めない)。
     * <p>スクリプトの解決は<b>全パック横断の索引</b>まで見た上での判定なので、
     * ここで見つからなければ本当にファイルがどこにも無い。
     */
    public static void verify() {
        List<String> missing = new ArrayList<>();
        //★同じ (パック, スクリプト) を二度調べない。
        //readScriptContent はパック zip を<b>先頭から展開して走査</b>するので、
        //車両 1 両ごとに呼ぶと数百 MB のパックを何百回も舐めることになり、
        //起動が固まったように見える。1 パック 1 スクリプトにつき 1 回で済ませる。
        Map<String, Boolean> resolved = new HashMap<>();

        for (VehicleDefinition def : VehicleRegistry.getAll()) {
            String path = def == null ? null : def.getScriptPath();
            if (path == null || path.isBlank()) {
                continue;
            }
            boolean ok = resolved.computeIfAbsent(def.getPackName() + '\0' + path,
                k -> resolvable(path, () -> VehiclePackLoader.readScriptContent(def)));
            if (!ok) {
                missing.add(String.format("車両 %s (パック: %s) → %s", def.getId(), def.getPackName(), path));
            }
        }

        for (RailDefinition def : RailRegistry.getAll()) {
            String path = def == null ? null : def.getScriptPath();
            if (path == null || path.isBlank()) {
                continue;
            }
            boolean ok = resolved.computeIfAbsent(def.getPackName() + '\0' + path,
                k -> resolvable(path, () -> RailPackLoader.readScriptContent(def)));
            if (!ok) {
                missing.add(String.format("レール %s (パック: %s) → %s", def.getId(), def.getPackName(), path));
            }
        }

        com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.info(
            "[RTMU] prerequisite check: {} scripts, {} missing", resolved.size(), missing.size());
        synchronized (PackPrerequisiteCheck.class) {
            MISSING.clear();
            MISSING.addAll(missing.size() > MAX_LISTED ? missing.subList(0, MAX_LISTED) : missing);
            if (missing.size() > MAX_LISTED) {
                MISSING.add(String.format("... 他 %d 件", missing.size() - MAX_LISTED));
            }
        }
        if (missing.isEmpty()) {
            return;
        }
        //サーバーやランチャーからも追えるようログにも残す。
        com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.warn(
            "[RTMU] {}", buildMessage(missing));
    }

    /**
     * スクリプトが解決できるか。
     * <p>まず<b>全パック横断の索引</b>で引く (索引は 1 回作れば以降は O(1))。
     * ここで見つかれば、重いパック走査は一切しない。索引で見つからなかったものだけ、
     * 本来の読み取り経路 (自パックの zip 走査) まで確認する。
     */
    private static boolean resolvable(String scriptPath, Supplier<String> fullRead) {
        try {
            if (jp.ngt.ngtlib.io.NGTFileLoader.findAsset(scriptPath) != null) {
                return true;
            }
        } catch (Exception ignored) {
            //索引が使えないときは下の本来の経路で判定する
        }
        try {
            return fullRead.get() != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static String buildMessage(List<String> missing) {
        StringBuilder sb = new StringBuilder();
        sb.append("前提パックが足りません。以下の定義が参照しているスクリプトが、")
          .append("導入済みのどのパックにも見つかりませんでした。\n")
          .append("(定義だけのパックを入れて、実体を持つ前提パックを入れ忘れているときに起きます)\n\n");
        int shown = Math.min(missing.size(), MAX_LISTED);
        for (int i = 0; i < shown; i++) {
            sb.append("  ").append(missing.get(i)).append('\n');
        }
        if (missing.size() > shown) {
            sb.append("  ... 他 ").append(missing.size() - shown).append(" 件\n");
        }
        return sb.toString();
    }
}
