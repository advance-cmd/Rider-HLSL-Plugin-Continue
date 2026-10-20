package com.hlsl.highlighting;

import com.hlsl.validation.HlslCompileRoot;
import com.hlsl.validation.HlslDxcSettings;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

/**
 * A struct name is coloured wherever it is used, also when the file only knows it through an include, or, in a
 * helper such as Shadow.hlsl, through the shader that includes the helper after declaring it.
 */
public class HlslStructNameAnnotatorTest extends BasePlatformTestCase {

    private static final String LIGHTING_UTIL = """
            #include "Common.hlsl"
            struct PassConstants
            {
                float4x4 ViewProj;
            };
            #include "Shadow.hlsl"
            struct AfterShadow { float Late; };
            """;

    private static final String SHADOW = """
            float ViewZ(PassConstants passData, Light light, AfterShadow late, CascadeData cascade)
            {
                return mul(float4(light.PosW,1),passData.ViewProj).w;
            }
            """;

    private boolean validation;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        getProject().getService(HlslCompileRoot.class).invalidate();
        // Only the colouring is looked at; DXC would compile the helper through its includer as well.
        validation = HlslDxcSettings.getInstance().isEnableValidation();
        HlslDxcSettings.getInstance().setEnableValidation(false);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            HlslDxcSettings.getInstance().setEnableValidation(validation);
        } finally {
            super.tearDown();
        }
    }

    /** The reported case: PassConstants is declared by LightingUtil.hlsl before it includes Shadow.hlsl. */
    public void testAStructTheIncluderDeclaresBeforeTheIncludeIsColoured() {
        addReportedLayout();
        openShadow(SHADOW);
        List<String> coloured = colouredStructNames();
        assertTrue("PassConstants is declared by the includer: " + coloured, coloured.contains("PassConstants"));
        assertTrue("Light comes from what the includer includes before the helper: " + coloured,
                coloured.contains("Light"));
        assertFalse("AfterShadow is declared after the include: " + coloured, coloured.contains("AfterShadow"));
    }

    public void testWhatTheHelperIncludesItselfIsStillColoured() {
        addReportedLayout();
        myFixture.addFileToProject("HLSL/Cascades.hlsl", "struct CascadeData { float Split; };\n");
        openShadow("#include \"Cascades.hlsl\"\n" + SHADOW);
        assertTrue(colouredStructNames().contains("CascadeData"));
    }

    /** A file with its own entry point is compiled on its own, so it has no includer to read. */
    public void testAFileWithItsOwnEntryPointSeesNoIncluder() {
        addReportedLayout();
        openShadow("// #pragma hlsl entry VS\n" + SHADOW);
        List<String> coloured = colouredStructNames();
        assertFalse(coloured.toString(), coloured.contains("PassConstants"));
    }

    /** The names are cached per file, and an edit in the includer must still reach the open helper. */
    public void testAnEditInTheIncluderRecoloursTheHelper() {
        PsiFile lightingUtil = addReportedLayout();
        openShadow(SHADOW);
        assertFalse(colouredStructNames().contains("CascadeData"));

        Document document = FileDocumentManager.getInstance().getDocument(lightingUtil.getVirtualFile());
        assertNotNull(document);
        WriteCommandAction.runWriteCommandAction(getProject(), () ->
                document.insertString(LIGHTING_UTIL.indexOf("#include \"Shadow.hlsl\""),
                        "struct CascadeData { float Split; };\n"));
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        List<String> coloured = colouredStructNames();
        assertTrue("the struct added to the includer is coloured: " + coloured, coloured.contains("CascadeData"));
    }

    private PsiFile addReportedLayout() {
        myFixture.addFileToProject("HLSL/Common.hlsl", "struct Light { float3 PosW; };\n");
        PsiFile lightingUtil = myFixture.addFileToProject("HLSL/LightingUtil.hlsl", LIGHTING_UTIL);
        myFixture.addFileToProject("HLSL/Triangle.hlsl", "#include \"LightingUtil.hlsl\"\n");
        return lightingUtil;
    }

    private void openShadow(String text) {
        PsiFile shadow = myFixture.addFileToProject("HLSL/Shadow.hlsl", text);
        myFixture.configureFromExistingVirtualFile(shadow.getVirtualFile());
    }

    private List<String> colouredStructNames() {
        String text = myFixture.getEditor().getDocument().getText();
        return myFixture.doHighlighting().stream()
                .filter(info -> info.forcedTextAttributesKey == HlslSyntaxHighlighter.STRUCT_NAME)
                .map(info -> text.substring(info.startOffset, info.endOffset))
                .toList();
    }
}
