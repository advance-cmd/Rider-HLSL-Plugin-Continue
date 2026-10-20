package com.hlsl.validation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The underline has to cover the symbol the compiler complained about, not the rest of the line. */
public class HlslDxcSpanTest {

    /** The reported case: an undeclared identifier in the middle of an expression. */
    @Test
    public void anUndeclaredIdentifierIsUnderlinedAlone() {
        String line = "float side = sign(dot(normal, 空25));          // 判断在平面的哪一侧";
        int column = columnOf(line, "空25");
        int start = HlslDxcDiagnostics.offsetForByteColumn(line, column);
        HlslDxcDiagnostics.Diagnostic diagnostic = new HlslDxcDiagnostics.Diagnostic(1, column, "error",
                "use of undeclared identifier '空25'");
        int end = HlslDxcAnnotator.spanEnd(line, start, diagnostic);
        assertEquals("空25", line.substring(start, end));
    }

    /** The comment after the statement must stay out of the underline. */
    @Test
    public void theRestOfTheLineIsNotUnderlined() {
        String line = "float flip = sign(dot(offset, missingName));";
        int column = columnOf(line, "missingName");
        int start = HlslDxcDiagnostics.offsetForByteColumn(line, column);
        HlslDxcDiagnostics.Diagnostic diagnostic = new HlslDxcDiagnostics.Diagnostic(1, column, "error",
                "use of undeclared identifier 'missingName'");
        int end = HlslDxcAnnotator.spanEnd(line, start, diagnostic);
        assertEquals("missingName", line.substring(start, end));
    }

    /** A message that quotes a name standing a little further along the line underlines that name. */
    @Test
    public void aNameCloseToThePositionIsUnderlined() {
        String line = "    float3 c = 1;";
        HlslDxcDiagnostics.Diagnostic diagnostic = new HlslDxcDiagnostics.Diagnostic(1, 5, "error",
                "redefinition of 'c'");
        int end = HlslDxcAnnotator.spanEnd(line, 4, diagnostic);
        assertEquals("float3 c", line.substring(4, end));
    }

    /** A quoted name is not chased across the whole line: the word at the position is underlined instead. */
    @Test
    public void aNameFarFromThePositionIsNotChased() {
        String line = "    float3 color = 1;                      // unrelated";
        HlslDxcDiagnostics.Diagnostic diagnostic = new HlslDxcDiagnostics.Diagnostic(1, 5, "error",
                "redefinition of 'c'");
        int end = HlslDxcAnnotator.spanEnd(line, 4, diagnostic);
        assertEquals("float3", line.substring(4, end));
    }

    /** Without a quoted name the identifier at the reported position is underlined. */
    @Test
    public void withoutAQuotedNameTheIdentifierAtThePositionIsUnderlined() {
        String line = "    return Normalize(input);";
        HlslDxcDiagnostics.Diagnostic diagnostic = new HlslDxcDiagnostics.Diagnostic(1, 12, "error",
                "no matching function for call");
        int start = 11;
        assertEquals("Normalize", line.substring(start, HlslDxcAnnotator.spanEnd(line, start, diagnostic)));
    }

    /** A position that points past the end of the text must not produce a span that runs off the line. */
    @Test
    public void aPositionAtTheLineEndStillYieldsAOneCharacterSpan() {
        String line = "int a = ;";
        HlslDxcDiagnostics.Diagnostic diagnostic = new HlslDxcDiagnostics.Diagnostic(1, 9, "error",
                "expected expression");
        int end = HlslDxcAnnotator.spanEnd(line, 8, diagnostic);
        assertEquals("the span must stay inside the line", line.length(), end);
        assertTrue("there must still be something to underline", end > 8 || line.isEmpty());
    }

    /** Columns from DXC count UTF-8 bytes, so a name after Chinese text still starts where it should. */
    @Test
    public void byteColumnsMapBackToTheRightCharacter() {
        String line = "// 注释 float side = 空25;";
        int column = line.substring(0, line.indexOf("空25")).getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 1;
        int start = HlslDxcDiagnostics.offsetForByteColumn(line, column);
        assertEquals(line.indexOf("空25"), start);
        HlslDxcDiagnostics.Diagnostic diagnostic = new HlslDxcDiagnostics.Diagnostic(1, column, "error",
                "use of undeclared identifier '空25'");
        assertEquals("空25", line.substring(start, HlslDxcAnnotator.spanEnd(line, start, diagnostic)));
    }

    private static int columnOf(String line, String name) {
        return line.substring(0, line.indexOf(name))
                .getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 1;
    }
}
