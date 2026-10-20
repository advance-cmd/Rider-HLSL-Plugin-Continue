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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.execution.ParametersListUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class HlslDxcAnnotator extends ExternalAnnotator<HlslDxcAnnotator.CollectedInfo, HlslDxcAnnotator.ValidationResult> {
    private static final Logger LOG = Logger.getInstance(HlslDxcAnnotator.class);
    private static final long TIMEOUT_SECONDS = 30;
    /** A wall of file-level annotations hides the code, so only a few are shown. */
    private static final int MAX_FILE_LEVEL_DIAGNOSTICS = 5;
    private static final Pattern UNDECLARED_IDENTIFIER =
            Pattern.compile("use of undeclared identifier '([A-Za-z_][A-Za-z0-9_]*)'");
    /** How far along the line the name quoted in a message may stand from the position DXC reported. */
    private static final int MAX_NAME_DISTANCE = 40;

    /**
     * One file of the include chain a helper is compiled through: its path, where it goes relative to the other
     * files of the chain, and the text it has in the editor, so unsaved edits are compiled too.
     */
    record ContextFile(String path, String relativePath, String text) { }

    public static final class CollectedInfo {
        final String fileContent;
        final String filePath;
        final long modificationStamp;
        final HlslDxcSettings.State settings;
        final HlslDxcSource source;
        /** Macros the project's build system passes, as "NAME=VALUE". */
        final List<String> buildMacros;
        /** The chain this file is compiled through, the root first and this file last; empty when it is compiled on its own. */
        final List<ContextFile> context;
        /** Where profile, entry point and HLSL version come from: the shader that is really compiled. */
        final HlslDxcSource options;
        /** Something to tell about the chain, such as a root named by a pragma that does not include the file. */
        final @Nullable String notice;

        CollectedInfo(String fileContent, String filePath, long modificationStamp, HlslDxcSettings.State settings) {
            this(fileContent, filePath, modificationStamp, settings, List.of());
        }

        CollectedInfo(String fileContent, String filePath, long modificationStamp, HlslDxcSettings.State settings,
                      List<String> buildMacros) {
            this(fileContent, filePath, modificationStamp, settings, buildMacros, List.of(), null);
        }

        CollectedInfo(String fileContent, String filePath, long modificationStamp, HlslDxcSettings.State settings,
                      List<String> buildMacros, List<ContextFile> context, @Nullable String notice) {
            this.fileContent = fileContent;
            this.filePath = filePath;
            this.modificationStamp = modificationStamp;
            this.settings = settings;
            this.source = new HlslDxcSource(fileContent);
            this.buildMacros = List.copyOf(buildMacros);
            this.context = List.copyOf(context);
            this.options = this.context.isEmpty() ? source : new HlslDxcSource(this.context.get(0).text());
            this.notice = notice;
        }

        /** The file DXC is asked to compile, which is the root of the chain when there is one. */
        String compiledPath() {
            return context.isEmpty() ? filePath : context.get(0).path();
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
        String text = document.getText();
        HlslDxcSource source = new HlslDxcSource(text);
        if (!source.shouldValidate(virtualFile.getPath(), settings.validateIncludeFiles)) return null;

        // A helper is compiled through the shader that includes it, which declares what the helper uses. A
        // file that sets its own profile or entry point is meant to be compiled on its own.
        List<ContextFile> context = List.of();
        String notice = null;
        VirtualFile compiled = virtualFile;
        String compiledText = text;
        if (!source.hasOption("profile") && !source.hasOption("entry")) {
            String preferredRoot = source.option("root", null);
            HlslCompileRoot roots = file.getProject().getService(HlslCompileRoot.class);
            HlslCompileRoot.Chain chain = roots == null ? null : roots.find(virtualFile, preferredRoot);
            List<ContextFile> chainContext = chain == null ? null : contextOf(chain, virtualFile, text);
            if (chainContext != null) {
                context = chainContext;
                compiled = chain.root();
                compiledText = chainContext.get(0).text();
            }
            if (preferredRoot != null && (chainContext == null || !compiled.getName().equalsIgnoreCase(preferredRoot))) {
                notice = "#pragma hlsl root " + preferredRoot + ": no shader of that name includes this file"
                        + (chainContext == null ? "." : ", so it is compiled through " + compiled.getName() + ".");
            }
        }
        List<String> buildMacros = settings.autoDetectBuildMacros
                ? detectBuildMacros(file.getProject(), compiled, compiledText) : List.of();
        return new CollectedInfo(text, virtualFile.getPath(), document.getModificationStamp(),
                settings, buildMacros, context, notice);
    }

    /**
     * The files of the chain with the text each has in the editor, and with their places relative to one
     * another, or {@code null} when the chain cannot be compiled from disk: DXC reads the includes outside the
     * chain from their own directories, so every file has to be a local one under a shared directory.
     */
    private static @Nullable List<ContextFile> contextOf(HlslCompileRoot.Chain chain, VirtualFile file, String text) {
        List<String> paths = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        for (VirtualFile step : chain.files()) {
            if (!step.isInLocalFileSystem()) return null;
            String content = step.equals(file) ? text : currentText(step);
            if (content == null) return null;
            paths.add(step.getPath());
            texts.add(content);
        }
        List<String> relative = relativeLayout(paths);
        if (relative == null) return null;
        List<ContextFile> context = new ArrayList<>();
        for (int i = 0; i < paths.size(); i++) context.add(new ContextFile(paths.get(i), relative.get(i), texts.get(i)));
        return context;
    }

    private static @Nullable String currentText(VirtualFile file) {
        Document document = FileDocumentManager.getInstance().getCachedDocument(file);
        if (document != null) return document.getText();
        try {
            return VfsUtilCore.loadText(file);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Each path relative to the deepest directory that holds all of them, so the chain can be written into a
     * temporary directory with the same layout and its relative includes still find each other. {@code null}
     * when the files share no directory, as on two drives.
     */
    static @Nullable List<String> relativeLayout(List<String> paths) {
        List<Path> absolute = new ArrayList<>();
        Path common = null;
        for (String path : paths) {
            Path file = Path.of(path).toAbsolutePath().normalize();
            Path directory = file.getParent();
            if (directory == null) return null;
            absolute.add(file);
            common = common == null ? directory : commonAncestor(common, directory);
            if (common == null) return null;
        }
        List<String> relative = new ArrayList<>();
        for (Path file : absolute) relative.add(common.relativize(file).toString().replace('\\', '/'));
        return relative;
    }

    private static @Nullable Path commonAncestor(Path a, Path b) {
        if (a.getRoot() == null || !a.getRoot().equals(b.getRoot())) return null;
        Path candidate = a;
        while (candidate != null && !b.startsWith(candidate)) candidate = candidate.getParent();
        return candidate;
    }

    /**
     * DXC compiles the shader standalone and never sees the macros the build system passes, so they are read
     * from the project's {@code D3D_SHADER_MACRO} tables. Without this, a shader that relies on them reports
     * "undeclared identifier" for a name the real build defines.
     */
    private static List<String> detectBuildMacros(Project project, VirtualFile virtualFile, String text) {
        HlslShaderMacroIndex index = project.getService(HlslShaderMacroIndex.class);
        if (index == null) return List.of();
        StringBuilder code = new StringBuilder(text);
        HlslIncludeIndex includes = project.getService(HlslIncludeIndex.class);
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
            Path objectFile = tempDirectory.resolve("shader.dxil");
            Path outputFile = tempDirectory.resolve("diagnostics.txt");
            // What DXC compiles, and where the diagnostics of the edited file point.
            Path compiledFile;
            Path editedFile;
            if (info.context.isEmpty()) {
                compiledFile = editedFile = tempDirectory.resolve("source.hlsl");
                Files.writeString(compiledFile, info.source.compilerText(), StandardCharsets.UTF_8);
            } else {
                // The chain is written with its layout kept, so its relative includes reach these copies, with
                // their unsaved edits, before the files on disk.
                Path chain = tempDirectory.resolve("chain");
                for (ContextFile file : info.context) {
                    Path copy = chain.resolve(file.relativePath());
                    Files.createDirectories(copy.getParent());
                    Files.writeString(copy, new HlslDxcSource(file.text()).compilerText(), StandardCharsets.UTF_8);
                }
                compiledFile = chain.resolve(info.context.get(0).relativePath());
                editedFile = chain.resolve(info.context.get(info.context.size() - 1).relativePath());
            }

            List<String> command = buildCommand(info, dxcPath, compiledFile, objectFile);
            ProcessBuilder builder = new ProcessBuilder(command);
            File originalDirectory = new File(info.compiledPath()).getAbsoluteFile().getParentFile();
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
            // Through a root, the other files of the chain are validated when they are opened themselves.
            List<HlslDxcDiagnostics.Diagnostic> diagnostics = HlslDxcDiagnostics.parse(output, process.exitValue(),
                    editedFile, info.filePath, info.options.hasOption("entry"),
                    info.context.isEmpty() && info.settings.validateIncludeFiles);
            return new ValidationResult(info, withContext(info, diagnostics));
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
            if (tempDirectory != null) deleteTemporaryDirectory(tempDirectory);
        }
    }

    /**
     * Says which shader a diagnostic was found through, since the edited file was not compiled on its own, and
     * puts the notice about the chain first.
     */
    private static List<HlslDxcDiagnostics.Diagnostic> withContext(CollectedInfo info,
                                                                   List<HlslDxcDiagnostics.Diagnostic> diagnostics) {
        if (info.context.isEmpty() && info.notice == null) return diagnostics;
        List<HlslDxcDiagnostics.Diagnostic> result = new ArrayList<>();
        if (info.notice != null) result.add(new HlslDxcDiagnostics.Diagnostic(0, 0, "info", info.notice));
        String root = info.context.isEmpty() ? null : new File(info.compiledPath()).getName();
        for (HlslDxcDiagnostics.Diagnostic diagnostic : diagnostics) {
            result.add(root == null ? diagnostic : new HlslDxcDiagnostics.Diagnostic(diagnostic.line(),
                    diagnostic.column(), diagnostic.severity(), diagnostic.message() + " (compiled through " + root + ")"));
        }
        return result;
    }

    static List<String> buildCommand(CollectedInfo info, String executable, Path sourceFile, Path objectFile) {
        HlslDxcSettings.State settings = info.settings;
        String profile = info.options.option("profile", settings.defaultProfile);
        List<String> command = new ArrayList<>(List.of(executable, "-T", profile));
        // Library profiles have no single entry point.
        if (!profile.startsWith("lib_")) {
            command.add("-E");
            command.add(info.options.option("entry", settings.defaultEntryPoint));
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
        String hlslVersion = info.options.option("hv", settings.hlslVersion);
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
        // Includes outside the chain are read from where its files really are, the root's directory first.
        Set<String> directories = new LinkedHashSet<>();
        if (info.context.isEmpty()) {
            directories.add(new File(info.filePath).getAbsoluteFile().getParent());
        } else {
            for (ContextFile file : info.context) directories.add(new File(file.path()).getAbsoluteFile().getParent());
        }
        for (String directory : directories) {
            if (directory != null) command.addAll(List.of("-I", directory));
        }
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

    private static void deleteTemporaryDirectory(Path directory) {
        try (Stream<Path> files = Files.walk(directory)) {
            files.sorted(Comparator.reverseOrder()).forEach(HlslDxcAnnotator::deleteTemporaryFile);
        } catch (IOException | UncheckedIOException e) {
            LOG.debug("Could not delete DXC temporary directory " + directory, e);
        }
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
