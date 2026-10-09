package com.hlsl.validation;

import org.junit.Test;

import static org.junit.Assert.*;

public class HlslDxcSourceTest {
    @Test
    public void honorsCommentPragmasAndHlslVersion() {
        HlslDxcSource source = new HlslDxcSource("// #pragma hlsl profile vs_6_6\n// #pragma hlsl entry VS\n// #pragma hlsl hv 2018\n");
        assertEquals("vs_6_6", source.option("profile", "ps_6_6"));
        assertEquals("VS", source.option("entry", "main"));
        assertEquals("2018", source.option("hv", "2021"));
    }

    @Test
    public void examplesInsideBlockCommentsDoNotConfigureTheCompiler() {
        HlslDxcSource source = new HlslDxcSource("/*\n#pragma hlsl profile cs_6_6\n// #pragma hlsl entry Example\n*/\nfloat4 PS() : SV_Target { return 0; }");
        assertEquals("ps_6_6", source.option("profile", "ps_6_6"));
        assertFalse(source.hasOption("entry"));
    }

    @Test
    public void removesPluginPragmasWithoutChangingOffsetsOrOtherDirectives() {
        String text = "#pragma hlsl entry PS\r\n#pragma pack_matrix(row_major)\r\nfloat4 PS() : SV_Target { return 0; }";
        String compiled = new HlslDxcSource(text).compilerText();
        assertEquals(text.length(), compiled.length());
        assertEquals(text.indexOf("float4"), compiled.indexOf("float4"));
        assertFalse(compiled.contains("#pragma hlsl"));
        assertTrue(compiled.contains("#pragma pack_matrix(row_major)"));
        assertEquals(text.chars().filter(c -> c == '\n').count(), compiled.chars().filter(c -> c == '\n').count());
    }

    @Test
    public void preservesBlockCommentBoundariesAroundPragmas() {
        String text = "/* explanation\n*/ #pragma hlsl entry PS /* more\ninformation */\nfloat4 PS() : SV_Target { return 0; }";
        String compiled = new HlslDxcSource(text).compilerText();
        assertTrue(compiled.startsWith("/* explanation\n*/"));
        assertTrue(compiled.contains("/* more\ninformation */"));
        assertEquals("PS", new HlslDxcSource(text).option("entry", "main"));
    }

    @Test
    public void includeFilesRequireOptInOrAnExplicitCompilePragma() {
        HlslDxcSource source = new HlslDxcSource("float3 helper(float3 x) { return x; }");
        assertFalse(source.shouldValidate("Lighting.HLSLI", false));
        assertFalse(source.shouldValidate("Lighting.fxh", false));
        assertFalse(source.shouldValidate("Lighting.ush", false));
        assertTrue(source.shouldValidate("Deferred.hlsl", false));
        assertTrue(source.shouldValidate("Lighting.hlsli", true));
        assertTrue(new HlslDxcSource("// #pragma hlsl profile lib_6_6").shouldValidate("Lighting.hlsli", false));
        assertFalse(new HlslDxcSource("// #pragma hlsl hv 2018").shouldValidate("Lighting.hlsli", false));
    }
}
