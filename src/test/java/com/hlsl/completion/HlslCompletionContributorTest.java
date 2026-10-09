package com.hlsl.completion;

import com.hlsl.HlslFileType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class HlslCompletionContributorTest extends BasePlatformTestCase {
    public void testUserDeferredShaderAgainstRealSources() throws Exception {
        // Defaults to the verbatim copy of the user's shader sources, so this stays a regression test;
        // HLSL_TEST_SOURCE_DIR points it at the live DX12 directory instead.
        String directory = System.getenv("HLSL_TEST_SOURCE_DIR");
        Path sourceDirectory = directory == null || directory.isBlank()
                ? Path.of("src", "test", "resources", "dx12")
                : Path.of(directory);
        myFixture.addFileToProject("LightingUtil.hlsl", Files.readString(sourceDirectory.resolve("LightingUtil.hlsl")));
        String shader = Files.readString(sourceDirectory.resolve("Deferred.hlsl"));
        String assignment = "Light L = gPass.Lights[kFirstExtraLight + id];";
        assertTrue("The expected real-world completion site must still be present", shader.contains(assignment));
        Set<String> passFields = Set.of("ViewProj", "ShadowTransform", "InvViewProj", "EyePosW", "padding",
                "AmbientLight", "FogColor", "FogStart", "FogRange", "Lights");
        Set<String> lightFields = Set.of("Strength", "FalloffStart", "Direction", "FalloffEnd", "Position", "SpotPower");
        assertProjectMembers("RealDeferredBuffer.hlsl", shader.replace(assignment,
                "Light L = gPass.<caret>Lights[kFirstExtraLight + id];"), passFields);
        assertProjectMembers("RealDeferredArray.hlsl", shader.replace(assignment,
                "Light L = gPass.Lights[kFirstExtraLight + id].<caret>;"), lightFields);
        assertProjectMembers("RealDeferredLocal.hlsl", shader.replace(assignment,
                assignment + "\n    L.<caret>;"), lightFields);
    }

    public void testConstantBufferMembersFromInclude() {
        myFixture.addFileToProject("LightingUtil.hlsl", """
                struct Light { float3 Strength; float3 Position; float FalloffEnd; };
                struct PassConstants { float4x4 InvViewProj; Light Lights[100]; float3 EyePosW; };
                """);
        var shader = myFixture.addFileToProject("Deferred.hlsl", """
                #include "LightingUtil.hlsl"
                ConstantBuffer<PassConstants> gPass : register(b1);
                void VS(uint id) { Light L = gPass.Lights[id]; }
                """);
        myFixture.configureFromExistingVirtualFile(shader.getVirtualFile());
        int offset = myFixture.getEditor().getDocument().getText().indexOf("gPass.Lights") + "gPass.".length();
        myFixture.getEditor().getCaretModel().moveToOffset(offset);
        assertEquals(Set.of("InvViewProj", "Lights", "EyePosW"), lookupNames());
    }

    public void testArrayMemberCompletion() {
        myFixture.configureByText(HlslFileType.INSTANCE, """
                struct Light { float3 Strength; float3 Position; float FalloffEnd; };
                struct PassConstants { Light Lights[100]; };
                ConstantBuffer<PassConstants> gPass;
                void VS(uint id) { gPass.Lights[id].<caret>; }
                """);
        assertEquals(Set.of("Strength", "Position", "FalloffEnd"), lookupNames());
    }

    public void testCommentsDoNotOfferCode() {
        myFixture.configureByText(HlslFileType.INSTANCE, "// nor<caret>");
        assertTrue(lookupNames().isEmpty());
    }

    public void testUnknownReceiverDoesNotOfferCode() {
        myFixture.configureByText(HlslFileType.INSTANCE, "float unrelated; void f() { unknown.<caret>; }");
        assertTrue(lookupNames().isEmpty());
    }

    public void testSemanticsExcludeKeywordsAndFunctions() {
        myFixture.configureByText(HlslFileType.INSTANCE, "float4 main() : <caret> { return 0; }");
        Set<String> names = lookupNames();
        assertTrue(names.contains("SV_Target"));
        assertFalse(names.contains("return"));
        assertFalse(names.contains("normalize"));
    }

    public void testExistingCallParenthesesAreReused() {
        myFixture.configureByText(HlslFileType.INSTANCE, "void f() { normaliz<caret> (float3(1, 0, 0)); }");
        myFixture.completeBasic();
        if (myFixture.getLookup() != null) myFixture.finishLookup('\n');
        myFixture.checkResult("void f() { normalize (<caret>float3(1, 0, 0)); }");
    }

    public void testOpeningParenthesisCompletionCharacterIsNotDuplicated() {
        myFixture.configureByText(HlslFileType.INSTANCE, "void f() { nor<caret>; }");
        myFixture.completeBasic();
        if (myFixture.getLookup() != null) myFixture.finishLookup('(');
        myFixture.checkResult("void f() { normalize(<caret>); }");
    }

    private Set<String> lookupNames() {
        LookupElement[] elements = myFixture.completeBasic();
        return elements == null ? Set.of() : Arrays.stream(elements).map(LookupElement::getLookupString).collect(Collectors.toSet());
    }

    private void assertProjectMembers(String name, String markedSource, Set<String> expected) {
        int offset = markedSource.indexOf("<caret>");
        var file = myFixture.addFileToProject(name, markedSource.replace("<caret>", ""));
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        myFixture.getEditor().getCaretModel().moveToOffset(offset);
        assertEquals(expected, lookupNames());
    }
}
