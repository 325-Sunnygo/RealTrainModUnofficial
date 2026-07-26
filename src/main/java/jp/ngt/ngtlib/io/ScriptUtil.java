package jp.ngt.ngtlib.io;

import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

/**
 * 本家 NGTLib (jp.ngt.ngtlib.io.ScriptUtil) の忠実移植。
 * 本家は JDK 同梱 Nashorn (jdk.nashorn.*) を使用していたが、Java 21 では
 * スタンドアロン版 (org.openjdk.nashorn) を使用する。エンジン挙動・フラグ
 * ("-doe", "--language=es6") および mozilla_compat.js ロードは本家と同一。
 *
 * 1.21 移植差分:
 * - LaunchClassLoader のクラスローダ除外処理は不要 (FML 構造が異なる) ため削除。
 * - NeoForge の TransformingClassLoader 下でスクリプトから Packages.jp.ngt.* を
 *   解決できるよう、MOD 自身のクラスローダを明示的に渡す。
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
        return SEM.getScriptEngine(
                new String[]{"-doe", "--language=es6"},
                ScriptUtil.class.getClassLoader());
    }

    /**
     * JavaScriptの実行
     */
    public static ScriptEngine doScript(String s) {
        ScriptEngine se = createEngine();
        try {
            if (se.toString().contains("Nashorn") || se.getClass().getName().contains("nashorn")) {
                // Java8ではimportPackage()が使えないので、その対策 (本家コメントのまま)
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
        //軽量化作業の計測点。描画用 render も含め全スクリプト呼び出しがここを通るので、
        //1 箇所で総実行時間を取れる (doScriptIgnoreError もこれを経由する)。
        //計測 OFF のときは nanoTime を呼ばない。
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

    public static Object doScriptIgnoreError(ScriptEngine se, String func, Object... args) {
        try {
            return doScriptFunction(se, func, args);
        } catch (Exception e) {
            //本家は printStackTrace のみだが、本番ログで追えるよう NGTLog にも出す
            NGTLog.debug("[ScriptUtil] %s failed: %s", func, String.valueOf(e.getCause() != null ? e.getCause() : e));
            e.printStackTrace();
            return null;
        }
    }

    public static Object getScriptField(ScriptEngine se, String fieldName) {
        return se.get(fieldName);
    }
}
