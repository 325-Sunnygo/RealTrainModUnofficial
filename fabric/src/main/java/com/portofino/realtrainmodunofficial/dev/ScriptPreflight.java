package com.portofino.realtrainmodunofficial.dev;

import com.portofino.realtrainmodunofficial.script.PackScriptSource;
import jp.ngt.ngtlib.io.ScriptUtil;

import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 開発用プリフライト。同梱している全スクリプトを検査する。ゲームを起動せずに
 * 「読めないスクリプト」を洗い出すための検証専用 (jar には含めない)。
 *
 * <p><b>構文検査</b>: prepare 済みソースを eval し、<b>パースエラーだけ</b>を本物の失敗として
 * 報告する。トップレベルでプリリュードのグローバル (NGTMath 等) を参照すると
 * ReferenceError になるが、それは実行時エラーであってスクリプトの不備ではないため除外する。
 *
 * <p><b>本番同等ロード</b>: プリリュードを付けて eval し、__bindFails を集計する。
 * プリリュードは Minecraft のレジストリ (jp.ngt.mccompat.init.Blocks) や FMLPaths に
 * 触るため、ゲーム外では初期化に失敗する。それは<b>環境要因</b>として区分する。
 */
public final class ScriptPreflight {

    private ScriptPreflight() {
    }

    public static void main(String[] args) throws Exception {
        Path scriptsDir = Path.of(args.length > 0
                ? args[0]
                : "src/main/resources/assets/minecraft/scripts");
        String only = args.length > 1 ? args[1] : null;
        if (!Files.isDirectory(scriptsDir)) {
            System.err.println("[preflight] scripts dir not found: " + scriptsDir.toAbsolutePath());
            System.exit(2);
            return;
        }

        List<Path> files;
        try (Stream<Path> s = Files.walk(scriptsDir)) {
            files = s.filter(p -> p.toString().endsWith(".js"))
                    .filter(p -> only == null || p.getFileName().toString().contains(only))
                    .sorted().toList();
        }

        int syntaxOk = 0;
        int syntaxFailed = 0;
        int envSkipped = 0;
        int bindWarn = 0;
        int runtimeSkipped = 0;
        int prepareFailed = 0;
        List<String> syntaxFailures = new ArrayList<>();
        List<String> binds = new ArrayList<>();

        for (Path f : files) {
            String rel = scriptsDir.relativize(f).toString().replace('\\', '/');
            String raw = PackScriptSource.decode(Files.readAllBytes(f));

            String prepared;
            boolean preparedOk = true;
            try {
                prepared = PackScriptSource.prepare(raw, rel, path -> readInclude(scriptsDir, path));
            } catch (Throwable t) {
                // prepare は gameDir 等に触るためゲーム外では失敗し得る。
                // 構文検査だけは生ソースで行う。
                prepared = raw;
                preparedOk = false;
                prepareFailed++;
                binds.add(rel + " [prepare-failed] " + t);
            }

            // --- 構文検査 (プリリュード無し) ---
            try {
                ScriptEngine se = ScriptUtil.createEngine();
                se.put(ScriptEngine.FILENAME, rel);
                ((javax.script.Compilable) se).compile(prepared);
                syntaxOk++;
            } catch (ScriptException e) {
                syntaxFailed++;
                syntaxFailures.add(formatSyntaxError(rel, prepared, e));
                continue;
            } catch (Throwable t) {
                // An unavailable engine is a failed check, never a syntax pass.
                syntaxFailed++;
                syntaxFailures.add(rel + " :: syntax check unavailable: " + t);
                continue;
            }

            // --- 本番同等ロード (プリリュード付き) ---
            try {
                ScriptEngine se = ScriptUtil.createEngine();
                se.put(ScriptEngine.FILENAME, rel);
                se.eval(PackScriptSource.PRELUDE + prepared);
                Object bindFails = se.get("__bindFails");
                if (bindFails != null && !bindFails.toString().isBlank()) {
                    bindWarn++;
                    binds.add(rel + " -> " + bindFails);
                }
            } catch (Throwable t) {
                String s = String.valueOf(t);
                if (s.contains("mccompat") || s.contains("gameDir") || s.contains("FMLPaths")
                        || t instanceof NoClassDefFoundError || t instanceof ExceptionInInitializerError) {
                    envSkipped++;
                } else if (isParseError(t)) {
                    syntaxFailed++;
                    syntaxFailures.add(rel + " :: load parse error: " + s);
                } else {
                    // 実行時エラー: スクリプト側の問題とは限らないため参考表示に留める。
                    runtimeSkipped++;
                    binds.add(rel + " [runtime-skipped] " + t);
                }
            }
            if (!preparedOk) {
                binds.add(rel + " [prepare-env-fallback: 生ソースで構文検査]");
            }
        }

        // --- プリリュード自己テスト ---
        // Java.type の代わりに素の java.lang.Class を返すバインダを使うと、
        // GL11.glPushMatrix() / new Parts(...) が壊れる (実機で発生した回帰)。
        // ここで「関数として呼べるか」を必ず検査する。
        List<String> bindFailures = new ArrayList<>();
        try {
            ScriptEngine se = ScriptUtil.createEngine();
            se.eval(PackScriptSource.PRELUDE);
            Object bad = se.eval(
                "(function(){ var b=[];"
                + " if (typeof GL11 !== 'function' || typeof GL11.glPushMatrix !== 'function') b.push('GL11');"
                + " if (typeof Parts !== 'function') b.push('Parts');"
                + " if (typeof ActionParts !== 'function') b.push('ActionParts');"
                + " if (typeof ResourceLocation !== 'function') b.push('ResourceLocation');"
                + " if (typeof NBTTagCompound !== 'function') b.push('NBTTagCompound');"
                + " return b.join(','); })()");
            if (bad != null && !bad.toString().isEmpty()) {
                bindFailures.add("prelude sanity: not callable/static -> " + bad);
            }
        } catch (Throwable t) {
            bindFailures.add("prelude sanity threw: " + t);
        }

        System.out.println("=== RTMU script preflight ===");
        System.out.println("total=" + files.size()
                + " syntaxOk=" + syntaxOk
                + " syntaxFailed=" + syntaxFailed
                + " prepareFailed=" + prepareFailed
                + " runtimeSkipped=" + runtimeSkipped
                + " envSkipped=" + envSkipped
                + " preludeBindWarn=" + bindWarn
                + " preludeSanityFailed=" + bindFailures.size());
        for (String m : bindFailures) {
            System.out.println("PRELUDE-FAIL " + m);
        }
        for (String m : syntaxFailures) {
            System.out.println("FAIL " + m);
        }
        if (envSkipped > 0) {
            System.out.println("ENV-SKIPPED " + envSkipped
                    + " (レジストリ/パス初期化が必要なため未検証。ゲーム内での確認が必要)");
        }
        for (String m : binds) {
            System.out.println("NOTE " + m);
        }
        if (syntaxFailed > 0 || prepareFailed > 0 || !bindFailures.isEmpty()) {
            System.exit(1);
        }
    }

    private static byte[] readInclude(Path scriptsDir, String resource) {
        String path = resource.replace('\\', '/');
        int colon = path.indexOf(':');
        if (colon >= 0) path = path.substring(colon + 1);
        Path candidate = path.startsWith("scripts/")
            ? scriptsDir.resolve(path.substring(8)).normalize()
            : scriptsDir.getParent().resolve(path).normalize();
        if (!Files.isRegularFile(candidate)) candidate = scriptsDir.resolve(path).normalize();
        if (!Files.isRegularFile(candidate)) return null;
        try {
            return Files.readAllBytes(candidate);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** Nashorn のパースエラーかどうか (実行時エラーと区別する)。 */
    private static boolean isParseError(Throwable t) {
        if (t instanceof ScriptException se) {
            Throwable cause = se.getCause();
            if (cause != null && cause.getClass().getName().contains("ParserException")) {
                return true;
            }
            String m = String.valueOf(se.getMessage());
            return m.contains("Expected") || m.contains("Missing") || m.contains("Unexpected")
                    || m.contains("Invalid") || m.contains("Octal") || m.contains("SyntaxError");
        }
        return false;
    }

    /** ScriptException の行番号から用意済みソースの該当行を出し、原因を追いやすくする。 */
    private static String formatSyntaxError(String rel, String prepared, ScriptException e) {
        int line = e.getLineNumber();
        String src = "";
        if (line > 0) {
            String[] lines = prepared.split("\n", -1);
            if (line <= lines.length) {
                src = " | " + lines[line - 1].trim();
            }
        }
        return rel + " :: line " + line + ": " + e.getMessage() + src;
    }
}
