package com.portofino.realtrainmodunofficial.util;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import jp.ngt.rtm.RTMConfig;

/** Original ModelPackLoadThread concurrency: one / CPU thirds / all processors. */
public final class LegacyLoadPolicy {
    private LegacyLoadPolicy() {}
    public static int workers(int speed,int processors) {
        int cpus=Math.max(1,processors);
        return speed==1?1:speed==3?cpus:Math.max(1,cpus/3);
    }
    private static final AtomicInteger IDS=new AtomicInteger();
    private static final ThreadPoolExecutor POOL=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new LinkedBlockingQueue<>(),task->{
        Thread thread=new Thread(task,"RTMU-Pack-Loader-"+IDS.incrementAndGet());thread.setDaemon(true);return thread;
    });
    public static synchronized Executor executor() {
        int count=workers(RTMConfig.loadSpeed,Runtime.getRuntime().availableProcessors());
        if(count>POOL.getMaximumPoolSize()){POOL.setMaximumPoolSize(count);POOL.setCorePoolSize(count);}
        else {POOL.setCorePoolSize(count);POOL.setMaximumPoolSize(count);}
        return POOL;
    }
}
