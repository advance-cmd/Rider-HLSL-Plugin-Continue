package com.hlsl.editor;

import com.hlsl.HlslFileType;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.awt.datatransfer.StringSelection;

/** Pasting code has to keep the indentation of the block, shifted to where it is pasted. */
public class HlslPasteTest extends BasePlatformTestCase {

    private static final String BLOCK = """
                float3 p = WorldPosAt(pixel);
                float3 n = normalize(gGBuffer1.Load(int3(pixel, 0)).xyz);
                float3 randVec = RandomVec(int2(pin.PosH.xy));""";

    /** Pasting a block into an empty file keeps the block exactly as it was copied. */
    public void testPastingAnIndentedBlockKeepsItsIndentation() {
        pasteInto("<caret>");
        assertEquals(BLOCK, documentText());
    }

    /** Pasting into a block shifts the whole block by that level, without changing relative indentation. */
    public void testPastingIntoABlockShiftsTheWholeBlock() {
        pasteInto("""
                float4 PS(float2 pixel) : SV_Target
                {
                    <caret>
                }""");
        assertEquals("""
                float4 PS(float2 pixel) : SV_Target
                {
                    float3 p = WorldPosAt(pixel);
                    float3 n = normalize(gGBuffer1.Load(int3(pixel, 0)).xyz);
                    float3 randVec = RandomVec(int2(pin.PosH.xy));
                }""", documentText());
    }

    /** Pasting a block that carries no indentation at all indents it by exactly one level. */
    public void testPastingAnUnindentedBlockIntoABlock() {
        pasteInto("""
                float4 PS(float2 pixel) : SV_Target
                {
                    <caret>
                }""", """
                float3 p = WorldPosAt(pixel);
                float3 n = normalize(gGBuffer1.Load(int3(pixel, 0)).xyz);""");
        assertEquals("""
                float4 PS(float2 pixel) : SV_Target
                {
                    float3 p = WorldPosAt(pixel);
                    float3 n = normalize(gGBuffer1.Load(int3(pixel, 0)).xyz);
                }""", documentText());
    }

    /**
     * The reported case: a four-space block pasted onto a blank line that already carries four spaces used to
     * be indented twice, because the platform adds the target indentation without removing the block's own.
     */
    public void testPastingOntoAnIndentedBlankLineDoesNotDoubleTheIndent() {
        pasteInto("float4 PS(float2 pixel) : SV_Target\n{\n    <caret>\n}\n");
        assertEquals("""
                float4 PS(float2 pixel) : SV_Target
                {
                    float3 p = WorldPosAt(pixel);
                    float3 n = normalize(gGBuffer1.Load(int3(pixel, 0)).xyz);
                    float3 randVec = RandomVec(int2(pin.PosH.xy));
                }
                """, documentText());
    }

    /** Pasting the same block twice must not push the second copy any deeper. */
    public void testPastingTwiceKeepsTheSameIndentation() {
        pasteInto("""
                float4 PS(float2 pixel) : SV_Target
                {
                    <caret>
                }""");
        // A second paste at the end of the block: the block goes onto a line of its own, as it would when
        // the caret is parked there in a real editor.
        String text = documentText();
        myFixture.getEditor().getCaretModel().moveToOffset(
                text.lastIndexOf("randVec") + "randVec".length() + " = RandomVec(int2(pin.PosH.xy));".length());
        myFixture.type('\n');
        myFixture.performEditorAction(IdeActions.ACTION_PASTE);
        assertTrue("the second copy must not drift deeper: " + documentText(),
                documentText().contains("\n    float3 randVec = RandomVec(int2(pin.PosH.xy));\n"
                        + "    float3 p = WorldPosAt(pixel);"));
    }

    /** Pasting inside a nested block lands at that nesting level. */
    public void testPastingIntoANestedBlock() {
        pasteInto("""
                float4 PS(float2 pixel) : SV_Target
                {
                    if (pixel.x > 0)
                    {
                        <caret>
                    }
                }""");
        assertEquals("""
                float4 PS(float2 pixel) : SV_Target
                {
                    if (pixel.x > 0)
                    {
                        float3 p = WorldPosAt(pixel);
                        float3 n = normalize(gGBuffer1.Load(int3(pixel, 0)).xyz);
                        float3 randVec = RandomVec(int2(pin.PosH.xy));
                    }
                }""", documentText());
    }

    private void pasteInto(String before) {
        pasteInto(before, BLOCK);
    }

    private void pasteInto(String before, String block) {
        myFixture.configureByText(HlslFileType.INSTANCE, before);
        CopyPasteManager.getInstance().setContents(new StringSelection(block));
        myFixture.performEditorAction(IdeActions.ACTION_PASTE);
    }

    private String documentText() {
        return myFixture.getEditor().getDocument().getText();
    }
}
