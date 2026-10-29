package jp.ngt.ngtlib.util;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Original name:version manifests; no network or wait on the game thread. */
public final class VersionChecker {
    public record Update(String name,String version,String homepage) {}
    private static final Map<String,PackInfo> CHECKS=new ConcurrentHashMap<>();
    private static volatile List<Update> updates=List.of();
    private static CompletableFuture<Void> checking;
    private VersionChecker(){}
    public static void addToCheckList(PackInfo info){if(info!=null&&info.name!=null&&info.version!=null)CHECKS.put(info.name+"\n"+info.version,info);}
    public static List<Update> getUpdates(){return jp.ngt.rtm.RTMConfig.versionCheck?updates:List.of();}
    public static Map<String,String> parseManifest(String text) {
        var result=new LinkedHashMap<String,String>();
        for(String line:text.split("\\R")){String[] fields=line.split(":");if(fields.length==2)result.put(fields[0],fields[1]);}
        return result;
    }
    public static List<Update> compare(List<PackInfo> packs,Map<String,String> versions) {
        var result=new ArrayList<Update>();var seen=new HashSet<String>();
        for(var pack:packs){String version=versions.get(pack.name);if(version!=null&&!version.equals(pack.version)&&seen.add(pack.name))result.add(new Update(pack.name,version,pack.homepage));}
        return List.copyOf(result);
    }
    public static void checkVersion(){checkVersionAsync();}
    public static synchronized CompletableFuture<Void> checkVersionAsync() {
        if(!jp.ngt.rtm.RTMConfig.versionCheck)return CompletableFuture.completedFuture(null);
        if(checking!=null&&!checking.isDone())return checking;
        var executor=Executors.newSingleThreadExecutor(task->{var thread=new Thread(task,"NGT Version Check");thread.setDaemon(true);return thread;});
        checking=CompletableFuture.runAsync(()->{
            try {
                for(byte[] metadata:jp.ngt.ngtlib.io.NGTFileLoader.readPackMetadata()) {
                    try{addToCheckList(new com.google.gson.Gson().fromJson(com.portofino.realtrainmodunofficial.util.PackTextDecoder.decodeJson(metadata),PackInfo.class));}catch(RuntimeException ignored){}
                }
                var packs=new ArrayList<>(CHECKS.values());var versions=new LinkedHashMap<String,String>();var visited=new HashSet<String>();
                var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NORMAL).build();
                for(var pack:packs) {
                    if(!jp.ngt.rtm.RTMConfig.versionCheck)break;
                    if(pack.updateURL==null||pack.updateURL.isBlank()||!visited.add(pack.updateURL))continue;
                    try {
                        URI uri=URI.create(pack.updateURL);
                        if(!"https".equalsIgnoreCase(uri.getScheme())&&!"http".equalsIgnoreCase(uri.getScheme()))continue;
                        var request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8)).GET().build();
                        var response=client.send(request,HttpResponse.BodyHandlers.ofInputStream());
                        try(var input=response.body()){if(response.statusCode()==200){byte[] bytes=input.readNBytes(1048577);if(bytes.length<=1048576)versions.putAll(parseManifest(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)));}}
                    } catch(InterruptedException interrupted){Thread.currentThread().interrupt();break;}
                    catch(Exception ignored){}
                }
                updates=compare(packs,versions);
            } finally {executor.shutdown();}
        },executor);
        return checking;
    }
}
