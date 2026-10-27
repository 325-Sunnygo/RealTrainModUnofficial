package com.portofino.realtrainmodunofficial.script;

import jp.ngt.ngtlib.io.ScriptUtil;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Only parsing/bytecode compilation runs here. Evaluation and game/GL calls stay on the caller thread. */
public final class AsyncScriptCompiler {
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor(task -> {
        Thread thread=new Thread(task,"RTMU Script Compiler");thread.setDaemon(true);return thread;
    });
    private static final ConcurrentMap<String,CompletableFuture<CompiledScript>> PENDING=new ConcurrentHashMap<>();
    private AsyncScriptCompiler() {}
    public static CompletableFuture<CompiledScript> request(String key,String filename,Supplier<String> source) {
        return PENDING.computeIfAbsent(key,ignored -> CompletableFuture.supplyAsync(() -> {
            try {
                String text=source.get();
                if(text==null || text.isBlank())throw new IllegalArgumentException("Script is empty: "+filename);
                ScriptEngine engine=ScriptUtil.createEngine();engine.put(ScriptEngine.FILENAME,filename);
                return ((Compilable)engine).compile(text);
            } catch(Exception failure) {throw new CompletionException("Failed to compile "+filename,failure);}
        },WORKER));
    }
    public static CompletableFuture<CompiledScript> requestPrepared(String key,String filename,Supplier<LegacyExecutionIncludes.Prepared> source) {
        return PENDING.computeIfAbsent(key,ignored -> CompletableFuture.supplyAsync(() -> {
            try {
                long started=System.nanoTime();
                var prepared=source.get();ScriptEngine engine=ScriptUtil.createEngine();engine.put(ScriptEngine.FILENAME,filename);prepared.bind(engine);
                var result=((Compilable)engine).compile(prepared.source());
                com.mojang.logging.LogUtils.getLogger().info("[RTMU] script compiled: {} in {} ms (entry chars={}, shared modules={}, expanded chars={})",filename,(System.nanoTime()-started)/1_000_000,prepared.source().length(),prepared.fragments().size(),prepared.expandedCharacters());
                return result;
            } catch(Exception failure) {throw new CompletionException("Failed to compile "+filename,failure);}
        },WORKER));
    }
    public static boolean isPending(String key) {var future=PENDING.get(key);return future!=null && !future.isDone();}
    public static void release(String key) {PENDING.remove(key);}
}
