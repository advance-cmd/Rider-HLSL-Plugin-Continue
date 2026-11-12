package com.hlsl.validation;

import com.hlsl.UnityTestSources;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * A .urtshader is compiled the way Unity's importer compiles it: the backend macro in front, the ray generation
 * shader behind, as a library, with the package files found by their Packages/ paths.
 */
public class HlslUrtShaderCompileTest {

    private static final String SHADER_PATH = "Assets/Rendering/Visibility.urtshader";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void theWrapperIsWhatUnityCompiles() {
        assertEquals("#define UNIFIED_RT_BACKEND_HARDWARE\n#line 1\nvoid f() {}\n#include \""
                        + HlslDxcAnnotator.URT_HARDWARE_RAYGEN + "\"\n",
                HlslDxcAnnotator.urtWrapper("void f() {}"));
    }

    @Test
    public void aUrtShaderIsALibraryAndFindsThePackages() {
        HlslDxcAnnotator.CollectedInfo info = info(UnityTestSources.SHADER, settings("dxc"));
        Path unity = Path.of("unity");
        List<String> command = HlslDxcAnnotator.buildCommand(info, "dxc", Path.of("source.hlsl"), Path.of("out.dxil"), unity);
        assertEquals(HlslDxcAnnotator.URT_PROFILE, command.get(command.indexOf("-T") + 1));
        assertFalse("a library has no entry point", command.contains("-E"));
        assertTrue(command.contains("-Wno-unknown-pragmas"));
        int last = command.lastIndexOf("-I");
        assertEquals("the copied packages are searched last", unity.toString(), command.get(last + 1));
    }

    @Test
    public void aProfilePragmaStillWins() {
        HlslDxcAnnotator.CollectedInfo info = info("// #pragma hlsl profile lib_6_6\n" + UnityTestSources.SHADER, settings("dxc"));
        List<String> command = HlslDxcAnnotator.buildCommand(info, "dxc", Path.of("source.hlsl"), Path.of("out.dxil"));
        assertEquals("lib_6_6", command.get(command.indexOf("-T") + 1));
    }

    /** With the real DXC: the shader compiles cleanly, as it does in Unity. */
    @Test
    public void theShaderCompilesWithoutDiagnostics() {
        HlslDxcAnnotator.ValidationResult result = new HlslDxcAnnotator().doAnnotate(
                info(UnityTestSources.SHADER, settings(requireDxc())));
        assertEquals("no diagnostics expected, got " + result.diagnostics(), List.of(), result.diagnostics());
    }

    /** A mistake is reported on its own line, not one line further down for the macro in front. */
    @Test
    public void aMistakeIsReportedOnItsLine() {
        String broken = UnityTestSources.SHADER.replace("    _Output[", "    float broken = undefinedThing;\n    _Output[");
        HlslDxcAnnotator.ValidationResult result = new HlslDxcAnnotator().doAnnotate(info(broken, settings(requireDxc())));
        HlslDxcDiagnostics.Diagnostic error = result.diagnostics().stream()
                .filter(d -> d.message().contains("undefinedThing")).findFirst().orElse(null);
        assertNotNull("the mistake is not reported: " + result.diagnostics(), error);
        assertEquals(UnityTestSources.TRACE_LINE + 1, error.line());
    }

    private static String requireDxc() {
        String dxc = HlslDxcSettings.autoDetectDxc();
        Assume.assumeTrue("DXC is not installed", dxc != null);
        return dxc;
    }

    private static HlslDxcSettings.State settings(String dxc) {
        HlslDxcSettings.State settings = new HlslDxcSettings.State();
        settings.dxcPath = dxc;
        settings.autoDetectBuildMacros = false;
        return settings;
    }

    /** The shader of the test project, with the package files it reaches laid out by their Packages/ paths. */
    private HlslDxcAnnotator.CollectedInfo info(String shaderText, HlslDxcSettings.State settings) {
        Path root;
        try {
            root = folder.newFolder("Project").toPath();
            // DXC runs in the shader's directory.
            Files.createDirectories(root.resolve(SHADER_PATH).getParent());
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        List<HlslDxcAnnotator.ContextFile> unityFiles = new ArrayList<>();
        String cache = UnityTestSources.CACHE_DIRECTORY;
        for (Map.Entry<String, String> file : UnityTestSources.project(SHADER_PATH).entrySet()) {
            if (!file.getKey().startsWith(cache)) continue;
            String projectPath = "Packages/" + UnityTestSources.PACKAGE + "/" + file.getKey().substring(cache.length());
            unityFiles.add(new HlslDxcAnnotator.ContextFile(path(root.resolve(file.getKey())), projectPath, file.getValue()));
        }
        return new HlslDxcAnnotator.CollectedInfo(shaderText, path(root.resolve(SHADER_PATH)), 1, settings,
                List.of(), List.of(), null, unityFiles);
    }

    private static String path(Path path) {
        return path.toString().replace(File.separatorChar, '/');
    }
}
