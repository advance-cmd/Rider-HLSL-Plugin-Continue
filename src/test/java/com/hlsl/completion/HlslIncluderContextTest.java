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
 * A helper such as Shadow.hlsl is included by LightingUtil.hlsl after the declarations it uses, so completion,
 * parameter info, hover and navigation in the helper see what the shaders on the chain declare above their
 * include of it, and nothing they declare below.
 */
public class HlslIncluderContextTest extends BasePlatformTestCase {

    private static final String CARET = "<caret>";

    private static final String LIGHTING_UTIL = """
            #include "Common.hlsl"
            // Constants of one pass
            struct PassConstants
            {
                float4x4 ViewProj;
                float4 EyePosW;
            };
            // Linear fog
            float3 ApplyFog(float3 color, float distToEye) { return color; }
            #include "Shadow.hlsl"
            struct AfterShadow { float Late; };
            float LateValue;
            """;

    private static final String SHADOW = """
            float ViewZ(PassConstants pass, float3 posW)
            {
                return mul(float4(posW,1),pass.ViewProj).w;
            }
            """;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        getProject().getService(HlslCompileRoot.class).invalidate();
    }

    /** The reported case: the members of the struct LightingUtil.hlsl declares before it includes Shadow.hlsl. */
    public void testCompletionListsTheMembersOfAStructTheIncluderDeclares() {
        addReportedLayout();
        openShadow(SHADOW.replace("pass.ViewProj", "pass." + CARET));
        myFixture.completeBasic();
        List<String> lookup = myFixture.getLookupElementStrings();
        assertNotNull("no completion list after pass.", lookup);
        assertTrue("expected the PassConstants fields, got " + lookup, lookup.containsAll(List.of("ViewProj", "EyePosW")));
        assertFalse("a field of a struct declared after the include is offered: " + lookup, lookup.contains("Late"));
    }

    public void testOnlyWhatComesBeforeTheIncludeIsVisible() {
        addReportedLayout();
        openShadow(SHADOW.replace("mul(float4(posW,1),pass.ViewProj).w", CARET));
        myFixture.completeBasic();
        List<String> lookup = myFixture.getLookupElementStrings();
        assertNotNull(lookup);
        assertTrue("the includer's declarations above the include are offered: " + lookup,
                lookup.containsAll(List.of("PassConstants", "ApplyFog")));
        assertTrue("what the includer includes above the include is offered: " + lookup, lookup.contains("PI"));
        assertTrue("the root of the chain contributes its declarations too: " + lookup, lookup.contains("ColorOnly"));
        assertFalse("declared after the include: " + lookup, lookup.contains("LateValue"));
        assertFalse("declared after the include: " + lookup, lookup.contains("AfterShadow"));
        assertFalse("the other root is not the one the helper is read through: " + lookup,
                lookup.contains("ObjectConstants"));
    }

    public void testThePragmaPicksTheRootWhoseDeclarationsAreSeen() {
        addReportedLayout();
        openShadow("// #pragma hlsl root Triangle.hlsl\n" + SHADOW.replace("mul(float4(posW,1),pass.ViewProj).w", CARET));
        myFixture.completeBasic();
        List<String> lookup = myFixture.getLookupElementStrings();
        assertNotNull(lookup);
        assertTrue("Triangle.hlsl declares ObjectConstants before its include: " + lookup,
                lookup.contains("ObjectConstants"));
        assertFalse("Color.hlsl is not the root any more: " + lookup, lookup.contains("ColorOnly"));
        assertTrue(lookup.contains("PassConstants"));
    }

    /** A file with its own entry point is compiled on its own, so it has no includer to read. */
    public void testAFileWithItsOwnEntryPointSeesNoIncluder() {
        addReportedLayout();
        openShadow("// #pragma hlsl entry VS\n" + SHADOW.replace("mul(float4(posW,1),pass.ViewProj).w", CARET));
        myFixture.completeBasic();
        List<String> lookup = myFixture.getLookupElementStrings();
        assertNotNull(lookup);
        assertFalse(lookup.toString(), lookup.contains("PassConstants"));
    }

    public void testTheHelpersOwnDeclarationWins() {
        addReportedLayout();
        String own = "struct PassConstants { float Own; };\n";
        openShadow(own + SHADOW.replace("pass.ViewProj", "pass." + CARET));
        myFixture.completeBasic();
        List<String> lookup = myFixture.getLookupElementStrings();
        if (lookup == null) {
            // The only member was inserted.
            assertTrue(myFixture.getEditor().getDocument().getText().contains("pass.Own"));
        } else {
            assertFalse("the includer's struct must not shadow the helper's own: " + lookup, lookup.contains("ViewProj"));
        }

        PsiElement target = singleTarget(SHADOW.indexOf("PassConstants") + own.length());
        assertEquals("Shadow.hlsl", target.getContainingFile().getName());
    }

    /** Edits in the includer reach the helper before they are saved. */
    public void testUnsavedEditsOfTheIncluderAreSeen() {
        PsiFile lightingUtil = addReportedLayout();
        Document document = FileDocumentManager.getInstance().getDocument(lightingUtil.getVirtualFile());
        assertNotNull(document);
        WriteCommandAction.runWriteCommandAction(getProject(), () ->
                document.insertString(LIGHTING_UTIL.indexOf("float4 EyePosW;"), "float CascadeCount;\n    "));
        openShadow(SHADOW.replace("pass.ViewProj", "pass." + CARET));
        myFixture.completeBasic();
        List<String> lookup = myFixture.getLookupElementStrings();
        assertNotNull(lookup);
        assertTrue("the unsaved field is offered: " + lookup, lookup.contains("CascadeCount"));
    }

    public void testParameterInfoShowsAFunctionTheIncluderDeclares() {
        addReportedLayout();
        openShadow(SHADOW.replace("mul(float4(posW,1),pass.ViewProj).w", "ApplyFog(posW, " + CARET + ").x"));
        MockCreateParameterInfoContext context =
                new MockCreateParameterInfoContext(myFixture.getEditor(), myFixture.getFile());
        assertNotNull("the call to ApplyFog has parameter info",
                new HlslParameterInfoHandler().findElementForParameterInfo(context));
        Object[] items = context.getItemsToShow();
        assertEquals(1, items.length);
        assertEquals("ApplyFog(float3 color, float distToEye)",
                ((HlslCompletionAnalysis.Signature) items[0]).label());
    }

    public void testHoverDescribesAStructTheIncluderDeclares() {
        addReportedLayout();
        openShadow(SHADOW);
        PsiElement element = myFixture.getFile().findElementAt(SHADOW.indexOf("PassConstants") + 2);
        assertNotNull(element);
        String hover = new HlslDocumentationProvider().generateHoverDoc(element, element);
        assertNotNull("hovering PassConstants in Shadow.hlsl must describe it", hover);
        assertTrue(hover, hover.contains("struct PassConstants"));
        assertTrue("the comment above the declaration is included: " + hover, hover.contains("Constants of one pass"));
    }

    /** Ctrl+Click on PassConstants in Shadow.hlsl opens its declaration in LightingUtil.hlsl. */
    public void testNavigationGoesToTheIncludersDeclaration() {
        addReportedLayout();
        openShadow(SHADOW);
        PsiElement target = singleTarget(SHADOW.indexOf("PassConstants") + 2);
        assertEquals("LightingUtil.hlsl", target.getContainingFile().getName());
        assertEquals(LIGHTING_UTIL.indexOf("PassConstants"), target.getTextRange().getStartOffset());
    }

    public void testNavigationReachesWhatTheIncluderIncludesBeforeTheHelper() {
        addReportedLayout();
        String text = SHADOW.replace("mul(float4(posW,1),pass.ViewProj).w", "PI");
        openShadow(text);
        PsiElement target = singleTarget(text.indexOf("PI;") + 1);
        assertEquals("Common.hlsl", target.getContainingFile().getName());
    }

    public void testNavigationDoesNotGoPastTheInclude() {
        addReportedLayout();
        String text = SHADOW.replace("mul(float4(posW,1),pass.ViewProj).w", "LateValue");
        openShadow(text);
        assertEquals("LateValue is declared after the include of Shadow.hlsl", 0,
                GotoDeclarationAction.findAllTargetElements(getProject(), myFixture.getEditor(),
                        text.indexOf("LateValue") + 2).length);
    }

    /**
     * The reported layout: LightingUtil.hlsl declares PassConstants and then includes Shadow.hlsl, and the two
     * pass shaders include LightingUtil.hlsl after a declaration of their own.
     */
    private PsiFile addReportedLayout() {
        myFixture.addFileToProject("HLSL/Common.hlsl", "static const float PI = 3.14159f;\n");
        PsiFile lightingUtil = myFixture.addFileToProject("HLSL/LightingUtil.hlsl", LIGHTING_UTIL);
        myFixture.addFileToProject("HLSL/Triangle.hlsl",
                "struct ObjectConstants { float4x4 World; };\n#include \"LightingUtil.hlsl\"\n");
        myFixture.addFileToProject("HLSL/Color.hlsl",
                "struct ColorOnly { float Tint; };\n#include \"LightingUtil.hlsl\"\n");
        return lightingUtil;
    }

    /** Opens Shadow.hlsl next to its includers, with the caret where {@code <caret>} stands. */
    private void openShadow(String text) {
        int caret = text.indexOf(CARET);
        PsiFile shadow = myFixture.addFileToProject("HLSL/Shadow.hlsl", text.replace(CARET, ""));
        myFixture.configureFromExistingVirtualFile(shadow.getVirtualFile());
        if (caret >= 0) myFixture.getEditor().getCaretModel().moveToOffset(caret);
    }

    private PsiElement singleTarget(int offset) {
        PsiElement[] targets = GotoDeclarationAction.findAllTargetElements(getProject(), myFixture.getEditor(), offset);
        assertEquals("expected exactly one target", 1, targets.length);
        return targets[0];
    }
}
