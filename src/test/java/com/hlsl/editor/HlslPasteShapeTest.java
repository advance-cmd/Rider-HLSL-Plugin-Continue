package com.hlsl.editor;

import com.hlsl.HlslFileType;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.awt.datatransfer.StringSelection;

/**
 * Pasting at the end of a line, and pasting a block that carries its own braces: the two shapes the
 * platform's own handling gets wrong.
 */
public class HlslPasteShapeTest extends BasePlatformTestCase {

    /**
     * A comment pasted at the end of a line gets a line of its own at that indentation. The platform used to
     * put the copied text above the line because it indented the line and moved what was already there down.
     */
    public void testPastingASingleLineComment() {
        paste("float4 PS(float2 aoPixel) : SV_Target\n{\n    int dir = 1;\n    int2 a = aoPixel + dir;<caret>\n}\n",
                "// 左右（或上下）各 5 个邻居，i = 0 是自己");
        assertEquals("""
                float4 PS(float2 aoPixel) : SV_Target
                {
                    int dir = 1;
                    int2 a = aoPixel + dir;
                    // 左右（或上下）各 5 个邻居，i = 0 是自己
                }
                """, documentText());
    }

    /**
     * The reported shape: a two-line clipboard whose first line carries no indentation. Both lines follow the
     * line they were pasted into, in the order they were copied.
     */
    public void testPastingTwoUnindentedLinesFollowsTheLine() {
        paste("float4 PS(float2 aoPixel) : SV_Target\n{\n    int dir = 1;\n    int2 a = aoPixel + dir;<caret>\n}\n",
                "// 左右（或上下）各 5 个邻居，i = 0 是自己\nint2 a = aoPixel + i * dir;");
        assertEquals("""
                float4 PS(float2 aoPixel) : SV_Target
                {
                    int dir = 1;
                    int2 a = aoPixel + dir;
                    // 左右（或上下）各 5 个邻居，i = 0 是自己
                    int2 a = aoPixel + i * dir;
                }
                """, documentText());
    }

    /** A block pasted into a loop body keeps the body's indentation instead of doubling it. */
    public void testPastingALoopIntoALoopBodyDoesNotDoubleTheIndent() {
        paste("void f()\n{\n    for (int i = -5; i <= 5; i++) {\n        <caret>\n    }\n}\n",
                "    for (int i = -5; i <= 5; i++) {\n\n    }");
        assertEquals("""
                void f()
                {
                    for (int i = -5; i <= 5; i++) {
                        for (int i = -5; i <= 5; i++) {
                        }
                    }
                }
                """, documentText());
    }

    /**
     * The reported shape: a block copied with its braces, pasted into a function body. The braces have to end
     * up at the level of the statement they belong to, not one level deeper, and the body between them one
     * level below that.
     */
    public void testPastingABracedBlockPlacesItsBracesByTheStatement() {
        String block = """
                if (i == 0 || (dot(neighborN, centerN) >= 0.8f && abs(neighborZ - centerZ) <= 0.2f))
                        {
                            sum += kBlurWeights[空32] * gAoMap.Load(int3(空33, 0)); // i 是 -5～5，数组下标是 0～10
                            weightSum += kBlurWeights[空32];
                        }""";
        paste("float4 PS(float2 pixel) : SV_Target\n{\n    float sum = 0;\n    <caret>\n}\n", indent(block, 4));
        assertEquals("""
                float4 PS(float2 pixel) : SV_Target
                {
                    float sum = 0;
                    if (i == 0 || (dot(neighborN, centerN) >= 0.8f && abs(neighborZ - centerZ) <= 0.2f))
                    {
                        sum += kBlurWeights[空32] * gAoMap.Load(int3(空33, 0)); // i 是 -5～5，数组下标是 0～10
                        weightSum += kBlurWeights[空32];
                    }
                }
                """, documentText());
    }

    /** The same block, pasted into a nested block, lands two levels in with its braces aligned. */
    public void testPastingABracedBlockIntoANestedBlock() {
        String block = """
                if (i == 0 || (dot(neighborN, centerN) >= 0.8f && abs(neighborZ - centerZ) <= 0.2f))
                        {
                            sum += kBlurWeights[空32];
                        }""";
        paste("float4 PS(float2 pixel) : SV_Target\n{\n    if (pixel.x > 0)\n    {\n        <caret>\n    }\n}\n",
                indent(block, 4));
        assertEquals("""
                float4 PS(float2 pixel) : SV_Target
                {
                    if (pixel.x > 0)
                    {
                        if (i == 0 || (dot(neighborN, centerN) >= 0.8f && abs(neighborZ - centerZ) <= 0.2f))
                        {
                            sum += kBlurWeights[空32];
                        }
                    }
                }
                """, documentText());
    }

    private static String indent(String text, int width) {
        String pad = " ".repeat(width);
        return pad + text.replace("\n", "\n" + pad);
    }

    private void paste(String before, String block) {
        myFixture.configureByText(HlslFileType.INSTANCE, before);
        CopyPasteManager.getInstance().setContents(new StringSelection(block));
        myFixture.performEditorAction(IdeActions.ACTION_PASTE);
    }

    private String documentText() {
        return myFixture.getEditor().getDocument().getText();
    }
}
