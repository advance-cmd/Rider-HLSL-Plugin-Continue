package com.hlsl.navigation;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/** Ctrl+Click on the path of an #include opens the included file. */
public class HlslIncludeNavigationTest extends BasePlatformTestCase {

    /** The reported case. */
    public void testCtrlClickOnAnIncludeOpensTheFile() {
        myFixture.addFileToProject("Shared.hlsl", "float3 Shade(float3 n)\n{\n    return n;\n}\n");
        myFixture.configureByText("Composite.hlsl", "#include \"Sha<caret>red.hlsl\"\n\nfloat4 main() : SV_Target\n{\n    return 0;\n}\n");
        PsiElement target = singleTarget();
        assertTrue("expected a file, got " + target, target instanceof PsiFile);
        assertEquals("Shared.hlsl", ((PsiFile) target).getName());
    }

    /** Ctrl+hover underlines the path with its quotes, not the whole directive. */
    public void testTheUnderlineCoversThePath() {
        myFixture.addFileToProject("Shared.hlsl", "");
        String text = "#include \"Shared.hlsl\"\n";
        myFixture.configureByText("Composite.hlsl", text);
        PsiReference reference = myFixture.getFile().findReferenceAt(text.indexOf("ared"));
        assertNotNull("the include path must carry a reference", reference);
        TextRange range = reference.getRangeInElement().shiftRight(reference.getElement().getTextRange().getStartOffset());
        assertEquals("\"Shared.hlsl\"", range.substring(text));
        assertNull("the directive keyword is not part of the path", myFixture.getFile().findReferenceAt(text.indexOf("include")));
    }

    public void testAngleBracketsAndSubdirectories() {
        myFixture.addFileToProject("common/Helper.hlsli", "float Helper() { return 1; }\n");
        myFixture.configureByText("Composite.hlsl", "#include <common/Hel<caret>per.hlsli>\n");
        PsiElement target = singleTarget();
        assertEquals("Helper.hlsli", ((PsiFile) target).getName());
    }

    /** Shared headers usually sit above the shader that includes them, which the include index allows for. */
    public void testAnIncludeIsFoundInAParentDirectory() {
        myFixture.addFileToProject("shaders/Common.hlsli", "static const float PI = 3.14159;\n");
        PsiFile pass = myFixture.addFileToProject("shaders/passes/Filter.hlsl", "#include \"Common.hlsli\"\n");
        myFixture.configureFromExistingVirtualFile(pass.getVirtualFile());
        myFixture.getEditor().getCaretModel().moveToOffset(pass.getText().indexOf("Common") + 2);
        PsiElement target = singleTarget();
        assertEquals("Common.hlsli", ((PsiFile) target).getName());
    }

    public void testOtherDirectivesCarryNoReference() {
        String text = "#define LIGHT_COUNT 4\n#pragma pack_matrix(row_major)\n";
        myFixture.configureByText("Composite.hlsl", text);
        assertNull(myFixture.getFile().findReferenceAt(text.indexOf("LIGHT_COUNT")));
        assertNull(myFixture.getFile().findReferenceAt(text.indexOf("pack_matrix")));
    }

    public void testAMissingFileResolvesToNothing() {
        myFixture.configureByText("Composite.hlsl", "#include \"Miss<caret>ing.hlsl\"\n");
        PsiReference reference = myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());
        assertNotNull(reference);
        assertNull(reference.resolve());
        assertEquals(0, GotoDeclarationAction.findAllTargetElements(
                getProject(), myFixture.getEditor(), myFixture.getCaretOffset()).length);
    }

    private PsiElement singleTarget() {
        PsiElement[] targets = GotoDeclarationAction.findAllTargetElements(
                getProject(), myFixture.getEditor(), myFixture.getCaretOffset());
        assertEquals("expected exactly one target", 1, targets.length);
        return targets[0];
    }
}
