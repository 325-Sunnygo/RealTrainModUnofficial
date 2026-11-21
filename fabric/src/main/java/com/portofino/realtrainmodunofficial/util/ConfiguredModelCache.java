package com.portofino.realtrainmodunofficial.util;

import java.util.function.*;

/** One configured budget shared by native meshes and script model graphs. */
public final class ConfiguredModelCache<V> {
    private record Key(Object owner,String name) {}
    private record Value(Object model,long bytes,Runnable release) {}
    private static final ProtectedModelCache<Key,Value> SHARED=new ProtectedModelCache<>(Value::bytes,
        () -> (long)jp.ngt.rtm.RTMConfig.fixRTMCachedModelMemoryLimitMiB*1024*1024,
        () -> java.util.concurrent.TimeUnit.SECONDS.toNanos(jp.ngt.rtm.RTMConfig.fixRTMCachedModelProtectSeconds),v -> v.release.run());
    private final Object owner=new Object();
    private final ToLongFunction<V> weight;
    private final Consumer<V> release;
    public ConfiguredModelCache(ToLongFunction<V> weight,Consumer<V> release){this.weight=weight;this.release=release;}
    @SuppressWarnings("unchecked") public V get(String name){var value=SHARED.get(new Key(owner,name));return value==null?null:(V)value.model;}
    public void put(String name,V model){SHARED.put(new Key(owner,name),new Value(model,weight.applyAsLong(model),()->release.accept(model)));}
    @SuppressWarnings("unchecked") public V computeIfAbsent(String name,Function<String,V> factory){
        var value=SHARED.computeIfAbsent(new Key(owner,name),key->{V model=factory.apply(name);return model==null?null:new Value(model,weight.applyAsLong(model),()->release.accept(model));});
        return value==null?null:(V)value.model;
    }
    /** Resource reload invalidates both representations of each source model together. */
    public void clear(){SHARED.clear();}
}
