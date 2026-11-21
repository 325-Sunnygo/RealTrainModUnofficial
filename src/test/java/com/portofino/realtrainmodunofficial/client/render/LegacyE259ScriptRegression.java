package com.portofino.realtrainmodunofficial.client.render;
import java.util.*;
import java.util.zip.ZipFile;
import com.portofino.realtrainmodunofficial.script.*;
import jp.ngt.ngtlib.renderer.*;
import jp.ngt.ngtlib.io.ScriptUtil;

public final class LegacyE259ScriptRegression {
    public static void run() throws Exception {
        String pack=System.getProperty("rtmu.e259Pack","");if(pack.isBlank())return;
        var testDir=java.nio.file.Path.of("build/e259-gpu-regression").toAbsolutePath();java.nio.file.Files.createDirectories(testDir);net.neoforged.fml.loading.FMLPaths.loadAbsolutePaths(testDir);
        Map<String,byte[]> files=new HashMap<>();String configText;
        try(var zip=new ZipFile(pack)) {
            for(var entry:Collections.list(zip.entries()))if(!entry.isDirectory()&&entry.getName().startsWith("assets/minecraft/"))try(var in=zip.getInputStream(entry)){files.put(entry.getName().substring(17),in.readAllBytes());}
            try(var in=zip.getInputStream(zip.getEntry("json-1.7.10/ModelTrain_E259O-1.json"))){configText=PackScriptSource.decode(in.readAllBytes());}
        }
        var field=jp.ngt.ngtlib.io.NGTFileLoader.class.getDeclaredField("CONTENT_CACHE");field.setAccessible(true);
        @SuppressWarnings("unchecked") var cache=(Map<String,byte[]>)field.get(null);files.forEach((name,bytes)->cache.put(name.toLowerCase(Locale.ROOT),bytes));
        var config=new com.google.gson.Gson().fromJson(configText,jp.ngt.rtm.modelpack.cfg.TrainConfig.class);config.init();
        var modelSet=new jp.ngt.rtm.modelpack.modelset.ModelSetCompat(config);
        var model=new jp.ngt.rtm.render.ModelObject(new jp.ngt.ngtlib.renderer.model.TextureSet[]{new jp.ngt.ngtlib.renderer.model.TextureSet(new jp.ngt.ngtlib.renderer.model.Material(new jp.ngt.mccompat.ResourceLocation("textures/hi03_e259/odoriko/e259-1.png")),0,true)});
        model.model=jp.ngt.ngtlib.renderer.model.ModelLoader.parse(files.get("models/hi03_e259/e259-1.mqoz"),"e259-1.mqoz");
        String name="scripts/hi03_e259/render_e259-1.js";
        var prepared=LegacyExecutionIncludes.prepare(PackScriptSource.decode(files.get(name)),name,files::get,PackScriptSource.PRELUDE);
        var engine=ScriptUtil.createEngine();prepared.bind(engine);engine.eval(prepared.source());
        var renderer=new jp.ngt.rtm.render.VehiclePartsRenderer("true");renderer.init(modelSet,model);renderer.setScript(engine);engine.put("renderer",renderer);ScriptUtil.doScriptFunction(engine,"init",modelSet,model);
        engine.put("testModelSet",modelSet);engine.put("testData",new jp.ngt.rtm.modelpack.state.DataMap());
        engine.eval("var testEntity={field_70177_z:0,field_70173_aa:1,field_70153_n:{},field_70170_p:{field_72995_K:true,func_82737_E:function(){return 1;}},isControlCar:function(){return false;},getSpeed:function(){return 0;},getNotch:function(){return 0;},getFormation:function(){return null;},func_145782_y:function(){return 2;},func_70070_b:function(){return 15728880;},getTrainStateData:function(){return 0;},getBogie:function(){return null;},getSeatRotationRaw:function(){return 0;},getSeatRotation:function(){return 0;},getModelSet:function(){return testModelSet;},getResourceState:function(){return {getDataMap:function(){return testData;},getResourceName:function(){return 'E259O-1';}};}};");
        var recorder=new GLRecorder();GLRecorder.activate(recorder);
        try {ScriptUtil.doScriptFunction(engine,"render",engine.get("testEntity"),0,0F);ScriptUtil.doScriptFunction(engine,"render",engine.get("testEntity"),1,0F);}finally{GLRecorder.deactivate();}
        var pose=new com.mojang.blaze3d.vertex.PoseStack();int body=0, unlitParts=0;
        for(var cmd:recorder.getCommands())switch(cmd.op) {
            case PUSH->pose.pushPose();case POP->pose.popPose();
            case TRANSLATE->pose.translate(cmd.a,cmd.b,cmd.c);case SCALE->pose.scale(cmd.a,cmd.b,cmd.c);
            case ROTATE->{if(cmd.payload instanceof org.joml.Quaternionf q)pose.mulPose(q);}
            case LOAD_MATRIX->pose.last().pose().set((float[])cmd.payload);
            case MULT_MATRIX->pose.mulPose(new org.joml.Matrix4f().set((float[])cmd.payload));
            case RENDER_PARTS,RENDER_GROUPS->{if(cmd.state != null && !cmd.state.enabled(0x0B50,true))unlitParts++;if(cmd.payload instanceof Set<?> names&&names.contains("body")){body++;var up=new org.joml.Vector3f(0,1,0).mulDirection(pose.last().pose());if(up.y<.99F)throw new AssertionError("E259 body inverted by script: "+up);}}
            default->{}
        }
        if(!pose.last().pose().equals(new org.joml.Matrix4f(),1E-5F))throw new AssertionError("E259 leaked transforms between frames");
        if(body!=2)throw new AssertionError("Expected E259 body in both passes, got "+body);
        if(unlitParts==0)throw new AssertionError("E259 interior did not exercise lighting-disabled rendering");
        System.out.println("Actual E259 script/init/entity render: "+unlitParts+" lighting-disabled parts recorded; body orientation: passed");
    }
}
