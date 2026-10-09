package com.hlsl.validation;

import org.junit.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

public class HlslDxcDiagnosticsTest {
    private final Path temporarySource = Path.of("build", "dxc snapshot", "source.hlsl").toAbsolutePath();
    private final Path originalSource = Path.of("build", "shader project", "Deferred.hlsl").toAbsolutePath();

    private List<HlslDxcDiagnostics.Diagnostic> parse(String output, int exitCode) {
        return parse(output, exitCode, true);
    }

    /** Include-file diagnostics are shown only when the user asked to validate include files. */
    private List<HlslDxcDiagnostics.Diagnostic> parse(String output, int exitCode, boolean includeOtherFiles) {
        return HlslDxcDiagnostics.parse(output, exitCode, temporarySource, originalSource.toString(), false,
                includeOtherFiles);
    }

    @Test
    public void includeFileDiagnosticsAreDroppedUnlessIncludeValidationIsEnabled() {
        Path include = originalSource.resolveSibling("LightingUtil.hlsl");
        String output = include + ":37:5: warning: 'NUM_DIR_LIGHTS' is not defined\n"
                + temporarySource + ":3:9: error: use of undeclared identifier 'light'";
        assertEquals(2, parse(output, 1).size());
        List<HlslDxcDiagnostics.Diagnostic> withoutIncludes = parse(output, 1, false);
        assertEquals(1, withoutIncludes.size());
        assertEquals(3, withoutIncludes.get(0).line());
        assertTrue(withoutIncludes.get(0).message().contains("undeclared identifier"));
    }

    @Test
    public void unviableOverloadNotesExplainTheErrorOnTheEditedFile() {
        // The real LearnDX12 case: the call passes a light index where a Light value is required.
        String output = temporarySource + ":127:16: error: no matching function for call to 'ComputePointLight'\n"
                + originalSource.resolveSibling("LightingUtil.hlsl")
                + ":119:8: note: candidate function not viable: no known conversion from 'uint' to 'Light'"
                + " for 1st argument";
        List<HlslDxcDiagnostics.Diagnostic> result = parse(output, 1, false);
        assertEquals("an include-file note must not become its own annotation: " + result, 1, result.size());
        HlslDxcDiagnostics.Diagnostic diagnostic = result.get(0);
        assertEquals(127, diagnostic.line());
        assertEquals("error", diagnostic.severity());
        assertTrue("the reason must be attached to the error: " + diagnostic.message(),
                diagnostic.message().contains("no known conversion from 'uint' to 'Light'"));
    }

    @Test
    public void positionlessCompilerChatterIsNotReportedAsAFileLevelWarning() {        assertTrue(parse("warning: unknown warning option '-Wno-payload-access-warning' [-Wunknown-warning-option]",
                0, false).isEmpty());
        assertEquals(1, parse("dxc.exe: error: invalid profile", 1, false).size());
    }

    @Test
    public void mapsSnapshotAndOriginalFileDiagnosticsToTheirActualLines() {
        List<HlslDxcDiagnostics.Diagnostic> result = parse(temporarySource + ":12:7: error: expected ';'\n"
                + originalSource + ":4:3: warning: implicit truncation", 1);
        assertEquals(2, result.size());
        assertEquals(new HlslDxcDiagnostics.Diagnostic(12, 7, "error", "expected ';'"), result.get(0));
        assertEquals(4, result.get(1).line());
    }

    @Test
    public void includeErrorsNeverUseTheCurrentFilesLineNumbers() {
        Path include = originalSource.resolveSibling("LightingUtil.hlsl");
        List<HlslDxcDiagnostics.Diagnostic> result = parse(include + ":37:5: error: undeclared identifier 'NUM_DIR_LIGHTS'", 1);
        assertEquals(1, result.size());
        assertEquals(0, result.get(0).line());
        assertEquals(0, result.get(0).column());
        assertTrue(result.get(0).message().contains("LightingUtil.hlsl:37:5"));
        assertTrue(result.get(0).message().contains("NUM_DIR_LIGHTS"));
    }

    @Test
    public void relativeIncludePathsAreNotMistakenForSnapshotSource() {
        List<HlslDxcDiagnostics.Diagnostic> result = parse("LightingUtil.hlsl:9:2: error: bad type", 1);
        assertEquals(0, result.get(0).line());
        assertTrue(result.get(0).message().startsWith("LightingUtil.hlsl:9:2:"));
    }

    @Test
    public void recognizesWindowsDriveLetterAndSpacesInPath() {
        List<HlslDxcDiagnostics.Diagnostic> result = parse("C:\\Shaders With Spaces\\LightingUtil.hlsl:42:6: warning: unused variable", 0);
        assertEquals(1, result.size());
        assertTrue(result.get(0).message().contains("C:\\Shaders With Spaces\\LightingUtil.hlsl:42:6:"));
    }

    @Test
    public void filteredMissingEntryIsNotResurrectedByRawOutputFallback() {
        assertTrue(parse("error: missing entry point definition\n", 1).isEmpty());
        assertTrue(parse("<built-in>:1:2: warning: internal detail\nerror: missing entry point definition", 1).isEmpty());
    }

    @Test
    public void missingEntryDoesNotSuppressRealSourceErrors() {
        List<HlslDxcDiagnostics.Diagnostic> result = parse("error: missing entry point definition\n"
                + temporarySource + ":3:9: error: use of undeclared identifier 'light'", 1);
        assertEquals(1, result.size());
        assertEquals(3, result.get(0).line());
        assertTrue(result.get(0).message().contains("undeclared identifier"));
    }

    @Test
    public void explicitlyConfiguredMissingEntryRemainsVisibleAtFileLevel() {
        List<HlslDxcDiagnostics.Diagnostic> result = HlslDxcDiagnostics.parse(
                "error: missing entry point definition", 1, temporarySource, originalSource.toString(), true, true);
        assertEquals(1, result.size());
        assertEquals(0, result.get(0).line());
    }

    @Test
    public void compilerWarningsAreNotFilteredUsingUnrelatedIdentifierUses() {
        List<HlslDxcDiagnostics.Diagnostic> result = parse(temporarySource + ":6:10: warning: parameter 'mat' is not used", 0);
        assertEquals(1, result.size());
        assertEquals("warning", result.get(0).severity());
    }

    @Test
    public void optionErrorsAndUnknownCompilerFailuresStayVisible() {
        List<HlslDxcDiagnostics.Diagnostic> parsed = parse("dxc.exe: error: invalid profile", 1);
        assertEquals(1, parsed.size());
        assertEquals("invalid profile", parsed.get(0).message());
        assertEquals(0, parsed.get(0).line());
        List<HlslDxcDiagnostics.Diagnostic> fallback = parse("dxc failed : Unknown argument: '-bad'", 1);
        assertEquals(1, fallback.size());
        assertEquals("info", fallback.get(0).severity());
    }

    @Test
    public void commandLineMacroErrorsRemainVisible() {
        List<HlslDxcDiagnostics.Diagnostic> result = parse("<command line>:1:2: error: invalid macro definition", 1);
        assertEquals(1, result.size());
        assertEquals(0, result.get(0).line());
        assertTrue(result.get(0).message().contains("invalid macro definition"));
    }

    @Test
    public void crashesWithoutOutputStillReportCompilerFailure() {
        List<HlslDxcDiagnostics.Diagnostic> result = parse("", 42);
        assertEquals(1, result.size());
        assertTrue(result.get(0).message().contains("42"));
    }

    @Test
    public void translatesUtf8ColumnsToDocumentOffsets() {
        String line = "/* 中文 😀 */ bad";
        int expected = line.indexOf("bad");
        int column = line.substring(0, expected).getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 1;
        assertEquals(expected, HlslDxcDiagnostics.offsetForByteColumn(line, column));
        assertEquals(0, HlslDxcDiagnostics.offsetForByteColumn(line, 0));
        assertEquals(line.length(), HlslDxcDiagnostics.offsetForByteColumn(line, 999));
    }
}
