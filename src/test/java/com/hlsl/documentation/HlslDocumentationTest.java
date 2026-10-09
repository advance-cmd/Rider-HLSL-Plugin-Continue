package com.hlsl.documentation;

import com.hlsl.completion.HlslCompletionAnalysis;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Hover documentation is built from the declaration text and the comment written above it. */
public class HlslDocumentationTest extends BasePlatformTestCase {

    public void testIncludedFunctionIsDocumentedWithItsSignatureAndComment() throws IOException {
        HlslCompletionAnalysis.Declaration declaration = HlslCompletionAnalysis.declarationOf("ApplyFog",
                "float3 c = ApplyFog();\n", 18, List.of(load("LightingUtil.hlsl")));
        assertNotNull("the declaration from the include chain must be found", declaration);
        assertEquals(HlslCompletionAnalysis.Kind.FUNCTION, declaration.kind());
        assertEquals("float3 ApplyFog(float3 color, float distToEye, float4 fogColor, float fogStart, float fogRange)",
                declaration.text());
        assertTrue("the comment above the declaration must be included: " + declaration.comment(),
                declaration.comment().startsWith("// 线性雾"));
        assertTrue("a multi-line comment belongs to the declaration as a whole: " + declaration.comment(),
                declaration.comment().contains("只处理 RGB"));
    }

    public void testMacrosStructsAndVariablesCarryTheirDeclarationText() {
        String source = """
                // 灯的盏数，必须和 C++ FrameResource.h 一致
                #define MaxLights 100

                // 每个 Pass 的常量
                struct PassConstants
                {
                    float4x4 ViewProj;
                };

                float4x4 gViewProj;
                """;
        HlslCompletionAnalysis.Declaration macro =
                HlslCompletionAnalysis.declarationOf("MaxLights", source, 0, List.of());
        assertEquals("#define MaxLights 100", macro.text());
        assertEquals("// 灯的盏数，必须和 C++ FrameResource.h 一致", macro.comment());

        HlslCompletionAnalysis.Declaration type =
                HlslCompletionAnalysis.declarationOf("PassConstants", source, 0, List.of());
        assertEquals("struct PassConstants", type.text());
        assertEquals("// 每个 Pass 的常量", type.comment());

        HlslCompletionAnalysis.Declaration variable =
                HlslCompletionAnalysis.declarationOf("gViewProj", source, 0, List.of());
        assertEquals("float4x4 gViewProj", variable.text());
        assertTrue("nothing was written above the variable: " + variable.comment(), variable.comment().isEmpty());
    }

    public void testHoverOnACallDescribesTheIncludedDeclaration() throws IOException {
        myFixture.addFileToProject("LightingUtil.hlsl", load("LightingUtil.hlsl"));
        String text = "#include \"LightingUtil.hlsl\"\nfloat3 c = ApplyFog(color, dist, fog, start, range);\n";
        myFixture.configureByText("Deferred.hlsl", text);
        PsiElement element = myFixture.getFile().findElementAt(text.indexOf("ApplyFog"));
        assertNotNull(element);
        String hover = new HlslDocumentationProvider().generateHoverDoc(element, element);
        assertNotNull("hovering a call must describe its declaration", hover);
        assertTrue(hover, hover.contains(
                "ApplyFog(float3 color, float distToEye, float4 fogColor, float fogStart, float fogRange)"));
        assertTrue("the comment above the declaration is included: " + hover, hover.contains("线性雾"));
    }

    public void testHoveringSomethingUndeclaredShowsNothing() {
        myFixture.configureByText("Deferred.hlsl", "float4 main() : SV_Target { return 0; }\n");
        PsiElement element = myFixture.getFile().findElementAt(1);
        assertNotNull(element);
        assertNull("a keyword has no declaration to show",
                new HlslDocumentationProvider().generateHoverDoc(element, element));
    }

    /**
     * Hover and Ctrl+Q resolve documentation through the providers the platform has registered, so the test
     * has to go through that registry. Calling the provider class directly hid a missing registration once.
     */
    public void testPlatformResolvesHoverThroughARegisteredTargetProvider() throws IOException {
        myFixture.addFileToProject("LightingUtil.hlsl", load("LightingUtil.hlsl"));
        String text = "#include \"LightingUtil.hlsl\"\nfloat3 c = ApplyFog(color, dist, fog, start, range);\n";
        myFixture.configureByText("Deferred.hlsl", text);
        // Hovering anywhere inside the word must work: the identifier is read from the document.
        PsiElement element = myFixture.getFile().findElementAt(text.indexOf("ApplyFog") + 3);
        assertNotNull(element);

        DocumentationTarget target = null;
        for (PsiDocumentationTargetProvider provider : PsiDocumentationTargetProvider.EP_NAME.getExtensionList()) {
            target = provider.documentationTarget(element, element);
            if (target != null) break;
        }
        assertNotNull("no registered documentation target provider knows this call", target);
        assertNotNull("the target would not be navigable", target.createPointer().dereference());
        assertNotNull("the presentation is missing", target.computePresentation());

        String hint = target.computeDocumentationHint();
        assertNotNull("hover would show nothing", hint);
        assertTrue(hint, hint.contains(
                "ApplyFog(float3 color, float distToEye, float4 fogColor, float fogStart, float fogRange)"));
        assertTrue("the comment above the declaration is included: " + hint, hint.contains("线性雾"));
        assertNotNull("Ctrl+Q would show nothing", target.computeDocumentation());
    }

    public void testWordsAreReadFromTheDocumentAtTheOffset() {
        String text = "float3 c = ApplyFog(color);";
        assertEquals("ApplyFog", HlslDocumentationLookup.nameAt(text, text.indexOf("ApplyFog") + 4));
        assertEquals("color", HlslDocumentationLookup.nameAt(text, text.indexOf("color")));
        assertNull(HlslDocumentationLookup.nameAt(text, text.indexOf(';')));
    }

    public void testCustomDocumentationElementIsOfferedForAHoveredCall() throws IOException {
        myFixture.addFileToProject("LightingUtil.hlsl", load("LightingUtil.hlsl"));
        String text = "#include \"LightingUtil.hlsl\"\nfloat3 c = ApplyFog(color, dist, fog, start, range);\n";
        myFixture.configureByText("Deferred.hlsl", text);
        PsiElement element = new HlslDocumentationProvider().getCustomDocumentationElement(
                myFixture.getEditor(), myFixture.getFile(), null, text.indexOf("ApplyFog") + 2);
        assertNotNull("the platform must be told which element to document", element);
        assertEquals("ApplyFog", element.getText());
    }

    private static String load(String name) throws IOException {
        try (InputStream in = HlslDocumentationTest.class.getResourceAsStream("/dx12/" + name)) {
            assertNotNull("missing test resource: /dx12/" + name, in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
