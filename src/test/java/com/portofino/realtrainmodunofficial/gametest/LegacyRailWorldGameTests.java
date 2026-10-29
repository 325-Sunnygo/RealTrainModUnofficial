package com.portofino.realtrainmodunofficial.gametest;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
import net.minecraft.core.BlockPos;
import jp.ngt.rtm.rail.*;
import jp.ngt.rtm.rail.util.*;
import net.minecraft.world.level.block.Blocks;
import java.util.*;

@GameTestHolder("realtrainmodunofficial")
@PrefixGameTestTemplate(false)
public final class LegacyRailWorldGameTests {
    @GameTest(template="signal_attachment_empty",timeoutTicks=100)
    public static void originalConfigBindings(GameTestHelper helper) throws Exception {
        var settings = jp.ngt.rtm.RTMConfigOptions.readLegacy(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("RTM.cfg"));
        helper.assertTrue(settings.size()==24,"RTM.cfg was not generated with every original setting");
        for (var option : jp.ngt.rtm.RTMConfigOptions.ALL) {
            Object expected = option.validate(settings.get(option));
            net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<?> configured = com.portofino.realtrainmodunofficial.RTMUConfig.SPEC.getValues().get(java.util.List.of(option.category(),option.key().replace('.', '_')));
            helper.assertTrue(option.validate(configured.get()).equals(expected),"Native settings screen disagrees with RTM.cfg: "+option.field());
            Object actual = jp.ngt.rtm.RTMConfig.class.getField(option.field()).get(null);
            if (expected instanceof Boolean) helper.assertTrue(expected.equals(actual),"Config was not bound: "+option.field());
            else {
                double value=((Number)expected).doubleValue();
                if(option.field().equals("trainSoundVol") || option.field().equals("gunSoundVol"))value/=100;
                helper.assertTrue(Math.abs(value-((Number)actual).doubleValue())<.00001,"Config was not bound: "+option.field());
            }
        }
        helper.succeed();
    }
    @GameTest(template="signal_attachment_empty",timeoutTicks=400)
    public static void e259SpawnPose(GameTestHelper helper) throws Exception {
        String pack=System.getProperty("rtmu.e259Pack","");if(pack.isBlank()){helper.succeed();return;}
        com.google.gson.JsonObject config;
        try(var zip=new java.util.zip.ZipFile(pack);var in=zip.getInputStream(zip.getEntry("json-1.7.10/ModelTrain_E259O-1.json"))){config=com.google.gson.JsonParser.parseString(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}
        var definition=new com.google.gson.Gson().fromJson("{\"id\":\"E259-pose-regression\",\"displayName\":\"E259-pose-regression\",\"modelScale\":1,\"modelOffset\":{\"x\":0,\"y\":0,\"z\":0},\"vehicleType\":\"EC\"}",com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition.class);definition.setLegacyConfig(config);
        var saved=com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry.getAll();var definitions=new ArrayList<>(saved);definitions.add(definition);com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry.setDefinitions(definitions);
        var level=helper.getLevel();int x=helper.absolutePos(BlockPos.ZERO).getX()+2048,z=helper.absolutePos(BlockPos.ZERO).getZ()+2048;
        for(int cx=(x-8)>>4;cx<=(x+8)>>4;cx++)for(int cz=(z-8)>>4;cz<=(z+104)>>4;cz++)level.getChunk(cx,cz);
        var a=point(x,z,0);var b=point(x,z+96,4);BlockMarker.createRail(level,x,80,z,List.of(a,b),new RailProperty("1067mm_Wood",Blocks.GRAVEL,0,.0625F,false),true,true);
        var train=new jp.ngt.rtm.entity.train.EntityTrain(jp.ngt.rtm.entity.RTMEntities.TRAIN.get(),level);
        train.moveTo(x+.5,80.0625+jp.ngt.rtm.entity.train.EntityTrainBase.TRAIN_HEIGHT,z+48,0,0);train.setModelName(definition.getId());train.spawnTrain(level);
        System.out.println("E259 spawned pose: yaw="+train.getYRot()+" pitch="+train.getXRot()+" roll="+train.rotationRoll);
        helper.assertTrue(Math.abs(train.getXRot())<1&&Math.abs(train.rotationRoll)<1,"E259 spawn flipped train: pitch="+train.getXRot()+", roll="+train.rotationRoll);
        helper.runAfterDelay(40,()->{
            System.out.println("E259 updated pose: yaw="+train.getYRot()+" pitch="+train.getXRot()+" roll="+train.rotationRoll);
            helper.assertTrue(Math.abs(train.getXRot())<1&&Math.abs(train.rotationRoll)<1,"E259 update flipped train: pitch="+train.getXRot()+", roll="+train.rotationRoll);
            train.discard();for(int i=0;i<2;i++)if(train.getBogie(i)!=null)train.getBogie(i).discard();
            com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry.setDefinitions(saved);helper.succeed();
        });
    }
    @GameTest(template="signal_attachment_empty",timeoutTicks=200,batch="config_consumers")
    public static void modelPackLoadModesStable(GameTestHelper helper) {
        int original=jp.ngt.rtm.RTMConfig.loadSpeed;
        java.util.List<String> expected=null;
        try {
            for(int mode:new int[]{1,2,3}) {
                jp.ngt.rtm.RTMConfig.loadSpeed=mode;
                com.portofino.realtrainmodunofficial.vehicle.VehiclePackLoader.reload();
                com.portofino.realtrainmodunofficial.rail.RailPackLoader.reload();
                com.portofino.realtrainmodunofficial.installedobject.InstalledObjectPackLoader.reload();
                var actual=new ArrayList<String>();
                com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry.getAll().forEach(d->actual.add("vehicle:"+d.getId()));
                com.portofino.realtrainmodunofficial.rail.RailRegistry.getAll().forEach(d->actual.add("rail:"+d.getId()));
                com.portofino.realtrainmodunofficial.installedobject.InstalledObjectRegistry.getAll().forEach(d->actual.add("installed:"+d.getId()));
                helper.assertTrue(!actual.isEmpty(),"parallel pack loading lost built-in definitions");
                if(expected==null)expected=actual;else helper.assertTrue(expected.equals(actual),"pack definitions/order changed with load speed "+mode);
            }
        }finally{jp.ngt.rtm.RTMConfig.loadSpeed=original;}
        helper.succeed();
    }
    @GameTest(template="signal_attachment_empty",timeoutTicks=200,batch="config_consumers")
    public static void markerPanelStateAndLimitsSync(GameTestHelper helper) {
        var level=helper.getLevel();var origin=helper.absolutePos(BlockPos.ZERO).offset(3000,140,3000);
        var end=origin.offset(0,0,16);level.getChunkAt(origin);level.getChunkAt(end);
        level.setBlockAndUpdate(origin,RTMRailBlocks.MARKER.get().defaultBlockState());
        level.setBlockAndUpdate(end,RTMRailBlocks.MARKER.get().defaultBlockState());
        var a=(TileEntityMarker)level.getBlockEntity(origin);var b=(TileEntityMarker)level.getBlockEntity(end);
        a.setMarkerRP(new RailPosition(origin.getX(),origin.getY(),origin.getZ(),(byte)0));
        b.setMarkerRP(new RailPosition(end.getX(),end.getY(),end.getZ(),(byte)4));
        a.setMarkersPos(List.of(new int[]{origin.getX(),origin.getY(),origin.getZ()},new int[]{end.getX(),end.getY(),end.getZ()}));
        var player=new net.minecraft.server.level.ServerPlayer(level.getServer(),level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"rtmu-config-test"),net.minecraft.server.level.ClientInformation.createDefault());player.setPos(origin.getX()+.5,origin.getY()+1,origin.getZ()+.5);
        var context=(net.neoforged.neoforge.network.handling.IPayloadContext)java.lang.reflect.Proxy.newProxyInstance(LegacyRailWorldGameTests.class.getClassLoader(),new Class<?>[]{net.neoforged.neoforge.network.handling.IPayloadContext.class},(proxy,method,args)->{
            if(method.getName().equals("player"))return player;
            if(method.getName().equals("enqueueWork")){((Runnable)args[0]).run();return java.util.concurrent.CompletableFuture.completedFuture(null);}
            return null;
        });
        int flags=MarkerState.LINE1.set(MarkerState.LINE2.set(0,true),true);
        for(var marker:List.of(a,b)) {
            var tag=marker.getMarkerRP().writeToNBT();tag.putInt("MarkerState",flags);tag.putFloat("Const_Limit_HP",5.2F);
            com.portofino.realtrainmodunofficial.network.MarkerAnchorPayload.handleOnServer(new com.portofino.realtrainmodunofficial.network.MarkerAnchorPayload(marker.getBlockPos(),tag),context);
        }
        for(var marker:List.of(a,b)) {
            helper.assertTrue(marker.getMarkerState()==flags&&!marker.fitNeighbor,"marker display/snapping state not synchronized");
            helper.assertTrue(Math.abs(marker.getMarkerRP().constLimitHP-5.2F)<.0001,"construction limit edit was lost");
            var saved=marker.saveWithFullMetadata(level.registryAccess());
            var restored=(TileEntityMarker)net.minecraft.world.level.block.entity.BlockEntity.loadStatic(marker.getBlockPos(),marker.getBlockState(),saved,level.registryAccess());
            helper.assertTrue(restored.getMarkerState()==flags&&!restored.fitNeighbor,"marker state changed after save/load");
        }
        helper.succeed();
    }
    private static RailPosition point(int x,int z,int direction) {
        var rp=new RailPosition(x,80,z,(byte)direction);rp.setPosition(x+0.5,80.0625,z);rp.anchorLengthHorizontal=24;rp.anchorYaw=direction*45;return rp;
    }
    @GameTest(template="signal_attachment_empty",timeoutTicks=400)
    public static void sectionCreateSaveReplaceAndRemove(GameTestHelper helper) {
        var level=helper.getLevel();int x=helper.absolutePos(BlockPos.ZERO).getX()+1024,z=helper.absolutePos(BlockPos.ZERO).getZ()+1024;
        for(int cx=(x-8)>>4;cx<=(x+8)>>4;cx++)for(int cz=(z-8)>>4;cz<=(z+104)>>4;cz++)level.getChunk(cx,cz);
        var a=point(x,z,0);var b=point(x,z+96,4);var property=new RailProperty("1067mm_Wood",Blocks.GRAVEL,0,0.0625F,true);
        helper.assertTrue(BlockMarker.createRail(level,x,80,z,List.of(a,b),property,true,true),"sectioned rail creation failed");
        var core=(TileEntityLargeRailNormalCore)level.getBlockEntity(new BlockPos(x,80,z));
        helper.assertTrue(core instanceof jp.kaiz.kaizpatch.rtm.rail.TileEntityLargeRailSectionCore && core.isRailSection(),"section core type/state missing");
        var cores=core.getRailGroupCorePositions();helper.assertTrue(cores.size()>=6,"no chunk splitting");
        var nbt=core.saveWithFullMetadata(level.registryAccess());
        var restored=net.minecraft.world.level.block.entity.BlockEntity.loadStatic(core.getBlockPos(),core.getBlockState(),nbt,level.registryAccess());
        helper.assertTrue(restored instanceof TileEntityLargeRailNormalCore copy && copy.isRailSection() && copy.getRailGroupId().equals(core.getRailGroupId()),"section NBT round trip lost group");
        var packet=net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(core);
        helper.assertTrue(packet.getTag().contains("RailSection"),"tile update packet lost section metadata");
        core.setSignal(7);var replacement=new RailProperty("1067mm_Concrete",Blocks.STONE,0,0.125F,false);core.replaceRail(replacement);
        for(var position:cores) {
            var other=(TileEntityLargeRailNormalCore)level.getBlockEntity(new BlockPos(position[0],position[1],position[2]));
            helper.assertTrue(other.getSignal()==7 && other.getProperty().railModel.equals(replacement.railModel),"logical group update did not propagate");
        }
        core.breakLogicalRail();
        for(var position:cores)helper.assertTrue(level.getBlockEntity(new BlockPos(position[0],position[1],position[2]))==null,"logical rail left a section core");
        helper.succeed();
    }
    @GameTest(template="signal_attachment_empty",timeoutTicks=400)
    public static void disabledAutoSplitStaysSingle(GameTestHelper helper) {
        var level=helper.getLevel();int x=helper.absolutePos(BlockPos.ZERO).getX()+1024,z=helper.absolutePos(BlockPos.ZERO).getZ()+1024;for(int cz=(z-8)>>4;cz<=(z+104)>>4;cz++)level.getChunk(x>>4,cz);
        var a=point(x,z,0);var b=point(x,z+96,4);
        helper.assertTrue(BlockMarker.createRail(level,x,80,z,List.of(a,b),new RailProperty("1067mm_Wood",Blocks.GRAVEL,0,0.0625F,false),true,true),"non-split creation failed");
        var core=(TileEntityLargeRailNormalCore)level.getBlockEntity(new BlockPos(x,80,z));helper.assertTrue(!core.isRailSection(),"AutoSplit=false ignored");
        var previous=core.getRailMap(null);var original=core.getRailPositions();core.setRailPositions(new RailPosition[]{a,point(x,z+80,4)});
        helper.assertTrue(core.getRailMap(null)!=previous&&core.getRailMap(null).getLength()<previous.getLength(),"setRailPositions retained the stale cached curve");
        core.setRailPositions(original);core.breakLogicalRail();helper.succeed();
    }
    @GameTest(template="signal_attachment_empty",timeoutTicks=1200)
    public static void srbxActualCreateAndUndo(GameTestHelper helper) throws Exception {
        String pack=System.getProperty("rtmu.srbxPack","");if(pack.isBlank())throw new IllegalStateException("Set -PrtmuSrbxPack to the actual SRBX ZIP");
        Map<String,byte[]> files=new HashMap<>();
        try(var zip=new java.util.zip.ZipFile(pack)) {for(var entry:Collections.list(zip.entries()))if(entry.getName().startsWith("assets/minecraft/") && !entry.isDirectory())try(var input=zip.getInputStream(entry)) {files.put(entry.getName().substring(17),input.readAllBytes());}}
        String name="scripts/superrailbuilderx/server_builder1.js";
        var source=com.portofino.realtrainmodunofficial.script.LegacyExecutionIncludes.prepare(com.portofino.realtrainmodunofficial.script.PackScriptSource.decode(files.get(name)),name,files::get,com.portofino.realtrainmodunofficial.script.PackScriptSource.SERVER_PRELUDE);
        String testId="srbx-async-server-regression";
        var definition=new com.google.gson.Gson().fromJson("{\"id\":\""+testId+"\"}",com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition.class);definition.setServerScriptPath(name);
        var savedDefinitions=com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry.getAll();
        var testDefinitions=new ArrayList<>(savedDefinitions);testDefinitions.add(definition);com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry.setDefinitions(testDefinitions);
        var pendingEntity=com.portofino.realtrainmodunofficial.registry.RealTrainModUnofficialEntities.CAR.get().create(helper.getLevel());pendingEntity.setVehicleId(testId);
        var ensureLoaded=com.portofino.realtrainmodunofficial.entity.CarEntity.class.getDeclaredMethod("ensureServerScriptLoaded");ensureLoaded.setAccessible(true);
        var scriptField=com.portofino.realtrainmodunofficial.entity.CarEntity.class.getDeclaredField("serverScript");scriptField.setAccessible(true);
        var release=new java.util.concurrent.CountDownLatch(1);
        var future=com.portofino.realtrainmodunofficial.script.AsyncScriptCompiler.requestPrepared("vehicle-server|"+testId,name,()->{
            try {if(!release.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("test timeout");}catch(InterruptedException e){throw new RuntimeException(e);}return source;
        });
        try {
            long started=System.nanoTime();
            ensureLoaded.invoke(pendingEntity);
            helper.assertTrue(scriptField.get(pendingEntity)==null,"server tick waited for compilation");
            helper.assertTrue((System.nanoTime()-started)/1_000_000<250,"server script loading blocked tick");
        } finally {release.countDown();}
        future.get(60,java.util.concurrent.TimeUnit.SECONDS);
        ensureLoaded.invoke(pendingEntity);
        var serverEntry=(com.portofino.realtrainmodunofficial.script.CarServerScripts.Entry)scriptField.get(pendingEntity);
        com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry.setDefinitions(savedDefinitions);
        helper.assertTrue(serverEntry!=null,"compiled server script was not initialized on next tick");
        var engine=serverEntry.engine();
        var level=helper.getLevel();int x=helper.absolutePos(BlockPos.ZERO).getX()+1024,z=helper.absolutePos(BlockPos.ZERO).getZ()+1024;for(int cx=(x-8)>>4;cx<=(x+8)>>4;cx++)for(int cz=(z-8)>>4;cz<=(z+104)>>4;cz++)level.getChunk(cx,cz);
        engine.put("testWorld",new jp.ngt.mccompat.WorldCompat(level));engine.put("testHost",jp.ngt.mccompat.PlayerCompat.EMPTY);
        engine.put("testProperty",new RailProperty("1067mm_Wood",Blocks.GRAVEL,0,0.0625F,true));
        engine.put("testStart",point(x,z,0));engine.put("testEnd",point(x,z+96,4));
        engine.eval("var testApi=RTMX_COMPAT_scripts_superrailbuilderx_SRBXApiCompat_1js5ute.SRBXApiCompat; var start=testApi.splitPointFromRailPosition(testStart,24,0), end=testApi.splitPointFromRailPosition(testEnd,24,0); var creation=testApi.createBuilderRail(testWorld,testHost,start,end,[],undefined,testProperty,false,true,false,undefined,undefined,true); var creationStatus=creation.status;");
        helper.assertTrue("ok".equals(engine.get("creationStatus")),"actual SRBX create returned "+engine.get("creationStatus"));
        engine.eval("var splitResult=testApi.splitBuilderRail(testWorld,testHost,creation.undoCore,creation.undoKey,0.5);var splitStatus=splitResult.status;");
        helper.assertTrue("ok".equals(engine.get("splitStatus")),"actual SRBX split returned "+engine.get("splitStatus"));
        engine.eval("var splitUndoStatus=testApi.undoSplitBuilderRail(testWorld,testHost,splitResult.undoToken);");
        helper.assertTrue("undo_ok".equals(engine.get("splitUndoStatus")),"actual SRBX split undo returned "+engine.get("splitUndoStatus"));
        engine.eval("var currentCore=testWorld.func_147438_o(creation.undoCore[0],creation.undoCore[1],creation.undoCore[2]);var cantPoints=testApi.copyRailPositions(currentCore.getLogicalRailPositions());cantPoints[0].cantCenter=3;cantPoints[1].cantCenter=3;testApi.updateCantRail(testWorld,currentCore,cantPoints);");
        var updated=(TileEntityLargeRailNormalCore)level.getBlockEntity(new BlockPos(x,80,z));
        helper.assertTrue(updated.getLogicalRailPositions()[0].cantCenter==3,"actual SRBX cant edit lost logical curve");
        engine.eval("creation.undoKey=testApi.getRailPositionCandidateKey(currentCore);");
        engine.eval("var undoResult=testApi.undoBuilderRail(testWorld,creation.undoCore[0],creation.undoCore[1],creation.undoCore[2],creation.undoKey);");
        helper.assertTrue("ok".equals(engine.get("undoResult")),"actual SRBX undo returned "+engine.get("undoResult"));
        var entity=com.portofino.realtrainmodunofficial.registry.RealTrainModUnofficialEntities.CAR.get().create(level);engine.put("testEntity",entity);
        var host=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"srbx_test"));
        var held=new net.minecraft.world.item.ItemStack(com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems.RAIL_ITEM.get());
        jp.ngt.rtm.item.ItemRail.writePropToItem(new RailProperty("1067mm_Wood",Blocks.GRAVEL,0,0.0625F,true),held);
        host.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,held);engine.put("testHost",jp.ngt.mccompat.PlayerCompat.of(host));

        engine.eval("var callbackCreate=processRequest(testEntity,testHost,{action:'create',start:start,end:end});");
        helper.assertTrue("ok".equals(engine.get("callbackCreate")),"SRBX processRequest create returned "+engine.get("callbackCreate"));
        var payload=new com.portofino.realtrainmodunofficial.network.CarScriptDataSyncPayload(entity.getId(),new HashMap<>(entity.scriptDataMap()));
        var buffer=io.netty.buffer.Unpooled.buffer();
        try {
            var codec=com.portofino.realtrainmodunofficial.network.CarScriptDataSyncPayload.STREAM_CODEC;
            codec.encode(buffer,payload);var decoded=codec.decode(buffer);
            var replica=com.portofino.realtrainmodunofficial.registry.RealTrainModUnofficialEntities.CAR.get().create(level);replica.applyScriptDataSync(decoded.data());
            helper.assertTrue(decoded.data().containsKey("builder1CreatedAsNormalCrossing") && replica.scriptDataMap().equals(entity.scriptDataMap()),"actual SRBX DataMap packet round trip changed data");
        } finally {buffer.release();}
        engine.eval("var callbackUndo=processRequest(testEntity,testHost,{action:'undo'});");
        helper.assertTrue("undo_ok".equals(engine.get("callbackUndo")),"SRBX processRequest undo returned "+engine.get("callbackUndo"));
        engine.eval("var moving=testApi.createBuilderRail(testWorld,testHost,start,end,[],undefined,testProperty,false,true,false,undefined,undefined,true);var movingCore=testWorld.func_147438_o(moving.undoCore[0],moving.undoCore[1],moving.undoCore[2]);var originalMovePoints=testApi.getEditableRailPositions(movingCore);var originalMoveStart=[originalMovePoints[0].posX,originalMovePoints[0].posY,originalMovePoints[0].posZ],originalMoveEnd=[originalMovePoints[1].posX,originalMovePoints[1].posY,originalMovePoints[1].posZ];var shifted=testApi.copyRailPositions(originalMovePoints);for(var i=0;i<2;i++)shifted[i].movePos(8,0,0);var movedStart=testApi.splitPointFromRailPosition(shifted[0],24,0),movedEnd=testApi.splitPointFromRailPosition(shifted[1],24,0);var moveStatus=testApi.moveBuilderRail(movingCore,moving.undoKey,originalMoveStart,originalMoveEnd,movedStart,movedEnd,testHost,true);");
        helper.assertTrue("ok".equals(engine.get("moveStatus")),"actual SRBX rail move returned "+engine.get("moveStatus"));
        var moved=(TileEntityLargeRailNormalCore)level.getBlockEntity(new BlockPos(x+8,80,z));
        helper.assertTrue(moved!=null && moved.isRailSection(),"actual SRBX move did not rebuild rail at target");
        moved.breakLogicalRail();
        engine.eval("testEntity.field_70153_n=testHost;onUpdate(testEntity,null);testEntity.field_70153_n=null;onUpdate(testEntity,null);");
        helper.assertTrue(entity.getResourceState().getDataMap().getString("VERSIONS").equals("1.0.0"),"actual SRBX onUpdate did not initialize state");
        serverEntry.onUpdate(entity);
        com.portofino.realtrainmodunofficial.script.CarServerScripts.forget(testId);
        entity.stopRiding();
        var serverPaths=new java.util.LinkedHashSet<String>();
        try(var zip=new java.util.zip.ZipFile(pack)) {
            for(var entry:Collections.list(zip.entries()))if(entry.getName().endsWith(".json") && entry.getName().contains("ModelVehicle_"))try(var input=zip.getInputStream(entry)) {
                var json=com.google.gson.JsonParser.parseString(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                if(json.has("serverScriptPath"))serverPaths.add(json.get("serverScriptPath").getAsString());
            }
        }
        int initialized=0;
        for(String serverPath:serverPaths) {
            if(serverPath.equals(name)) {initialized++;continue;}
            var other=jp.ngt.ngtlib.io.ScriptUtil.createEngine();
            var prepared=com.portofino.realtrainmodunofficial.script.LegacyExecutionIncludes.prepare(com.portofino.realtrainmodunofficial.script.PackScriptSource.decode(files.get(serverPath)),serverPath,files::get,com.portofino.realtrainmodunofficial.script.PackScriptSource.SERVER_PRELUDE);
            other.put(javax.script.ScriptEngine.FILENAME,serverPath);prepared.bind(other);other.eval(prepared.source());
            var tool=com.portofino.realtrainmodunofficial.registry.RealTrainModUnofficialEntities.CAR.get().create(level);
            tool.field_70153_n=jp.ngt.mccompat.PlayerCompat.of(host);other.put("testEntity",tool);
            other.eval("onUpdate(testEntity,null);");tool.field_70153_n=null;other.eval("onUpdate(testEntity,null);");tool.stopRiding();initialized++;
        }
        helper.assertTrue(initialized==8,"expected all eight unique SRBX server entry points");
        System.out.println("Actual SRBX server entry points: "+initialized+" passed (load/onUpdate; create/undo/split/cant/move exercised)");
        helper.succeed();
    }
}
