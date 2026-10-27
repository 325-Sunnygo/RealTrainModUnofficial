package com.portofino.realtrainmodunofficial.script;
import java.util.*;
import java.nio.charset.StandardCharsets;
import jp.ngt.ngtlib.io.ScriptUtil;

public final class LegacyFastIncludeRegression {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        String padding="/*"+"x".repeat(140000)+"*/\n";
        var files=Map.of("child.js",(padding+"var local=7; function included(){return local;} count++;\n").getBytes(StandardCharsets.UTF_8));
        String ordinary="var count=0;function run(){var before=typeof local;var fn=included();\n//include <child.js>\nreturn [before,fn,local].join(',');}\n//include <child.js>\nvar result=run();";
        var fallback=LegacyExecutionIncludes.prepare(ordinary,"ordinary.js",files::get,"");
        check(fallback.binding()==null,"general includes must retain textual declaration hoisting");
        var engine=ScriptUtil.createEngine();engine.eval(fallback.source());
        check("undefined,,7".equals(engine.get("result")),"included vars and function bodies are hoisted in their original function scope");
        String guarded="var RTMX_COMPAT_TARGETS={};function RTMX_loadCompatTarget_fixture(){};var count=0;function run(){\n//include <child.js>\nreturn included();}\n//include <child.js>\n//include <child.js>\n//include <child.js>\nvar result=run();";
        var fast=LegacyExecutionIncludes.prepare(guarded,"generated.js",files::get,"");
        check(fast.binding()!=null&&fast.source().length()<2000,"guarded target graph compiles without repeated module bodies");
        var compiled=ScriptUtil.createEngine();fast.bind(compiled);compiled.eval(fast.source());
        check(((Number)compiled.get("count")).intValue()==4&&((Number)compiled.get("result")).intValue()==7,"repeated modules execute in the current function scope");
        var strict=LegacyExecutionIncludes.prepare("'use strict';\n"+guarded,"strict.js",files::get,"");
        check(strict.binding()==null,"strict eval must retain literal include semantics");
        try {LegacyExecutionIncludes.prepare("//include <missing.js>","entry.js",files::get,"");throw new AssertionError("missing accepted");}catch(IllegalArgumentException expected){checks++;}
        try {LegacyExecutionIncludes.prepare("//include <entry.js>","entry.js",files::get,"");throw new AssertionError("cycle accepted");}catch(IllegalArgumentException expected){checks++;}
        var recorder=new jp.ngt.ngtlib.renderer.GLRecorder();jp.ngt.ngtlib.renderer.GLRecorder.activate(recorder);
        try {
            var matrix=jp.ngt.ngtlib.renderer.BufferUtilsCompat.createFloatBuffer(16);
            jp.ngt.ngtlib.renderer.GL11Facade.glMatrixMode(5890);jp.ngt.ngtlib.renderer.GL11Facade.glScalef(.5,.5,1);
            jp.ngt.ngtlib.renderer.GL11Facade.glGetFloat(2984,matrix);
            check(matrix.position()==0&&matrix.get(0)==.5F,"texture matrix query preserves buffer position");
            float[] uv=recorder.matrices.uv(1,1);check(uv[0]==.5F&&uv[1]==.5F,"GUI atlas texture matrix applies to coordinates");
            check(jp.ngt.ngtlib.renderer.GL11Facade.glGetError()==0,"recording does not call removed GL entry points");
        }finally {jp.ngt.ngtlib.renderer.GLRecorder.deactivate();}
        System.out.println("Fast include and GUI matrix regressions: "+checks+" passed");
    }
}
