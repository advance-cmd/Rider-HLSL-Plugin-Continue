package com.hlsl.validation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the macros a build system hands to the shader compiler, e.g.
 * {@code const D3D_SHADER_MACRO lightDefines[] = { {"NUM_DIR_LIGHTS", "1"}, {nullptr, nullptr} };}.
 * DXC compiles the shader standalone and cannot see them, so a shader that relies on build-provided
 * macros would otherwise report phantom "undeclared identifier" errors.
 */
final class HlslShaderMacros {

    /** One macro table. Matching the table first keeps unrelated {@code {"a", "b"}} pairs out. */
    private static final Pattern TABLE = Pattern.compile(
            "D3D_SHADER_MACRO\\s+[A-Za-z_][A-Za-z0-9_]*\\s*(?:\\[[^\\]]*\\])?\\s*=\\s*\\{(.*?)\\}\\s*;",
            Pattern.DOTALL);

    /** A literal table entry. An entry whose value is a runtime expression cannot become a -D value. */
    private static final Pattern ENTRY = Pattern.compile(
            "\\{\\s*\"([A-Za-z_][A-Za-z0-9_]*)\"\\s*,\\s*\"([^\"\\r\\n]*)\"\\s*\\}");

    private HlslShaderMacros() { }

    /** Collects every literal entry of every macro table found in {@code text}. */
    static void collect(CharSequence text, Map<String, Set<String>> into) {
        Matcher table = TABLE.matcher(text);
        while (table.find()) {
            Matcher entry = ENTRY.matcher(table.group(1));
            while (entry.find()) {
                into.computeIfAbsent(entry.group(1), key -> new LinkedHashSet<>()).add(entry.group(2));
            }
        }
    }

    /**
     * Macros that resolve to one literal value. A project may pass a different value per draw call
     * (a runtime expression, or 0 for one pass and 64 for another), and guessing one of them would
     * validate the shader against a configuration the build never uses.
     */
    static Map<String, String> unambiguous(Map<String, Set<String>> values) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> macro : values.entrySet()) {
            if (macro.getValue().size() == 1) result.put(macro.getKey(), macro.getValue().iterator().next());
        }
        return result;
    }

    /** Keeps only the macros the given shader source mentions, as "NAME=VALUE" arguments. */
    static List<String> definitionsFor(Map<String, String> macros, CharSequence code) {
        List<String> definitions = new ArrayList<>();
        for (Map.Entry<String, String> macro : macros.entrySet()) {
            Pattern mention = Pattern.compile("\\b" + Pattern.quote(macro.getKey()) + "\\b");
            if (mention.matcher(code).find()) definitions.add(macro.getKey() + "=" + macro.getValue());
        }
        return definitions;
    }

    /** The name part of a "NAME=VALUE" definition. */
    static String nameOf(String definition) {
        int equals = definition.indexOf('=');
        return equals < 0 ? definition : definition.substring(0, equals);
    }
}
