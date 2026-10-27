package com.portofino.realtrainmodunofficial.script;

import com.portofino.realtrainmodunofficial.util.PackTextDecoder;
import jp.ngt.mccompat.ChunkCoordinates;
import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;

import javax.script.ScriptEngine;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;

/** Standalone regression checks; no Minecraft bootstrap or graphics context required. */
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
        // Different case is significant for assets in ZIP files.
        equal("ab", LegacyScriptIncludes.expand("//include <A.js>\n//include <a.js>", new HashSet<>(), p -> p.equals("A.js") ? "a" : "b").replace("\n", ""));
        String[][] names = {
                {"Packages.old.Class", "Packages.java.lang.String"},
                {"Packages.old.ClassMP", "Packages.java.lang.Integer"}
        };
        equal("Packages.java.lang.String.valueOf(1)", LegacyClassReferences.remap("Packages.old.Class.valueOf(1)", names));
        equal("Packages.java.lang.Integer", LegacyClassReferences.remap("Packages.old.ClassMP", names));
        equal("Packages.old.ClassOther", LegacyClassReferences.remap("Packages.old.ClassOther", names));
        equal("java.lang.String", LegacyClassReferences.remap("old.Class", names));
        equal("Java.type('java.lang.String')", LegacyClassReferences.remap("Java.type('old.Class')", names));
        equal("Java . type ( \"java.lang.String\" )", LegacyClassReferences.remap("Java . type ( \"old.Class\" )", names));
        equal("'Packages.old.Class'", LegacyClassReferences.remap("'Packages.old.Class'", names));
        equal("// Packages.old.Class\n/* old.Class */", LegacyClassReferences.remap("// Packages.old.Class\n/* old.Class */", names));
        equal("`Packages.old.Class`", LegacyClassReferences.remap("`Packages.old.Class`", names));
        equal("x.old.Class", LegacyClassReferences.remap("x.old.Class", names));
        equal("42", engine.eval(LegacyClassReferences.remap("Java.type('old.Class').valueOf(42)", names)));
        engine.eval("load('nashorn:mozilla_compat.js');");
        equal("42", engine.eval(LegacyClassReferences.remap("Packages.old.Class.valueOf(42)", names)));
        String[][] valueTypes = {{"Packages.old.Tile", "Packages.java.lang.Integer.TYPE"}};
        equal("Java.type('java.lang.Integer').TYPE", LegacyClassReferences.remap("Java.type('old.Tile')", valueTypes));
        equal("Packages.java.lang.Integer.TYPE", LegacyClassReferences.remap("Packages.old.Tile", valueTypes));
        equal("Packages.java.lang.Integer.TYPE", LegacyClassReferences.remap("Packages.java.lang.Integer.TYPE", valueTypes));
        equal(Integer.TYPE, engine.eval(LegacyClassReferences.remap("Java.type('old.Tile')", valueTypes)));
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
        String opaque = "var s='x.seatRotation'; /* x.seatRotation */ var r=/x.seatRotation/; x.seatRotation;";
        equal("var s='x.seatRotation'; /* x.seatRotation */ var r=/x.seatRotation/; x.getSeatRotationRaw();",
                LegacySourceMask.transform(opaque, text -> text.replace(".seatRotation", ".getSeatRotationRaw()")));
        equal("x / y.getSeatRotationRaw()", LegacySourceMask.transform("x / y.seatRotation", text -> text.replace(".seatRotation", ".getSeatRotationRaw()")));
        equal("return /[\\/].seatRotation/", LegacySourceMask.transform("return /[\\/].seatRotation/", text -> text.replace(".seatRotation", ".getSeatRotationRaw()")));
        String large = "var label='ordinary literal';\n".repeat(30000);
        equal(large, LegacyClassReferences.remap(large, names));
        equal(large, LegacySourceMask.transform(large, text -> text));
        equal("NotJava.type('old.Class')", LegacyClassReferences.remap("NotJava.type('old.Class')", names));
        engine.eval(PackScriptSource.prepare("var gui=new Packages.net.minecraft.client.gui.Gui();", "srbx-gui.js", path -> null));
        equal(true, engine.get("gui") instanceof jp.ngt.mccompat.Gui);
        String japanese = "var name='①髙﨑';";
        equal(japanese, PackTextDecoder.decodeText(japanese.getBytes(Charset.forName("MS932"))));
        equal("var x=1;", PackTextDecoder.decodeText("\ufeffvar x=1;".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        System.out.println("Legacy script regressions: " + checks + " passed");
    }
}
