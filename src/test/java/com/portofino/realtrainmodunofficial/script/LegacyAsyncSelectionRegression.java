package com.portofino.realtrainmodunofficial.script;
import java.util.*;
import java.util.zip.ZipFile;
import jp.ngt.ngtlib.io.ScriptUtil;

/** The real SRBX script must load and render through… */
public final class LegacyAsyncSelectionRegression {
    private static int checks;
    private static void check(boolean ok,String message) {checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        if(args.length>0 && !args[0].isBlank()) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());
            net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
            Map<String,byte[]> files=new HashMap<>();
            try(var zip=new ZipFile(args[0])) {for(var entry:Collections.list(zip.entries()))if(!entry.isDirectory() && entry.getName().startsWith("assets/minecraft/"))try(var input=zip.getInputStream(entry)) {files.put(entry.getName().substring(17),input.readAllBytes());}}
            String name="scripts/superrailbuilderx/render_builder1.js";
            long started=System.nanoTime();
            var prepared=LegacyExecutionIncludes.prepare(PackScriptSource.decode(files.get(name)),name,files::get,PackScriptSource.PRELUDE);
            var engine=ScriptUtil.doScript(prepared.source(),name);
            long loadMs=java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);
            check(ScriptUtil.hasFunction(engine,"init") && ScriptUtil.hasFunction(engine,"render"),"SRBX keeps its original callbacks");
            var config=new com.google.gson.Gson().fromJson("{\"name\":\"SuperRailBuilderX_builder1\",\"vehicleType\":\"Car\"}",jp.ngt.rtm.modelpack.cfg.VehicleConfig.class);config.init();
            var modelSet=new jp.ngt.rtm.modelpack.modelset.ModelSetVehicle(config);
            var model=new jp.ngt.rtm.render.ModelObject(new jp.ngt.ngtlib.renderer.model.TextureSet[0]);
            model.model=jp.ngt.ngtlib.renderer.model.ModelLoader.parse(files.get("models/superrailbuilderx/builder1.mqo"),"builder1.mqo");
            var renderer=new jp.ngt.rtm.render.VehiclePartsRenderer("true");renderer.init(modelSet,model);renderer.setScript(engine);engine.put("renderer",renderer);
            long initStart=System.nanoTime();ScriptUtil.doScriptFunction(engine,"init",modelSet,model);long initMs=java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-initStart);
            var recorder=new jp.ngt.ngtlib.renderer.GLRecorder();jp.ngt.ngtlib.renderer.GLRecorder.activate(recorder);
            long renderStart=System.nanoTime();
            try {ScriptUtil.doScriptFunction(engine,"render",null,0,0F);ScriptUtil.doScriptFunction(engine,"render",null,1,0F);check(recorder.hasGeometry(),"SRBX selection preview still emits geometry");}finally {jp.ngt.ngtlib.renderer.GLRecorder.deactivate();}
            long renderMs=java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-renderStart);
            System.out.println("Actual SRBX selection timings: load="+loadMs+"ms init="+initMs+"ms firstPreview="+renderMs+"ms");
        }
        System.out.println("Synchronous selection regressions: "+checks+" passed");
    }
}
