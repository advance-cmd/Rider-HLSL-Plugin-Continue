package com.hlsl.completion;

import com.hlsl.ShaderFixtures;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

/** Parameter info reads calls through the same declaration reader as completion. */
public class HlslParameterInfoTest {

    private static final String CARET = "<caret>";

    /** The real declarations below come from a shader source that is not in the repository. */
    @org.junit.Before
    public void skipWhenTheShadersAreNotAvailable() {
        org.junit.Assume.assumeTrue("the real shader sources are not available", ShaderFixtures.available());
    }

    /** Includes for the synthetic cases below. */
    private static final String LIGHTING = """
            float3 ComputePointLight(Light L, MaterialConstants mat, float3 pos, float3 normal, float3 toEye)
            {
                return float3(0, 0, 0);
            }

            float3 ApplyFog(float3 color, float distToEye, float4 fogColor, float fogStart, float fogRange)
            {
                return color;
            }

            float3 Sample(float2 uv, Texture2D tex) { return 0; }
            float3 Sample(float2 uv) { return 0; }

            float4 Blend(float4 a, float4 b = float4(0, 0, 0, 1)) { return a + b; }
            float4 Vertex(float3 pos : POSITION, out float2 uv : TEXCOORD) { return 0; }
            """;

    /** Removes the {@code <caret>} marker and reports the call at that offset. */
    private static HlslCompletionAnalysis.CallInfo callAt(String pattern) {
        int offset = pattern.indexOf(CARET);
        assertTrue("the snippet has no caret: " + pattern, offset >= 0);
        return HlslCompletionAnalysis.callAt(pattern.replace(CARET, ""), offset, List.of(LIGHTING));
    }

    @Test
    public void reportsTheCalleeAndTheArgumentUnderTheCaret() {
        HlslCompletionAnalysis.CallInfo first = callAt("float3 c = ApplyFog(<caret>color);");
        assertNotNull("a call to an included function must be recognized", first);
        assertEquals("ApplyFog", first.name());
        assertEquals(0, first.parameterIndex());
        assertEquals(1, first.signatures().size());
        assertEquals("ApplyFog(float3 color, float distToEye, float4 fogColor, float fogStart, float fogRange)",
                first.signatures().get(0).label());

        HlslCompletionAnalysis.CallInfo second = callAt("float3 c = ApplyFog(color, <caret>dist);");
        assertNotNull(second);
        assertEquals("the comma count must select the parameter", 1, second.parameterIndex());
    }

    @Test
    public void nestedCallsAndCommasInsideArgumentsDoNotShiftTheParameterIndex() {
        HlslCompletionAnalysis.CallInfo call = callAt("float3 c = ApplyFog(float3(1, 1, 1), <caret>depth);");
        assertNotNull(call);
        assertEquals("ApplyFog", call.name());
        assertEquals("commas inside float3(...) must not count", 1, call.parameterIndex());
    }

    @Test
    public void caretOutsideTheCallProducesNothing() {
        assertNull("after the closing parenthesis there is no call context",
                callAt("float3 c = ApplyFog(color)<caret>;"));
        assertNull("inside the callee name there is no argument context",
                callAt("float3 c = Apply<caret>Fog(color);"));
    }

    @Test
    public void everyVisibleOverloadIsOfferedAndHighlightedPositionsAreExact() {
        HlslCompletionAnalysis.CallInfo call = callAt("float3 d = Sample(<caret>uv);");
        assertNotNull(call);
        assertEquals("both overloads must be offered", 2, call.signatures().size());

        HlslCompletionAnalysis.Signature twoParameters = call.signatures().get(0);
        assertEquals("Sample(float2 uv, Texture2D tex)", twoParameters.label());
        assertEquals(7, twoParameters.parameterRange(0).getStartOffset());
        assertEquals(16, twoParameters.parameterRange(0).getEndOffset());

        HlslCompletionAnalysis.Signature oneParameter = call.signatures().get(1);
        assertEquals("Sample(float2 uv)", oneParameter.label());
        assertEquals(7, oneParameter.parameterRange(0).getStartOffset());
        assertEquals("the range stops before the closing parenthesis", 16,
                oneParameter.parameterRange(0).getEndOffset());
    }

    @Test
    public void defaultValuesAndSemanticsAreSkipped() {
        HlslCompletionAnalysis.Signature blend = callAt("float4 x = Blend(<caret>a);").signatures().get(0);
        assertEquals("a default value must not become a parameter",
                "Blend(float4 a, float4 b)", blend.label());
        HlslCompletionAnalysis.Signature vertex = callAt("float4 x = Vertex(<caret>pos);").signatures().get(0);
        assertEquals("semantics are not part of the parameter list",
                "Vertex(float3 pos, float2 uv)", vertex.label());
    }

    @Test
    public void controlFlowParenthesesConstructorsAndUnknownCalleesAreNotCalls() {
        assertNull("if (...) is not a call", callAt("if (<caret>depth > 0) { }"));
        assertNull("a value constructor is not a call", callAt("float4 c = float4(<caret>0, 0, 0, 1);"));
        assertNull("an unknown function produces nothing", callAt("ReadGBuffer(<caret>pixel);"));
    }

    @Test
    public void declarationListsAreNotCalls() {
        assertNull("an incomplete declaration must not look like a call",
                callAt("float3 Combine(float3 a, <caret>float3 b) { return a + b; }"));
        assertNull("nor does a prototype", callAt("float3 Combine(<caret>float3 a);"));
    }

    @Test
    public void theRealApplyFogDeclarationIsDescribedWithAllFiveParameters() throws IOException {
        String lighting = loadLightingUtil();
        String shader = "res.rgb = ApplyFog(res.rgb, distToEye, FogColorAt(toEye), <caret>);";
        HlslCompletionAnalysis.CallInfo call = HlslCompletionAnalysis.callAt(
                shader.replace(CARET, ""), shader.indexOf(CARET), List.of(lighting));
        assertNotNull("the real ApplyFog declaration must be found", call);
        HlslCompletionAnalysis.Signature signature = call.signatures().get(0);
        assertEquals("ApplyFog", signature.name());
        assertEquals(5, signature.parameters().size());
        assertEquals("float3", signature.parameters().get(0).type());
        assertEquals("color", signature.parameters().get(0).name());
        assertEquals("the caret sits in the fourth argument", 3, call.parameterIndex());
    }

    @Test
    public void theRealComputePointLightDeclarationIsDescribed() throws IOException {
        String lighting = loadLightingUtil();
        String shader = "float3 c = ComputePointLight(<caret>);";
        HlslCompletionAnalysis.CallInfo call = HlslCompletionAnalysis.callAt(
                shader.replace(CARET, ""), shader.indexOf(CARET), List.of(lighting));
        assertNotNull(call);
        assertEquals("ComputePointLight(Light L, MaterialConstants mat, float3 pos, float3 normal, float3 toEye)",
                call.signatures().get(0).label());
    }

    private static String loadLightingUtil() throws IOException {
        return ShaderFixtures.requireLightingUtil();
    }
}
