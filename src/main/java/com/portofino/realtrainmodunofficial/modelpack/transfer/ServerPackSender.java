package com.portofino.realtrainmodunofficial.modelpack.transfer;

import com.google.gson.Gson;
import com.portofino.realtrainmodunofficial.network.ServerModelPackPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import java.util.concurrent.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class ServerPackSender {
    private record Transfer(String session,Map<ServerPackStorage.Pack,Path> files,long created) {}
    private static final Map<UUID,Transfer> TRANSFERS=new ConcurrentHashMap<>();
    private static final Set<UUID> BUSY=ConcurrentHashMap.newKeySet();
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(task->{Thread t=new Thread(task,"RTMU-Server-Packs");t.setDaemon(true);return t;});
    private ServerPackSender(){}
    public static void forget(UUID player){TRANSFERS.remove(player);BUSY.remove(player);}
    public static void handle(ServerModelPackPayload request,ServerPlayer player) {
        if(!jp.ngt.rtm.RTMConfig.useServerModelPack) {reply(player,new ServerModelPackPayload(ServerModelPackPayload.FAILED,request.session(),"",0,0,new byte[0]));return;}
        if(!BUSY.add(player.getUUID()))return;
        IO.execute(()->{
            try {
                if(request.action()==ServerModelPackPayload.LIST) {
                    var previous=TRANSFERS.get(player.getUUID());
                    if(previous!=null && System.nanoTime()-previous.created<TimeUnit.SECONDS.toNanos(5))return;
                    UUID.fromString(request.session());
                    var files=ServerPackStorage.discover(FMLPaths.GAMEDIR.get());
                    TRANSFERS.put(player.getUUID(),new Transfer(request.session(),files,System.nanoTime()));
                    byte[] manifest=new Gson().toJson(files.keySet()).getBytes(StandardCharsets.UTF_8);
                    reply(player,new ServerModelPackPayload(ServerModelPackPayload.MANIFEST,request.session(),"",0,0,manifest));
                } else {
                    var transfer=TRANSFERS.get(player.getUUID());
                    if(transfer==null||!transfer.session.equals(request.session()))return;
                    var pack=transfer.files.keySet().stream().filter(p->p.name().equals(request.name())).findFirst().orElseThrow();
                    Path path=transfer.files.get(pack);
                    if(request.offset()>=pack.size()||Files.size(path)!=pack.size()||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new java.io.IOException("Pack changed during transfer");
                    byte[] bytes=new byte[(int)Math.min(ServerPackStorage.CHUNK,pack.size()-request.offset())];
                    try(var file=new java.io.RandomAccessFile(path.toFile(),"r")){file.seek(request.offset());file.readFully(bytes);}
                    reply(player,new ServerModelPackPayload(ServerModelPackPayload.CHUNK,request.session(),pack.name(),pack.size(),request.offset(),bytes));
                }
            } catch(Exception failure) {reply(player,new ServerModelPackPayload(ServerModelPackPayload.FAILED,request.session(),"",0,0,new byte[0]));}
            finally {BUSY.remove(player.getUUID());}
        });
    }
    private static void reply(ServerPlayer player,ServerModelPackPayload message) {
        player.server.execute(()->{if(!player.hasDisconnected())PacketDistributor.sendToPlayer(player,message);});
    }
}
