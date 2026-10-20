package com.hlsl.lexer;

import com.hlsl.psi.HlslTokenTypes;
import com.intellij.psi.tree.IElementType;
import org.junit.Test;

import static org.junit.Assert.*;

public class HlslLexerTest {
    @Test
    public void incrementalRestartsPreserveEveryToken() {
        String source = "struct /* definition */ FrameConstants { float4 CameraPos; };\r\n"
                + "ConstantBuffer<FrameConstants> gFrame;\r\n"
                + "gFrame. /* member */ CameraPos.xyz; texture.Sample(sampler, uv);\r\n"
                + "class Surface {}; interface Lighting {}; enum Mode { Lit };";
        HlslLexer full = new HlslLexer();
        full.start(source);
        while (full.getTokenType() != null) {
            HlslLexer restarted = new HlslLexer();
            restarted.start(source, full.getTokenStart(), source.length(), full.getState());
            assertEquals("Restart at " + full.getTokenStart(), full.getTokenType(), restarted.getTokenType());
            assertEquals(full.getTokenEnd(), restarted.getTokenEnd());
            full.advance();
        }
    }

    @Test
    public void windowsMacroContinuationStaysInPreprocessor() {
        String directive = "#define READ_PASS(x) \\\r\n    gFrame.x";
        HlslLexer lexer = new HlslLexer();
        lexer.start(directive + "\r\nfloat value;");
        assertEquals(HlslTokenTypes.PREPROCESSOR, lexer.getTokenType());
        assertEquals(directive + "\r", lexer.getTokenText());
        lexer.advance();
        lexer.advance();
        assertEquals(HlslTokenTypes.TYPE_KEYWORD, lexer.getTokenType());
    }

    @Test
    public void recognizesNamedTypeDeclarations() {
        for (String keyword : new String[]{"struct", "class", "interface", "enum"}) {
            HlslLexer lexer = new HlslLexer();
            lexer.start(keyword + " Example {}");
            assertEquals(HlslTokenTypes.KEYWORD, lexer.getTokenType());
            lexer.advance();
            lexer.advance();
            assertEquals(HlslTokenTypes.STRUCT_NAME, lexer.getTokenType());
        }
    }
}
