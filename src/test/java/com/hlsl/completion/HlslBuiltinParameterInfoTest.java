package com.hlsl.completion;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Parameter info for what the compiler provides: the methods of the built-in resource types, found through the
 * type of the receiver, and the intrinsic functions.
 */
public class HlslBuiltinParameterInfoTest {

    private static final String CARET = "<caret>";

    private static final String RESOURCES = """
            struct Particle { float3 Position; };
            Texture2D<float4> gColor : register(t0);
            Texture2D gPlain : register(t1);
            Texture2D gMaps[4] : register(t2);
            RWStructuredBuffer<Particle> gParticles : register(u0);
            AppendStructuredBuffer<Particle> gEmitted : register(u1);
            SamplerState gSampler : register(s0);
            float3 Sample(float2 uv) { return 0; }
            """;

    private static HlslCompletionAnalysis.CallInfo callAt(String body) {
        String text = RESOURCES + "void f(uint i)\n{\n    uint w, h, levels;\n    " + body + "\n}\n";
        int offset = text.indexOf(CARET);
        assertTrue("the snippet has no caret: " + body, offset >= 0);
        return HlslCompletionAnalysis.callAt(text.replace(CARET, ""), offset, List.of());
    }

    private static List<String> labels(HlslCompletionAnalysis.CallInfo call) {
        assertNotNull("no parameter info", call);
        return call.signatures().stream().map(HlslCompletionAnalysis.Signature::label).toList();
    }

    /** The reported case: nothing was shown inside the parentheses of GetDimensions. */
    @Test
    public void getDimensionsOfATextureOffersBothForms() {
        HlslCompletionAnalysis.CallInfo call = callAt("gColor.GetDimensions(" + CARET + ");");
        assertEquals("GetDimensions", call.name());
        assertEquals(List.of(
                "GetDimensions(out uint width, out uint height)",
                "GetDimensions(uint mipLevel, out uint width, out uint height, out uint numberOfLevels)"),
                labels(call));
        assertEquals(0, call.parameterIndex());
        assertEquals(1, callAt("gColor.GetDimensions(w, " + CARET + ");").parameterIndex());
    }

    @Test
    public void eachResourceTypeHasItsOwnForms() {
        assertEquals(List.of("GetDimensions(out uint numStructs, out uint stride)"),
                labels(callAt("gParticles.GetDimensions(" + CARET + ");")));
        assertEquals("an element of an array of textures is a texture",
                List.of("GetDimensions(out uint width, out uint height)",
                        "GetDimensions(uint mipLevel, out uint width, out uint height, out uint numberOfLevels)"),
                labels(callAt("gMaps[i].GetDimensions(" + CARET + ");")));
    }

    @Test
    public void theElementTypeOfTheReceiverTakesThePlaceOfT() {
        assertEquals(List.of("Append(Particle value)"), labels(callAt("gEmitted.Append(" + CARET + ");")));
        HlslCompletionAnalysis.Signature sample = callAt("float4 c = gPlain.Sample(" + CARET + ");").signatures().get(0);
        assertEquals("Sample(SamplerState s, float2 location)", sample.label());
        assertEquals("a texture without a type argument holds float4", "float4", sample.returnType());
    }

    @Test
    public void aMethodIsNeverConfusedWithAGlobalFunctionOfTheSameName() {
        assertEquals(List.of("Sample(SamplerState s, float2 location)"),
                labels(callAt("float4 c = gColor.Sample(" + CARET + ");")));
        assertEquals(List.of("Sample(float2 uv)"), labels(callAt("float3 c = Sample(" + CARET + ");")));
    }

    @Test
    public void anIntrinsicFunctionShowsItsForm() {
        HlslCompletionAnalysis.CallInfo call = callAt("float3 c = lerp(w, h, " + CARET + ");");
        assertEquals(List.of("lerp(T x, T y, T s)"), labels(call));
        assertEquals(2, call.parameterIndex());
        assertEquals(List.of("sincos(T x, out T s, out T c)"), labels(callAt("sincos(w, " + CARET + ");")));
    }

    @Test
    public void unknownReceiversMethodsAndConstructorsShowNothing() {
        assertNull("an unknown receiver has no methods", callAt("gMissing.GetDimensions(" + CARET + ");"));
        assertNull("a texture has no such method", callAt("gColor.Append(" + CARET + ");"));
        assertNull("a constructor is not a call", callAt("float4 c = float4(" + CARET + ");"));
        assertNull("after the closing parenthesis there is no call", callAt("gColor.GetDimensions(w, h)" + CARET + ";"));
    }
}
