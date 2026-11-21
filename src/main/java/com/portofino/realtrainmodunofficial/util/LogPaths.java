package com.portofino.realtrainmodunofficial.util;

/** ログに出すファイルパスから利用者の個人情報を伏せるヘルパ。 */
public final class LogPaths {

    private static final String HOME = System.getProperty("user.home", "");

    private LogPaths() {
    }

    /** ログ出力用にパスを無害化する。ホームディレクトリ配下なら先頭を ~ に置き換える。 */
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
