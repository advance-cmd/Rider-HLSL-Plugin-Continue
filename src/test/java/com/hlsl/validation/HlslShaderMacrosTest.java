package com.hlsl.validation;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class HlslShaderMacrosTest {

    /** Shaped after a real C++ build file, including a runtime-expression value and a terminator. */
    private static final String MAIN_CPP = """
            const std::string extraLights = std::to_string(mManyLights ? kExtraLights : 0);
            const D3D_SHADER_MACRO lightDefines[] = {
                {"SUN_COUNT", "1"},
                {"LAMP_COUNT", "1"},
                {"SPOT_COUNT", "1"},
                {"EXTRA_COUNT", extraLights.c_str()},
                {"FOG", "1"},
                {nullptr, nullptr}
            };
            const D3D_SHADER_MACRO alphaTestDefines[] = {
                {"SUN_COUNT", "1"},
                {"USE_CUTOUT", "1"},
                {nullptr, nullptr}
            };
            // An unrelated table with the same brace shape must not be read as shader macros.
            const std::map<std::string, std::string> uiLabels = {{"Apple", "1"}, {"Bream", "2"}};
            """;

    @Test
    public void readsLiteralMacrosFromShaderMacroTablesOnly() {
        Map<String, Set<String>> values = new LinkedHashMap<>();
        HlslShaderMacros.collect(MAIN_CPP, values);
        assertEquals(Set.of("1"), values.get("SUN_COUNT"));
        assertEquals(Set.of("1"), values.get("SPOT_COUNT"));
        assertEquals(Set.of("1"), values.get("USE_CUTOUT"));
        assertNull("an unrelated initializer list is not a macro table", values.get("Apple"));
        assertNull("a runtime value cannot become a -D argument", values.get("EXTRA_COUNT"));
        assertNull("the nullptr terminator is not a macro", values.get("nullptr"));
    }

    @Test
    public void conflictingValuesAreNotResolvedByGuessing() {
        Map<String, Set<String>> values = new LinkedHashMap<>();
        HlslShaderMacros.collect("""
                const D3D_SHADER_MACRO a[] = {{"EXTRA_COUNT", "0"}, {nullptr, nullptr}};
                const D3D_SHADER_MACRO b[] = {{"EXTRA_COUNT", "64"}, {nullptr, nullptr}};
                const D3D_SHADER_MACRO c[] = {{"STABLE", "1"}, {nullptr, nullptr}};
                """, values);
        Map<String, String> resolved = HlslShaderMacros.unambiguous(values);
        assertFalse("a macro that differs per pass is not usable", resolved.containsKey("EXTRA_COUNT"));
        assertEquals("1", resolved.get("STABLE"));
    }

    @Test
    public void onlyMacrosTheShaderMentionsBecomeArguments() {
        Map<String, String> macros = new LinkedHashMap<>();
        macros.put("SUN_COUNT", "1");
        macros.put("USE_CUTOUT", "1");
        List<String> definitions = HlslShaderMacros.definitionsFor(macros,
                "static const uint kFirstExtra = SUN_COUNT + LAMP_COUNT;\n");
        assertEquals(List.of("SUN_COUNT=1"), definitions);
    }

    @Test
    public void macroNamesAreMatchedAsWholeWords() {
        Map<String, String> macros = Map.of("FOG", "1");
        assertTrue(HlslShaderMacros.definitionsFor(macros, "#ifdef FOG\n").contains("FOG=1"));
        assertTrue("FOGGY must not count as a use of FOG",
                HlslShaderMacros.definitionsFor(macros, "float FOGGY = 1;").isEmpty());
    }

    @Test
    public void definitionsExposeTheirName() {
        assertEquals("SUN_COUNT", HlslShaderMacros.nameOf("SUN_COUNT=1"));
        assertEquals("BARE", HlslShaderMacros.nameOf("BARE"));
    }
}
