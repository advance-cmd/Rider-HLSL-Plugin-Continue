package com.hlsl.documentation;

import com.hlsl.HlslFileType;
import com.hlsl.validation.HlslDxcSettings;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/** Hover on a semantic explains what the pipeline does with it, in the configured language. */
public class HlslSemanticsTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        HlslDxcSettings.getInstance().setSemanticsHintsEnabled(true);
        HlslDxcSettings.getInstance().setHintLanguage("en");
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            HlslDxcSettings.getInstance().setSemanticsHintsEnabled(true);
            HlslDxcSettings.getInstance().setHintLanguage("auto");
        } finally {
            super.tearDown();
        }
    }

    /** The reported case: the semantic of a pixel shader entry point. */
    public void testHoveringSVTargetExplainsTheRenderTarget() {
        String text = "float4 main(float4 posH : SV_Position) : SV_Target\n{\n    return 0;\n}\n";
        myFixture.configureByText(HlslFileType.INSTANCE, text);
        PsiElement element = myFixture.getFile().findElementAt(text.indexOf("SV_Target") + 3);
        assertNotNull(element);
        String hover = new HlslSemanticsDocumentationProvider().generateHoverDoc(element, element);
        assertNotNull("hovering SV_Target must say what it is", hover);
        assertTrue(hover, hover.contains("SV_Target"));
        assertTrue("the description is missing: " + hover, hover.contains("Render target output"));
    }

    /** A vertex input semantic, and the same mechanism through the provider the platform calls. */
    public void testHoveringAnInputSemanticGoesThroughThePlatformEntryPoint() {
        String text = "struct VSInput\n{\n    float3 pos : POSITION;\n    float2 uv : TEXCOORD0;\n};\n";
        myFixture.configureByText(HlslFileType.INSTANCE, text);
        int offset = text.indexOf("TEXCOORD0") + 2;
        PsiElement claimed = new HlslSemanticsDocumentationProvider()
                .getCustomDocumentationElement(myFixture.getEditor(), myFixture.getFile(), null, offset);
        assertNotNull("the semantics provider must claim the hovered semantic", claimed);
        assertEquals("TEXCOORD0", claimed.getText());
        String doc = new HlslSemanticsDocumentationProvider().generateDoc(claimed, claimed);
        assertNotNull(doc);
        assertTrue(doc, doc.contains("Texture coordinate set 0"));
    }

    /** An interpolation modifier after the colon is explained too. */
    public void testInterpolationModifiersAreExplained() {
        String text = "struct PSInput\n{\n    float2 uv : linear;\n    float3 n : nointerpolation;\n};\n";
        myFixture.configureByText(HlslFileType.INSTANCE, text);
        PsiElement element = myFixture.getFile().findElementAt(text.indexOf("nointerpolation") + 3);
        assertNotNull(element);
        String doc = new HlslSemanticsDocumentationProvider().generateDoc(element, element);
        assertNotNull(doc);
        assertTrue(doc, doc.contains("Do not interpolate"));
    }

    /** register is the other thing the user asked for: it is not a semantic but it sits after a colon. */
    public void testRegisterIsExplained() {
        String text = "cbuffer Pass : register(b0)\n{\n    float4x4 ViewProj;\n};\n";
        myFixture.configureByText(HlslFileType.INSTANCE, text);
        PsiElement element = myFixture.getFile().findElementAt(text.indexOf("register") + 2);
        assertNotNull(element);
        String doc = new HlslSemanticsDocumentationProvider().generateDoc(element, element);
        assertNotNull("register must be explained", doc);
        assertTrue(doc, doc.contains("Pipeline slot"));
    }

    /** The language is switchable: the same semantic reads differently in Chinese. */
    public void testLanguageCanBeSwitchedToChinese() {
        HlslDxcSettings.getInstance().setHintLanguage("zh");
        String english = HlslSemantics.description("SV_Target");
        assertNotNull(english);
        assertTrue("expected Chinese text, got: " + english, english.contains("渲染目标"));

        String text = "float4 main() : SV_Target\n{\n    return 0;\n}\n";
        myFixture.configureByText(HlslFileType.INSTANCE, text);
        PsiElement element = myFixture.getFile().findElementAt(text.indexOf("SV_Target") + 3);
        String doc = new HlslSemanticsDocumentationProvider().generateDoc(element, element);
        assertNotNull(doc);
        assertTrue("the hover must be Chinese as well: " + doc, doc.contains("渲染目标"));

        HlslDxcSettings.getInstance().setHintLanguage("en");
        assertTrue(HlslSemantics.description("SV_Target").contains("Render target"));
    }

    /** Everything in the table has a description in both languages. */
    public void testEveryEntryIsTranslated() {
        HlslDxcSettings.getInstance().setHintLanguage("zh");
        int checked = 0;
        for (String name : new String[]{"SV_Target", "SV_Position", "SV_DispatchThreadID", "TEXCOORD0", "POSITION",
                "NORMAL", "linear", "centroid", "nointerpolation", "noperspective", "sample", "register"}) {
            String chinese = HlslSemantics.description(name);
            assertNotNull("no Chinese description for " + name, chinese);
            assertFalse("the Chinese text of " + name + " is the English one", chinese.contains("the "));
            checked++;
        }
        HlslDxcSettings.getInstance().setHintLanguage("en");
        for (String name : new String[]{"SV_Target", "SV_Position", "SV_DispatchThreadID", "TEXCOORD0", "POSITION",
                "NORMAL", "linear", "centroid", "nointerpolation", "noperspective", "sample", "register"}) {
            assertNotNull("no English description for " + name, HlslSemantics.description(name));
        }
        assertEquals(12, checked);
    }

    /** A struct field name is not a semantic: the declaration provider keeps answering those. */
    public void testAPlainIdentifierIsNotClaimed() {
        String text = "float3 c = ApplyFog(color);\n";
        myFixture.configureByText(HlslFileType.INSTANCE, text);
        PsiElement claimed = new HlslSemanticsDocumentationProvider()
                .getCustomDocumentationElement(myFixture.getEditor(), myFixture.getFile(), null,
                        text.indexOf("ApplyFog") + 2);
        assertNull("a call is not a semantic", claimed);
    }

    /** A semantic name used as a variable is not after a colon, so it is not claimed either. */
    public void testASemanticNameWithoutAColonIsNotClaimed() {
        String text = "float TEXCOORD0 = 1.0;\n";
        myFixture.configureByText(HlslFileType.INSTANCE, text);
        PsiElement claimed = new HlslSemanticsDocumentationProvider()
                .getCustomDocumentationElement(myFixture.getEditor(), myFixture.getFile(), null, 8);
        assertNull("without a colon there is no semantic", claimed);
    }

    /** The hints can be switched off entirely. */
    public void testHintsCanBeSwitchedOff() {
        HlslDxcSettings.getInstance().setSemanticsHintsEnabled(false);
        String text = "float4 main() : SV_Target\n{\n    return 0;\n}\n";
        myFixture.configureByText(HlslFileType.INSTANCE, text);
        PsiElement element = myFixture.getFile().findElementAt(text.indexOf("SV_Target") + 3);
        assertNull(new HlslSemanticsDocumentationProvider().generateDoc(element, element));
        assertNull(new HlslSemanticsDocumentationProvider().getCustomDocumentationElement(
                myFixture.getEditor(), myFixture.getFile(), null, text.indexOf("SV_Target") + 3));
    }

    /**
     * Hover in a current IDE goes through the registered documentation target providers, so the test follows
     * that registry rather than calling the provider class directly.
     */
    public void testPlatformResolvesSemanticsThroughARegisteredTargetProvider() {
        String text = "float4 main() : SV_Target\n{\n    return 0;\n}\n";
        myFixture.configureByText(HlslFileType.INSTANCE, text);
        PsiElement element = myFixture.getFile().findElementAt(text.indexOf("SV_Target") + 3);
        assertNotNull(element);

        DocumentationTarget target = null;
        for (PsiDocumentationTargetProvider provider : PsiDocumentationTargetProvider.EP_NAME.getExtensionList()) {
            target = provider.documentationTarget(element, element);
            if (target != null) break;
        }
        assertNotNull("no registered provider knows the semantic", target);
        assertNotNull(target.createPointer().dereference());
        assertTrue(target.computePresentation().getPresentableText().contains("SV_Target"));

        String hint = target.computeDocumentationHint();
        assertNotNull("hover would show nothing", hint);
        assertTrue(hint, hint.contains("Render target output"));
        assertNotNull("Ctrl+Q would show nothing", target.computeDocumentation());
    }
}
