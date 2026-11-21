package com.portofino.realtrainmodunofficial.script;

import com.portofino.realtrainmodunofficial.util.PackTextDecoder;
import jp.ngt.mccompat.ChunkCoordinates;
import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;

import javax.script.ScriptEngine;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;

/** Standalone regression checks; */
public final class LegacyScriptRegression {
    private static int checks;

    private static void equal(Object expected, Object actual) {
        checks++;
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }

    public static void main(String[] args) throws Exception {
        ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine("--language=es6");
        Map<String, String> files = Map.of(
                "step.js", "value = value * 10 + 2;",
                "nested.js", "//include <step.js>\nvalue = value * 10 + 3;",
                "cycle.js", "//include <cycle.js>",
                "escaped.js", "value = '$1\\\\';");
        String source = "var value = 1;\n//include <step.js>\nvalue = value * 10 + 4;\n//include <step.js>\nvalue;";
        equal(1242, ((Number) engine.eval(LegacyScriptIncludes.expand(source, new HashSet<>(), files::get))).intValue());
        equal(123, ((Number) engine.eval(LegacyScriptIncludes.expand("var value=1;\n//include <nested.js>\nvalue;", new HashSet<>(), files::get))).intValue());
        equal("x\r\ny\r\nz", LegacyScriptIncludes.expand("x\r\n//include <a.js>\r\nz", new HashSet<>(), p -> "y"));
        equal("value = '$1\\\\';", LegacyScriptIncludes.expand("//include <escaped.js>", new HashSet<>(), files::get));
        for (String path : new String[]{"cycle.js", "missing.js"}) {
            HashSet<String> active = new HashSet<>();
            try {
                LegacyScriptIncludes.expand("//include <" + path + ">", active, files::get);
                throw new AssertionError("Expected include error for " + path);
            } catch (IllegalArgumentException expected) {
                equal(true, expected.getMessage().contains(path));
                equal(true, active.isEmpty());
            }
        }
        // Different case is significant for assets in ZIP f…
        equal("ab", LegacyScriptIncludes.expand("//include <A.js>\n//include <a.js>", new HashSet<>(), p -> p.equals("A.js") ? "a" : "b").replace("\n", ""));
        // KaizPatchX と同じ: スクリプト本文は書き換えず、//include だけ展開する。
        String rawScript = "var s='x.seatRotation'; // x.seatRotation";
        equal(rawScript, PackScriptSource.prepare(rawScript, "raw.js", path -> null));
        String included = PackScriptSource.prepare("var x=1;\n//include <inc.js>\n", "main.js", path -> "var y=2;".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        equal("var x=1;\nvar y=2;\n", included);
        ScriptEngine importEngine = jp.ngt.ngtlib.io.ScriptUtil.doScript(
                PackScriptSource.PRELUDE
                        + "importPackage(Packages.jp.ngt.rtm.block);"
                        + "importPackage(Packages.org.lwjgl.opengl);"
                        + "importClass(Packages.jp.ngt.rtm.render.Parts);"
                        + "var blockClass = BlockScaffoldStairs;"
                        + "var glClass = GL11;"
                        + "var partsClass = Parts;",
                "package-import.js");
        equal("jp.ngt.rtm.block.BlockScaffoldStairs", String.valueOf(importEngine.get("blockClass")).replaceAll("^StaticClass\\[|\\]$", ""));
        equal("jp.ngt.ngtlib.renderer.GL11Facade", String.valueOf(importEngine.get("glClass")).replaceAll("^StaticClass\\[|\\]$", ""));
        equal("jp.ngt.rtm.render.Parts", String.valueOf(importEngine.get("partsClass")).replaceAll("^StaticClass\\[|\\]$", ""));

        // ModelSetBase が本家と同じく serverSE を構築時に読み込むこと。
        jp.ngt.rtm.modelpack.cfg.TrainConfig serverCfg = new jp.ngt.rtm.modelpack.cfg.TrainConfig();
        serverCfg.trainName = "script-load-test";
        serverCfg.serverScriptPath = "scripts/server/223.js";
        serverCfg.init();
        jp.ngt.rtm.modelpack.modelset.ModelSetTrain serverSet = new jp.ngt.rtm.modelpack.modelset.ModelSetTrain(serverCfg);
        equal(true, serverSet.serverSE != null);
        equal(true, jp.ngt.ngtlib.io.ScriptUtil.hasFunction(serverSet.serverSE, "onUpdate"));

        ChunkCoordinates coordinates = new ChunkCoordinates(-2, 64, 3);
        engine.put("coordinates", coordinates);
        equal(-2, ((Number) engine.eval("coordinates.field_71574_a")).intValue());
        engine.eval("coordinates.field_71574_a = 7;");
        equal(7, coordinates.posX);
        coordinates.posZ = 9;
        equal(9, ((Number) engine.eval("coordinates.field_71573_c")).intValue());
        equal(0x123456, jp.ngt.ngtlib.util.ColorUtil.encode(0x12, 0x34, 0x56));
        equal(0x12, jp.ngt.ngtlib.util.ColorUtil.getR(0x123456));
        equal(0x34, jp.ngt.ngtlib.util.ColorUtil.getG(0x123456));
        equal(0x56, jp.ngt.ngtlib.util.ColorUtil.getB(0x123456));
        equal(0x123456, jp.ngt.ngtlib.util.ColorUtil.multiplicating(0x123456, 0xffffff));
        equal(0.0D, jp.ngt.ngtlib.math.PerlinNoise.perlin(0, 0, 0));
        equal(jp.ngt.ngtlib.math.PerlinNoise.perlin(0.25, 0.5, 0.75),
                jp.ngt.ngtlib.math.PerlinNoise.perlin(256.25, 256.5, 256.75));
        String japanese = "var name='①髙﨑';";
        equal(japanese, PackTextDecoder.decodeText(japanese.getBytes(Charset.forName("MS932"))));
        equal("var x=1;", PackTextDecoder.decodeText("\ufeffvar x=1;".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        System.out.println("Legacy script regressions: " + checks + " passed");
    }
}
