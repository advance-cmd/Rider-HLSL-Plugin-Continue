package com.hlsl.validation;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;

@Service(Service.Level.APP)
@State(name = "HlslDxcSettings", storages = @Storage("hlslDxcSettings.xml"))
public final class HlslDxcSettings implements PersistentStateComponent<HlslDxcSettings.State> {

    public static class State {
        public String dxcPath = "";
        public String defaultProfile = "ps_6_6";
        public String defaultEntryPoint = "main";
        public String hlslVersion = "2021";
        public boolean enableValidation = true;
        public boolean validateIncludeFiles = false;
        public boolean treatWarningsAsErrors = false;
        /**
         * Macros the build system passes to the compiler, e.g. "LIGHT_COUNT=4;USE_FOG=1".
         * Without these, shaders that rely on build-provided macros look broken to a standalone compile.
         */
        public String preprocessorDefines = "";
        /** Reads the macros the build system passes from the project's D3D_SHADER_MACRO tables. */
        public boolean autoDetectBuildMacros = true;
        /** Adds clang's whole `-Weverything` set, which buries HLSL in idiomatic-code warnings. */
        public boolean extraClangWarnings = false;
        // Warning groups: DXC reports its own defaults already, so every extra group is opt-in.
        public boolean warnUnusedVariable = false;
        public boolean warnUnusedParameter = false;
        public boolean warnUnreachableCode = false;
        public boolean warnUninitializedVariable = false;
        public boolean warnImplicitTruncation = false;
        public boolean warnConversion = false;
        public boolean warnPayloadAccess = false;
        public boolean warnEffectsSyntax = false;
        public String additionalArgs = "";
        /** Explains semantics such as SV_Target or TEXCOORD when the mouse rests on them. */
        public boolean semanticsHints = true;
        /** Language of those explanations: "auto" follows the IDE, otherwise a language tag such as "zh". */
        public String hintLanguage = "auto";
    }

    private State state = new State();

    public static HlslDxcSettings getInstance() {
        return ApplicationManager.getApplication().getService(HlslDxcSettings.class);
    }

    @Override
    public @Nullable State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    State snapshot() {
        return XmlSerializerUtil.createCopy(state);
    }

    boolean matches(State snapshot) {
        return Objects.equals(state.dxcPath, snapshot.dxcPath)
                && Objects.equals(state.defaultProfile, snapshot.defaultProfile)
                && Objects.equals(state.defaultEntryPoint, snapshot.defaultEntryPoint)
                && Objects.equals(state.hlslVersion, snapshot.hlslVersion)
                && Objects.equals(state.additionalArgs, snapshot.additionalArgs)
                && Objects.equals(state.preprocessorDefines, snapshot.preprocessorDefines)
                && state.autoDetectBuildMacros == snapshot.autoDetectBuildMacros
                && state.extraClangWarnings == snapshot.extraClangWarnings
                && state.enableValidation == snapshot.enableValidation
                && state.validateIncludeFiles == snapshot.validateIncludeFiles
                && state.treatWarningsAsErrors == snapshot.treatWarningsAsErrors
                && state.warnUnusedVariable == snapshot.warnUnusedVariable
                && state.warnUnusedParameter == snapshot.warnUnusedParameter
                && state.warnUnreachableCode == snapshot.warnUnreachableCode
                && state.warnUninitializedVariable == snapshot.warnUninitializedVariable
                && state.warnImplicitTruncation == snapshot.warnImplicitTruncation
                && state.warnConversion == snapshot.warnConversion
                && state.warnPayloadAccess == snapshot.warnPayloadAccess
                && state.warnEffectsSyntax == snapshot.warnEffectsSyntax
                && state.semanticsHints == snapshot.semanticsHints
                && Objects.equals(state.hintLanguage, snapshot.hintLanguage);
    }

    public String getDxcPath() { return state.dxcPath; }
    public void setDxcPath(String path) { state.dxcPath = path; }

    public String getDefaultProfile() { return state.defaultProfile; }
    public void setDefaultProfile(String profile) { state.defaultProfile = profile; }

    public String getDefaultEntryPoint() { return state.defaultEntryPoint; }
    public void setDefaultEntryPoint(String entry) { state.defaultEntryPoint = entry; }

    public String getHlslVersion() { return state.hlslVersion; }
    public void setHlslVersion(String version) { state.hlslVersion = version; }

    public boolean isEnableValidation() { return state.enableValidation; }
    public void setEnableValidation(boolean enable) { state.enableValidation = enable; }

    public boolean isValidateIncludeFiles() { return state.validateIncludeFiles; }
    public void setValidateIncludeFiles(boolean enable) { state.validateIncludeFiles = enable; }

    public boolean isTreatWarningsAsErrors() { return state.treatWarningsAsErrors; }
    public void setTreatWarningsAsErrors(boolean treat) { state.treatWarningsAsErrors = treat; }

    public boolean isWarnUnusedVariable() { return state.warnUnusedVariable; }
    public void setWarnUnusedVariable(boolean v) { state.warnUnusedVariable = v; }

    public boolean isWarnUnusedParameter() { return state.warnUnusedParameter; }
    public void setWarnUnusedParameter(boolean v) { state.warnUnusedParameter = v; }

    public boolean isWarnUnreachableCode() { return state.warnUnreachableCode; }
    public void setWarnUnreachableCode(boolean v) { state.warnUnreachableCode = v; }

    public boolean isWarnUninitializedVariable() { return state.warnUninitializedVariable; }
    public void setWarnUninitializedVariable(boolean v) { state.warnUninitializedVariable = v; }

    public boolean isWarnImplicitTruncation() { return state.warnImplicitTruncation; }
    public void setWarnImplicitTruncation(boolean v) { state.warnImplicitTruncation = v; }

    public boolean isWarnConversion() { return state.warnConversion; }
    public void setWarnConversion(boolean v) { state.warnConversion = v; }

    public boolean isWarnPayloadAccess() { return state.warnPayloadAccess; }
    public void setWarnPayloadAccess(boolean v) { state.warnPayloadAccess = v; }

    public boolean isWarnEffectsSyntax() { return state.warnEffectsSyntax; }
    public void setWarnEffectsSyntax(boolean v) { state.warnEffectsSyntax = v; }

    public String getAdditionalArgs() { return state.additionalArgs; }
    public void setAdditionalArgs(String args) { state.additionalArgs = args; }

    /** Whether semantics such as SV_Target are explained on hover. */
    public boolean isSemanticsHintsEnabled() { return state.semanticsHints; }
    public void setSemanticsHintsEnabled(boolean enabled) { state.semanticsHints = enabled; }

    /** "auto" follows the IDE's own language; otherwise a language tag such as "en" or "zh". */
    public String getHintLanguage() { return state.hintLanguage; }
    public void setHintLanguage(String language) { state.hintLanguage = language; }

    public String getPreprocessorDefines() { return state.preprocessorDefines; }
    public void setPreprocessorDefines(String defines) { state.preprocessorDefines = defines; }

    public boolean isAutoDetectBuildMacros() { return state.autoDetectBuildMacros; }
    public void setAutoDetectBuildMacros(boolean enabled) { state.autoDetectBuildMacros = enabled; }

    public boolean isExtraClangWarnings() { return state.extraClangWarnings; }
    public void setExtraClangWarnings(boolean enabled) { state.extraClangWarnings = enabled; }

    /**
     * Returns the resolved DXC path: user setting if non-empty, otherwise auto-detected.
     */
    public @Nullable String getResolvedDxcPath() {
        String path = state.dxcPath;
        if (path != null && !path.isBlank()) {
            return path;
        }
        return autoDetectDxc();
    }

    /**
     * Try to find dxc.exe in common install locations.
     */
    public static @Nullable String autoDetectDxc() {
        // 1. Check PATH
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(File.pathSeparator)) {
                File dxc = new File(dir, "dxc.exe");
                if (dxc.isFile() && dxc.canExecute()) return dxc.getAbsolutePath();
                dxc = new File(dir, "dxc");
                if (dxc.isFile() && dxc.canExecute()) return dxc.getAbsolutePath();
            }
        }

        // 2. Windows SDK
        Path windowsKits = Path.of("C:\\Program Files (x86)\\Windows Kits\\10\\bin");
        if (Files.isDirectory(windowsKits)) {
            try (Stream<Path> versions = Files.list(windowsKits)) {
                String found = versions
                        .sorted(java.util.Comparator.reverseOrder())
                        .map(v -> v.resolve("x64").resolve("dxc.exe"))
                        .filter(Files::isRegularFile)
                        .map(Path::toString)
                        .findFirst()
                        .orElse(null);
                if (found != null) return found;
            } catch (Exception ignored) {}
        }

        // 3. Vulkan SDK
        String vulkanSdk = System.getenv("VULKAN_SDK");
        if (vulkanSdk != null) {
            File dxc = new File(vulkanSdk, "Bin\\dxc.exe");
            if (dxc.isFile()) return dxc.getAbsolutePath();
            dxc = new File(vulkanSdk, "bin/dxc");
            if (dxc.isFile()) return dxc.getAbsolutePath();
        }

        return null;
    }
}
