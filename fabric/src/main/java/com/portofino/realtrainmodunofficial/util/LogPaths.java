package com.portofino.realtrainmodunofficial.util;

/**
 * ログに出すファイルパスから利用者の個人情報を伏せるヘルパ。
 *
 * <p>不具合報告では latest.log をそのまま添付してもらうことが多い。パックの読み込みは
 * ホームディレクトリ配下 (macOS なら {@code /Users/<名前>/Library/...}) を走査するため、
 * 素のパスを出すと<b>報告者の OS ユーザー名が第三者に見える</b>。原因調査に必要なのは
 * 「どのパック/ファイルか」であって設置場所ではないので、ホーム配下は {@code ~} に畳む。
 *
 * <p>相対パス (パック内の {@code assets/.../train.json} 等) は個人情報を含まないので素通しする。
 */
public final class LogPaths {

    private static final String HOME = System.getProperty("user.home", "");

    private LogPaths() {
    }

    /**
     * ログ出力用にパスを無害化する。ホームディレクトリ配下なら先頭を {@code ~} に置き換える。
     * <p>slf4j は引数を遅延評価するため、戻り値はそのまま {@code {}} へ渡してよい。
     */
    public static Object safe(Object path) {
        if (path == null) {
            return null;
        }
        String s = String.valueOf(path);
        if (!HOME.isEmpty() && s.startsWith(HOME)) {
            return "~" + s.substring(HOME.length());
        }
        return s;
    }
}
