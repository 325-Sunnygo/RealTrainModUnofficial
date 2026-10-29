package com.portofino.realtrainmodunofficial.util;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Parse concurrently, then publish on the caller in original pack/entry order. */
public final class OrderedPackTasks implements AutoCloseable {
    private static final ThreadLocal<OrderedPackTasks> BATCH=new ThreadLocal<>();
    private static final ThreadLocal<List<Runnable>> CAPTURE=new ThreadLocal<>();
    private final ArrayDeque<CompletableFuture<List<Runnable>>> pending=new ArrayDeque<>();
    private boolean closed;
    private OrderedPackTasks(){if(BATCH.get()!=null)throw new IllegalStateException("Nested pack batch");BATCH.set(this);}
    public static OrderedPackTasks begin(){return new OrderedPackTasks();}
    public static void submit(Runnable parser) {
        var batch=BATCH.get();
        if(batch==null){parser.run();return;}
        if(batch.pending.size()>=256)batch.publishNext();
        batch.pending.add(CompletableFuture.supplyAsync(()->{
            var result=new ArrayList<Runnable>();CAPTURE.set(result);
            try{parser.run();return result;}finally{CAPTURE.remove();}
        },LegacyLoadPolicy.executor()));
    }
    /** Directory-pack IO is part of the configured worker workload as well. */
    public static void read(java.nio.file.Path path,Consumer<byte[]> parser) {
        submit(()->{
            try{parser.accept(java.nio.file.Files.readAllBytes(path));}
            catch(java.io.IOException failure){com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.warn("Could not read model configuration {}",path.getFileName(),failure);}
        });
    }
    public static <T> void publish(T value,Consumer<T> target) {publish(()->target.accept(value));}
    public static void publish(Runnable registration) {
        var capture=CAPTURE.get();
        if(capture!=null){capture.add(registration);return;}
        var batch=BATCH.get();
        if(batch==null)registration.run();
        else batch.pending.add(CompletableFuture.completedFuture(List.of(registration)));
    }
    private void publishNext(){for(var commit:pending.removeFirst().join())commit.run();}
    @Override public void close() {
        if(closed)return;closed=true;
        try{while(!pending.isEmpty())publishNext();}
        finally{BATCH.remove();}
    }
}
