package com.portofino.realtrainmodunofficial.script;

import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Expands legacy include directives at their original position, as KaizPatchX does. */
public final class LegacyScriptIncludes {
    private static final Pattern INCLUDE = Pattern.compile("(?m)^[\\t ]*//include[\\t ]*<([^>\\r\\n]+)>");

    private LegacyScriptIncludes() {}

    public static String expand(String source, Set<String> active, Function<String, String> loader) {
        Matcher matcher = INCLUDE.matcher(source);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String path = matcher.group(1).trim();
            if (!active.add(path)) {
                throw new IllegalArgumentException("Cyclic script include: " + active + " -> " + path);
            }
            String expanded;
            try {
                String included = loader.apply(path);
                if (included == null) {
                    throw new IllegalArgumentException("Script include not found: " + path);
                }
                expanded = expand(included, active, loader);
            } finally {
                active.remove(path);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(expanded));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
