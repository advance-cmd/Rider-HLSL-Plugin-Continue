package com.hlsl.editor;

import com.hlsl.HlslFileType;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/** Enter has to lay out a block the way the editor is expected to: body indented, closing brace aligned. */
public class HlslIndentTest extends BasePlatformTestCase {

    /** The reported case: the caret is already between the braces. */
    public void testEnterInsideAnEmptyFunctionBody() {
        myFixture.configureByText(HlslFileType.INSTANCE, "float OcclusionFunction(float dist) {<caret>}");
        enter();
        myFixture.checkResult("""
                float OcclusionFunction(float dist) {
                    <caret>
                }""");
    }

    /** The same block, but written with the brace on its own line. */
    public void testEnterAfterAnOpeningBraceOnItsOwnLine() {
        myFixture.configureByText(HlslFileType.INSTANCE, """
                float OcclusionFunction(float dist)
                {<caret>
                }""");
        enter();
        myFixture.checkResult("""
                float OcclusionFunction(float dist)
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
                "float3 c = ApplyFog(color, dist);\nfloat f() {<caret>}\n");
        enter();
        myFixture.checkResult("""
                float3 c = ApplyFog(color, dist);
                float f() {
                    <caret>
                }
                """);
    }

    /** Parentheses are not a block: Enter inside a call must not add a body. */
    public void testEnterInsideParenthesesIsNotRewritten() {
        myFixture.configureByText(HlslFileType.INSTANCE, "float3 c = ApplyFog(<caret>);");
        enter();
        assertTrue("the call must not gain a block: " + documentText(),
                documentText().startsWith("float3 c = ApplyFog(\n"));
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

    private void enter() {
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER);
    }

    private String documentText() {
        return myFixture.getEditor().getDocument().getText();
    }
}
