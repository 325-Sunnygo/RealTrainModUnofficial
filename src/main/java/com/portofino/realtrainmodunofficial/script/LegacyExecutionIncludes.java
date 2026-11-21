package com.portofino.realtrainmodunofficial.script;

import java.util.*;
import java.util.function.Function;
import java.util.regex.*;
import javax.script.ScriptEngine;

/** Direct eval preserves non-strict var/function inc… */
public final class LegacyExecutionIncludes {
    private static final Pattern INCLUDE=Pattern.compile("(?m)^[\\t ]*//include[\\t ]*<([^>\\r\\n]+)>");
    private static final Pattern LEXICAL=Pattern.compile("\\b(?:let|const|class)\\s+[A-Za-z_$]");
    public record Prepared(String source,String binding,Map<String,String> fragments,long expandedCharacters) {
        public void bind(ScriptEngine engine) {if(binding!=null)engine.put(binding,fragments);}
    }
    private LegacyExecutionIncludes() {}
    public static Prepared prepare(String source,String path,Function<String,byte[]> loader,String prelude) {
        // KaizPatchX と同じ: //include を展開した本文をそのまま評価する。
        String literal=PackScriptSource.prepare(source,path,loader);
        return new Prepared(prelude+literal,null,Map.of(),literal.length());
    }
}
