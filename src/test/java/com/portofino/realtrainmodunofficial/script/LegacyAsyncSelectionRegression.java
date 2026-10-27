package com.portofino.realtrainmodunofficial.script;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.ZipFile;
import jp.ngt.ngtlib.io.ScriptUtil;

/** The real SRBX script must compile without blocking the selection/render caller. */
public final class LegacyAsyncSelectionRegression {
    private static int checks;
    private static void check(boolean ok,String message) {checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        long requestStart=System.nanoTime();
        var blocked=AsyncScriptCompiler.request("blocked-regression","blocked.js",()-> {
            entered.countDown();try {if(!release.await(10,TimeUnit.SECONDS))throw new AssertionError("supplier timeout");}catch(InterruptedException failure) {throw new RuntimeException(failure);}
            return "var executionThread=Java.type('java.lang.Thread').currentThread().getName();";
        });
        try {
            check(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-requestStart)<250,"selection request must return before compilation");
            check(entered.await(5,TimeUnit.SECONDS) && !blocked.isDone(),"expensive source work runs asynchronously");
            check(AsyncScriptCompiler.request("blocked-regression","blocked.js",()-> {throw new AssertionError("duplicate compile");})==blocked,"repeated frames share the same compilation");
        } finally {release.countDown();}
        var compiled=blocked.get(30,TimeUnit.SECONDS);
        check(compiled.getEngine().get("executionThread")==null,"worker must not evaluate pack top-level code");
        compiled.eval();check(Thread.currentThread().getName().equals(compiled.getEngine().get("executionThread")),"evaluation remains on caller/render thread");
        AsyncScriptCompiler.release("blocked-regression");
        var failed=AsyncScriptCompiler.request("syntax-regression","invalid.js",()->"function (");
        try {failed.get(30,TimeUnit.SECONDS);throw new AssertionError("syntax error accepted");}catch(ExecutionException expected) {checks++;}finally {AsyncScriptCompiler.release("syntax-regression");}
        if(args.length>0 && !args[0].isBlank()) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());
            net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
            Map<String,byte[]> files=new HashMap<>();
            try(var zip=new ZipFile(args[0])) {for(var entry:Collections.list(zip.entries()))if(!entry.isDirectory() && entry.getName().startsWith("assets/minecraft/"))try(var input=zip.getInputStream(entry)) {files.put(entry.getName().substring(17),input.readAllBytes());}}
            String name="scripts/superrailbuilderx/render_builder1.js";
            long started=System.nanoTime();
            var future=AsyncScriptCompiler.requestPrepared("real-srbx-selection",name,()->LegacyExecutionIncludes.prepare(PackScriptSource.decode(files.get(name)),name,files::get,PackScriptSource.PRELUDE));
            long requestMs=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);
            check(requestMs<250,"real SRBX selection must not parse/compile on the UI thread");
            var script=future.get(120,TimeUnit.SECONDS);long compileMs=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);
            long evalStart=System.nanoTime();script.eval();long evalMs=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-evalStart);
            var engine=script.getEngine();
            check(ScriptUtil.hasFunction(engine,"init") && ScriptUtil.hasFunction(engine,"render"),"compiled SRBX keeps its original callbacks");
            var config=new com.google.gson.Gson().fromJson("{\"name\":\"SuperRailBuilderX_builder1\",\"vehicleType\":\"Car\"}",jp.ngt.rtm.modelpack.cfg.VehicleConfig.class);config.init();
            var modelSet=new jp.ngt.rtm.modelpack.modelset.ModelSetVehicle(config);
            var model=new jp.ngt.rtm.render.ModelObject(new jp.ngt.ngtlib.renderer.model.TextureSet[0]);
            model.model=jp.ngt.ngtlib.renderer.model.ModelLoader.parse(files.get("models/superrailbuilderx/builder1.mqo"),"builder1.mqo");
            var renderer=new jp.ngt.rtm.render.VehiclePartsRenderer("true");renderer.init(modelSet,model);renderer.setScript(engine);engine.put("renderer",renderer);
            long initStart=System.nanoTime();ScriptUtil.doScriptFunction(engine,"init",modelSet,model);long initMs=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-initStart);
            var recorder=new jp.ngt.ngtlib.renderer.GLRecorder();jp.ngt.ngtlib.renderer.GLRecorder.activate(recorder);
            long renderStart=System.nanoTime();
            try {ScriptUtil.doScriptFunction(engine,"render",null,0,0F);ScriptUtil.doScriptFunction(engine,"render",null,1,0F);check(recorder.hasGeometry(),"SRBX selection preview still emits geometry");}finally {jp.ngt.ngtlib.renderer.GLRecorder.deactivate();}
            long renderMs=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-renderStart);
            System.out.println("Actual SRBX selection timings: request="+requestMs+"ms compile="+compileMs+"ms eval="+evalMs+"ms init="+initMs+"ms firstPreview="+renderMs+"ms");
            AsyncScriptCompiler.release("real-srbx-selection");
        }
        System.out.println("Async selection regressions: "+checks+" passed");
    }
}
