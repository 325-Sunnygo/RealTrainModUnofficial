package com.portofino.realtrainmodunofficial.script;

import com.portofino.realtrainmodunofficial.util.*;
import com.portofino.realtrainmodunofficial.modelpack.transfer.ServerPackStorage;
import com.portofino.realtrainmodunofficial.network.*;
import java.nio.file.*;
import java.util.*;

public final class LegacyConfigRuntimeRegression {
    private static int checks;
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        for(var direction:net.minecraft.core.Direction.values()) {
            var normal=net.minecraft.world.phys.Vec3.atLowerCornerOf(direction.getNormal());
            var u=direction.getAxis()==net.minecraft.core.Direction.Axis.Y?new net.minecraft.world.phys.Vec3(1,0,0):new net.minecraft.world.phys.Vec3(0,1,0).cross(normal).normalize();
            var v=normal.cross(u);
            var corners=List.of(u.add(v),u.subtract(v),u.scale(-1).add(v),u.scale(-1).subtract(v));
            var eye=normal.scale(4).add(u.scale(.3));
            var view=com.portofino.realtrainmodunofficial.client.mirror.MirrorGeometry.view(eye,direction,corners,128);
            check(view!=null,"mirror front rejected: "+direction);
            check(Math.abs(view.eye().dot(normal)+4)<1e-6,"reflection plane changed: "+direction);
            for(var corner:corners) {
                var uv=com.portofino.realtrainmodunofficial.client.mirror.MirrorGeometry.uv(view,corner);
                check(Math.min(Math.abs(uv[0]),Math.abs(uv[0]-1))<1e-5&&Math.min(Math.abs(uv[1]),Math.abs(uv[1]-1))<1e-5,"off-axis reflection UV changed: "+direction);
                var delta=corner.subtract(view.eye());
                var clip=new org.joml.Vector4f((float)delta.x,(float)delta.y,(float)delta.z,1).mul(view.rotation()).mul(view.projection());
                check(Math.abs(Math.abs(clip.x/clip.w)-1)<1e-5&&Math.abs(Math.abs(clip.y/clip.w)-1)<1e-5,"mirror frustum no longer passes through corners: "+direction);
            }
            check(com.portofino.realtrainmodunofficial.client.mirror.MirrorGeometry.view(normal.scale(-4),direction,corners,128)==null,"back-facing mirror captured");
        }
        check(!com.portofino.realtrainmodunofficial.client.mirror.MirrorRenderer.due(10,9,2)&&com.portofino.realtrainmodunofficial.client.mirror.MirrorRenderer.due(11,9,2),"mirror refresh frequency ignored");
        long[] clock={0},limit={10},protection={100};var discarded=new ArrayList<String>();
        var cache=new ProtectedModelCache<String,String>(v->6,()->limit[0],()->protection[0],discarded::add,()->clock[0]);
        cache.put("a","first");cache.put("b","second");check(cache.retainedBytes()==12&&cache.size()==2,"recent model was evicted while protected");
        clock[0]=101;check("second".equals(cache.get("b")),"active model lost");check(cache.size()==1&&discarded.equals(List.of("first")),"old model did not release after protection window");
        limit[0]=0;protection[0]=0;cache.trim();check(cache.size()==0&&cache.retainedBytes()==0,"updated model memory budget ignored");
        check(LegacyLoadPolicy.workers(1,12)==1&&LegacyLoadPolicy.workers(2,12)==4&&LegacyLoadPolicy.workers(3,12)==12,"load speed differs from original concurrency");
        check(LegacyLoadPolicy.workers(2,1)==1,"small CPU load speed has no worker");
        var range=com.portofino.realtrainmodunofficial.client.sound.LegacyScriptSoundManager.class;
        check(com.portofino.realtrainmodunofficial.client.sound.LegacyScriptSoundManager.rangeVolume(1,1,0,48)==1,"near running sound volume changed");
        check(com.portofino.realtrainmodunofficial.client.sound.LegacyScriptSoundManager.rangeVolume(1,1,24,48)==.5F,"running sound falloff ignored");
        check(com.portofino.realtrainmodunofficial.client.sound.LegacyScriptSoundManager.rangeVolume(1,1,48,48)==0,"sound audible past configured range");
        check(com.portofino.realtrainmodunofficial.client.sound.LegacyScriptSoundManager.rangeVolume(1,1,0,0)==0,"zero sound range is not mute");
        var buffer=io.netty.buffer.Unpooled.buffer();
        try {
            var sound=new TrainSoundPayload(7,"rtm:horn",.8F,1.1F,128F);TrainSoundPayload.STREAM_CODEC.encode(buffer,sound);
            check(sound.equals(TrainSoundPayload.STREAM_CODEC.decode(buffer)),"server sound range lost in network round trip");
            buffer.clear();var message=new ServerModelPackPayload(ServerModelPackPayload.CHUNK,UUID.randomUUID().toString(),"ModelPack_test.zip",500,0,new byte[]{1,2,3});
            ServerModelPackPayload.STREAM_CODEC.encode(buffer,message);var copy=ServerModelPackPayload.STREAM_CODEC.decode(buffer);
            check(copy.name().equals(message.name())&&Arrays.equals(copy.data(),message.data()),"model pack network codec changed bytes");
        } finally {buffer.release();}
        boolean original=jp.ngt.rtm.RTMConfig.versionCheck;
        try {
            jp.ngt.rtm.RTMConfig.versionCheck=false;
            com.portofino.realtrainmodunofficial.online.RtmuOnlineServices.init();
            var started=com.portofino.realtrainmodunofficial.online.RtmuOnlineServices.class.getDeclaredField("started");started.setAccessible(true);
            check(!started.getBoolean(null),"disabled version check still started network worker");
        } finally {jp.ngt.rtm.RTMConfig.versionCheck=original;}
        var root=Files.createTempDirectory("rtmu-server-pack-transfer");
        try {
            Path mods=Files.createDirectories(root.resolve("mods"));Path zip=mods.resolve("ModelPack_test.zip");
            byte[] payload=new byte[40000];new Random(42).nextBytes(payload);
            try(var output=new java.util.zip.ZipOutputStream(Files.newOutputStream(zip))){output.putNextEntry(new java.util.zip.ZipEntry("assets/minecraft/models/test.mqo"));output.write(payload);output.closeEntry();}
            Files.copy(zip,mods.resolve("private.zip"));
            var files=ServerPackStorage.discover(root);check(files.size()==1,"server exposed archives outside ModelPack_ prefix");
            var pack=files.keySet().iterator().next();Path downloads=root.resolve("download");byte[] contents=Files.readAllBytes(zip);
            try(var receiver=new ServerPackStorage.Receiver(downloads,pack)) {
                try{receiver.accept(1,new byte[]{0});throw new AssertionError("out of order chunk accepted");}catch(java.io.IOException expected){checks++;}
                for(int offset=0;offset<contents.length;offset+=ServerPackStorage.CHUNK)receiver.accept(offset,Arrays.copyOfRange(contents,offset,Math.min(contents.length,offset+ServerPackStorage.CHUNK)));
            }
            check(Arrays.equals(contents,Files.readAllBytes(downloads.resolve(pack.name()))),"model pack transfer changed archive bytes");
            for(String bad:new String[]{"../ModelPack_x.zip","ModelPack_../x.zip","ModelPack_a\\x.zip","ModelPack_x.zip:evil","private.zip"}) {
                try{ServerPackStorage.validateName(bad);throw new AssertionError("unsafe model pack name accepted");}catch(IllegalArgumentException expected){checks++;}
            }
            var bad=new ServerPackStorage.Pack(pack.name(),pack.size(),"0".repeat(64));
            try(var receiver=new ServerPackStorage.Receiver(root.resolve("corrupt"),bad)) {
                try{for(int offset=0;offset<contents.length;offset+=ServerPackStorage.CHUNK)receiver.accept(offset,Arrays.copyOfRange(contents,offset,Math.min(contents.length,offset+ServerPackStorage.CHUNK)));throw new AssertionError("bad digest accepted");}
                catch(java.io.IOException expected){checks++;}
            }
            try(var paths=Files.list(root.resolve("corrupt"))){check(paths.findAny().isEmpty(),"interrupted model pack left partial files");}
        } finally {try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
        System.out.println("Legacy config runtime/cache/load/sound/transfer: "+checks+" passed");
    }
}
