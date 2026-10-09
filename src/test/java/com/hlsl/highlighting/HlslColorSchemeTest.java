package com.hlsl.highlighting;

import com.hlsl.psi.HlslTokenTypes;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import org.junit.Test;

import java.awt.Color;
import java.awt.Font;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * A shader's own functions, structs and variables used to look like plain text: the token types were mapped
 * correctly, but the scheme keys they inherited have no colour of their own. These keys now carry colours, so
 * the palette has to stay complete and readable.
 */
public class HlslColorSchemeTest {

    @Test
    public void everyStyledTokenHasAColour() {
        for (TextAttributesKey key : styledKeys()) {
            assertNotNull(key.getExternalName() + " has no default attributes", key.getDefaultAttributes());
            assertNotNull(key.getExternalName() + " has no foreground colour",
                    key.getDefaultAttributes().getForegroundColor());
        }
    }

    @Test
    public void typesFunctionsFieldsAndVariablesAreDistinguishable() {
        Set<Color> hues = new LinkedHashSet<>();
        hues.add(colourOf(HlslSyntaxHighlighter.TYPE_KEYWORD));
        hues.add(colourOf(HlslSyntaxHighlighter.FUNCTION_CALL));
        hues.add(colourOf(HlslSyntaxHighlighter.FIELD_ACCESS));
        hues.add(colourOf(HlslSyntaxHighlighter.IDENTIFIER));
        assertEquals("types, functions, fields and variables must not share one colour: " + hues, 4, hues.size());

        assertEquals("a struct name is a type",
                colourOf(HlslSyntaxHighlighter.TYPE_KEYWORD), colourOf(HlslSyntaxHighlighter.STRUCT_NAME));
        assertEquals("built-ins keep the function hue",
                colourOf(HlslSyntaxHighlighter.FUNCTION_CALL), colourOf(HlslSyntaxHighlighter.BUILTIN_FUNCTION));
        assertEquals("and are set in italics instead",
                Font.ITALIC, HlslSyntaxHighlighter.BUILTIN_FUNCTION.getDefaultAttributes().getFontType());
        assertEquals("method calls read as functions",
                colourOf(HlslSyntaxHighlighter.FUNCTION_CALL), colourOf(HlslSyntaxHighlighter.INSTANCE_METHOD_CALL));
    }

    @Test
    public void tokenTypesMapToTheirOwnKeys() {
        HlslSyntaxHighlighter highlighter = new HlslSyntaxHighlighter();
        assertEquals("HLSL_TYPE", onlyKey(highlighter.getTokenHighlights(HlslTokenTypes.TYPE_KEYWORD)));
        assertEquals("HLSL_STRUCT_NAME", onlyKey(highlighter.getTokenHighlights(HlslTokenTypes.STRUCT_NAME)));
        assertEquals("HLSL_FUNCTION_CALL", onlyKey(highlighter.getTokenHighlights(HlslTokenTypes.FUNCTION_CALL)));
        assertEquals("HLSL_BUILTIN_FUNCTION", onlyKey(highlighter.getTokenHighlights(HlslTokenTypes.BUILTIN_FUNCTION)));
        assertEquals("HLSL_INSTANCE_METHOD_CALL",
                onlyKey(highlighter.getTokenHighlights(HlslTokenTypes.INSTANCE_METHOD_CALL)));
        assertEquals("HLSL_FIELD_ACCESS", onlyKey(highlighter.getTokenHighlights(HlslTokenTypes.FIELD_ACCESS)));
        assertEquals("HLSL_IDENTIFIER", onlyKey(highlighter.getTokenHighlights(HlslTokenTypes.IDENTIFIER)));
        assertEquals("HLSL_SEMANTIC", onlyKey(highlighter.getTokenHighlights(HlslTokenTypes.SEMANTIC)));
        assertEquals("HLSL_KEYWORD", onlyKey(highlighter.getTokenHighlights(HlslTokenTypes.KEYWORD)));
    }

    private static Set<TextAttributesKey> styledKeys() {
        return new LinkedHashSet<>(List.of(
                HlslSyntaxHighlighter.TYPE_KEYWORD,
                HlslSyntaxHighlighter.STRUCT_NAME,
                HlslSyntaxHighlighter.FUNCTION_CALL,
                HlslSyntaxHighlighter.INSTANCE_METHOD_CALL,
                HlslSyntaxHighlighter.BUILTIN_FUNCTION,
                HlslSyntaxHighlighter.FIELD_ACCESS,
                HlslSyntaxHighlighter.IDENTIFIER,
                HlslSyntaxHighlighter.SEMANTIC));
    }

    private static Color colourOf(TextAttributesKey key) {
        return key.getDefaultAttributes().getForegroundColor();
    }

    private static String onlyKey(TextAttributesKey[] keys) {
        assertEquals("exactly one key per token", 1, keys.length);
        return keys[0].getExternalName();
    }
}
