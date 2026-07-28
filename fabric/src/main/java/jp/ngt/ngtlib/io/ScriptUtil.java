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
     * 関数の有無の記録 (エンジンごと)。
     *
     * <p>★<b>「無い」と断定するのは実際に呼んで {@code NoSuchMethodException} が出たときだけ</b>。
     * 以前はここで {@code ScriptEngine.get(name) != null} を見て、null なら呼ばずに諦めていた。
     * ところが Nashorn の {@code get} が引くのは ENGINE_SCOPE の束縛で、
     * <b>関数が定義されていても引けないことがある</b> (スクリプトを別スコープで評価した場合など)。
     * その結果、実在する関数を黙って呼ばなくなり、踏切のランプが止まる・
     * スクリプトが進まないといった「例外も出ないのに動かない」状態を作っていた。
     *
     * <p>未確認のものは<b>あるものとして呼びに行く</b>。1 回だけ例外を踏むが、
     * その結果を控えるので 2 回目以降は呼ばない。毎フレーム例外を投げていた元の問題は解消したまま。
     */
    private static final java.util.Map<ScriptEngine, java.util.Map<String, Boolean>> FUNCTION_CACHE =
        java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private static java.util.Map<String, Boolean> tableOf(ScriptEngine se) {
        return FUNCTION_CACHE.computeIfAbsent(se, k -> new java.util.concurrent.ConcurrentHashMap<>());
    }

    /**
     * その関数を呼びに行く価値があるか。
     * <p>過去に「無い」と確定したものだけ false。未確認は true (呼んで確かめる)。
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
     *
     * <p>定義されていないと分かっている関数は呼ばない。以前はそのまま invokeFunction して
     * {@code NoSuchMethodException} を発生させ {@code printStackTrace()} まで出していたため、
     * {@code renderRailDynamic} を持たないレールパックではレール 1 本 1 フレームにつき
     * 例外 2 個とスタックトレース 1 本になっていた (例外の生成はスタックトレース収集を伴うので高い)。
     *
     * <p>ただし<b>呼ぶ前に諦めるのは「実際に無かった」と確認済みのものだけ</b>。
     * {@link #hasFunction} の説明を参照。
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
                //ここで初めて「無い」と確定する。以後は呼ばない。
                table.put(func, Boolean.FALSE);
                return null;
            }
            //中身のエラーは関数が「ある」証拠。次も呼ぶ。ログは種類ごとに 1 回。
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
