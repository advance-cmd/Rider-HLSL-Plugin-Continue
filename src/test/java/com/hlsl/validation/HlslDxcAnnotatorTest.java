package com.hlsl.validation;

import org.junit.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

public class HlslDxcAnnotatorTest {
    @Test
    public void quotedIncludePathsAndDefinitionsStaySingleArguments() {
        HlslDxcSettings.State settings = new HlslDxcSettings.State();
        settings.additionalArgs = "-I \"C:\\Shader Includes\\Common\" -DNUM_DIR_LIGHTS=1 -DNAME=\"Hello World\"";
        HlslDxcAnnotator.CollectedInfo info = new HlslDxcAnnotator.CollectedInfo("", "build/Deferred.hlsl", 1, settings);
        List<String> args = HlslDxcAnnotator.buildCommand(info, "dxc", Path.of("source.hlsl"), Path.of("shader.dxil"));
        assertTrue(args.contains("C:\\Shader Includes\\Common"));
        assertTrue(args.contains("-DNUM_DIR_LIGHTS=1"));
        assertTrue(args.contains("-DNAME=Hello World"));
        assertFalse(args.contains("Includes\\Common\""));
    }

    @Test
    public void filePragmasOverrideDefaultsAndLibraryProfilesDoNotRequireAnEntry() {
        HlslDxcSettings.State settings = new HlslDxcSettings.State();
        String text = "// #pragma hlsl profile lib_6_6\n// #pragma hlsl hv 2018\n";
        HlslDxcAnnotator.CollectedInfo info = new HlslDxcAnnotator.CollectedInfo(text, "build/Lighting.hlsli", 1, settings);
        List<String> args = HlslDxcAnnotator.buildCommand(info, "dxc", Path.of("source.hlsl"), Path.of("shader.dxil"));
        assertEquals("lib_6_6", args.get(args.indexOf("-T") + 1));
        assertEquals("2018", args.get(args.indexOf("-HV") + 1));
        assertFalse(args.contains("-E"));
        assertEquals("shader.dxil", args.get(args.indexOf("-Fo") + 1));
    }

    @Test
    public void quietByDefaultAndOnlyWithWarningFlagsDxcUnderstands() {
        HlslDxcSettings.State settings = new HlslDxcSettings.State();
        HlslDxcAnnotator.CollectedInfo info = new HlslDxcAnnotator.CollectedInfo("", "build/Deferred.hlsl", 1, settings);
        List<String> args = HlslDxcAnnotator.buildCommand(info, "dxc", Path.of("source.hlsl"), Path.of("shader.dxil"));
        assertFalse("clang's full warning set must stay opt-in", args.contains("-Weverything"));
        assertFalse("no extra warning groups are enabled by default", args.contains("-Wconversion"));
        for (String arg : args) {
            assertFalse("dxc has no such option: " + arg, arg.startsWith("-Wimplicit-truncation"));
            assertFalse("dxc has no such option: " + arg, arg.startsWith("-Wpayload-access"));
        }
    }

    @Test
    public void configuredMacrosAndWarningGroupsReachTheCommandLine() {
        HlslDxcSettings.State settings = new HlslDxcSettings.State();
        settings.preprocessorDefines = "NUM_DIR_LIGHTS=1; NUM_POINT_LIGHTS=1, NUM_SPOT_LIGHTS=1";
        settings.warnImplicitTruncation = true;
        settings.warnUnusedParameter = true;
        HlslDxcAnnotator.CollectedInfo info = new HlslDxcAnnotator.CollectedInfo("", "build/Deferred.hlsl", 1, settings);
        List<String> args = HlslDxcAnnotator.buildCommand(info, "dxc", Path.of("source.hlsl"), Path.of("shader.dxil"));
        assertTrue(args.contains("-DNUM_DIR_LIGHTS=1"));
        assertTrue(args.contains("-DNUM_POINT_LIGHTS=1"));
        assertTrue(args.contains("-DNUM_SPOT_LIGHTS=1"));
        // DXC reports implicit truncation of vector type under -Wconversion.
        assertTrue(args.contains("-Wconversion"));
        assertTrue(args.contains("-Wunused-parameter"));
    }

    @Test
    public void fullClangWarningsSuppressTheGroupsThatAreUnchecked() {
        HlslDxcSettings.State settings = new HlslDxcSettings.State();
        settings.extraClangWarnings = true;
        HlslDxcAnnotator.CollectedInfo info = new HlslDxcAnnotator.CollectedInfo("", "build/Deferred.hlsl", 1, settings);
        List<String> args = HlslDxcAnnotator.buildCommand(info, "dxc", Path.of("source.hlsl"), Path.of("shader.dxil"));
        assertTrue(args.contains("-Weverything"));
        assertTrue(args.contains("-Wno-unused-variable"));
        assertTrue(args.contains("-Wno-conversion"));
    }

    @Test
    public void detectedBuildMacrosBecomeDefineArgumentsAndSettingsWin() {
        HlslDxcSettings.State settings = new HlslDxcSettings.State();
        HlslDxcAnnotator.CollectedInfo info = new HlslDxcAnnotator.CollectedInfo("", "build/Deferred.hlsl", 1, settings,
                List.of("NUM_DIR_LIGHTS=1", "NUM_SPOT_LIGHTS=1"));
        List<String> args = HlslDxcAnnotator.buildCommand(info, "dxc", Path.of("source.hlsl"), Path.of("shader.dxil"));
        assertTrue(args.contains("-DNUM_DIR_LIGHTS=1"));
        assertTrue(args.contains("-DNUM_SPOT_LIGHTS=1"));

        settings.preprocessorDefines = "NUM_DIR_LIGHTS=4";
        List<String> overridden = HlslDxcAnnotator.buildCommand(info, "dxc",
                Path.of("source.hlsl"), Path.of("shader.dxil"));
        assertTrue("an explicitly configured macro must win", overridden.contains("-DNUM_DIR_LIGHTS=4"));
        assertFalse(overridden.contains("-DNUM_DIR_LIGHTS=1"));
        assertTrue(overridden.contains("-DNUM_SPOT_LIGHTS=1"));

        settings.preprocessorDefines = "";
        settings.autoDetectBuildMacros = false;
        List<String> off = HlslDxcAnnotator.buildCommand(info, "dxc", Path.of("source.hlsl"), Path.of("shader.dxil"));
        assertFalse(off.contains("-DNUM_SPOT_LIGHTS=1"));
    }

    @Test
    public void resultsOnlyApplyToTheDocumentSnapshotThatWasCompiled() {
        HlslDxcAnnotator.CollectedInfo info = new HlslDxcAnnotator.CollectedInfo("old source", "shader.hlsl", 5, new HlslDxcSettings.State());
        assertTrue(info.matches("shader.hlsl", 5, "old source"));
        assertFalse(info.matches("shader.hlsl", 6, "old source"));
        assertFalse(info.matches("shader.hlsl", 5, "new source"));
        assertFalse(info.matches("renamed.hlsl", 5, "old source"));
    }

    @Test
    public void settingsSnapshotsAreIndependentAndDiscardOutdatedResults() {
        HlslDxcSettings settings = new HlslDxcSettings();
        HlslDxcSettings.State snapshot = settings.snapshot();
        assertTrue(settings.matches(snapshot));
        settings.setAdditionalArgs("-DNUM_DIR_LIGHTS=1");
        assertEquals("", snapshot.additionalArgs);
        assertFalse(settings.matches(snapshot));
        settings.setAdditionalArgs("");
        assertTrue(settings.matches(snapshot));
        settings.setValidateIncludeFiles(true);
        assertFalse(settings.matches(snapshot));
    }
}
