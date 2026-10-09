package com.hlsl.highlighting;

import com.hlsl.psi.HlslTokenTypes;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.ui.JBColor;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import java.awt.Color;
import java.awt.Font;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.hlsl.lexer.HlslLexer;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

public class HlslSyntaxHighlighter extends SyntaxHighlighterBase {

    // The keys below carry their own colours instead of inheriting a scheme key: the platform defines no
    // colour for function calls at all, and the ones for class names and local variables are the plain text
    // colour in Darcula, which left a shader's own functions, structs and variables looking unhighlighted.
    // JBColor picks the light or the dark value for the active theme, so this also works in custom themes,
    // and every colour stays adjustable under Settings → Editor → Color Scheme → HLSL.
    private static final int LIGHT_TYPE = 0x0E7C6B;
    private static final int LIGHT_FUNCTION = 0x00627A;
    private static final int LIGHT_FIELD = 0x871094;
    private static final int LIGHT_VARIABLE = 0x1B5E9C;
    private static final int LIGHT_SEMANTIC = 0x9E880D;
    private static final int DARK_TYPE = 0x4EC9B0;
    private static final int DARK_FUNCTION = 0xFFC66D;
    private static final int DARK_FIELD = 0xC77DBB;
    private static final int DARK_VARIABLE = 0x9CDCFE;
    private static final int DARK_SEMANTIC = 0xB5CEA8;

    private static TextAttributesKey coloured(String name, int light, int dark) {
        return createTextAttributesKey(name, attributes(light, dark, Font.PLAIN));
    }

    /** Intrinsics are set in italics, which reads as "built in" without needing a second hue. */
    private static TextAttributesKey colouredItalic(String name, int light, int dark) {
        return createTextAttributesKey(name, attributes(light, dark, Font.ITALIC));
    }

    private static TextAttributes attributes(int light, int dark, int fontType) {
        return new TextAttributes(new JBColor(light, dark), null, null, null, fontType);
    }

    public static final TextAttributesKey KEYWORD =
            createTextAttributesKey("HLSL_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD);
    public static final TextAttributesKey TYPE_KEYWORD = coloured("HLSL_TYPE", LIGHT_TYPE, DARK_TYPE);
    public static final TextAttributesKey BUILTIN_FUNCTION =
            colouredItalic("HLSL_BUILTIN_FUNCTION", LIGHT_FUNCTION, DARK_FUNCTION);
    public static final TextAttributesKey FUNCTION_CALL =
            coloured("HLSL_FUNCTION_CALL", LIGHT_FUNCTION, DARK_FUNCTION);
    public static final TextAttributesKey INSTANCE_METHOD_CALL =
            coloured("HLSL_INSTANCE_METHOD_CALL", LIGHT_FUNCTION, DARK_FUNCTION);
    public static final TextAttributesKey FIELD_ACCESS = coloured("HLSL_FIELD_ACCESS", LIGHT_FIELD, DARK_FIELD);
    public static final TextAttributesKey SEMANTIC = coloured("HLSL_SEMANTIC", LIGHT_SEMANTIC, DARK_SEMANTIC);
    public static final TextAttributesKey PREPROCESSOR =
            createTextAttributesKey("HLSL_PREPROCESSOR", DefaultLanguageHighlighterColors.METADATA);
    public static final TextAttributesKey NUMBER =
            createTextAttributesKey("HLSL_NUMBER", DefaultLanguageHighlighterColors.NUMBER);
    public static final TextAttributesKey STRING =
            createTextAttributesKey("HLSL_STRING", DefaultLanguageHighlighterColors.STRING);
    public static final TextAttributesKey LINE_COMMENT =
            createTextAttributesKey("HLSL_LINE_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);
    public static final TextAttributesKey BLOCK_COMMENT =
            createTextAttributesKey("HLSL_BLOCK_COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT);
    public static final TextAttributesKey OPERATOR =
            createTextAttributesKey("HLSL_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN);
    public static final TextAttributesKey SEMICOLON =
            createTextAttributesKey("HLSL_SEMICOLON", DefaultLanguageHighlighterColors.SEMICOLON);
    public static final TextAttributesKey COMMA =
            createTextAttributesKey("HLSL_COMMA", DefaultLanguageHighlighterColors.COMMA);
    public static final TextAttributesKey DOT =
            createTextAttributesKey("HLSL_DOT", DefaultLanguageHighlighterColors.DOT);
    public static final TextAttributesKey PAREN =
            createTextAttributesKey("HLSL_PAREN", DefaultLanguageHighlighterColors.PARENTHESES);
    public static final TextAttributesKey BRACE =
            createTextAttributesKey("HLSL_BRACE", DefaultLanguageHighlighterColors.BRACES);
    public static final TextAttributesKey BRACKET =
            createTextAttributesKey("HLSL_BRACKET", DefaultLanguageHighlighterColors.BRACKETS);
    public static final TextAttributesKey STRUCT_NAME = coloured("HLSL_STRUCT_NAME", LIGHT_TYPE, DARK_TYPE);
    public static final TextAttributesKey IDENTIFIER = coloured("HLSL_IDENTIFIER", LIGHT_VARIABLE, DARK_VARIABLE);
    public static final TextAttributesKey BAD_CHARACTER =
            createTextAttributesKey("HLSL_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER);

    private static final TextAttributesKey[] KEYWORD_KEYS = {KEYWORD};
    private static final TextAttributesKey[] TYPE_KEYS = {TYPE_KEYWORD};
    private static final TextAttributesKey[] BUILTIN_KEYS = {BUILTIN_FUNCTION};
        private static final TextAttributesKey[] FUNCTION_CALL_KEYS = {FUNCTION_CALL};
    private static final TextAttributesKey[] INSTANCE_METHOD_CALL_KEYS = {INSTANCE_METHOD_CALL};
    private static final TextAttributesKey[] FIELD_ACCESS_KEYS = {FIELD_ACCESS};
    private static final TextAttributesKey[] SEMANTIC_KEYS = {SEMANTIC};
    private static final TextAttributesKey[] PREPROCESSOR_KEYS = {PREPROCESSOR};
    private static final TextAttributesKey[] NUMBER_KEYS = {NUMBER};
    private static final TextAttributesKey[] STRING_KEYS = {STRING};
    private static final TextAttributesKey[] LINE_COMMENT_KEYS = {LINE_COMMENT};
    private static final TextAttributesKey[] BLOCK_COMMENT_KEYS = {BLOCK_COMMENT};
    private static final TextAttributesKey[] OPERATOR_KEYS = {OPERATOR};
    private static final TextAttributesKey[] SEMICOLON_KEYS = {SEMICOLON};
    private static final TextAttributesKey[] COMMA_KEYS = {COMMA};
    private static final TextAttributesKey[] DOT_KEYS = {DOT};
    private static final TextAttributesKey[] PAREN_KEYS = {PAREN};
    private static final TextAttributesKey[] BRACE_KEYS = {BRACE};
    private static final TextAttributesKey[] BRACKET_KEYS = {BRACKET};
    private static final TextAttributesKey[] STRUCT_NAME_KEYS = {STRUCT_NAME};
    private static final TextAttributesKey[] IDENTIFIER_KEYS = {IDENTIFIER};
    private static final TextAttributesKey[] BAD_CHARACTER_KEYS = {BAD_CHARACTER};
    private static final TextAttributesKey[] EMPTY_KEYS = new TextAttributesKey[0];

    @Override
    public @NotNull Lexer getHighlightingLexer() {
        return new HlslLexer();
    }

    @Override
    public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
        if (tokenType.equals(HlslTokenTypes.KEYWORD)) return KEYWORD_KEYS;
        if (tokenType.equals(HlslTokenTypes.TYPE_KEYWORD)) return TYPE_KEYS;
        if (tokenType.equals(HlslTokenTypes.BUILTIN_FUNCTION)) return BUILTIN_KEYS;
        if (tokenType.equals(HlslTokenTypes.FUNCTION_CALL)) return FUNCTION_CALL_KEYS;
        if (tokenType.equals(HlslTokenTypes.INSTANCE_METHOD_CALL)) return INSTANCE_METHOD_CALL_KEYS;
        if (tokenType.equals(HlslTokenTypes.FIELD_ACCESS)) return FIELD_ACCESS_KEYS;
        if (tokenType.equals(HlslTokenTypes.SEMANTIC)) return SEMANTIC_KEYS;
        if (tokenType.equals(HlslTokenTypes.PREPROCESSOR)) return PREPROCESSOR_KEYS;
        if (tokenType.equals(HlslTokenTypes.NUMBER)) return NUMBER_KEYS;
        if (tokenType.equals(HlslTokenTypes.STRING)) return STRING_KEYS;
        if (tokenType.equals(HlslTokenTypes.LINE_COMMENT)) return LINE_COMMENT_KEYS;
        if (tokenType.equals(HlslTokenTypes.BLOCK_COMMENT)) return BLOCK_COMMENT_KEYS;
        if (tokenType.equals(HlslTokenTypes.OPERATOR)) return OPERATOR_KEYS;
        if (tokenType.equals(HlslTokenTypes.SEMICOLON)) return SEMICOLON_KEYS;
        if (tokenType.equals(HlslTokenTypes.COMMA)) return COMMA_KEYS;
        if (tokenType.equals(HlslTokenTypes.DOT)) return DOT_KEYS;
        if (tokenType.equals(HlslTokenTypes.LPAREN) || tokenType.equals(HlslTokenTypes.RPAREN)) return PAREN_KEYS;
        if (tokenType.equals(HlslTokenTypes.LBRACE) || tokenType.equals(HlslTokenTypes.RBRACE)) return BRACE_KEYS;
        if (tokenType.equals(HlslTokenTypes.LBRACKET) || tokenType.equals(HlslTokenTypes.RBRACKET)) return BRACKET_KEYS;
        if (tokenType.equals(HlslTokenTypes.STRUCT_NAME)) return STRUCT_NAME_KEYS;
        if (tokenType.equals(HlslTokenTypes.IDENTIFIER)) return IDENTIFIER_KEYS;
        if (tokenType.equals(TokenType.BAD_CHARACTER)) return BAD_CHARACTER_KEYS;
        return EMPTY_KEYS;
    }
}
