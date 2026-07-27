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
        ScriptEngine se = SEM.getScriptEngine(
                new String[]{"-doe", "--language=es6"},
                ScriptUtil.class.getClassLoader());
        //★本家 doScript:47-51 と同じ mozilla_compat ロードをここへ引き上げる。
        //本家は doScript の中でだけ読んでいたが、RTMU はエンジン生成箇所が複数あり、
        //読んでいない経路 (サーバー/サウンドスクリプト) で importPackage が未定義になる。
        //旧 injectScriptCompatibility が importPackage を no-op で生やして隠していたため、
        //それを撤去した時点で「"importPackage" is not defined」が表面化した。
        //importPackage は未定義名しか束縛しないので、後段の PRELUDE の束縛を壊さない。
        try {
            se.eval("load(\"nashorn:mozilla_compat.js\");");
        } catch (javax.script.ScriptException e) {
            NGTLog.debug("[ScriptUtil] mozilla_compat load failed: %s", e.getMessage());
        }
        return se;
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

    /**
     * そのスクリプトに関数が定義されているか。
     * <p>Nashorn は {@code function foo(){}} をグローバルに束縛するので、束縛を引けば分かる。
     */
    /**
     * 関数の有無の記録。スクリプトは読み込み後に関数が増減しないので 1 回引けば足りる。
     * <p>{@code ScriptEngine.get} は Nashorn のグローバル切替を伴うため、レール 1 本ごと
     * 毎フレーム引くと馬鹿にならない。エンジンごとに弱参照で持つ。
     */
    private static final java.util.Map<ScriptEngine, java.util.Map<String, Boolean>> FUNCTION_CACHE =
        java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    public static boolean hasFunction(ScriptEngine se, String func) {
        if (se == null || func == null) {
            return false;
        }
        java.util.Map<String, Boolean> perEngine = FUNCTION_CACHE.computeIfAbsent(
            se, k -> new java.util.concurrent.ConcurrentHashMap<>());
        Boolean known = perEngine.get(func);
        if (known != null) {
            return known;
        }
        boolean exists;
        try {
            exists = se.get(func) != null;
        } catch (Throwable t) {
            //引けないエンジンは「ある」ものとして従来どおり呼びに行く
            exists = true;
        }
        perEngine.put(func, exists);
        return exists;
    }

    /** 同じ失敗を何度もログに流さないための記録 (種類ごとに 1 回)。 */
    private static final java.util.Set<String> LOGGED_FAILURES =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * 失敗しても続行するスクリプト呼び出し。
     *
     * <p>★<b>定義されていない関数は呼びに行かない</b>。以前はそのまま invokeFunction して
     * {@code NoSuchMethodException} を発生させ、{@code RuntimeException} で包み直し、
     * {@code printStackTrace()} で毎回スタックトレースを吐いていた。
     * {@code renderRailDynamic} を定義していないレールパックでは<b>1 本 1 フレームにつき
     * 例外 2 個とスタックトレース 1 本</b>になり、レールを数百本並べると描画時間の大半を
     * そこで使っていた (例外の生成はスタックトレース収集を伴うため非常に高い)。
     *
     * <p>本当に失敗したときのログも<b>種類ごとに 1 回</b>に絞る。毎フレーム出ると
     * それ自体が重く、ログも読めなくなる。
     */
    public static Object doScriptIgnoreError(ScriptEngine se, String func, Object... args) {
        if (!hasFunction(se, func)) {
            return null;
        }
        try {
            return doScriptFunction(se, func, args);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
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
