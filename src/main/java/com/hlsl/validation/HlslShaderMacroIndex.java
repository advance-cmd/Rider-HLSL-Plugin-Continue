package com.hlsl.validation;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Finds the shader macros the project's build system passes, so a standalone DXC compile can be given the
 * same defines. Without them a shader's {@code #ifdef}-guarded code is compiled with the wrong configuration,
 * which both hides real errors and invents phantom ones.
 *
 * <p>The scan is bounded, cancellable and cached, because it runs from the annotator.</p>
 */
@Service(Service.Level.PROJECT)
public final class HlslShaderMacroIndex {

    private static final Set<String> SCANNED_EXTENSIONS = Set.of("cpp", "cxx", "cc", "c", "h", "hpp", "hxx", "inl", "ipp");
    /** Directories whose contents are build output rather than sources. */
    private static final Set<String> SKIPPED_DIRECTORIES = Set.of(
            ".git", ".vs", ".idea", "x64", "debug", "release", "obj", "bin", "packages",
            "intermediate", "generated", "node_modules", "build", "out");
    private static final int MAX_FILES = 1000;
    private static final int MAX_DIRECTORY_DEPTH = 8;
    private static final long MAX_FILE_LENGTH = 4L * 1024 * 1024;
    private static final long RESCAN_NANOS = TimeUnit.MINUTES.toNanos(2);

    private final Project project;
    private volatile Snapshot snapshot;

    public HlslShaderMacroIndex(Project project) {
        this.project = project;
    }

    private record Snapshot(Map<String, String> macros, long scannedAt) { }

    /** Returns "NAME=VALUE" for the build-provided macros this shader mentions, or an empty list. */
    public List<String> definitionsFor(VirtualFile context, CharSequence code) {
        Map<String, String> macros = macros(context);
        return macros.isEmpty() ? List.of() : HlslShaderMacros.definitionsFor(macros, code);
    }

    /** Drops the cached scan, so a configuration change takes effect on the next validation. */
    public void invalidate() {
        snapshot = null;
    }

    /** Macros resolved by the last scan, for display. The settings page must not start a scan on the UI thread. */
    public List<String> cachedDefinitions() {
        Snapshot cached = snapshot;
        if (cached == null) return List.of();
        List<String> definitions = new ArrayList<>();
        for (Map.Entry<String, String> macro : cached.macros().entrySet()) {
            definitions.add(macro.getKey() + "=" + macro.getValue());
        }
        return definitions;
    }

    private Map<String, String> macros(VirtualFile context) {
        Snapshot cached = snapshot;
        if (cached != null && System.nanoTime() - cached.scannedAt() < RESCAN_NANOS) return cached.macros();
        Map<String, Set<String>> values = new LinkedHashMap<>();
        List<VirtualFile> candidates = candidateFiles();
        for (VirtualFile file : candidates) {
            collect(file, values);
        }
        if (values.isEmpty()) {
            // Rider and CLion keep C++ sources in their own project model, so the platform's content roots
            // may not contain them. The shader's own build directory is a reliable fallback.
            VirtualFile root = buildRoot(context);
            if (root != null) {
                List<VirtualFile> files = new ArrayList<>();
                collectSources(root, files, 0);
                for (VirtualFile file : files) {
                    collect(file, values);
                }
            }
        }
        Map<String, String> resolved = HlslShaderMacros.unambiguous(values);
        snapshot = new Snapshot(resolved, System.nanoTime());
        return resolved;
    }

    private void collect(VirtualFile file, Map<String, Set<String>> values) {
        ProgressManager.checkCanceled();
        try {
            String text = VfsUtilCore.loadText(file);
            if (text.contains("D3D_SHADER_MACRO")) HlslShaderMacros.collect(text, values);
        } catch (IOException | RuntimeException ignored) {
            // An unreadable or binary file simply contributes no macros.
        }
    }

    private List<VirtualFile> candidateFiles() {
        List<VirtualFile> candidates = new ArrayList<>();
        ProjectFileIndex.getInstance(project).iterateContent(file -> {
            ProgressManager.checkCanceled();
            if (candidates.size() >= MAX_FILES) return false;
            if (isScannableSource(file)) candidates.add(file);
            return true;
        });
        return candidates;
    }

    private static boolean isScannableSource(VirtualFile file) {
        if (file.isDirectory()) return false;
        String extension = file.getExtension();
        if (extension == null || !SCANNED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) return false;
        long length = file.getLength();
        return length < 0 || length <= MAX_FILE_LENGTH;
    }

    /** The first ancestor that holds a build file, which is what the shaders are compiled from. */
    private static VirtualFile buildRoot(VirtualFile context) {
        if (context == null) return null;
        VirtualFile directory = context.getParent();
        for (int depth = 0; directory != null && depth < MAX_DIRECTORY_DEPTH; depth++) {
            if (holdsBuildFile(directory)) return directory;
            directory = directory.getParent();
        }
        return null;
    }

    private static boolean holdsBuildFile(VirtualFile directory) {
        for (VirtualFile child : directory.getChildren()) {
            if (child.isDirectory()) continue;
            String name = child.getName().toLowerCase(Locale.ROOT);
            if (name.endsWith(".sln") || name.endsWith(".vcxproj") || name.endsWith(".vcproj")
                    || name.equals("cmakelists.txt")) return true;
        }
        return false;
    }

    private static void collectSources(VirtualFile directory, List<VirtualFile> into, int depth) {
        if (depth > MAX_DIRECTORY_DEPTH || into.size() >= MAX_FILES) return;
        ProgressManager.checkCanceled();
        for (VirtualFile child : directory.getChildren()) {
            if (into.size() >= MAX_FILES) return;
            if (child.isDirectory()) {
                if (!SKIPPED_DIRECTORIES.contains(child.getName().toLowerCase(Locale.ROOT))) {
                    collectSources(child, into, depth + 1);
                }
            } else if (isScannableSource(child)) {
                into.add(child);
            }
        }
    }
}
