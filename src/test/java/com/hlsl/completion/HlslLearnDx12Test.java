package com.hlsl.completion;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Reproduction against the UNMODIFIED LearnDX12 shader sources (src/test/resources/dx12).
 * These are the files the user actually edits, so they must not be trimmed to make a test pass.
 */
public class HlslLearnDx12Test {

    private static final String FILE = "Deferred.hlsl";

    @Test
    public void gPassTypedPrefixOnRealFile() throws IOException {
        HlslCompletionAnalysis.Result result = analyzeAt("gPass.AmbientLight.rgb", "gPass.inv<caret>");
        assertEquals(HlslCompletionAnalysis.Context.MEMBER, result.context());
        String names = result.candidates().toString();
        assertTrue("expected InvViewProj at gPass.inv, got " + names, names.contains("InvViewProj"));
    }

    @Test
    public void gPassBareDotOnRealFile() throws IOException {
        HlslCompletionAnalysis.Result result = analyzeAt("gPass.AmbientLight.rgb", "gPass.<caret>");
        assertEquals(HlslCompletionAnalysis.Context.MEMBER, result.context());
        String names = result.candidates().toString();
        assertTrue("expected InvViewProj/EyePosW/Lights at gPass., got " + names,
                names.contains("InvViewProj") && names.contains("EyePosW") && names.contains("Lights"));
    }

    @Test
    public void gPassInsideLightVolumeFunction() throws IOException {
        HlslCompletionAnalysis.Result result = analyzeAt("gPass.EyePosW - posW", "gPass.inv<caret>");
        assertEquals(HlslCompletionAnalysis.Context.MEMBER, result.context());
        assertTrue("expected InvViewProj inside PS_LightVolume, got " + result.candidates(),
                result.candidates().toString().contains("InvViewProj"));
    }

    @Test
    public void textureBuiltinMethodCompletion() throws IOException {
        HlslCompletionAnalysis.Result result = analyzeAt("gDepthMap.GetDimensions(width, height);",
                "gDepthMap.Get<caret>Dimensions(width, height);");
        assertEquals(HlslCompletionAnalysis.Context.MEMBER, result.context());
        String names = result.candidates().toString();
        assertTrue("expected GetDimensions on Texture2D<float>, got " + names, names.contains("GetDimensions"));
    }

    @Test
    public void textureBuiltinBareDotCompletion() throws IOException {
        HlslCompletionAnalysis.Result result = analyzeAt("gDepthMap.GetDimensions(width, height);",
                "gDepthMap.<caret>GetDimensions(width, height);");
        assertEquals(HlslCompletionAnalysis.Context.MEMBER, result.context());
        String names = result.candidates().toString();
        assertTrue("expected GetDimensions/Sample/Load on Texture2D, got " + names,
                names.contains("GetDimensions") && names.contains("Sample") && names.contains("Load"));
    }

    @Test
    public void declaredVariableNameIsNotCompleted() throws IOException {
        HlslCompletionAnalysis.Result result = analyzeAt("int2(pin.PosH.xy)", "int2 pix<caret>el = int2(pin.PosH.xy)");
        assertEquals("typing a variable name after a type must not offer completions",
                HlslCompletionAnalysis.Context.SUPPRESSED, result.context());
    }

    @Test
    public void declaredParameterNameIsNotCompleted() throws IOException {
        HlslCompletionAnalysis.Result result = analyzeAt("int2 pixel, out MaterialConstants mat",
                "int2 pix<caret>el, out MaterialConstants mat");
        assertEquals("typing a parameter name after a type must not offer completions",
                HlslCompletionAnalysis.Context.SUPPRESSED, result.context());
    }

    @Test
    public void customStructVariableNameIsNotCompleted() throws IOException {
        HlslCompletionAnalysis.Result result = analyzeAt("MaterialConstants mat;", "MaterialConstants ma<caret>t;");
        assertEquals("typing a variable name after a struct type must not offer completions",
                HlslCompletionAnalysis.Context.SUPPRESSED, result.context());
    }

    /** Replaces the first occurrence of {@code needle} and places the caret at the end of {@code replacement}. */
    private static HlslCompletionAnalysis.Result analyzeAt(String needle, String replacement) throws IOException {
        String source = load(FILE);
        int at = source.indexOf(needle);
        assertTrue("needle not found in " + FILE + ": " + needle, at >= 0);
        String patched = source.substring(0, at) + replacement + source.substring(at + needle.length());
        int caret = patched.indexOf("<caret>");
        assertTrue("caret missing in replacement", caret >= 0);
        String withoutCaret = patched.substring(0, caret) + patched.substring(caret + "<caret>".length());
        return HlslCompletionAnalysis.analyze(withoutCaret, caret, List.of(load("LightingUtil.hlsl")));
    }

    private static String load(String name) throws IOException {
        try (InputStream in = HlslLearnDx12Test.class.getResourceAsStream("/dx12/" + name)) {
            assertNotNull("missing test resource: /dx12/" + name, in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
