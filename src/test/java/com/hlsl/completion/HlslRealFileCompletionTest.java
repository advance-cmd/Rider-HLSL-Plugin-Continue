package com.hlsl.completion;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.*;

/** End-to-end reproduction using the real LearnDX12 shader files. */
public class HlslRealFileCompletionTest {

    @Test
    public void gPassMemberCompletionWithTypedPrefix() throws IOException {
        String source = deferredWithCaret("gPass.Lights[kFirstExtraLight + id]", "gPass.inv");
        HlslCompletionAnalysis.Result result = HlslCompletionAnalysis.analyze(source, source.indexOf("<caret>"),
                List.of(load("LightingUtil.hlsl")));
        assertEquals(HlslCompletionAnalysis.Context.MEMBER, result.context());
        String names = result.candidates().toString();
        assertTrue("expected member completion at gPass.inv, got " + names,
                names.contains("InvViewProj") && names.contains("EyePosW") && names.contains("Lights"));
    }

    @Test
    public void gPassMemberCompletionBareDot() throws IOException {
        String source = deferredWithCaret("gPass.Lights[kFirstExtraLight + id]", "gPass.");
        HlslCompletionAnalysis.Result result = HlslCompletionAnalysis.analyze(source, source.indexOf("<caret>"),
                List.of(load("LightingUtil.hlsl")));
        assertEquals(HlslCompletionAnalysis.Context.MEMBER, result.context());
        String names = result.candidates().toString();
        assertTrue("expected member completion at gPass., got " + names,
                names.contains("InvViewProj") && names.contains("EyePosW") && names.contains("Lights"));
    }

    @Test
    public void includeParsingFindsLightingUtil() throws IOException {
        List<String> includes = HlslIncludeIndex.parseIncludes(load("Deferred.hlsl"));
        assertTrue("Deferred.hlsl must resolve LightingUtil.hlsl, got " + includes,
                includes.contains("LightingUtil.hlsl"));
    }

    private static String deferredWithCaret(String needle, String replacement) throws IOException {
        String deferred = load("Deferred.hlsl");
        int at = deferred.indexOf(needle);
        assertTrue("needle not found: " + needle, at >= 0);
        return deferred.substring(0, at) + replacement + "<caret>" + deferred.substring(at + needle.length());
    }

    private static String load(String name) throws IOException {
        try (InputStream in = HlslRealFileCompletionTest.class.getResourceAsStream("/samples/" + name)) {
            assertNotNull("missing test resource: " + name, in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}