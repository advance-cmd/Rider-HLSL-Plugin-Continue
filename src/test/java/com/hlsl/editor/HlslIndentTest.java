package com.hlsl.editor;

import com.hlsl.HlslFileType;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/** Enter has to lay out a block the way the editor is expected to: body indented, closing brace aligned. */
public class HlslIndentTest extends BasePlatformTestCase {

    /** The reported case: the caret is already between the braces. */
    public void testEnterInsideAnEmptyFunctionBody() {
        myFixture.configureByText(HlslFileType.INSTANCE, "float Falloff(float dist) {<caret>}");
        enter();
        myFixture.checkResult("""
                float Falloff(float dist) {
                    <caret>
                }""");
    }

    /** The same block, but written with the brace on its own line. */
    public void testEnterAfterAnOpeningBraceOnItsOwnLine() {
        myFixture.configureByText(HlslFileType.INSTANCE, """
                float Falloff(float dist)
                {<caret>
                }""");
        enter();
        myFixture.checkResult("""
                float Falloff(float dist)
                {
                    <caret>
                }""");
    }

    /** Nested blocks indent one step per level. */
    public void testEnterInsideANestedBlock() {
        myFixture.configureByText(HlslFileType.INSTANCE, """
                float f(float dist)
                {
                    if (dist > 0)
                    {<caret>
                    }
                }""");
        enter();
        myFixture.checkResult("""
                float f(float dist)
                {
                    if (dist > 0)
                    {
                        <caret>
                    }
                }""");
    }

    /** A function written on one line keeps the surrounding code untouched. */
    public void testEnterInsideAnEmptyBodyDoesNotTouchTheSurroundingCode() {
        myFixture.configureByText(HlslFileType.INSTANCE,
                "float3 c = AddHaze(color, dist);\nfloat f() {<caret>}\n");
        enter();
        myFixture.checkResult("""
                float3 c = AddHaze(color, dist);
                float f() {
                    <caret>
                }
                """);
    }

    /** Parentheses are not a block: Enter inside a call must not add a body. */
    public void testEnterInsideParenthesesIsNotRewritten() {
        myFixture.configureByText(HlslFileType.INSTANCE, "float3 c = AddHaze(<caret>);");
        enter();
        assertTrue("the call must not gain a block: " + documentText(),
                documentText().startsWith("float3 c = AddHaze(\n"));
        assertFalse("a closing paren must not be moved to its own line", documentText().contains(")\n"));
    }

    /**
     * A closing brace typed on a blank line lines up with the line that opened the block. The handler is
     * called directly: the test fixture would auto-insert a closing brace of its own while typing.
     */
    public void testTypingAClosingBraceDedents() {
        myFixture.configureByText(HlslFileType.INSTANCE, """
                float f()
                {
                    float3 c = 0;
                    }<caret>""");
        WriteCommandAction.runWriteCommandAction(getProject(), "dedent brace", null,
                () -> new HlslClosingBraceHandler().charTyped('}', getProject(), myFixture.getEditor(),
                        myFixture.getFile()), myFixture.getFile());
        assertEquals("""
                float f()
                {
                    float3 c = 0;
                }""", documentText());
    }

    /**
     * The empty body of a nested block on one line: the braces are split by the platform and the body line
     * gets the indentation of the block, which the line indent provider answers for HLSL.
     */
    public void testEnterInsideAnEmptyNestedBlock() {
        myFixture.configureByText(HlslFileType.INSTANCE, """
                float f(float dist)
                {
                    if (dist > 0) {<caret>}
                }""");
        enter();
        myFixture.checkResult("""
                float f(float dist)
                {
                    if (dist > 0) {
                        <caret>
                    }
                }""");
    }

    /** A brace inside a comment or a string is not a block opener. */
    public void testBracesInCommentsAndStringsDoNotChangeTheIndent() {
        myFixture.configureByText(HlslFileType.INSTANCE, """
                // { not a block
                float f()
                {<caret>
                }""");
        enter();
        myFixture.checkResult("""
                // { not a block
                float f()
                {
                    <caret>
                }""");
    }

    /**
     * The reported case: an if without braces, whose body must hang one level below the if instead of
     * lining up with it.
     */
    public void testEnterAfterAnUnbracedIfIndentsItsBody() {
        myFixture.configureByText(HlslFileType.INSTANCE, """
                float4 PS_Deferred(float2 pixel) : SV_Target
                {
                    if (gDepth.Load(int3(pixel, 0)) >= 1.0f)
                        return 1.0f;<caret>
                }""");
        enter();
        myFixture.checkResult("""
                float4 PS_Deferred(float2 pixel) : SV_Target
                {
                    if (gDepth.Load(int3(pixel, 0)) >= 1.0f)
                        return 1.0f;
                    <caret>
                }""");
    }

    /** The same rule for an if written on one line with its body. */
    public void testEnterAfterAnUnbracedIfOnOneLine() {
        myFixture.configureByText(HlslFileType.INSTANCE, """
                float f(float d)
                {
                    if (d > 0) return d;<caret>
                }""");
        enter();
        myFixture.checkResult("""
                float f(float d)
                {
                    if (d > 0) return d;
                    <caret>
                }""");
    }

    /** A loop header behaves like an if. */
    public void testEnterAfterAnUnbracedLoopIndentsItsBody() {
        myFixture.configureByText(HlslFileType.INSTANCE, """
                void f()
                {
                    for (int i = 0; i < 4; i++)<caret>
                }""");
        enter();
        myFixture.checkResult("""
                void f()
                {
                    for (int i = 0; i < 4; i++)
                        <caret>
                }""");
    }

    /** The statement after an else hangs one level below the else, like any other body. */
    public void testEnterOnAnUnbracedElse() {
        myFixture.configureByText(HlslFileType.INSTANCE, """
                float f(float d)
                {
                    if (d > 0)
                        return 1;
                    else<caret>
                }""");
        enter();
        myFixture.checkResult("""
                float f(float d)
                {
                    if (d > 0)
                        return 1;
                    else
                        <caret>
                }""");
    }

    /** A call ending in a closing parenthesis is not a control statement, so nothing is indented. */
    public void testACallIsNotMistakenForAControlStatement() {
        myFixture.configureByText(HlslFileType.INSTANCE, """
                void f()
                {
                    AddHaze(color, dist);<caret>
                }""");
        enter();
        myFixture.checkResult("""
                void f()
                {
                    AddHaze(color, dist);
                    <caret>
                }""");
    }

    private void enter() {
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER);
    }

    private String documentText() {
        return myFixture.getEditor().getDocument().getText();
    }
}
