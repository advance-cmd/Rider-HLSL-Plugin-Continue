package com.hlsl.validation;

import com.hlsl.HlslFileType;
import com.hlsl.completion.HlslIncludeIndex;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Finds the shader a file is compiled as part of.
 *
 * <p>A helper such as Shadow.hlsl is written to be included after the declarations it uses: LightingUtil.hlsl
 * declares PassConstants and then includes it. Compiled on its own it reports those declarations as undefined,
 * but the build never compiles it on its own; it compiles Triangle.hlsl, which includes LightingUtil.hlsl,
 * which includes Shadow.hlsl. That chain is found by reading the includes of the shaders around the file.</p>
 *
 * <p>The shaders that include a helper sit in its directory or below the directory above it, so that is what is
 * scanned. The scan is bounded and cached for a short while, because it runs for every validation; the includes
 * of each shader come from the include index, which follows unsaved edits.</p>
 */
@Service(Service.Level.PROJECT)
public final class HlslCompileRoot {

    private static final int MAX_VISITED = 5000;
    private static final int MAX_DIRECTORY_DEPTH = 4;
    private static final long RESCAN_NANOS = TimeUnit.SECONDS.toNanos(30);
    /** Directories whose contents are build output rather than sources. */
    private static final Set<String> SKIPPED_DIRECTORIES = Set.of(
            ".git", ".vs", ".idea", "x64", "debug", "release", "obj", "bin", "packages",
            "intermediate", "generated", "node_modules", "build", "out");

    private final Project project;
    private final Map<VirtualFile, Snapshot> scans = new ConcurrentHashMap<>();

    private record Snapshot(List<VirtualFile> shaders, long scannedAt) { }

    /** The include chain from the shader that is compiled down to the file: the root first, the file last. */
    public record Chain(List<VirtualFile> files) {
        public VirtualFile root() {
            return files.get(0);
        }
    }

    public HlslCompileRoot(Project project) {
        this.project = project;
    }

    /** Drops the cached scans, so shaders created since are seen on the next validation. */
    public void invalidate() {
        scans.clear();
    }

    /**
     * The chain {@code file} is compiled through, or {@code null} when no shader includes it. A shader named by
     * {@code preferredRoot} wins when it does include the file; otherwise the root fewest includes away from the
     * file is taken, and between those the first by path, so the choice does not change from one run to the next.
     */
    public @Nullable Chain find(@NotNull VirtualFile file, @Nullable String preferredRoot) {
        HlslIncludeIndex index = project.getService(HlslIncludeIndex.class);
        if (index == null) return null;
        Map<VirtualFile, List<VirtualFile>> includers = new HashMap<>();
        for (VirtualFile shader : shadersAround(file)) {
            ProgressManager.checkCanceled();
            for (VirtualFile included : index.directIncludes(shader)) {
                if (!included.equals(shader)) includers.computeIfAbsent(included, key -> new ArrayList<>()).add(shader);
            }
        }
        return chain(file, includers, preferredRoot);
    }

    /**
     * The chain {@code file} is compiled through according to its own text, which code insight follows as well:
     * the shader its {@code #pragma hlsl root} names when that one includes it, and none when the file sets its
     * own profile or entry point, because such a file is compiled on its own.
     */
    public @Nullable Chain findFor(@NotNull VirtualFile file, @NotNull CharSequence text) {
        HlslDxcSource source = new HlslDxcSource(text.toString());
        if (source.hasOption("profile") || source.hasOption("entry")) return null;
        return find(file, source.option("root", null));
    }

    /** Walks up from {@code file} through its includers and picks the root the chain starts from. */
    static @Nullable Chain chain(VirtualFile file, Map<VirtualFile, List<VirtualFile>> includers,
                                 @Nullable String preferredRoot) {
        // `towards` remembers, for each shader reached, the file one step closer to the edited one.
        Map<VirtualFile, VirtualFile> towards = new HashMap<>();
        Map<VirtualFile, Integer> distance = new HashMap<>();
        Deque<VirtualFile> queue = new ArrayDeque<>();
        List<VirtualFile> roots = new ArrayList<>();
        distance.put(file, 0);
        queue.add(file);
        while (!queue.isEmpty()) {
            VirtualFile current = queue.poll();
            List<VirtualFile> parents = includers.getOrDefault(current, List.of());
            if (parents.isEmpty() && !current.equals(file)) roots.add(current);
            for (VirtualFile parent : parents) {
                if (distance.containsKey(parent)) continue;
                distance.put(parent, distance.get(current) + 1);
                towards.put(parent, current);
                queue.add(parent);
            }
        }
        Comparator<VirtualFile> closestFirst = Comparator.<VirtualFile>comparingInt(distance::get)
                .thenComparing(VirtualFile::getPath);
        VirtualFile root = null;
        if (preferredRoot != null) {
            root = distance.keySet().stream()
                    .filter(shader -> !shader.equals(file) && shader.getName().equalsIgnoreCase(preferredRoot))
                    .min(closestFirst).orElse(null);
        }
        if (root == null) root = roots.stream().min(closestFirst).orElse(null);
        if (root == null) return null;
        List<VirtualFile> files = new ArrayList<>();
        for (VirtualFile step = root; step != null; step = towards.get(step)) files.add(step);
        return new Chain(List.copyOf(files));
    }

    private List<VirtualFile> shadersAround(VirtualFile file) {
        VirtualFile directory = file.getParent();
        if (directory == null) return List.of();
        VirtualFile scanRoot = directory.getParent() != null ? directory.getParent() : directory;
        Snapshot cached = scans.get(scanRoot);
        if (cached != null && System.nanoTime() - cached.scannedAt() < RESCAN_NANOS) return cached.shaders();
        List<VirtualFile> shaders = new ArrayList<>();
        collectShaders(scanRoot, shaders, new int[1], 0);
        List<VirtualFile> snapshot = List.copyOf(shaders);
        scans.put(scanRoot, new Snapshot(snapshot, System.nanoTime()));
        return snapshot;
    }

    private static void collectShaders(VirtualFile directory, List<VirtualFile> into, int[] visited, int depth) {
        if (depth > MAX_DIRECTORY_DEPTH) return;
        ProgressManager.checkCanceled();
        for (VirtualFile child : directory.getChildren()) {
            if (++visited[0] > MAX_VISITED) return;
            if (child.isDirectory()) {
                if (!SKIPPED_DIRECTORIES.contains(child.getName().toLowerCase(Locale.ROOT))) {
                    collectShaders(child, into, visited, depth + 1);
                }
            } else if (FileTypeRegistry.getInstance().isFileOfType(child, HlslFileType.INSTANCE)) {
                into.add(child);
            }
        }
    }
}
