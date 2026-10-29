package com.portofino.realtrainmodunofficial.client;

import com.google.gson.Gson;
import com.portofino.realtrainmodunofficial.modelpack.transfer.ServerPackStorage;
import com.portofino.realtrainmodunofficial.network.ServerModelPackPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;

/** Client download runs off-thread; activating completed archives happens on the game thread. */
public final class ServerPackClient {
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(task->{Thread t=new Thread(task,"RTMU-Pack-Download");t.setDaemon(true);return t;});
    private static volatile String session="";
    private static final ArrayDeque<ServerPackStorage.Pack> pending=new ArrayDeque<>();
    private static ServerPackStorage.Pack current;
    private static ServerPackStorage.Receiver receiver;
    private static Path directory;
    private ServerPackClient(){}
    public static void begin() {
        if(!jp.ngt.rtm.RTMConfig.useServerModelPack)return;
        var mc=Minecraft.getInstance();var server=mc.getCurrentServer();
        String serverKey=server==null?"integrated":server.ip;
        String expected=UUID.randomUUID().toString();session=expected;
        try {directory=FMLPaths.CONFIGDIR.get().resolve("realtrainmodunofficial/server_modelpacks").resolve(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(serverKey.getBytes(StandardCharsets.UTF_8))).substring(0,24));}
        catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
        IO.execute(()->{closeReceiver();pending.clear();current=null;send(expected,new ServerModelPackPayload(ServerModelPackPayload.LIST,expected,"",0,0,new byte[0]));});
    }
    public static void end() {
        session="";IO.execute(()->{closeReceiver();pending.clear();current=null;});
        boolean active=ServerPackStorage.activeDirectory()!=null;ServerPackStorage.activate(null);
        if(active)reload();
    }
    public static void handle(ServerModelPackPayload message) {
        if(!jp.ngt.rtm.RTMConfig.useServerModelPack||!message.session().equals(session))return;
        IO.execute(()->{
            if(!message.session().equals(session))return;
            try {
                if(message.action()==ServerModelPackPayload.FAILED) {abort(message.session());return;}
                if(message.action()==ServerModelPackPayload.MANIFEST) {
                    closeReceiver();current=null;
                    var packs=new Gson().fromJson(new String(message.data(),StandardCharsets.UTF_8),ServerPackStorage.Pack[].class);
                    if(packs==null||packs.length>ServerPackStorage.MAX_FILES)throw new IllegalArgumentException("Invalid model pack manifest");
                    long total=0;var names=new HashSet<String>();pending.clear();
                    for(var pack:packs){if(!names.add(pack.name()))throw new IllegalArgumentException("Duplicate model pack");total+=pack.size();pending.add(pack);}
                    if(total>ServerPackStorage.MAX_TOTAL)throw new IllegalArgumentException("Model pack transfer exceeds limit");
                    // Each manifest has its own activation directory, so removed server packs cannot remain active.
                    directory=directory.resolve("manifest-"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(message.data())).substring(0,24));
                    next(message.session());
                } else if(message.action()==ServerModelPackPayload.CHUNK) {
                    if(current==null||receiver==null||!current.name().equals(message.name())||current.size()!=message.size())throw new IllegalArgumentException("Unexpected model pack chunk");
                    if(receiver.accept(message.offset(),message.data())){closeReceiver();current=null;next(message.session());}
                    else request(message.session());
                }
            }catch(Exception failure){abort(message.session());}
        });
    }
    private static void next(String expected) throws Exception {
        while((current=pending.poll())!=null) {
            Path existing=directory.resolve(current.name());
            if(Files.isRegularFile(existing,LinkOption.NOFOLLOW_LINKS)&&Files.size(existing)==current.size()&&ServerPackStorage.sha256(existing).equals(current.sha256()))continue;
            receiver=new ServerPackStorage.Receiver(directory,current);request(expected);return;
        }
        Minecraft.getInstance().execute(()->{if(expected.equals(session)){ServerPackStorage.activate(directory);reload();}});
    }
    private static void request(String expected) {
        send(expected,new ServerModelPackPayload(ServerModelPackPayload.GET,expected,current.name(),current.size(),receiver.offset(),new byte[0]));
    }
    private static void send(String expected,ServerModelPackPayload payload) {
        Minecraft.getInstance().execute(()->{if(expected.equals(session)&&jp.ngt.rtm.RTMConfig.useServerModelPack)PacketDistributor.sendToServer(payload);});
    }
    private static void abort(String expected) {
        closeReceiver();pending.clear();current=null;
        Minecraft.getInstance().execute(()->{if(expected.equals(session)){session="";var player=Minecraft.getInstance().player;if(player!=null)player.displayClientMessage(Component.literal("RTM: Server model-pack transfer unavailable or interrupted."),false);}});
    }
    private static void closeReceiver() {if(receiver!=null)try{receiver.close();}catch(java.io.IOException ignored){}finally{receiver=null;}}
    private static void reload() {
        jp.ngt.ngtlib.io.NGTFileLoader.invalidateAssets();
        com.portofino.realtrainmodunofficial.client.model.MqoModelLoader.invalidateModels();
        com.portofino.realtrainmodunofficial.client.render.RailScriptRenderers.invalidateAll();
        com.portofino.realtrainmodunofficial.client.render.VehicleScriptRenderers.invalidateAll();
        com.portofino.realtrainmodunofficial.vehicle.VehiclePackLoader.reload();
        com.portofino.realtrainmodunofficial.rail.RailPackLoader.reload();
        com.portofino.realtrainmodunofficial.installedobject.InstalledObjectPackLoader.reload();
        com.portofino.realtrainmodunofficial.client.sound.ExternalSoundPackBridge.rebuild();
        Minecraft.getInstance().reloadResourcePacks();
    }
}
