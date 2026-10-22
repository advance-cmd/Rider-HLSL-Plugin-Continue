package com.hlsl.completion;

import com.hlsl.documentation.HlslDocumentationProvider;
import com.hlsl.parameterinfo.HlslParameterInfoHandler;
import com.hlsl.validation.HlslCompileRoot;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.testFramework.utils.parameterInfo.MockCreateParameterInfoContext;

import java.util.List;

/**
 * A helper such as Helper.hlsl is included by Shared.hlsl after the declarations it uses, so completion,
 * parameter info, hover and navigation in the helper see what the shaders on the chain declare above their
 * include of it, and nothing they declare below.
 */
public class HlslIncluderContextTest extends BasePlatformTestCase {

    private static final String CARET = "<caret>";

    private static final String SHARED = """
            #include "Common.hlsl"
            // Constants of one pass
            struct FrameConstants
            {
                float4x4 WorldToClip;
                float4 CameraPos;
            };
            // Linear fog
            float3 AddHaze(float3 color, float viewDistance) { return color; }
            #include "Helper.hlsl"
            struct AfterHelper { float Late; };
            float LateValue;
            """;

    private static final String HELPER = """
            float DepthOf(FrameConstants pass, float3 worldPos)
            {
                return mul(float4(worldPos,1),pass.WorldToClip).w;
            }
            """;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        getProject().getService(HlslCompileRoot.class).invalidate();
    }

    /** The reported case: the members of the struct Shared.hlsl declares before it includes Helper.hlsl. */
    public void testCompletionListsTheMembersOfAStructTheIncluderDeclares() {
        addReportedLayout();
        openHelper(HELPER.replace("pass.WorldToClip", "pass." + CARET));
        myFixture.completeBasic();
        List<String> lookup = myFixture.getLookupElementStrings();
        assertNotNull("no completion list after pass.", lookup);
        assertTrue("expected the FrameConstants fields, got " + lookup, lookup.containsAll(List.of("WorldToClip", "CameraPos")));
        assertFalse("a field of a struct declared after the include is offered: " + lookup, lookup.contains("Late"));
    }

    public void testOnlyWhatComesBeforeTheIncludeIsVisible() {
        addReportedLayout();
        openHelper(HELPER.replace("mul(float4(worldPos,1),pass.WorldToClip).w", CARET));
        myFixture.completeBasic();
        List<String> lookup = myFixture.getLookupElementStrings();
        assertNotNull(lookup);
        assertTrue("the includer's declarations above the include are offered: " + lookup,
                lookup.containsAll(List.of("FrameConstants", "AddHaze")));
        assertTrue("what the includer includes above the include is offered: " + lookup, lookup.contains("PI"));
        assertTrue("the root of the chain contributes its declarations too: " + lookup, lookup.contains("ColorOnly"));
        assertFalse("declared after the include: " + lookup, lookup.contains("LateValue"));
        assertFalse("declared after the include: " + lookup, lookup.contains("AfterHelper"));
        assertFalse("the other root is not the one the helper is read through: " + lookup,
                lookup.contains("DrawConstants"));
    }

    public void testThePragmaPicksTheRootWhoseDeclarationsAreSeen() {
        addReportedLayout();
        openHelper("// #pragma hlsl root Main.hlsl\n" + HELPER.replace("mul(float4(worldPos,1),pass.WorldToClip).w", CARET));
        myFixture.completeBasic();
        List<String> lookup = myFixture.getLookupElementStrings();
        assertNotNull(lookup);
        assertTrue("Main.hlsl declares DrawConstants before its include: " + lookup,
                lookup.contains("DrawConstants"));
        assertFalse("Alt.hlsl is not the root any more: " + lookup, lookup.contains("ColorOnly"));
        assertTrue(lookup.contains("FrameConstants"));
    }

    /** A file with its own entry point is compiled on its own, so it has no includer to read. */
    public void testAFileWithItsOwnEntryPointSeesNoIncluder() {
        addReportedLayout();
        openHelper("// #pragma hlsl entry VS\n" + HELPER.replace("mul(float4(worldPos,1),pass.WorldToClip).w", CARET));
        myFixture.completeBasic();
        List<String> lookup = myFixture.getLookupElementStrings();
        assertNotNull(lookup);
        assertFalse(lookup.toString(), lookup.contains("FrameConstants"));
    }

    public void testTheHelpersOwnDeclarationWins() {
        addReportedLayout();
        String own = "struct FrameConstants { float Own; };\n";
        openHelper(own + HELPER.replace("pass.WorldToClip", "pass." + CARET));
        myFixture.completeBasic();
        List<String> lookup = myFixture.getLookupElementStrings();
        if (lookup == null) {
            // The only member was inserted.
            assertTrue(myFixture.getEditor().getDocument().getText().contains("pass.Own"));
        } else {
            assertFalse("the includer's struct must not shadow the helper's own: " + lookup, lookup.contains("WorldToClip"));
        }

        PsiElement target = singleTarget(HELPER.indexOf("FrameConstants") + own.length());
        assertEquals("Helper.hlsl", target.getContainingFile().getName());
    }

    /** Edits in the includer reach the helper before they are saved. */
    public void testUnsavedEditsOfTheIncluderAreSeen() {
        PsiFile shared = addReportedLayout();
        Document document = FileDocumentManager.getInstance().getDocument(shared.getVirtualFile());
        assertNotNull(document);
        WriteCommandAction.runWriteCommandAction(getProject(), () ->
                document.insertString(SHARED.indexOf("float4 CameraPos;"), "float SplitCount;\n    "));
        openHelper(HELPER.replace("pass.WorldToClip", "pass." + CARET));
        myFixture.completeBasic();
        List<String> lookup = myFixture.getLookupElementStrings();
        assertNotNull(lookup);
        assertTrue("the unsaved field is offered: " + lookup, lookup.contains("SplitCount"));
    }

    public void testParameterInfoShowsAFunctionTheIncluderDeclares() {
        addReportedLayout();
        openHelper(HELPER.replace("mul(float4(worldPos,1),pass.WorldToClip).w", "AddHaze(worldPos, " + CARET + ").x"));
        MockCreateParameterInfoContext context =
                new MockCreateParameterInfoContext(myFixture.getEditor(), myFixture.getFile());
        assertNotNull("the call to AddHaze has parameter info",
                new HlslParameterInfoHandler().findElementForParameterInfo(context));
        Object[] items = context.getItemsToShow();
        assertEquals(1, items.length);
        assertEquals("AddHaze(float3 color, float viewDistance)",
                ((HlslCompletionAnalysis.Signature) items[0]).label());
    }

    public void testHoverDescribesAStructTheIncluderDeclares() {
        addReportedLayout();
        openHelper(HELPER);
        PsiElement element = myFixture.getFile().findElementAt(HELPER.indexOf("FrameConstants") + 2);
        assertNotNull(element);
        String hover = new HlslDocumentationProvider().generateHoverDoc(element, element);
        assertNotNull("hovering FrameConstants in Helper.hlsl must describe it", hover);
        assertTrue(hover, hover.contains("struct FrameConstants"));
        assertTrue("the comment above the declaration is included: " + hover, hover.contains("Constants of one pass"));
    }

    /** Ctrl+Click on FrameConstants in Helper.hlsl opens its declaration in Shared.hlsl. */
    public void testNavigationGoesToTheIncludersDeclaration() {
        addReportedLayout();
        openHelper(HELPER);
        PsiElement target = singleTarget(HELPER.indexOf("FrameConstants") + 2);
        assertEquals("Shared.hlsl", target.getContainingFile().getName());
        assertEquals(SHARED.indexOf("FrameConstants"), target.getTextRange().getStartOffset());
    }

    public void testNavigationReachesWhatTheIncluderIncludesBeforeTheHelper() {
        addReportedLayout();
        String text = HELPER.replace("mul(float4(worldPos,1),pass.WorldToClip).w", "PI");
        openHelper(text);
        PsiElement target = singleTarget(text.indexOf("PI;") + 1);
        assertEquals("Common.hlsl", target.getContainingFile().getName());
    }

    public void testNavigationDoesNotGoPastTheInclude() {
        addReportedLayout();
        String text = HELPER.replace("mul(float4(worldPos,1),pass.WorldToClip).w", "LateValue");
        openHelper(text);
        assertEquals("LateValue is declared after the include of Helper.hlsl", 0,
                GotoDeclarationAction.findAllTargetElements(getProject(), myFixture.getEditor(),
                        text.indexOf("LateValue") + 2).length);
    }

    /**
     * The reported layout: Shared.hlsl declares FrameConstants and then includes Helper.hlsl, and the two
     * pass shaders include Shared.hlsl after a declaration of their own.
     */
    private PsiFile addReportedLayout() {
        myFixture.addFileToProject("HLSL/Common.hlsl", "static const float PI = 3.14159f;\n");
        PsiFile shared = myFixture.addFileToProject("HLSL/Shared.hlsl", SHARED);
        myFixture.addFileToProject("HLSL/Main.hlsl",
                "struct DrawConstants { float4x4 World; };\n#include \"Shared.hlsl\"\n");
        myFixture.addFileToProject("HLSL/Alt.hlsl",
                "struct ColorOnly { float Tint; };\n#include \"Shared.hlsl\"\n");
        return shared;
    }

    /** Opens Helper.hlsl next to its includers, with the caret where {@code <caret>} stands. */
    private void openHelper(String text) {
        int caret = text.indexOf(CARET);
        PsiFile helper = myFixture.addFileToProject("HLSL/Helper.hlsl", text.replace(CARET, ""));
        myFixture.configureFromExistingVirtualFile(helper.getVirtualFile());
        if (caret >= 0) myFixture.getEditor().getCaretModel().moveToOffset(caret);
    }

    private PsiElement singleTarget(int offset) {
        PsiElement[] targets = GotoDeclarationAction.findAllTargetElements(getProject(), myFixture.getEditor(), offset);
        assertEquals("expected exactly one target", 1, targets.length);
        return targets[0];
    }
}
