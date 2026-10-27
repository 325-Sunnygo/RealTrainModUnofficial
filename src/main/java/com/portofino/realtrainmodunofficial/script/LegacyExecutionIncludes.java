package com.portofino.realtrainmodunofficial.script;

import java.util.*;
import java.util.function.Function;
import java.util.regex.*;
import javax.script.ScriptEngine;

/** Direct eval preserves non-strict var/function include scope without parsing unused target branches. */
public final class LegacyExecutionIncludes {
    private static final Pattern INCLUDE=Pattern.compile("(?m)^[\\t ]*//include[\\t ]*<([^>\\r\\n]+)>");
    private static final Pattern LEXICAL=Pattern.compile("\\b(?:let|const|class)\\s+[A-Za-z_$]");
    public record Prepared(String source,String binding,Map<String,String> fragments,long expandedCharacters) {
        public void bind(ScriptEngine engine) {if(binding!=null)engine.put(binding,fragments);}
    }
    private LegacyExecutionIncludes() {}
    public static Prepared prepare(String source,String path,Function<String,byte[]> loader,String prelude) {
        Map<String,String> modules=new LinkedHashMap<>();Set<String> active=new LinkedHashSet<>();
        String root=path==null?"<entry>":path;collect(root,source,loader,modules,active);
        long expanded=length(root,modules,new HashMap<>());
        boolean[] unsafe={false};long unique=0;
        for(String text:modules.values()) {
            unique+=text.length();
            if(text.contains("\"use strict\"") || text.contains("'use strict'"))unsafe[0]=true;
            LegacySourceMask.transform(text,code->{if(LEXICAL.matcher(code).find())unsafe[0]=true;return code;});
        }
        boolean guardedTargets=modules.values().stream().anyMatch(text->text.contains("function RTMX_loadCompatTarget_") && text.contains("RTMX_COMPAT_TARGETS"));
        if(!guardedTargets || unsafe[0] || expanded<262144 || expanded<unique*2) {
            String literal=PackScriptSource.prepare(source,path,loader);
            return new Prepared(prelude+literal,null,Map.of(),expanded);
        }
        String binding="__rtmuIncludeSources";
        for(boolean conflict=true;conflict;) {conflict=false;for(String text:modules.values())if(text.contains(binding)) {binding+="_";conflict=true;break;}}
        Map<String,String> fragments=new LinkedHashMap<>();
        for(var module:modules.entrySet()) {
            Matcher matcher=INCLUDE.matcher(module.getValue());StringBuilder compact=new StringBuilder();
            while(matcher.find()) {
                String key=new com.google.gson.Gson().toJson(matcher.group(1).trim());
                matcher.appendReplacement(compact,Matcher.quoteReplacement("eval(String("+binding+".get("+key+")));"));
            }
            matcher.appendTail(compact);
            fragments.put(module.getKey(),PackScriptSource.remapExecutionSource(compact.toString()));
        }
        String main=fragments.remove(root);
        return new Prepared(prelude+main,binding,Map.copyOf(fragments),expanded);
    }
    private static void collect(String path,String source,Function<String,byte[]> loader,Map<String,String> modules,Set<String> active) {
        if(active.contains(path))throw new IllegalArgumentException("Cyclic script include: "+active+" -> "+path);
        if(modules.containsKey(path))return;
        active.add(path);
        String text=TypeScriptTranspiler.isTypeScript(path)?TypeScriptTranspiler.toJavaScript(source):source;
        modules.put(path,text);Matcher matcher=INCLUDE.matcher(text);
        while(matcher.find()) {
            String child=matcher.group(1).trim();
            if(active.contains(child))throw new IllegalArgumentException("Cyclic script include: "+active+" -> "+child);
            if(modules.containsKey(child))continue;
            byte[] bytes=loader.apply(child);if(bytes==null)throw new IllegalArgumentException("Script include not found: "+child);
            collect(child,PackScriptSource.decode(bytes),loader,modules,active);
        }
        active.remove(path);
    }
    private static long length(String path,Map<String,String> modules,Map<String,Long> cache) {
        if(cache.containsKey(path))return cache.get(path);String text=modules.get(path);long total=text.length();Matcher matcher=INCLUDE.matcher(text);
        while(matcher.find())total+=length(matcher.group(1).trim(),modules,cache)-matcher.group().length();
        cache.put(path,total);return total;
    }
}
