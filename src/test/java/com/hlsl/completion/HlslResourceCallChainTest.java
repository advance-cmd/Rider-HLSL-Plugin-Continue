package com.hlsl.completion;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Members of a resource call's result, as in {@code gGBuffer1.Load(int3(pixel, 0)).} */
public class HlslResourceCallChainTest {

    private static final String SOURCE = """
            Texture2D<float4> gGBuffer1 : register(t1);
            Texture2D<float> gDepthMap : register(t2);
            RWTexture2D<float4> gOutput : register(u0);

            float4 PS(float2 pixel) : SV_Target
            {
                float4 color = gGBuffer1.Load(int3(pixel * 2, 0));
                float depth = gDepthMap.Load(int3(pixel, 0));
                float4 direct = gGBuffer1.Sample(sampler0, pixel);
                float4 indexed = gGBuffer1[uint2(pixel)];
                return color;
            }
            """;

    /** The reported case: the caret right after the argument list of a Load call. */
    @Test
    public void loadResultOffersSwizzles() {
        HlslCompletionAnalysis.Result result = analyzeAfter("gGBuffer1.Load(int3(pixel * 2, 0))");
        assertEquals(HlslCompletionAnalysis.Context.MEMBER, result.context());
        assertTrue("expected swizzles after Load, got " + names(result),
                names(result).contains("xyz") && names(result).contains("rgba"));
    }

    /** The element type decides the swizzle alphabet: a Texture2D<float> yields a scalar. */
    @Test
    public void loadResultOfAScalarTextureOffersScalarSwizzles() {
        HlslCompletionAnalysis.Result result = analyzeAfter("gDepthMap.Load(int3(pixel, 0))");
        assertEquals(HlslCompletionAnalysis.Context.MEMBER, result.context());
        assertTrue("expected the scalar swizzles after Load, got " + names(result),
                names(result).contains("xxx") && !names(result).contains("xyz"));
    }

    /** A Sample call keeps working. */
    @Test
    public void sampleResultStillOffersSwizzles() {
        HlslCompletionAnalysis.Result result = analyzeAfter("gGBuffer1.Sample(sampler0, pixel)");
        assertEquals(HlslCompletionAnalysis.Context.MEMBER, result.context());
        assertTrue("expected swizzles after Sample, got " + names(result), names(result).contains("xyz"));
    }

    /** A swizzle of a Load result can itself be swizzled again. */
    @Test
    public void swizzleOfALoadResultOffersSwizzles() {
        // The source already reads the swizzle, so the caret goes right after it.
        String marked = SOURCE.replace("gGBuffer1.Load(int3(pixel * 2, 0))",
                "gGBuffer1.Load(int3(pixel * 2, 0)).xyz<caret>");
        int caret = marked.indexOf("<caret>");
        HlslCompletionAnalysis.Result result =
                HlslCompletionAnalysis.analyze(marked.replace("<caret>", ""), caret, List.of());
        assertEquals(HlslCompletionAnalysis.Context.MEMBER, result.context());
        assertTrue("expected swizzles after .xyz, got " + names(result), names(result).contains("xyz"));
    }

    /** Indexing a resource yields its element type. */
    @Test
    public void indexingAResourceOffersSwizzles() {
        HlslCompletionAnalysis.Result result = analyzeAfter("gGBuffer1[uint2(pixel)]");
        assertEquals(HlslCompletionAnalysis.Context.MEMBER, result.context());
        assertTrue("expected swizzles after an index, got " + names(result), names(result).contains("xyz"));
    }

    private static HlslCompletionAnalysis.Result analyzeAfter(String expression) {
        String marked = SOURCE.replace(expression, expression + ".<caret>");
        int caret = marked.indexOf("<caret>");
        return HlslCompletionAnalysis.analyze(marked.replace("<caret>", ""), caret, List.of());
    }

    private static String names(HlslCompletionAnalysis.Result result) {
        return result.candidates().toString();
    }
}
