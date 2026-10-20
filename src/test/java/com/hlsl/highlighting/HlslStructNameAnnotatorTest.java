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
 * helper such as Helper.hlsl, through the shader that includes the helper after declaring it.
 */
public class HlslStructNameAnnotatorTest extends BasePlatformTestCase {

    private static final String SHARED = """
            #include "Common.hlsl"
            struct FrameConstants
            {
                float4x4 WorldToClip;
            };
            #include "Helper.hlsl"
            struct AfterHelper { float Late; };
            """;

    private static final String HELPER = """
            float DepthOf(FrameConstants passData, Light light, AfterHelper late, CascadeData cascade)
            {
                return mul(float4(light.WorldPos,1),passData.WorldToClip).w;
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

    /** The reported case: FrameConstants is declared by Shared.hlsl before it includes Helper.hlsl. */
    public void testAStructTheIncluderDeclaresBeforeTheIncludeIsColoured() {
        addReportedLayout();
        openHelper(HELPER);
        List<String> coloured = colouredStructNames();
        assertTrue("FrameConstants is declared by the includer: " + coloured, coloured.contains("FrameConstants"));
        assertTrue("Light comes from what the includer includes before the helper: " + coloured,
                coloured.contains("Light"));
        assertFalse("AfterHelper is declared after the include: " + coloured, coloured.contains("AfterHelper"));
    }

    public void testWhatTheHelperIncludesItselfIsStillColoured() {
        addReportedLayout();
        myFixture.addFileToProject("HLSL/Cascades.hlsl", "struct CascadeData { float Split; };\n");
        openHelper("#include \"Cascades.hlsl\"\n" + HELPER);
        assertTrue(colouredStructNames().contains("CascadeData"));
    }

    /** A file with its own entry point is compiled on its own, so it has no includer to read. */
    public void testAFileWithItsOwnEntryPointSeesNoIncluder() {
        addReportedLayout();
        openHelper("// #pragma hlsl entry VS\n" + HELPER);
        List<String> coloured = colouredStructNames();
        assertFalse(coloured.toString(), coloured.contains("FrameConstants"));
    }

    /** The names are cached per file, and an edit in the includer must still reach the open helper. */
    public void testAnEditInTheIncluderRecoloursTheHelper() {
        PsiFile shared = addReportedLayout();
        openHelper(HELPER);
        assertFalse(colouredStructNames().contains("CascadeData"));

        Document document = FileDocumentManager.getInstance().getDocument(shared.getVirtualFile());
        assertNotNull(document);
        WriteCommandAction.runWriteCommandAction(getProject(), () ->
                document.insertString(SHARED.indexOf("#include \"Helper.hlsl\""),
                        "struct CascadeData { float Split; };\n"));
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        List<String> coloured = colouredStructNames();
        assertTrue("the struct added to the includer is coloured: " + coloured, coloured.contains("CascadeData"));
    }

    private PsiFile addReportedLayout() {
        myFixture.addFileToProject("HLSL/Common.hlsl", "struct Light { float3 WorldPos; };\n");
        PsiFile shared = myFixture.addFileToProject("HLSL/Shared.hlsl", SHARED);
        myFixture.addFileToProject("HLSL/Main.hlsl", "#include \"Shared.hlsl\"\n");
        return shared;
    }

    private void openHelper(String text) {
        PsiFile helper = myFixture.addFileToProject("HLSL/Helper.hlsl", text);
        myFixture.configureFromExistingVirtualFile(helper.getVirtualFile());
    }

    private List<String> colouredStructNames() {
        String text = myFixture.getEditor().getDocument().getText();
        return myFixture.doHighlighting().stream()
                .filter(info -> info.forcedTextAttributesKey == HlslSyntaxHighlighter.STRUCT_NAME)
                .map(info -> text.substring(info.startOffset, info.endOffset))
                .toList();
    }
}
