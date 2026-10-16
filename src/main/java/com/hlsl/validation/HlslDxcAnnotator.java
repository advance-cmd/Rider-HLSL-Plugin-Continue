package com.hlsl.validation;

import com.hlsl.HlslFileType;
import com.hlsl.completion.HlslIncludeIndex;
import com.hlsl.documentation.HlslBuiltins;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.execution.ParametersListUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HlslDxcAnnotator extends ExternalAnnotator<HlslDxcAnnotator.CollectedInfo, HlslDxcAnnotator.ValidationResult> {
    private static final Logger LOG = Logger.getInstance(HlslDxcAnnotator.class);
    private static final long TIMEOUT_SECONDS = 30;
    /** A wall of file-level annotations hides the code, so only a few are shown. */
    private static final int MAX_FILE_LEVEL_DIAGNOSTICS = 5;
    private static final Pattern UNDECLARED_IDENTIFIER =
            Pattern.compile("use of undeclared identifier '([A-Za-z_][A-Za-z0-9_]*)'");
    /** How far along the line the name quoted in a message may stand from the position DXC reported. */
    private static final int MAX_NAME_DISTANCE = 40;

    public static final class CollectedInfo {
        final String fileContent;
        final String filePath;
        final long modificationStamp;
        final HlslDxcSettings.State settings;
        final HlslDxcSource source;
        /** Macros the project's build system passes, as "NAME=VALUE". */
        final List<String> buildMacros;

        CollectedInfo(String fileContent, String filePath, long modificationStamp, HlslDxcSettings.State settings) {
            this(fileContent, filePath, modificationStamp, settings, List.of());
        }

        CollectedInfo(String fileContent, String filePath, long modificationStamp, HlslDxcSettings.State settings,
                      List<String> buildMacros) {
            this.fileContent = fileContent;
            this.filePath = filePath;
            this.modificationStamp = modificationStamp;
            this.settings = settings;
            this.source = new HlslDxcSource(fileContent);
            this.buildMacros = List.copyOf(buildMacros);
        }

        boolean matches(String currentPath, long currentStamp, String currentText) {
            return filePath.equals(currentPath) && modificationStamp == currentStamp && fileContent.equals(currentText);
        }
    }

    public record ValidationResult(CollectedInfo info, List<HlslDxcDiagnostics.Diagnostic> diagnostics) {}

    @Override
    public @Nullable CollectedInfo collectInformation(@NotNull PsiFile file) {
        HlslDxcSettings.State settings = HlslDxcSettings.getInstance().snapshot();
        if (!settings.enableValidation || !(file.getFileType() instanceof HlslFileType)) return null;
        VirtualFile virtualFile = file.getVirtualFile();
        // The built-ins file only lists declarations for reading; compiling it would report every one of them.
        if (virtualFile == null || HlslBuiltins.isBuiltinsFile(virtualFile)) return null;
        Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
        if (document == null) return null;
        if (!new HlslDxcSource(document.getText())
                .shouldValidate(virtualFile.getPath(), settings.validateIncludeFiles)) return null;
        List<String> buildMacros = settings.autoDetectBuildMacros
                ? detectBuildMacros(file, virtualFile, document) : List.of();
        return new CollectedInfo(document.getText(), virtualFile.getPath(), document.getModificationStamp(),
                settings, buildMacros);
    }

    /**
     * DXC compiles the shader standalone and never sees the macros the build system passes, so they are read
     * from the project's {@code D3D_SHADER_MACRO} tables. Without this, a shader that relies on them reports
     * "undeclared identifier" for a name the real build defines.
     */
    private static List<String> detectBuildMacros(PsiFile file, VirtualFile virtualFile, Document document) {
        HlslShaderMacroIndex index = file.getProject().getService(HlslShaderMacroIndex.class);
        if (index == null) return List.of();
        StringBuilder code = new StringBuilder(document.getText());
        HlslIncludeIndex includes = file.getProject().getService(HlslIncludeIndex.class);
        if (includes != null) {
            // A macro may only be mentioned by an included helper, so the whole include chain counts.
            for (CharSequence include : includes.collectTransitiveSources(virtualFile)) {
                code.append('\n').append(include);
            }
        }
        return index.definitionsFor(virtualFile, code);
    }

    @Override
    public @Nullable ValidationResult doAnnotate(CollectedInfo info) {
        if (info == null) return null;
        String dxcPath = info.settings.dxcPath;
        if (dxcPath == null || dxcPath.isBlank()) dxcPath = HlslDxcSettings.autoDetectDxc();
        if (dxcPath == null || dxcPath.isBlank()) {
            return message(info, "DXC not found. Configure the compiler in Settings → Tools → HLSL / DXC.");
        }

        Path tempDirectory = null;
        Process process = null;
        try {
            ProgressManager.checkCanceled();
            tempDirectory = Files.createTempDirectory("hlsl_validate_");
            Path tempFile = tempDirectory.resolve("source.hlsl");
            Path objectFile = tempDirectory.resolve("shader.dxil");
            Path outputFile = tempDirectory.resolve("diagnostics.txt");
            Files.writeString(tempFile, info.source.compilerText(), StandardCharsets.UTF_8);

            List<String> command = buildCommand(info, dxcPath, tempFile, objectFile);
            ProcessBuilder builder = new ProcessBuilder(command);
            File originalDirectory = new File(info.filePath).getAbsoluteFile().getParentFile();
            if (originalDirectory != null) builder.directory(originalDirectory);
            // Never block reading a pipe before waiting for the process: a stalled DXC must time out.
            builder.redirectErrorStream(true).redirectOutput(outputFile.toFile());
            process = builder.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
            while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                ProgressManager.checkCanceled();
                if (System.nanoTime() >= deadline) {
                    return message(info, "DXC timed out after " + TIMEOUT_SECONDS + " seconds.");
                }
            }
            ProgressManager.checkCanceled();
            String output = Files.readString(outputFile, StandardCharsets.UTF_8);
            return new ValidationResult(info, HlslDxcDiagnostics.parse(output, process.exitValue(), tempFile,
                    info.filePath, info.source.hasOption("entry"), info.settings.validateIncludeFiles));
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProcessCanceledException(e);
        } catch (Exception e) {
            LOG.warn("DXC validation failed", e);
            return message(info, "DXC execution failed: " + e.getMessage());
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                try {
                    process.waitFor(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (tempDirectory != null) {
                deleteTemporaryFile(tempDirectory.resolve("source.hlsl"));
                deleteTemporaryFile(tempDirectory.resolve("shader.dxil"));
                deleteTemporaryFile(tempDirectory.resolve("diagnostics.txt"));
                deleteTemporaryFile(tempDirectory);
            }
        }
    }

    static List<String> buildCommand(CollectedInfo info, String executable, Path sourceFile, Path objectFile) {
        HlslDxcSettings.State settings = info.settings;
        String profile = info.source.option("profile", settings.defaultProfile);
        List<String> command = new ArrayList<>(List.of(executable, "-T", profile));
        // Library profiles have no single entry point.
        if (!profile.startsWith("lib_")) {
            command.add("-E");
            command.add(info.source.option("entry", settings.defaultEntryPoint));
        }
        command.addAll(List.of("-nologo", "-Vd"));
        // `-Weverything` is clang's entire warning set (hundreds of groups) and drowns idiomatic HLSL in noise,
        // so it is opt-in. DXC's own defaults are quiet, and each extra group is enabled explicitly.
        boolean everything = settings.extraClangWarnings;
        if (everything) {
            command.addAll(List.of("-Weverything", "-Wno-unused-macros", "-Wno-missing-prototypes",
                    "-Wno-missing-variable-declarations"));
        }
        addWarningGroup(command, everything, settings.warnUnusedVariable, "-Wunused-variable");
        addWarningGroup(command, everything, settings.warnUnusedParameter, "-Wunused-parameter");
        addWarningGroup(command, everything, settings.warnUnreachableCode,
                "-Wunreachable-code", "-Wunreachable-code-return");
        addWarningGroup(command, everything, settings.warnUninitializedVariable, "-Wuninitialized");
        // DXC reports "implicit truncation of vector type" under -Wconversion. There is no -Wimplicit-truncation
        // (or -Wpayload-access-warning) in DXC: passing them only adds "unknown warning option" lines.
        addWarningGroup(command, everything, settings.warnConversion || settings.warnImplicitTruncation, "-Wconversion");
        addWarningGroup(command, everything, settings.warnEffectsSyntax, "-Weffects-syntax");
        String hlslVersion = info.source.option("hv", settings.hlslVersion);
        if (hlslVersion != null && !hlslVersion.isBlank()) command.addAll(List.of("-HV", hlslVersion));
        if (settings.treatWarningsAsErrors) command.add("-WX");
        // Macros the build system passes: without them a standalone compile reports phantom errors.
        Set<String> defined = new HashSet<>();
        for (String define : parseDefines(settings.preprocessorDefines)) {
            defined.add(HlslShaderMacros.nameOf(define));
            command.add("-D" + define);
        }
        if (settings.autoDetectBuildMacros) {
            // An explicitly configured macro always wins over what was detected in the project.
            for (String macro : info.buildMacros) {
                if (defined.add(HlslShaderMacros.nameOf(macro))) command.add("-D" + macro);
            }
        }
        if (settings.additionalArgs != null && !settings.additionalArgs.isBlank()) {
            command.addAll(ParametersListUtil.parse(settings.additionalArgs));
        }
        String originalDirectory = new File(info.filePath).getAbsoluteFile().getParent();
        if (originalDirectory != null) command.addAll(List.of("-I", originalDirectory));
        command.addAll(List.of("-Fo", objectFile.toString(), sourceFile.toString()));
        return command;
    }

    /** Enables a warning group, or suppresses it when the full clang set is on and the group is unchecked. */
    private static void addWarningGroup(List<String> command, boolean everything, boolean enabled, String... groups) {
        if (enabled) {
            command.addAll(List.of(groups));
        } else if (everything) {
            for (String group : groups) command.add("-Wno-" + group.substring("-W".length()));
        }
    }

    /** Accepts "NAME=VALUE" entries separated by ';', ',' or whitespace. */
    static List<String> parseDefines(String text) {
        List<String> defines = new ArrayList<>();
        if (text == null || text.isBlank()) return defines;
        for (String entry : text.split("[;,\\s]+")) {
            if (!entry.isBlank()) defines.add(entry.trim());
        }
        return defines;
    }

    private static ValidationResult message(CollectedInfo info, String text) {
        return new ValidationResult(info, List.of(new HlslDxcDiagnostics.Diagnostic(0, 0, "info", text)));
    }

    private static void deleteTemporaryFile(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOG.debug("Could not delete DXC temporary file " + file, e);
        }
    }

    @Override
    public void apply(@NotNull PsiFile file, ValidationResult result, @NotNull AnnotationHolder holder) {
        if (result == null || result.diagnostics().isEmpty() || !HlslDxcSettings.getInstance().matches(result.info().settings)) return;
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile == null) return;
        Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
        if (document == null || !result.info().matches(virtualFile.getPath(), document.getModificationStamp(), document.getText())) return;

        int fileLevel = 0;
        int suppressed = 0;
        for (HlslDxcDiagnostics.Diagnostic diagnostic : result.diagnostics()) {
            HighlightSeverity severity = switch (diagnostic.severity()) {
                case "error" -> HighlightSeverity.ERROR;
                case "warning" -> HighlightSeverity.WARNING;
                case "info" -> HighlightSeverity.WEAK_WARNING;
                default -> HighlightSeverity.INFORMATION;
            };
            if (diagnostic.line() == 0) {
                // A pile of file-level annotations covers the top of the editor, so keep only a few.
                if (++fileLevel > MAX_FILE_LEVEL_DIAGNOSTICS) {
                    suppressed++;
                    continue;
                }
                holder.newAnnotation(severity, annotated(diagnostic.message())).fileLevel().create();
            } else if (diagnostic.line() <= document.getLineCount()) {
                int lineStart = document.getLineStartOffset(diagnostic.line() - 1);
                int lineEnd = document.getLineEndOffset(diagnostic.line() - 1);
                CharSequence line = document.getImmutableCharSequence().subSequence(lineStart, lineEnd);
                int start = lineStart + HlslDxcDiagnostics.offsetForByteColumn(line, diagnostic.column());
                if (start == lineEnd) start = lineStart;
                int end = spanEnd(line, start - lineStart, diagnostic) + lineStart;
                holder.newAnnotation(severity, annotated(diagnostic.message()))
                        .range(new TextRange(start, Math.max(start, end))).create();
            }
        }
        if (suppressed > 0) {
            holder.newAnnotation(HighlightSeverity.WEAK_WARNING,
                    "DXC reported " + suppressed + " further file-level diagnostic(s).").fileLevel().create();
        }
    }

    /**
     * Where the underline has to stop. DXC reports a position but no length, so the name it quoted in its
     * message is looked up near that position and only that name is underlined. Without a name to find, the
     * annotation covers the identifier that starts there, and failing that a single character — never the
     * rest of the line, which used to drag a red line from the mistake to the end of the statement.
     */
    static int spanEnd(CharSequence line, int start, HlslDxcDiagnostics.Diagnostic diagnostic) {
        if (start < 0 || line.length() == 0) return 0;
        String name = diagnostic.mentionedName();
        if (name != null) {
            int at = indexOfName(line, name, start);
            if (at >= 0) return at + name.length();
        }
        int wordEnd = start;
        while (wordEnd < line.length() && isIdentifierPart(line.charAt(wordEnd))) wordEnd++;
        if (wordEnd > start) return wordEnd;
        return Math.min(line.length(), start + 1);
    }

    /**
     * The name close to the reported position. The search looks a little behind it, because a message about a
     * redefinition can point at the type in front of the name rather than at the name itself, and it stops
     * before the end of the statement so an unrelated name further along is never picked up.
     */
    private static int indexOfName(CharSequence line, String name, int from) {
        int searchFrom = Math.max(0, from - 2);
        int searchTo = Math.min(line.length(), from + MAX_NAME_DISTANCE);
        int at = indexOf(line, name, searchFrom);
        if (at < 0 || at + name.length() > searchTo) return -1;
        // Only a whole identifier counts: the "c" of "float3 c" must not match the c inside "float3".
        boolean startsWord = at == 0 || !isIdentifierPart(line.charAt(at - 1));
        int after = at + name.length();
        boolean endsWord = after >= line.length() || !isIdentifierPart(line.charAt(after));
        return startsWord && endsWord ? at : -1;
    }

    private static int indexOf(CharSequence line, String name, int from) {
        outer:
        for (int i = from; i + name.length() <= line.length(); i++) {
            for (int j = 0; j < name.length(); j++) {
                if (line.charAt(i + j) != name.charAt(j)) continue outer;
            }
            return i;
        }
        return -1;
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /**
     * DXC compiles the shader standalone and cannot see the macros the build system passes, so an undeclared
     * capitalised identifier is usually that, not a mistake in the shader.
     */
    private static String annotated(String message) {
        Matcher matcher = UNDECLARED_IDENTIFIER.matcher(message);
        if (matcher.find() && matcher.group(1).equals(matcher.group(1).toUpperCase(Locale.ROOT))) {
            return "DXC: " + message + " — the build system may pass " + matcher.group(1)
                    + " as a macro; add it under Settings → Tools → HLSL / DXC → Preprocessor defines.";
        }
        return "DXC: " + message;
    }
}
