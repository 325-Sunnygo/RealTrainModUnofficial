package com.portofino.realtrainmodunofficial.util;

/**
 * ログに出すファイルパスから利用者の個人情報を伏せるヘルパ。
 * 不具合報告では latest.log をそのまま添付してもらうことが多い。
 */
public final class LogPaths {

    private static final String HOME = System.getProperty("user.home", "");

    private LogPaths() {
    }

    /**
     * ログ出力用にパスを無害化する。ホームディレクトリ配下なら先頭を ~ に置き換える。
     * slf4j は引数を遅延評価するため、戻り値はそのまま {} へ渡してよい。
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
