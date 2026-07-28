package jp.ngt.ngtlib.io;

import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

/**
 * 本家 NGTLib (jp.ngt.ngtlib.io.ScriptUtil) の忠実移植。
 * 本家は JDK 同梱 Nashorn (jdk.nashorn.*) を使用していたが、Java 21 では
 * スタンドアロン版 (org.openjdk.nashorn) を使用する。
 */
public final class ScriptUtil {
    private static NashornScriptEngineFactory SEM;

    private ScriptUtil() {
    }

    private static void init() {
        SEM = new NashornScriptEngineFactory();
    }

    /**
     * エンジン生成。本家 ScriptUtil:40 と同一 (-doe / --language=es6)。
     * RTMU 内でエンジンを作る箇所は全てここを通す (以前は別実装が並立していた)。
     */
    public static ScriptEngine createEngine() {
        if (SEM == null) {
            init();
        }
        // MOD のクラスローダを appLoader として渡す (Packages.jp.ngt.* 解決の鍵)
        ScriptEngine se = SEM.getScriptEngine(
                new String[]{"-doe", "--language=es6"},
                ScriptUtil.class.getClassLoader());
        // ★本家 doScript:47-51 と同じ mozilla_compat ロードをここへ引き上げる。
        // 本家は doScript の中でだけ読んでいたが、RTMU はエンジン生成箇所が複数あり、
        // 読んでいない経路 (サーバー/サウンドスクリプト) で importPackage が未定義になる。
        try {
            se.eval("load(\"nashorn:mozilla_compat.js\");");
        } catch (javax.script.ScriptException e) {
            NGTLog.debug("[ScriptUtil] mozilla_compat load failed: %s", e.getMessage());
        }
        return se;
    }

    /** JavaScriptの実行 */
    public static ScriptEngine doScript(String s) {
        ScriptEngine se = createEngine();
        try {
            if (se.toString().contains("Nashorn") || se.getClass().getName().contains("nashorn")) {
                // Java8ではimportPackageが使えないので、その対策 (本家コメントのまま)
                se.eval("load(\"nashorn:mozilla_compat.js\");");
            }

            se.eval(s);
            Object bindFails = se.get("__bindFails");
            if (bindFails != null && !bindFails.toString().isBlank()) {
                NGTLog.debug("[ScriptUtil] prelude bind failed: %s", bindFails.toString());
            }
            return se;
        } catch (ScriptException e) {
            throw new RuntimeException("Script exec error" + "\n" + s, e);
        }
    }

    public static Object doScriptFunction(ScriptEngine se, String func, Object... args) {
        // 軽量化作業の計測点。
        // 1 箇所で総実行時間を取れる (doScriptIgnoreError もこれを経由する)。
        if (!com.portofino.realtrainmodunofficial.perf.RtmuProfiler.enabled) {
            try {
                return ((Invocable) se).invokeFunction(func, args);
            } catch (NoSuchMethodException | ScriptException e) {
                throw new RuntimeException("Script exec error : " + func, e);
            }
        }
        long t0 = System.nanoTime();
        try {
            return ((Invocable) se).invokeFunction(func, args);
        } catch (NoSuchMethodException | ScriptException e) {
            throw new RuntimeException("Script exec error : " + func, e);
        } finally {
            com.portofino.realtrainmodunofficial.perf.RtmuProfiler.addScriptCall(System.nanoTime() - t0);
        }
    }

    /**
     * そのスクリプトに関数が定義されているか。
     * Nashorn は function foo{} をグローバルに束縛するので、束縛を引けば分かる。
     */
    /**
     * 関数の有無の記録 (エンジンごと)。
     * ★「無い」と断定するのは実際に呼んで NoSuchMethodException が出たときだけ。
     */
    private static final java.util.Map<ScriptEngine, java.util.Map<String, Boolean>> FUNCTION_CACHE =
        java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private static java.util.Map<String, Boolean> tableOf(ScriptEngine se) {
        return FUNCTION_CACHE.computeIfAbsent(se, k -> new java.util.concurrent.ConcurrentHashMap<>());
    }

    /**
     * その関数を呼びに行く価値があるか。
     * 過去に「無い」と確定したものだけ false。未確認は true (呼んで確かめる)。
     */
    public static boolean hasFunction(ScriptEngine se, String func) {
        if (se == null || func == null) {
            return false;
        }
        Boolean known = tableOf(se).get(func);
        return known == null || known;
    }

    /** 同じ失敗を何度もログに流さないための記録 (種類ごとに 1 回)。 */
    private static final java.util.Set<String> LOGGED_FAILURES =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * 失敗しても続行するスクリプト呼び出し。
     * 定義されていないと分かっている関数は呼ばない。
     */
    public static Object doScriptIgnoreError(ScriptEngine se, String func, Object... args) {
        if (se == null || func == null) {
            return null;
        }
        java.util.Map<String, Boolean> table = tableOf(se);
        if (Boolean.FALSE.equals(table.get(func))) {
            return null;
        }
        try {
            Object result = doScriptFunction(se, func, args);
            table.put(func, Boolean.TRUE);
            return result;
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof NoSuchMethodException) {
                // ここで初めて「無い」と確定する。以後は呼ばない。
                table.put(func, Boolean.FALSE);
                return null;
            }
            // 中身のエラーは関数が「ある」証拠。次も呼ぶ。ログは種類ごとに 1 回。
            table.put(func, Boolean.TRUE);
            String key = func + "|" + cause;
            if (LOGGED_FAILURES.size() < 256 && LOGGED_FAILURES.add(key)) {
                NGTLog.debug("[ScriptUtil] %s failed: %s", func, String.valueOf(cause));
            }
            return null;
        }
    }

    public static Object getScriptField(ScriptEngine se, String fieldName) {
        return se.get(fieldName);
    }
}
