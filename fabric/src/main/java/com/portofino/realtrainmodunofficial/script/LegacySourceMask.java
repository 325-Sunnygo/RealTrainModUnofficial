package com.portofino.realtrainmodunofficial.script;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/** Keeps literal values and comments opaque while legacy member names are rewritten. */
public final class LegacySourceMask {
    private LegacySourceMask() {}

    public static String transform(String source, UnaryOperator<String> transform) {
        String prefix = "__RTMU_LITERAL_";
        while (source.contains(prefix)) prefix += "_";
        List<String> originals = new ArrayList<>();
        List<String> markers = new ArrayList<>();
        StringBuilder masked = new StringBuilder(source.length());
        boolean expressionStart = true;
        for (int i = 0; i < source.length();) {
            int start = i;
            char c = source.charAt(i);
            boolean comment = c == '/' && i + 1 < source.length()
                    && (source.charAt(i + 1) == '/' || source.charAt(i + 1) == '*');
            if (comment) {
                if (source.charAt(i + 1) == '/') {
                    int end = source.indexOf('\n', i + 2);
                    i = end < 0 ? source.length() : end;
                } else {
                    int end = source.indexOf("*/", i + 2);
                    i = end < 0 ? source.length() : end + 2;
                }
            } else if (c == '\'' || c == '"' || c == '`') {
                i++;
                while (i < source.length()) {
                    char next = source.charAt(i++);
                    if (next == '\\' && i < source.length()) i++;
                    else if (next == c) break;
                }
                expressionStart = false;
            } else if (c == '/' && expressionStart) {
                i++;
                boolean bracket = false;
                while (i < source.length()) {
                    char next = source.charAt(i++);
                    if (next == '\\' && i < source.length()) i++;
                    else if (next == '[') bracket = true;
                    else if (next == ']') bracket = false;
                    else if (next == '/' && !bracket) break;
                    else if (next == '\n' || next == '\r') break;
                }
                while (i < source.length() && Character.isJavaIdentifierPart(source.charAt(i))) i++;
                expressionStart = false;
            } else {
                if (Character.isJavaIdentifierStart(c)) {
                    i++;
                    while (i < source.length() && Character.isJavaIdentifierPart(source.charAt(i))) i++;
                    String token = source.substring(start, i);
                    expressionStart = switch (token) {
                        case "return", "throw", "case", "delete", "void", "typeof", "new", "in", "instanceof", "yield" -> true;
                        default -> false;
                    };
                    masked.append(token);
                } else {
                    masked.append(c);
                    i++;
                    if (!Character.isWhitespace(c)) expressionStart = "(=:[,!&|?{};+-*%~<>".indexOf(c) >= 0;
                }
                continue;
            }
            String original = source.substring(start, i);
            String marker = prefix + originals.size() + "__";
            // Keep the number of lines for diagnostics in the prepared source.
            String lines = "\n".repeat((int) original.chars().filter(ch -> ch == '\n').count());
            marker = comment ? "/*" + marker + lines + "*/" : "'" + marker + "'" + (lines.isEmpty() ? "" : "/*" + lines + "*/");
            originals.add(original);
            markers.add(marker);
            masked.append(marker);
        }
        String result = transform.apply(masked.toString());
        // Restore in one scan: repeatedly replacing every literal scanned the
        // entire expanded script once per string (millions of copies in SRBX).
        StringBuilder restored = new StringBuilder(source.length());
        int cursor = 0;
        while (cursor < result.length()) {
            int at = result.indexOf(prefix, cursor);
            if (at < 0) { restored.append(result, cursor, result.length()); break; }
            int end = result.indexOf("__", at + prefix.length());
            int index = Integer.parseInt(result.substring(at + prefix.length(), end));
            String marker = markers.get(index);
            int begin = at - (marker.startsWith("/*") ? 2 : 1);
            restored.append(result, cursor, begin).append(originals.get(index));
            cursor = begin + marker.length();
        }
        return restored.toString();
    }
}
