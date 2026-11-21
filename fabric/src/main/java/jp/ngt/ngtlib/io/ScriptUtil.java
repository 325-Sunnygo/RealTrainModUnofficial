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

    private static volatile javax.script.ScriptEngineFactory rhinoFactory;

    /** 使う JS エンジンの種類。 */
    private enum EngineKind { GRAAL, NASHORN, RHINO }

    /** GraalJS エンジンクラス (バージョンによりパッケージが異なる)。 */
    private static Class<?> graalFactoryClass() throws ClassNotFoundException {
        try {
            return Class.forName("com.oracle.truffle.js.scriptengine.GraalJSEngineFactory");
        } catch (ClassNotFoundException oldLayout) {
            return Class.forName("com.oracle.truffle.js.scriptengine.GraalJSEngineFactory");
        }
    }

    /** エンジン選択。
     *  1) -Drtmu.scriptEngine=graal|nashorn|rhino で明示指定
     *  2) 未指定: ★GraalJS (同梱, Nashorn 互換モード) → Nashorn → Rhino。 */
    private static EngineKind selectEngine() {
        String prop = System.getProperty("rtmu.scriptEngine", "");
        if (prop.equalsIgnoreCase("graal") || prop.equalsIgnoreCase("graaljs")) {
            return EngineKind.GRAAL;
        }
        if (prop.equalsIgnoreCase("nashorn")) {
            return EngineKind.NASHORN;
        }
        if (prop.equalsIgnoreCase("rhino")) {
            return EngineKind.RHINO;
        }
        try {
            graalFactoryClass();
            return EngineKind.GRAAL;
        } catch (Throwable noGraal) {
            // fall through
        }
        try {
            Class.forName("org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory");
            return EngineKind.NASHORN;
        } catch (Throwable noNashorn) {
            return EngineKind.RHINO;
        }
    }

    /** GraalJS エンジン生成 (Nashorn 互換モード + 全ホストアクセス)。
     *  GraalJS は本体 jar に同梱 (リロケーションなし — Truffle はリロケーション非対応)。
     *  25.x API: GraalJSScriptEngine.create(Engine, Context.Builder)。 */
    private static ScriptEngine createGraalEngine() throws Exception {
        Class<?> contextBuilderClass = Class.forName("org.graalvm.polyglot.Context$Builder");
        Object builder = Class.forName("org.graalvm.polyglot.Context")
            .getDeclaredMethod("newBuilder", String[].class)
            .invoke(null, (Object) new String[]{"js"});
        contextBuilderClass.getMethod("allowHostClassLookup", java.util.function.Predicate.class)
            .invoke(builder, (java.util.function.Predicate<String>) name -> true);
        // ★ホストクラスは RTMU のクラスローダから解決する (Truffle 実装が他 mod
        //  (KubeJS 等) のクラスローダから読まれていると、RTMU のクラスが見えなくなる)。
        try {
            contextBuilderClass.getMethod("hostClassLoader", ClassLoader.class)
                .invoke(builder, ScriptUtil.class.getClassLoader());
        } catch (Throwable noHostClassLoaderOption) {
            // (古い GraalVM では無いオプション)
        }
        contextBuilderClass.getMethod("allowHostClassLoading", boolean.class).invoke(builder, true);
        contextBuilderClass.getMethod("allowAllAccess", boolean.class).invoke(builder, true);
        contextBuilderClass.getMethod("option", String.class, String.class)
            .invoke(builder, "js.nashorn-compat", "true");
        // (engine.WarnInterpreterOnly は Engine レベルオプションのため除外)
        Object engine = Class.forName("org.graalvm.polyglot.Engine")
            .getDeclaredMethod("newBuilder").invoke(null);
        engine = Class.forName("org.graalvm.polyglot.Engine$Builder")
            .getMethod("build").invoke(engine);
        Class<?> engineClass = Class.forName("com.oracle.truffle.js.scriptengine.GraalJSScriptEngine");
        return (ScriptEngine) engineClass
            .getMethod("create", Class.forName("org.graalvm.polyglot.Engine"), contextBuilderClass)
            .invoke(null, engine, builder);
    }

    /** Rhino 用 Nashorn 互換プリリュード (Java.type シム等)。実験的オプション。 */
    private static final String RHINO_PRELUDE =
        "var Java = {};\n"
        + "Java.type = function(name) {\n"
        + "  var scope = Packages;\n"
        + "  var parts = String(name).split('.');\n"
        + "  for (var i = 0; i < parts.length; i++) {\n"
        + "    scope = scope[parts[i]];\n"
        + "  }\n"
        + "  var f = function() {\n"
        + "    switch (arguments.length) {\n"
        + "      case 0: return new scope();\n"
        + "      case 1: return new scope(arguments[0]);\n"
        + "      case 2: return new scope(arguments[0], arguments[1]);\n"
        + "      case 3: return new scope(arguments[0], arguments[1], arguments[2]);\n"
        + "      case 4: return new scope(arguments[0], arguments[1], arguments[2], arguments[3]);\n"
        + "      case 5: return new scope(arguments[0], arguments[1], arguments[2], arguments[3], arguments[4]);\n"
        + "      case 6: return new scope(arguments[0], arguments[1], arguments[2], arguments[3], arguments[4], arguments[5]);\n"
        + "      default: throw new Error('Too many constructor args: ' + name);\n"
        + "    }\n"
        + "  };\n"
        + "  for (var k in scope) {\n"
        + "    try { f[k] = scope[k]; } catch (e) {}\n"
        + "  }\n"
        + "  return f;\n"
        + "};\n"
        + "Java.from = function(value) {\n"
        + "  var out = [];\n"
        + "  for (var i = 0; i < value.length; i++) out.push(value[i]);\n"
        + "  return out;\n"
        + "};\n";

    /** エンジン生成。本家 ScriptUtil:40 と同一 (-doe / --language=es6)。
     *  -Drtmu.scriptEngine=rhino / Nashorn 無し環境では軽量 Rhino エンジンを使う。 */
    public static ScriptEngine createEngine() {
        EngineKind kind = selectEngine();
        if (kind == EngineKind.RHINO) {
            // Rhino 依存は同梱していないため Nashorn へフォールバック
            kind = EngineKind.NASHORN;
        }
        if (kind == EngineKind.GRAAL) {
            try {
                return createGraalEngine();
            } catch (Throwable t) {
                NGTLog.debug("[ScriptUtil] GraalJS init failed, fallback: %s", String.valueOf(t));
            }
        }
        // Nashorn (本家 RTM/NGTLib と同じ)
        if (SEM == null) {
            init();
        }
        // MOD のクラスローダを appLoader として渡す (Packages.jp.ngt.* 解決用)
        ScriptEngine se = SEM.getScriptEngine(
                new String[]{"-doe", "--language=es6"},
                ScriptUtil.class.getClassLoader());
        // ★本家 doScript:47-51 と同じ mozilla_compat ロード
        try {
            se.eval("load(\"nashorn:mozilla_compat.js\");");
        } catch (javax.script.ScriptException e) {
            NGTLog.debug("[ScriptUtil] mozilla_compat load failed: %s", e.getMessage());
        }
        return se;
    }

    /** JavaScriptの実行 (本家 ScriptUtil.doScript(String) と同じ名前無し実行)。 */
    public static ScriptEngine doScript(String s) {
        return doScript(s, "<unnamed script>");
    }

    /**
     * 本家 ScriptUtil.doScript(String, String) 完全移植。
     * 本家は ScriptEngine.FILENAME を設定してから mozilla_compat を読み、本文を評価する。
     * FILENAME は Nashorn のスタックトレース/行番号表示に使われるため、
     * 欠落するとエラー箇所が "<eval>" になり原因が追えなくなる。
     */
    public static ScriptEngine doScript(String s, String fileName) {
        if (s != null && !s.contains("__rtmBind")) {
            try {
                s = com.portofino.realtrainmodunofficial.script.PackScriptSource.SERVER_PRELUDE + s;
            } catch (Throwable ignored) {
                // (索引クラスが無い環境ではそのまま)
            }
        }
        ScriptEngine se = createEngine();
        try {
            se.put(ScriptEngine.FILENAME, fileName);
            // スクリプトの importPackage (GraalJS は遅延解決が無い) 用のクラス索引
            try {
                se.put("__rtmClassesOf", new com.portofino.realtrainmodunofficial.script.LegacyPackageIndexHelper());
            } catch (Throwable noIndex) {
                // (分離 mod など索引クラスが無い環境では省略)
            }
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
            throw new RuntimeException("Script exec error in " + fileName, e);
        }
    }

    /**
     * 本家 ScriptUtil.doScript(ResourceLocation) 完全移植。
     * パック内スクリプトを NGTText.getText(rl, true) で読み、そのパスを FILENAME にして実行する。
     */
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

    /**
     * そのスクリプトに関数が定義されているか。
     * Nashorn は function foo{} をグローバルに束縛するので、束縛を引けば分かる。
     */
    /** Inspect the current binding: scripts can install callbacks after their first tick. */
    public static boolean hasFunction(ScriptEngine se, String func) {
        if (se == null || func == null) {
            return false;
        }
        // GraalJS の javax.script ラッパは se.get() の戻り値が Function か判別できない
        // (PolyglotMapAndFunction)。 Nashorn の場合だけ高速判定し、それ以外は
        // 実呼び出しで判別する (doScriptIgnoreError は未定義を握りつぶす)。
        Object value = se.get(func);
        if (value == null) {
            return false;
        }
        if (value instanceof org.openjdk.nashorn.api.scripting.JSObject js) {
            return js.isFunction();
        }
        String engineName = String.valueOf(se.getFactory().getEngineName());
        if (engineName.contains("Graal")) {
            // 関数なら呼べる。 未定義なら invokeFunction が NoSuchMethodException。
            try {
                ((javax.script.Invocable) se).invokeFunction(func);
                return true;
            } catch (NoSuchMethodException notDefined) {
                return false;
            } catch (Throwable callable) {
                // 呼べたが中で失敗 = 関数は存在する
                return true;
            }
        }
        return value instanceof javax.script.Invocable;
    }

    /** 同じ失敗を何度もログに流さないための記録 (種類ごとに 1 回)。 */
    private static final java.util.Set<String> LOGGED_FAILURES =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * 失敗しても続行するスクリプト呼び出し。
     * 関数は後から定義され得るため、呼び出すたびに現在の定義を使う。
     */
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
