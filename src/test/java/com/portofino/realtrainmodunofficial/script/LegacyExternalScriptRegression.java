package com.portofino.realtrainmodunofficial.script;

import java.util.*;
import java.util.zip.ZipFile;
import jp.ngt.ngtlib.io.ScriptUtil;

/** Loads real external renderer entry points without a game or a world. */
public final class LegacyExternalScriptRegression {
    public static void main(String[] args) throws Exception {
        net.neoforged.fml.loading.LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var testDir=java.nio.file.Path.of("build/srbx-gui-regression").toAbsolutePath();java.nio.file.Files.createDirectories(testDir);net.neoforged.fml.loading.FMLPaths.loadAbsolutePaths(testDir);
        var files = new HashMap<String,byte[]>();
        var entries = new LinkedHashSet<String>();
        try (var zip = new ZipFile(args[0])) {
            for (var entry : Collections.list(zip.entries())) {
                if (entry.isDirectory()) continue;
                String name=entry.getName();
                try (var input=zip.getInputStream(entry)) {
                    byte[] bytes=input.readAllBytes();
                    if (name.startsWith("assets/minecraft/")) files.put(name.substring(17),bytes);
                    if (name.endsWith(".json") && name.contains("ModelVehicle_")) {
                        var config=com.google.gson.JsonParser.parseString(PackScriptSource.decode(bytes)).getAsJsonObject();
                        entries.add(config.getAsJsonObject("model").get("rendererPath").getAsString());
                    }
                }
            }
        }
        for (String name:entries) {
            long start=System.nanoTime();
            var bundle=LegacyExecutionIncludes.prepare(PackScriptSource.decode(files.get(name)),name,files::get,PackScriptSource.PRELUDE);String prepared=bundle.source();
            var engine=ScriptUtil.createEngine();
            engine.put(javax.script.ScriptEngine.FILENAME,name);
            bundle.bind(engine);engine.eval(prepared);
            if (!ScriptUtil.hasFunction(engine,"init") || !ScriptUtil.hasFunction(engine,"render")) throw new AssertionError(name);
            var renderer=new jp.ngt.rtm.render.VehiclePartsRenderer("true");
            var config=new com.google.gson.Gson().fromJson("{\"name\":\"SuperRailBuilderX_builder1\",\"vehicleType\":\"Car\"}",jp.ngt.rtm.modelpack.cfg.VehicleConfig.class);config.init();
            var modelSet=new jp.ngt.rtm.modelpack.modelset.ModelSetVehicle(config);
            var model=new jp.ngt.rtm.render.ModelObject(new jp.ngt.ngtlib.renderer.model.TextureSet[0]);
            model.model=jp.ngt.ngtlib.renderer.model.ModelLoader.parse(files.get("models/superrailbuilderx/builder1.mqo"),"builder1.mqo");
            renderer.init(modelSet,model);
            renderer.setScript(engine);engine.put("renderer",renderer);
            ScriptUtil.doScriptFunction(engine,"init",modelSet,model);
            var recorder=new jp.ngt.ngtlib.renderer.GLRecorder();
            jp.ngt.ngtlib.renderer.GLRecorder.activate(recorder);
            try {
                ScriptUtil.doScriptFunction(engine,"render",null,0,0F);
                ScriptUtil.doScriptFunction(engine,"render",null,1,0F);
                // Call the actual HUD function, which the null vehicle preview never enters.
                engine.eval("renderSharedGui('SRBX regression',new ResourceLocation('minecraft','textures/superrailbuilderx/icon_builder1.png'),[],null)");
                if(recorder.getCommands().stream().noneMatch(cmd->cmd.op==jp.ngt.ngtlib.renderer.GLRecorder.Op.DRAW_TEXT))throw new AssertionError("SRBX HUD did not draw text: "+name);
                if (!recorder.hasGeometry()) throw new AssertionError("No preview geometry: "+name);
            } finally { jp.ngt.ngtlib.renderer.GLRecorder.deactivate(); }
            System.out.println("External renderer loaded/init/preview: "+name+" chars="+prepared.length()+" ms="+(System.nanoTime()-start)/1000000);
            engine=null; prepared=null; System.gc();
        }
        System.out.println("External renderer entry points: "+entries.size()+" passed (load/init/preview/HUD recording; no world callbacks)");
    }
}
