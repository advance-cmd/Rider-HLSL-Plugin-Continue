package com.hlsl.completion;

import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class HlslCompletionAnalysisTest {
    private static final String LIGHTING = """
            #define MaxLights 100
            struct Light { float3 Strength; float FalloffStart; float3 Direction;
                           float FalloffEnd; float3 Position; float SpotPower; };
            struct PassConstants { float4x4 InvViewProj; float3 EyePosW; Light Lights[MaxLights]; };
            float3 ComputeLight(Light light) { float hiddenInHeader = 1; return light.Strength; }
            """;

    @Test
    public void constantBufferUsesIncludedStructureFields() {
        HlslCompletionAnalysis.Result result = analyze("""
                #include "LightingUtil.hlsl"
                ConstantBuffer<PassConstants> gPass : register(b1);
                void VS(uint id) { Light L = gPass.<caret>Lights[id]; }
                """, LIGHTING);
        assertEquals(HlslCompletionAnalysis.Context.MEMBER, result.context());
        assertEquals(Set.of("InvViewProj", "EyePosW", "Lights"), names(result));
    }

    @Test
    public void followsArrayIndexAndConstantBufferMemberChain() {
        HlslCompletionAnalysis.Result result = analyze("""
                ConstantBuffer<PassConstants> gPass : register(b1);
                void VS(uint id) { gPass.Lights[1 + id].<caret>; }
                """, LIGHTING);
        assertEquals(Set.of("Strength", "FalloffStart", "Direction", "FalloffEnd", "Position", "SpotPower"), names(result));
    }

    @Test
    public void localLightUsesItsDeclaredType() {
        HlslCompletionAnalysis.Result result = analyze("""
                ConstantBuffer<PassConstants> gPass : register(b1);
                void VS(uint id) { Light L = gPass.Lights[id]; L.<caret>; }
                """, LIGHTING);
        assertTrue(names(result).containsAll(Set.of("Strength", "Position", "FalloffEnd")));
        assertFalse(names(result).contains("Lights"));
    }

    @Test
    public void vectorSwizzlesRespectDimensionAndAlphabet() {
        Set<String> names = names(analyze("void f() { float2 uv; uv.<caret>; }"));
        assertTrue(names.containsAll(Set.of("x", "y", "xy", "yx", "rg", "xxxx")));
        assertFalse(names.contains("z"));
        assertFalse(names.contains("rgb"));
        assertFalse(names.contains("xr"));
    }

    @Test
    public void unknownMemberReceiverDoesNotOfferGlobals() {
        HlslCompletionAnalysis.Result result = analyze("float globalValue; void f() { unknown.<caret>; }");
        assertEquals(HlslCompletionAnalysis.Context.MEMBER, result.context());
        assertTrue(result.candidates().isEmpty());
    }

    @Test
    public void arrayNeedsIndexBeforeItsElementMembers() {
        assertTrue(analyze("Light lights[8]; void f() { lights.<caret>; }", LIGHTING).candidates().isEmpty());
    }

    @Test
    public void onlyVisibleDeclarationsBecomeCandidates() {
        Set<String> names = names(analyze("""
                float globalValue;
                struct Vertex { float privateField; };
                float Previous(float previousParameter) { float previousLocal; return missingCall(typo); }
                void Current(float currentParameter) {
                    float currentLocal;
                    { float expiredLocal; }
                    <caret>
                    float laterLocal;
                }
                """));
        assertTrue(names.containsAll(Set.of("globalValue", "Vertex", "Previous", "Current", "currentParameter", "currentLocal")));
        assertFalse(names.contains("privateField"));
        assertFalse(names.contains("previousParameter"));
        assertFalse(names.contains("previousLocal"));
        assertFalse(names.contains("expiredLocal"));
        assertFalse(names.contains("laterLocal"));
        assertFalse(names.contains("missingCall"));
        assertFalse(names.contains("typo"));
    }

    @Test
    public void localShadowingControlsMemberType() {
        Set<String> names = names(analyze("float4 value; void f() { float2 value; value.<caret>; }"));
        assertTrue(names.contains("xy"));
        assertFalse(names.contains("z"));
    }

    @Test
    public void loopVariableDoesNotEscapeLoop() {
        assertTrue(names(analyze("void f() { for (int i = 0; i < 4; ++i) { <caret> } }" )).contains("i"));
        assertFalse(names(analyze("void f() { for (int i = 0; i < 4; ++i) { } <caret> }" )).contains("i"));
    }

    @Test
    public void cbufferFieldsAreVisibleButStructFieldsAreNot() {
        Set<String> names = names(analyze("cbuffer Frame { float exposure; }; struct S { float hidden; }; <caret>"));
        assertTrue(names.contains("exposure"));
        assertFalse(names.contains("hidden"));
        assertFalse(names.contains("Frame"));
    }

    @Test
    public void declarationSemanticsAreSeparateFromTernaryAndCaseColons() {
        assertEquals(HlslCompletionAnalysis.Context.SEMANTIC, analyze("struct S { float4 pos : <caret>; };").context());
        assertEquals(HlslCompletionAnalysis.Context.SEMANTIC, analyze("float4 main() : <caret> { return 0; }").context());
        assertEquals(HlslCompletionAnalysis.Context.CODE, analyze("void f() { float x = flag ? 1 : <caret>; }").context());
        assertEquals(HlslCompletionAnalysis.Context.CODE, analyze("void f() { switch (x) { case 1: <caret>; } }").context());
    }

    @Test
    public void suppressesCommentStringAndDirectiveCompletion() {
        for (String source : List.of("// comment <caret>", "/* comment <caret> */", "\"text <caret>\"", "#include \"<caret>\"", "#define X <caret>")) {
            assertEquals(source, HlslCompletionAnalysis.Context.SUPPRESSED, analyze(source).context());
        }
        assertEquals(HlslCompletionAnalysis.Context.CODE, analyze("/* done */ <caret>").context());
    }

    @Test
    public void includedFilesExportDeclarationsAndLiveMacrosOnly() {
        Set<String> exports = HlslCompletionAnalysis.collectExportedIdentifiers("""
                #define LIVE_MACRO(x) fakeCall(x)
                #define REMOVED_MACRO 1
                #undef REMOVED_MACRO
                // #define COMMENTED 1
                struct S { float field; };
                cbuffer Frame { float exposure; };
                Texture2D<float4> image;
                float globalA, globalB;
                float Fn(float parameter) { float local; return unknownCall(parameter); }
                """);
        assertEquals(Set.of("LIVE_MACRO", "S", "exposure", "image", "globalA", "globalB", "Fn"), exports);
    }

    @Test
    public void includeLocalsNeverLeakIntoCurrentFile() {
        Set<String> names = names(analyze("void f() { <caret> }", LIGHTING));
        assertTrue(names.containsAll(Set.of("Light", "PassConstants", "ComputeLight", "MaxLights")));
        assertFalse(names.contains("light"));
        assertFalse(names.contains("hiddenInHeader"));
        assertFalse(names.contains("Strength"));
    }

    private static HlslCompletionAnalysis.Result analyze(String markedSource, String... includes) {
        int offset = markedSource.indexOf("<caret>");
        assertTrue(offset >= 0);
        String source = markedSource.replace("<caret>", "");
        return HlslCompletionAnalysis.analyze(source, offset, List.of(includes));
    }

    private static Set<String> names(HlslCompletionAnalysis.Result result) {
        return result.candidates().stream().map(HlslCompletionAnalysis.Candidate::name).collect(Collectors.toSet());
    }
}
