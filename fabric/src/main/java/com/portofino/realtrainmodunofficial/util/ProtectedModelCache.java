package com.portofino.realtrainmodunofficial.util;

import java.util.LinkedHashMap;
import java.util.function.*;

/** Weighted LRU: recently used models remain protected even while over budget. */
public final class ProtectedModelCache<K,V> {
    private record Entry<V>(V value,long bytes,long touched) {}
    private final LinkedHashMap<K,Entry<V>> entries=new LinkedHashMap<>(16,.75F,true);
    private final ToLongFunction<V> weight;
    private final LongSupplier budget,protection,clock;
    private final Consumer<V> dispose;
    private long bytes;
    public ProtectedModelCache(ToLongFunction<V> weight,LongSupplier budget,LongSupplier protection,Consumer<V> dispose) {
        this(weight,budget,protection,dispose,System::nanoTime);
    }
    public ProtectedModelCache(ToLongFunction<V> weight,LongSupplier budget,LongSupplier protection,Consumer<V> dispose,LongSupplier clock) {
        this.weight=weight;this.budget=budget;this.protection=protection;this.dispose=dispose;this.clock=clock;
    }
    public synchronized V get(K key) {
        var entry=entries.get(key);
        if(entry==null){trim();return null;}
        entries.put(key,new Entry<>(entry.value,entry.bytes,clock.getAsLong()));trim();return entry.value;
    }
    public synchronized V put(K key,V value) {
        long size=Math.max(0,weight.applyAsLong(value));
        var previous=entries.put(key,new Entry<>(value,size,clock.getAsLong()));bytes+=size;
        if(previous!=null){bytes-=previous.bytes;if(previous.value!=value)dispose.accept(previous.value);}
        trim();return previous==null?null:previous.value;
    }
    public V computeIfAbsent(K key,Function<K,V> factory) {
        V found=get(key);if(found!=null)return found;
        V loaded=factory.apply(key);if(loaded==null)return null;
        synchronized(this) {
            found=get(key);
            if(found!=null){if(found!=loaded)dispose.accept(loaded);return found;}
            put(key,loaded);return loaded;
        }
    }
    public synchronized void trim() {
        long now=clock.getAsLong(),limit=Math.max(0,budget.getAsLong()),age=Math.max(0,protection.getAsLong());
        var iterator=entries.entrySet().iterator();
        while(bytes>limit && iterator.hasNext()) {
            var entry=iterator.next().getValue();
            if(now-entry.touched<age)continue;
            iterator.remove();bytes-=entry.bytes;dispose.accept(entry.value);
        }
    }
    public synchronized long retainedBytes(){return bytes;}
    public synchronized int size(){return entries.size();}
    public synchronized void clear(){entries.values().forEach(e->dispose.accept(e.value));entries.clear();bytes=0;}
}
