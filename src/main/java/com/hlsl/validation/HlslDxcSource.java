package com.hlsl.validation;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** File-local compiler options. Comment pragmas are intentional plugin configuration. */
final class HlslDxcSource {
    private static final Pattern PRAGMA = Pattern.compile(
            "^[\\t ]*(?://[\\t ]*)?#[\\t ]*pragma[\\t ]+hlsl[\\t ]+(profile|entry|hv)[\\t ]+([^\\s/]+)[^\\r\\n]*",
            Pattern.MULTILINE);
    private static final Set<String> INCLUDE_EXTENSIONS = Set.of("hlsli", "fxh", "ush", "h", "inc");

    private final Map<String, String> pragmas;
    private final String compilerText;

    HlslDxcSource(String text) {
        pragmas = new LinkedHashMap<>();
        char[] compilerChars = text.toCharArray();
        String visibleText = maskBlockComments(text);
        Matcher matcher = PRAGMA.matcher(visibleText);
        while (matcher.find()) {
            pragmas.putIfAbsent(matcher.group(1), matcher.group(2));
            // DXC does not know the plugin's pragmas. Keep offsets and line numbers intact.
            for (int i = visibleText.indexOf('#', matcher.start()); i < matcher.end(); i++) {
                // Keep block comments, including one that begins here and ends on a later line.
                if (visibleText.charAt(i) != ' ' || text.charAt(i) == ' ') compilerChars[i] = ' ';
            }
        }
        compilerText = new String(compilerChars);
    }

    String option(String key, String fallback) {
        return pragmas.getOrDefault(key, fallback);
    }

    boolean hasOption(String key) {
        return pragmas.containsKey(key);
    }

    boolean shouldValidate(String filePath, boolean validateIncludes) {
        int dot = filePath.lastIndexOf('.');
        String extension = dot < 0 ? "" : filePath.substring(dot + 1).toLowerCase(Locale.ROOT);
        return !INCLUDE_EXTENSIONS.contains(extension) || validateIncludes
                || hasOption("profile") || hasOption("entry");
    }

    String compilerText() {
        return compilerText;
    }

    private static String maskBlockComments(String text) {
        char[] masked = text.toCharArray();
        boolean block = false;
        boolean line = false;
        char quote = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            char next = i + 1 < text.length() ? text.charAt(i + 1) : 0;
            if (c == '\n' || c == '\r') {
                line = false;
                continue;
            }
            if (block) {
                masked[i] = ' ';
                if (c == '*' && next == '/') {
                    masked[++i] = ' ';
                    block = false;
                }
            } else if (line) {
                continue;
            } else if (quote != 0) {
                if (c == '\\') i++;
                else if (c == quote) quote = 0;
            } else if (c == '/' && next == '*') {
                masked[i] = masked[++i] = ' ';
                block = true;
            } else if (c == '/' && next == '/') {
                line = true;
            } else if (c == '"' || c == '\'') {
                quote = c;
            }
        }
        return new String(masked);
    }
}
