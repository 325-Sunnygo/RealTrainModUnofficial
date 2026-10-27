package com.portofino.realtrainmodunofficial.script;

import java.util.LinkedHashMap;
import java.util.Map;

/** Class reference rewriting without changing ordinary string values or comments. */
public final class LegacyClassReferences {
    private LegacyClassReferences() {}

    public static String remap(String source, String[][] replacements) {
        Map<String, String> names = new LinkedHashMap<>();
        for (String[] pair : replacements) {
            names.put(pair[0], pair[1]);
            if (pair[0].startsWith("Packages.")) {
                names.put(pair[0].substring(9), pair[1].substring(9));
            }
        }
        StringBuilder out = new StringBuilder(source.length());
        for (int i = 0; i < source.length();) {
            char c = source.charAt(i);
            int start = i;
            if (c == '/' && i + 1 < source.length()
                    && (source.charAt(i + 1) == '/' || source.charAt(i + 1) == '*')) {
                if (source.charAt(i + 1) == '/') {
                    i = source.indexOf('\n', i + 2);
                    if (i < 0) i = source.length();
                } else {
                    int end = source.indexOf("*/", i + 2);
                    i = end < 0 ? source.length() : end + 2;
                }
                out.append(source, start, i);
            } else if (c == '\'' || c == '"' || c == '`') {
                i++;
                while (i < source.length()) {
                    char next = source.charAt(i++);
                    if (next == '\\' && i < source.length()) i++;
                    else if (next == c) break;
                }
                String literal = source.substring(start, i);
                if (c != '`' && literal.length() >= 2
                        && isJavaTypeArgument(source, start)) {
                    String name = literal.substring(1, literal.length() - 1);
                    String mapped = names.getOrDefault(name, name);
                    int close = i;
                    while (close < source.length() && Character.isWhitespace(source.charAt(close))) close++;
                    if (mapped.endsWith(".TYPE") && close < source.length() && source.charAt(close) == ')') {
                        out.append(c).append(mapped, 0, mapped.length() - 5).append(c).append(").TYPE");
                        i = close + 1;
                    } else {
                        out.append(c).append(mapped).append(c);
                    }
                } else {
                    out.append(literal);
                }
            } else if (Character.isJavaIdentifierStart(c)) {
                i++;
                while (i < source.length() && (Character.isJavaIdentifierPart(source.charAt(i))
                        || source.charAt(i) == '.')) i++;
                out.append(remapName(source.substring(start, i), names));
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static boolean isJavaTypeArgument(String source, int end) {
        int i = end - 1;
        String[] tokens = {"(", "type", ".", "Java"};
        for (String token : tokens) {
            while (i >= 0 && Character.isWhitespace(source.charAt(i))) i--;
            int start = i - token.length() + 1;
            if (start < 0 || !source.regionMatches(start, token, 0, token.length())) return false;
            i = start - 1;
        }
        return i < 0 || !Character.isJavaIdentifierPart(source.charAt(i));
    }

    private static String remapName(String token, Map<String, String> names) {
        // Exact lookup first, then enclosing classes. Never match prefixes of class names.
        String name = token;
        while (true) {
            String replacement = names.get(name);
            if (replacement != null) {
                if (token.equals(replacement) || token.startsWith(replacement + ".")) return token;
                return replacement + token.substring(name.length());
            }
            int dot = name.lastIndexOf('.');
            if (dot < 0) return token;
            name = name.substring(0, dot);
        }
    }
}
