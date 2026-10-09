package com.hlsl.highlighting;

import com.hlsl.psi.HlslTokenTypes;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.*;

/**
 * A shader's own functions used to look like plain text while built-ins were coloured: the token types were
 * mapped correctly, but the keys they use have no colours in the schemes the IDE ships.
 */
public class HlslColorSchemeTest {

    private static final List<String> SCHEMES = List.of("/colorSchemes/HLSLDefault.xml", "/colorSchemes/HLSLDarcula.xml");
    private static final List<String> KEYS =
            List.of("HLSL_FUNCTION_CALL", "HLSL_INSTANCE_METHOD_CALL", "HLSL_FIELD_ACCESS");

    @Test
    public void bundledSchemesColourFunctionsMethodsAndFields() throws IOException {
        for (String scheme : SCHEMES) {
            String xml = read(scheme);
            for (String key : KEYS) {
                assertTrue(scheme + " must colour " + key, xml.contains("name=\"" + key + "\""));
            }
            assertTrue(scheme + " must set a foreground colour", xml.contains("name=\"FOREGROUND\""));
        }
    }

    @Test
    public void descriptorRegistersTheBundledSchemes() throws IOException {
        String descriptor = read("/META-INF/plugin.xml");
        assertTrue(descriptor, descriptor.contains("additionalTextAttributes scheme=\"Default\""));
        assertTrue(descriptor, descriptor.contains("additionalTextAttributes scheme=\"Darcula\""));
        assertTrue(descriptor, descriptor.contains("colorSchemes/HLSLDefault.xml"));
        assertTrue(descriptor, descriptor.contains("colorSchemes/HLSLDarcula.xml"));
    }

    @Test
    public void functionCallsAndBuiltinsUseTheirOwnKeys() {
        HlslSyntaxHighlighter highlighter = new HlslSyntaxHighlighter();
        assertEquals("HLSL_FUNCTION_CALL",
                onlyKey(highlighter.getTokenHighlights(HlslTokenTypes.FUNCTION_CALL)));
        assertEquals("HLSL_BUILTIN_FUNCTION",
                onlyKey(highlighter.getTokenHighlights(HlslTokenTypes.BUILTIN_FUNCTION)));
        assertEquals("HLSL_INSTANCE_METHOD_CALL",
                onlyKey(highlighter.getTokenHighlights(HlslTokenTypes.INSTANCE_METHOD_CALL)));
        assertEquals("HLSL_FIELD_ACCESS",
                onlyKey(highlighter.getTokenHighlights(HlslTokenTypes.FIELD_ACCESS)));
    }

    private static String onlyKey(TextAttributesKey[] keys) {
        assertEquals("exactly one key per token", 1, keys.length);
        return keys[0].getExternalName();
    }

    private static String read(String path) throws IOException {
        try (InputStream in = HlslColorSchemeTest.class.getResourceAsStream(path)) {
            assertNotNull("missing resource: " + path, in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
