package com.hlsl.completion;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The paths a Unity shader includes by. Unity resolves {@code #include "Packages/<package>/..."} and
 * {@code #include "Assets/..."} against the project, not against the including file. A package embedded in
 * the project sits in its Packages directory under its own name, but a package Unity downloaded sits in
 * Library/PackageCache under its name and a hash, as com.unity.render-pipelines.core@84bf82d10e47, and the
 * hash changes whenever the package is updated, so the directory is found by its name alone.
 *
 * <p>Nothing here writes to the Unity project: the paths are only read.</p>
 */
public final class HlslUnityPaths {

    private static final String PACKAGES = "Packages/";
    private static final String ASSETS = "Assets/";
    private static final String PACKAGE_CACHE = "Library/PackageCache";
    private static final int MAX_ROOT_WALK = 32;

    private HlslUnityPaths() {
    }

    /** True for a path Unity resolves against the project rather than against the including file. */
    public static boolean isProjectPath(@NotNull String include) {
        String normalized = include.replace('\\', '/');
        return normalized.startsWith(PACKAGES) || normalized.startsWith(ASSETS);
    }

    /**
     * The Unity project {@code file} belongs to: the nearest directory above it that holds
     * Packages/manifest.json and Library/PackageCache, or ProjectSettings when Unity has not filled the
     * Library yet. {@code null} outside a Unity project.
     */
    public static @Nullable VirtualFile projectRoot(@NotNull VirtualFile file) {
        VirtualFile directory = file.isDirectory() ? file : file.getParent();
        for (int walked = 0; directory != null && walked < MAX_ROOT_WALK; walked++) {
            ProgressManager.checkCanceled();
            if (isProjectRoot(directory)) return directory;
            directory = directory.getParent();
        }
        return null;
    }

    private static boolean isProjectRoot(VirtualFile directory) {
        VirtualFile manifest = directory.findFileByRelativePath(PACKAGES + "manifest.json");
        if (manifest == null || manifest.isDirectory()) return false;
        VirtualFile cache = directory.findFileByRelativePath(PACKAGE_CACHE);
        return cache != null && cache.isDirectory() || directory.findChild("ProjectSettings") != null;
    }

    /**
     * The file a project path names, as {@code Packages/com.unity.render-pipelines.core/Runtime/TraceRay.hlsl}:
     * the embedded package first, then the downloaded one in the package cache whatever its hash. {@code null}
     * when the include is not a project path, the file is not in a Unity project, or no such file exists.
     */
    public static @Nullable VirtualFile resolve(@NotNull VirtualFile fromFile, @NotNull String include) {
        String normalized = include.replace('\\', '/');
        boolean packages = normalized.startsWith(PACKAGES);
        if (!packages && !normalized.startsWith(ASSETS)) return null;
        VirtualFile root = projectRoot(fromFile);
        if (root == null) return null;
        VirtualFile inProject = root.findFileByRelativePath(normalized);
        if (isFile(inProject)) return inProject;
        if (!packages) return null;

        int slash = normalized.indexOf('/', PACKAGES.length());
        if (slash < 0) return null;
        String name = normalized.substring(PACKAGES.length(), slash);
        String rest = normalized.substring(slash + 1);
        VirtualFile cache = root.findFileByRelativePath(PACKAGE_CACHE);
        if (cache == null || !cache.isDirectory()) return null;
        VirtualFile found = null;
        long newest = Long.MIN_VALUE;
        for (VirtualFile directory : cache.getChildren()) {
            ProgressManager.checkCanceled();
            String directoryName = directory.getName();
            if (!directory.isDirectory() || !(directoryName.equals(name) || directoryName.startsWith(name + "@"))) {
                continue;
            }
            VirtualFile candidate = directory.findFileByRelativePath(rest);
            // An old copy can linger beside the current one after an update; the newer directory is current.
            if (isFile(candidate) && directory.getTimeStamp() > newest) {
                found = candidate;
                newest = directory.getTimeStamp();
            }
        }
        return found;
    }

    /**
     * Where {@code file} is in Unity's layout, which is the path another file includes it by:
     * {@code Packages/<package>/...} for a file of an embedded or a downloaded package, {@code Assets/...} for
     * a file of the project. {@code null} for any other file.
     */
    public static @Nullable String projectPath(@NotNull VirtualFile root, @NotNull VirtualFile file) {
        String relative = VfsUtilCore.getRelativePath(file, root, '/');
        if (relative == null) return null;
        if (relative.startsWith(PACKAGE_CACHE + "/")) {
            String inCache = relative.substring(PACKAGE_CACHE.length() + 1);
            int slash = inCache.indexOf('/');
            if (slash < 0) return null;
            String directory = inCache.substring(0, slash);
            int at = directory.indexOf('@');
            return PACKAGES + (at < 0 ? directory : directory.substring(0, at)) + inCache.substring(slash);
        }
        return relative.startsWith(PACKAGES) || relative.startsWith(ASSETS) ? relative : null;
    }

    /** True for a file of a package Unity downloaded, which is Unity's to change, not the user's. */
    public static boolean isInPackageCache(@NotNull VirtualFile file) {
        VirtualFile root = projectRoot(file);
        if (root == null) return false;
        String relative = VfsUtilCore.getRelativePath(file, root, '/');
        return relative != null && relative.startsWith(PACKAGE_CACHE + "/");
    }

    private static boolean isFile(@Nullable VirtualFile file) {
        return file != null && file.isValid() && !file.isDirectory();
    }
}
