package jp.ngt.ngtlib.io;

import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

/** 本家 NGTLib (jp.ngt.ngtlib.io.ScriptUtil) の忠実移植。 */
public final class ScriptUtil {
    private static NashornScriptEngineFactory SEM;

    private ScriptUtil() {
    }

    private static void init() {
        SEM = new NashornScriptEngineFactory();
    }

    /** エンジン生成。本家 ScriptUtil:40 と同一 (-doe / --language=es… */
    public static ScriptEngine createEngine() {
        if (SEM == null) {
            init();
        }
        // MOD のクラスローダを appLoader として渡す (Packages.jp.ngt.* 解…
        ScriptEngine se = SEM.getScriptEngine(
                new String[]{"-doe", "--language=es6"},
                ScriptUtil.class.getClassLoader());
        // ★本家 doScript:47-51 と同じ mozilla_compat ロードをここへ引き上げ…
        try {
            se.eval("load(\"nashorn:mozilla_compat.js\");");
        } catch (javax.script.ScriptException e) {
            NGTLog.debug("[ScriptUtil] mozilla_compat load failed: %s", e.getMessage());
        }
        return se;
    }

    /** JavaScriptの実行 (本家 ScriptUtil.doScript(String) と同じ… */
    public static ScriptEngine doScript(String s) {
        return doScript(s, "<unnamed script>");
    }

    /** 本家 ScriptUtil.doScript(String, String) 完全移植。 */
    public static ScriptEngine doScript(String s, String fileName) {
        ScriptEngine se = createEngine();
        try {
            se.put(ScriptEngine.FILENAME, fileName);
            // createEngine() が本家と同じ mozilla_compat を読み込み済み。
            se.eval(s);
            Object bindFails = se.get("__bindFails");
            if (bindFails != null && !bindFails.toString().isBlank()) {
                NGTLog.debug("[ScriptUtil] prelude bind failed: %s", bindFails.toString());
            }
            return se;
        } catch (ScriptException e) {
            throw new RuntimeException("Script exec error in " + fileName, e);
        }
    }

    /** 本家 ScriptUtil.doScript(ResourceLocation) 完全移植。 */
    public static ScriptEngine doScript(jp.ngt.mccompat.ResourceLocation resource) {
        return doScript(NGTText.getText(resource, true), resource.getResourcePath());
    }

    /** 本家 (実 ResourceLocation) 版。Java 側から本家シグネチャで呼ぶ用。 */
    public static ScriptEngine doScript(net.minecraft.resources.ResourceLocation resource) {
        return doScript(NGTText.getText(resource, true), resource.getPath());
    }

    public static Object doScriptFunction(ScriptEngine se, String func, Object... args) {
        try {
            return ((Invocable) se).invokeFunction(func, args);
        } catch (NoSuchMethodException | ScriptException e) {
            throw new RuntimeException("Script exec error : " + func, e);
        }
    }

    /** そのスクリプトに関数が定義されているか。 Nashorn は function foo{} をグロ… */
    /** Inspect the current binding: scripts can install… */
    public static boolean hasFunction(ScriptEngine se, String func) {
        if (se == null || func == null) {
            return false;
        }
        Object value = se.get(func);
        return value instanceof org.openjdk.nashorn.api.scripting.JSObject function
                && function.isFunction();
    }

    /** 同じ失敗を何度もログに流さないための記録 (種類ごとに 1 回)。 */
    private static final java.util.Set<String> LOGGED_FAILURES =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 失敗しても続行するスクリプト呼び出し。 関数は後から定義され得るため、呼び出すたびに現在の定義を使… */
    public static Object doScriptIgnoreError(ScriptEngine se, String func, Object... args) {
        if (se == null || func == null) {
            return null;
        }
        try {
            Object result = doScriptFunction(se, func, args);
            return result;
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof NoSuchMethodException) {
                // 未定義のイベントは無視する。後から定義された場合は次回呼び出す。
                return null;
            }
            // 中身のエラーは関数が「ある」証拠。次も呼ぶ。ログは種類ごとに 1 回。
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
