package com.portofino.realtrainmodunofficial.script;

import com.portofino.realtrainmodunofficial.vehicle.*;
import jp.ngt.rtm.modelpack.cfg.*;
import java.util.*;

/** Actual pack JSON -> native definition -> script configuration, without a world. */
public final class LegacyVehicleConfigRegression {
    private static int checks;
    private static void check(boolean ok,String message) {checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        String json="""
          {"trainName":"regression_config","trainType":"DL",
           "trainModel2":{"modelFile":"test.mqo","textures":[["default","test.png"]]},
           "smoothing":true,"doCulling":true,"notDisplayCab":true,"isSingleTrain":true,
           "offset":[1,2,3],"scale":2,"maxSpeed":[0.1,0.2,0.3],
           "accuracy":"LOW","serverScriptPath":"scripts/server.js","guiScriptPath":"scripts/gui.js",
           "rollsignTexture":"sign.png","sound_Joint":"joint","wheelRotationSpeed":2,
           "jointDelay":[[0,1,2],[0,3]],"smoke":[[0,1,2,"steam",1,2,0.1]],
           "customButtons":[["a","b"]],"headLights":[{"type":1,"color":123,"pos":[1,2,3],"r":4}],
           "door_left":[{"objects":["door"],"pos":[1,2,3],"transform":[[1,0,0]],"childParts":[]}],
           "renderAABB":[-1,-2,-3,1,2,3],"tags":"rail","defaultData":"mode=(Int)2"}
          """;
        var parse=VehiclePackLoader.class.getDeclaredMethod("parseTrainJson",byte[].class,String.class,String.class);parse.setAccessible(true);
        var loadedField=VehiclePackLoader.class.getDeclaredField("LOADED");loadedField.setAccessible(true);
        @SuppressWarnings("unchecked") var loaded=(List<VehicleDefinition>)loadedField.get(null);
        var previous=List.copyOf(loaded);var registry=VehicleRegistry.getAll();
        try {
            parse.invoke(null,json.getBytes(java.nio.charset.StandardCharsets.UTF_8),"regression","ModelTrain_regression_config.json");
            var def=loaded.get(loaded.size()-1);VehicleRegistry.setDefinitions(List.of(def));TrainConfigAdapter.clearCache();
            var cfg=TrainConfigAdapter.get(def.getId());
            check(cfg.trainType.equals("DL"),"diesel type reaches script config");
            check(cfg.smoothing && cfg.doCulling,"render flags reach script config");
            check(cfg.scale==2 && Arrays.equals(cfg.offset,new float[]{1,2,3}),"model placement reaches script config");
            check(cfg.notDisplayCab && cfg.isSingleTrain,"cab and single-train flags retained");
            var manager=jp.ngt.rtm.modelpack.ModelPackManager.INSTANCE;
            var handle=java.lang.invoke.MethodHandles.publicLookup().findVirtual(manager.getClass(),"getModelSet",java.lang.invoke.MethodType.methodType(jp.ngt.rtm.modelpack.modelset.ModelSetBase.class,String.class,String.class));
            try {
                var found=(jp.ngt.rtm.modelpack.modelset.ModelSetBase<?>)handle.invokeWithArguments(manager,"ModelTrain",def.getId());
                var missing=(jp.ngt.rtm.modelpack.modelset.ModelSetBase<?>)handle.invokeWithArguments(manager,"ModelTrain","missing-model");
                check(found.getConfig()==cfg && !found.isDummy() && missing.isDummy(),"SRBX exact MethodHandles model lookup and dummy semantics");
            } catch(Throwable failure) {throw new AssertionError(failure);}
            var originalConfig=def.getLegacyConfig();var highConfig=originalConfig.deepCopy();highConfig.addProperty("accuracy","HIGH");def.setLegacyConfig(highConfig);
            check(def.getModelAccuracy()==jp.ngt.ngtlib.renderer.model.VecAccuracy.LOW,"native config precision matches original non-MEDIUM rule");def.setLegacyConfig(originalConfig);
            check(cfg.accuracy.equals("LOW") && def.getModelAccuracy()==jp.ngt.ngtlib.renderer.model.VecAccuracy.LOW,"precision retained from raw config");
            check(cfg.jointDelay[0].length==3 && cfg.wheelRotationSpeed==2 && cfg.smoke.length==1,"joint wheel and smoke fields retained");
            check(cfg.headLights[0].color==123 && cfg.door_left[0].objects[0].equals("door"),"typed lights and recursive parts retained");
            check(cfg.customButtons[0][1].equals("b") && cfg.customButtonTips.length==1,"custom button defaults follow source");
            check(cfg.sound_Joint.equals("rtm:joint") && cfg.serverScriptPath.equals("scripts/server.js") && cfg.guiScriptPath.equals("scripts/gui.js"),"sound domains and script paths retained");
            check(cfg.getModel()!=null && cfg.getModel().modelFile.equals("test.mqo"),"model source preserved for script getters");
            check(cfg.renderAABB[0]==-1 && cfg.defaultValues[0].key.equals("mode"),"base configuration fields initialized");
            check(cfg.maxSpeed.length==3 && Math.abs(cfg.maxSpeed[2]-0.3F)<1e-6,"non-cab speed profile is not replaced by five-notch default");
            String carJson="{\"name\":\"config_car\",\"vehicleType\":\"Car\",\"model\":{\"modelFile\":\"car.obj\"},\"maxSpeed\":[0.2,0.7],\"friction\":[0.6,0.8],\"accuracy\":\"LOW\",\"customButtons\":[[\"one\",\"two\"]]}";
            parse.invoke(null,carJson.getBytes(java.nio.charset.StandardCharsets.UTF_8),"regression","ModelVehicle_config_car.json");
            var carDefinition=loaded.get(loaded.size()-1);VehicleRegistry.setDefinitions(List.of(carDefinition));
            var carConfig=VehicleConfigAdapter.get(carDefinition);
            check(carConfig.getName().equals("config_car") && carConfig.getModel().modelFile.equals("car.obj"),"ModelVehicle keeps its original config type and model source");
            check(carConfig.getMaxSpeed(true)==0.2F && carConfig.getMaxSpeed(false)==0.7F && carConfig.getFriction(true)==0.6F,"ground/air vehicle profiles are not converted to train notches");
            check(carConfig.getPlayerPos().length==1 && carConfig.accuracy.equals("LOW"),"vehicle default seating and precision follow source");
            check(manager.getModelSet("ModelVehicle",carDefinition.getId()).getConfig()==carConfig,"model manager resolves original VehicleConfig");
            var property=new jp.ngt.rtm.rail.util.RailProperty("1067mm_Wood",net.minecraft.world.level.block.Blocks.GRAVEL,0,0.0625F,false);
            var propertyNbt=new net.minecraft.nbt.CompoundTag();property.writeToNBT(propertyNbt);
            check(!jp.ngt.rtm.rail.util.RailProperty.readFromNBT(propertyNbt).autoSplit,"AutoSplit disabled state persists");
            propertyNbt.remove("AutoSplit");check(jp.ngt.rtm.rail.util.RailProperty.readFromNBT(propertyNbt).autoSplit,"legacy rails without AutoSplit default to enabled");
            var payload=new com.portofino.realtrainmodunofficial.network.SelectModelPayload("1067mm_Wood","","",0xFFFFFF,false);
            var buffer=io.netty.buffer.Unpooled.buffer();
            try {var codec=com.portofino.realtrainmodunofficial.network.SelectModelPayload.STREAM_CODEC;codec.encode(buffer,payload);check(codec.decode(buffer).equals(payload),"rail selection network preserves disabled AutoSplit");} finally {buffer.release();}
        } finally {loaded.clear();loaded.addAll(previous);VehicleRegistry.setDefinitions(registry);TrainConfigAdapter.clearCache();}
        System.out.println("Legacy vehicle JSON/config regressions: "+checks+" passed");
    }
}
