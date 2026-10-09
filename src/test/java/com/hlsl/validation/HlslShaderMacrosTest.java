package com.hlsl.validation;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class HlslShaderMacrosTest {

    /** Shaped after the real LearnDX12 main.cpp, including a runtime-expression value and a terminator. */
    private static final String MAIN_CPP = """
            const std::string extraLights = std::to_string(mManyLights ? kExtraLights : 0);
            const D3D_SHADER_MACRO lightDefines[] = {
                {"NUM_DIR_LIGHTS", "1"},
                {"NUM_POINT_LIGHTS", "1"},
                {"NUM_SPOT_LIGHTS", "1"},
                {"NUM_EXTRA_LIGHTS", extraLights.c_str()},
                {"FOG", "1"},
                {nullptr, nullptr}
            };
            const D3D_SHADER_MACRO alphaTestDefines[] = {
                {"NUM_DIR_LIGHTS", "1"},
                {"ALPHA_TEST", "1"},
                {nullptr, nullptr}
            };
            // An unrelated table with the same brace shape must not be read as shader macros.
            const std::map<std::string, std::string> uiLabels = {{"Apple", "1"}, {"Bream", "2"}};
            """;

    @Test
    public void readsLiteralMacrosFromShaderMacroTablesOnly() {
        Map<String, Set<String>> values = new LinkedHashMap<>();
        HlslShaderMacros.collect(MAIN_CPP, values);
        assertEquals(Set.of("1"), values.get("NUM_DIR_LIGHTS"));
        assertEquals(Set.of("1"), values.get("NUM_SPOT_LIGHTS"));
        assertEquals(Set.of("1"), values.get("ALPHA_TEST"));
        assertNull("an unrelated initializer list is not a macro table", values.get("Apple"));
        assertNull("a runtime value cannot become a -D argument", values.get("NUM_EXTRA_LIGHTS"));
        assertNull("the nullptr terminator is not a macro", values.get("nullptr"));
    }

    @Test
    public void conflictingValuesAreNotResolvedByGuessing() {
        Map<String, Set<String>> values = new LinkedHashMap<>();
        HlslShaderMacros.collect("""
                const D3D_SHADER_MACRO a[] = {{"NUM_EXTRA_LIGHTS", "0"}, {nullptr, nullptr}};
                const D3D_SHADER_MACRO b[] = {{"NUM_EXTRA_LIGHTS", "64"}, {nullptr, nullptr}};
                const D3D_SHADER_MACRO c[] = {{"STABLE", "1"}, {nullptr, nullptr}};
                """, values);
        Map<String, String> resolved = HlslShaderMacros.unambiguous(values);
        assertFalse("a macro that differs per pass is not usable", resolved.containsKey("NUM_EXTRA_LIGHTS"));
        assertEquals("1", resolved.get("STABLE"));
    }

    @Test
    public void onlyMacrosTheShaderMentionsBecomeArguments() {
        Map<String, String> macros = new LinkedHashMap<>();
        macros.put("NUM_DIR_LIGHTS", "1");
        macros.put("ALPHA_TEST", "1");
        List<String> definitions = HlslShaderMacros.definitionsFor(macros,
                "static const uint kFirstExtraLight = NUM_DIR_LIGHTS + NUM_POINT_LIGHTS;\n");
        assertEquals(List.of("NUM_DIR_LIGHTS=1"), definitions);
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
        assertEquals("NUM_DIR_LIGHTS", HlslShaderMacros.nameOf("NUM_DIR_LIGHTS=1"));
        assertEquals("BARE", HlslShaderMacros.nameOf("BARE"));
    }
}
