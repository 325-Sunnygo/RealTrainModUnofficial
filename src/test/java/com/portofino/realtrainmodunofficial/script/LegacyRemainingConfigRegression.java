package com.portofino.realtrainmodunofficial.script;

import com.portofino.realtrainmodunofficial.util.OrderedPackTasks;
import com.portofino.realtrainmodunofficial.client.renderer.MarkerPanel;
import jp.ngt.ngtlib.util.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Behavioral checks for the remaining original config consumers. */
public final class LegacyRemainingConfigRegression {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        int speed=jp.ngt.rtm.RTMConfig.loadSpeed;
        try {
            for(int mode:new int[]{1,2,3}) {
                jp.ngt.rtm.RTMConfig.loadSpeed=mode;
                var registrations=new ArrayList<Integer>();var workers=ConcurrentHashMap.<String>newKeySet();
                Thread caller=Thread.currentThread();
                try(var batch=OrderedPackTasks.begin()) {
                    for(int i=0;i<300;i++) {
                        int index=i;
                        OrderedPackTasks.submit(()->{workers.add(Thread.currentThread().getName());
                            try{Thread.sleep(index%3);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}
                            OrderedPackTasks.publish(index,value->{check(Thread.currentThread()==caller,"registry published on worker");registrations.add(value);});
                        });
                    }
                }
                check(registrations.equals(java.util.stream.IntStream.range(0,300).boxed().toList()),"parallel load reordered or lost definitions");
                if(mode==1)check(workers.size()==1,"slow load used multiple workers");
                else if(Runtime.getRuntime().availableProcessors()>=6)check(workers.size()>1,"parallel setting did not parse concurrently");
            }
        } finally {jp.ngt.rtm.RTMConfig.loadSpeed=speed;}
        var capture=com.portofino.realtrainmodunofficial.client.mirror.MirrorRenderer.class.getDeclaredField("rendering");capture.setAccessible(true);
        var nativeView=com.portofino.realtrainmodunofficial.client.mirror.IrisMirrorView.class.getDeclaredField("vanillaView");nativeView.setAccessible(true);
        var active=com.portofino.realtrainmodunofficial.client.ShaderCompat.class.getDeclaredField("cachedActive");active.setAccessible(true);
        boolean oldCapture=capture.getBoolean(null),oldNative=nativeView.getBoolean(null),oldActive=active.getBoolean(null);
        int epoch=com.portofino.realtrainmodunofficial.client.ShaderCompat.epoch();
        try {
            capture.setBoolean(null,true);active.setBoolean(null,true);nativeView.setBoolean(null,true);
            com.portofino.realtrainmodunofficial.client.ShaderCompat.refresh();
            check(!com.portofino.realtrainmodunofficial.client.ShaderCompat.active(),"native sky view still used Iris entity programs");
            nativeView.setBoolean(null,false);
            check(com.portofino.realtrainmodunofficial.client.ShaderCompat.active(),"secondary sky view disabled the user's shader pack");
            check(com.portofino.realtrainmodunofficial.client.ShaderCompat.epoch()==epoch,"mirror capture triggered a global mesh rebuild");
        }finally{capture.setBoolean(null,oldCapture);nativeView.setBoolean(null,oldNative);active.setBoolean(null,oldActive);}
        for(int i=0;i<6;i++) {
            var eye=new Vec3(0,.85+.6*i,-5);var hit=MarkerPanel.pick(eye,new Vec3(0,0,1),Vec3.ZERO,0);
            check(hit!=null&&hit.button()==i&&Math.abs(hit.distance()-5)<1e-8,"marker panel picked wrong state");
            check(MarkerPanel.pick(eye,new Vec3(0,0,-1),Vec3.ZERO,0)==null,"marker panel picked behind camera");
        }
        check(MarkerPanel.pick(new Vec3(2,1,-5),new Vec3(0,0,1),Vec3.ZERO,0)==null,"marker panel hit outside width");
        var versions=VersionChecker.parseManifest("Example:2\nOther:alpha\ninvalid\nBad:too:many");
        var updates=VersionChecker.compare(List.of(new PackInfo("Example","https://example.invalid/","", "1"),new PackInfo("Other",null,"","alpha")),versions);
        check(updates.size()==1&&updates.getFirst().version().equals("2"),"original pack version comparison changed");
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        var requests=new AtomicInteger();server.createContext("/versions",exchange->{requests.incrementAndGet();byte[] body="ConfigRegressionPack:next".getBytes(java.nio.charset.StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,body.length);try(var out=exchange.getResponseBody()){out.write(body);}});server.start();
        boolean checkVersion=jp.ngt.rtm.RTMConfig.versionCheck;
        try {
            VersionChecker.addToCheckList(new PackInfo("ConfigRegressionPack",null,"http://127.0.0.1:"+server.getAddress().getPort()+"/versions","before"));
            jp.ngt.rtm.RTMConfig.versionCheck=false;VersionChecker.checkVersionAsync().get(1,TimeUnit.SECONDS);check(requests.get()==0,"disabled checker contacted update server");
            jp.ngt.rtm.RTMConfig.versionCheck=true;VersionChecker.checkVersionAsync().get(15,TimeUnit.SECONDS);
            check(requests.get()==1&&VersionChecker.getUpdates().stream().anyMatch(u->u.name().equals("ConfigRegressionPack")&&u.version().equals("next")),"pack update request/result was lost");
        }finally{jp.ngt.rtm.RTMConfig.versionCheck=checkVersion;server.stop(0);}
        System.out.println("Remaining config ordered loading / marker panel / real HTTP updates: "+checks+" passed");
    }
}
