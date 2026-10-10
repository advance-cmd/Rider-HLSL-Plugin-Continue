package com.hlsl.editor;

import com.hlsl.HlslFileType;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.awt.datatransfer.StringSelection;

/**
 * Pasting code keeps the shape of the block and puts it at the indentation of the line it lands on.
 *
 * <p>These tests go through the real paste action, because the platform's own paste handling is what used to
 * bracket a pasted block with the target indentation and leave the block itself where it was copied.</p>
 */
public class HlslPasteTest extends BasePlatformTestCase {

    /** The block the user copied: three statements, four spaces in, as they sit in a function body. */
    private static final String BLOCK = """
                float3 p = WorldPosAt(pixel);
                float3 n = normalize(gGBuffer1.Load(int3(pixel, 0)).xyz);
                float3 randVec = RandomVec(int2(pin.PosH.xy));""";

    /** Pasting into an empty file keeps the block exactly as it was copied. */
    public void testPastingAnIndentedBlockKeepsItsIndentation() {
        paste("<caret>");
        assertEquals(BLOCK, documentText());
    }

    /** Pasting onto a blank line that already carries the block's indentation changes nothing. */
    public void testPastingOntoAnIndentedBlankLineKeepsTheBlockAsItIs() {
        paste("float4 PS(float2 pixel) : SV_Target\n{\n    <caret>\n}\n");
        assertEquals("""
                float4 PS(float2 pixel) : SV_Target
                {
                    float3 p = WorldPosAt(pixel);
                    float3 n = normalize(gGBuffer1.Load(int3(pixel, 0)).xyz);
                    float3 randVec = RandomVec(int2(pin.PosH.xy));
                }
                """, documentText());
    }

    /** Pasting onto a line with nothing in front of the caret indents the block by that level. */
    public void testPastingOntoALineWithoutIndentationIndentsTheBlock() {
        paste("float4 PS(float2 pixel) : SV_Target\n{\n<caret>\n}\n");
        assertEquals("""
                float4 PS(float2 pixel) : SV_Target
                {
                    float3 p = WorldPosAt(pixel);
                    float3 n = normalize(gGBuffer1.Load(int3(pixel, 0)).xyz);
                    float3 randVec = RandomVec(int2(pin.PosH.xy));
                }
                """, documentText());
    }

    /** A block copied without indentation gains the indentation of the line it is pasted on. */
    public void testPastingAnUnindentedBlockIntoABlock() {
        paste("float4 PS(float2 pixel) : SV_Target\n{\n    <caret>\n}\n", """
                float3 p = WorldPosAt(pixel);
                float3 n = normalize(gGBuffer1.Load(int3(pixel, 0)).xyz);""");
        assertEquals("""
                float4 PS(float2 pixel) : SV_Target
                {
                    float3 p = WorldPosAt(pixel);
                    float3 n = normalize(gGBuffer1.Load(int3(pixel, 0)).xyz);
                }
                """, documentText());
    }

    /** Inside a nested block the pasted code sits at that nesting level. */
    public void testPastingIntoANestedBlock() {
        paste("float4 PS(float2 pixel) : SV_Target\n{\n    if (pixel.x > 0)\n    {\n        <caret>\n    }\n}\n");
        assertEquals("""
                float4 PS(float2 pixel) : SV_Target
                {
                    if (pixel.x > 0)
                    {
                        float3 p = WorldPosAt(pixel);
                        float3 n = normalize(gGBuffer1.Load(int3(pixel, 0)).xyz);
                        float3 randVec = RandomVec(int2(pin.PosH.xy));
                    }
                }
                """, documentText());
    }

    /** Pasting behind existing code moves the block onto a line of its own at that indentation. */
    public void testPastingBehindExistingCode() {
        paste("float4 PS(float2 pixel) : SV_Target\n{\n    float3 x = 1;<caret>\n}\n");
        assertEquals("""
                float4 PS(float2 pixel) : SV_Target
                {
                    float3 x = 1;
                    float3 p = WorldPosAt(pixel);
                    float3 n = normalize(gGBuffer1.Load(int3(pixel, 0)).xyz);
                    float3 randVec = RandomVec(int2(pin.PosH.xy));
                }
                """, documentText());
    }

    /** The relative indentation inside the block survives: a nested line stays one level further in. */
    public void testRelativeIndentationInsideTheBlockSurvives() {
        String block = "    if (pixel.x > 0)\n    {\n        return 1;\n    }";
        paste("float4 PS(float2 pixel) : SV_Target\n{\n    <caret>\n}\n", block);
        assertEquals("""
                float4 PS(float2 pixel) : SV_Target
                {
                    if (pixel.x > 0)
                    {
                        return 1;
                    }
                }
                """, documentText());
    }

    /** Pasting the same block twice must not push the second copy any deeper. */
    public void testPastingTwiceKeepsTheSameIndentation() {
        paste("float4 PS(float2 pixel) : SV_Target\n{\n    <caret>\n}\n");
        // The second paste goes onto a fresh line at the end of the block, which is where a user would put
        // the caret after the first one.
        myFixture.getEditor().getCaretModel().moveToOffset(documentText().lastIndexOf("\n}"));
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER);
        CopyPasteManager.getInstance().setContents(new StringSelection(BLOCK));
        myFixture.performEditorAction(IdeActions.ACTION_PASTE);
        assertEquals("""
                float4 PS(float2 pixel) : SV_Target
                {
                    float3 p = WorldPosAt(pixel);
                    float3 n = normalize(gGBuffer1.Load(int3(pixel, 0)).xyz);
                    float3 randVec = RandomVec(int2(pin.PosH.xy));
                    float3 p = WorldPosAt(pixel);
                    float3 n = normalize(gGBuffer1.Load(int3(pixel, 0)).xyz);
                    float3 randVec = RandomVec(int2(pin.PosH.xy));
                }
                """, documentText());
    }

    private void paste(String before) {
        paste(before, BLOCK);
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
