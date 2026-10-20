package com.hlsl.validation;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Compiles a helper through its root shader with the real DXC. Skipped when no DXC is installed, because only
 * the compiler can tell whether the chain is put together the way the build puts it together.
 */
public class HlslDxcRootCompileTest {

    private static final String LIGHTING_UTIL = """
            struct PassConstants
            {
                float4x4 ViewProj;
            };
            #include "Shadow.hlsl"
            """;
    private static final String SHADOW = """
            float ViewZ(PassConstants pass, float3 posW)
            {
                return mul(float4(posW,1),pass.ViewProj).w;
            }
            """;
    private static final String TRIANGLE = """
            #include "LightingUtil.hlsl"
            ConstantBuffer<PassConstants> gPass : register(b1);
            float4 main(float4 posW : POSITION) : SV_Target
            {
                return ViewZ(gPass, posW.xyz);
            }
            """;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private HlslDxcSettings.State settings;
    private Path shaders;

    @Before
    public void setUp() throws IOException {
        String dxc = HlslDxcSettings.autoDetectDxc();
        Assume.assumeTrue("DXC is not installed", dxc != null);
        settings = new HlslDxcSettings.State();
        settings.dxcPath = dxc;
        settings.autoDetectBuildMacros = false;
        shaders = folder.newFolder("HLSL").toPath();
        write("LightingUtil.hlsl", LIGHTING_UTIL);
        write("Shadow.hlsl", SHADOW);
        write("Triangle.hlsl", TRIANGLE);
    }

    /** What the reported problem looked like: compiled on its own, the helper has never seen PassConstants. */
    @Test
    public void onItsOwnTheHelperReportsTheTypeItsIncluderDeclares() {
        HlslDxcAnnotator.ValidationResult result = new HlslDxcAnnotator().doAnnotate(
                new HlslDxcAnnotator.CollectedInfo(SHADOW, path("Shadow.hlsl"), 1, settings));
        assertTrue("expected the standalone error, got " + result.diagnostics(), result.diagnostics().stream()
                .anyMatch(d -> d.message().contains("unknown type name 'PassConstants'")));
    }

    @Test
    public void throughItsRootTheHelperCompilesCleanly() {
        HlslDxcAnnotator.ValidationResult result = new HlslDxcAnnotator().doAnnotate(throughTriangle(SHADOW));
        assertEquals("no diagnostics expected, got " + result.diagnostics(), List.of(), result.diagnostics());
    }

    /** An unsaved edit of the helper is what gets compiled, and its error lands on the helper's own line. */
    @Test
    public void anUnsavedMistakeInTheHelperIsReportedOnItsLine() {
        String edited = SHADOW.replace("return mul(", "float broken = undefinedThing;\n    return mul(");
        HlslDxcAnnotator.ValidationResult result = new HlslDxcAnnotator().doAnnotate(throughTriangle(edited));
        HlslDxcDiagnostics.Diagnostic error = result.diagnostics().stream()
                .filter(d -> d.message().contains("undefinedThing")).findFirst().orElse(null);
        assertNotNull("the unsaved mistake is not reported: " + result.diagnostics(), error);
        assertEquals(3, error.line());
        assertTrue(error.message(), error.message().endsWith("(compiled through Triangle.hlsl)"));
    }

    /** A mistake in the root belongs to the root, which reports it when it is opened itself. */
    @Test
    public void aMistakeInTheRootIsNotReportedOnTheHelper() {
        write("Triangle.hlsl", TRIANGLE.replace("return ViewZ(", "return CalcShadowFactor() + ViewZ("));
        HlslDxcAnnotator.ValidationResult result = new HlslDxcAnnotator().doAnnotate(throughTriangle(SHADOW));
        assertEquals("the root's error must stay on the root, got " + result.diagnostics(), List.of(), result.diagnostics());
    }

    private HlslDxcAnnotator.CollectedInfo throughTriangle(String shadowText) {
        List<HlslDxcAnnotator.ContextFile> context = List.of(
                new HlslDxcAnnotator.ContextFile(path("Triangle.hlsl"), "Triangle.hlsl", read("Triangle.hlsl")),
                new HlslDxcAnnotator.ContextFile(path("LightingUtil.hlsl"), "LightingUtil.hlsl", LIGHTING_UTIL),
                new HlslDxcAnnotator.ContextFile(path("Shadow.hlsl"), "Shadow.hlsl", shadowText));
        return new HlslDxcAnnotator.CollectedInfo(shadowText, path("Shadow.hlsl"), 1, settings, List.of(), context, null);
    }

    private String path(String name) {
        return shaders.resolve(name).toString().replace(File.separatorChar, '/');
    }

    private void write(String name, String text) {
        try {
            Files.writeString(shaders.resolve(name), text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private String read(String name) {
        try {
            return Files.readString(shaders.resolve(name), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
