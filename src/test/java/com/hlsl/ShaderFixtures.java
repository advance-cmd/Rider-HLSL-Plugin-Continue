package com.hlsl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads real shader sources for the tests that check completion and documentation against full files rather
 * than trimmed snippets.
 *
 * <p>The sources are not part of the repository: they belong to a private project and
 * {@code src/test/resources/dx12} is git-ignored. Put them there, or point the
 * {@code HLSL_TEST_SOURCE_DIR} environment variable at a directory of shader sources, to run the tests that
 * depend on them; the Gradle build skips those test classes when neither is present.</p>
 */
public final class ShaderFixtures {

    /** Directories of local shader sources, ignored by git. */
    private static final Path LOCAL_DIR = Path.of("src", "test", "resources", "dx12");
    private static final Path LOCAL_SAMPLE_DIR = Path.of("src", "test", "resources", "samples");

    private ShaderFixtures() {
    }

    /** Whether the real shader sources are available to the tests. */
    public static boolean available() {
        return sourceDirectory() != null || readFromClasspath("LightingUtil.hlsl") != null;
    }

    /** Reads a shader source, failing loudly when the fixtures are missing. */
    public static String require(String name) throws IOException {
        String classpath = readFromClasspath("/dx12/" + name);
        if (classpath != null) return classpath;
        classpath = readFromClasspath("/samples/" + name);
        if (classpath != null) return classpath;
        for (Path directory : localDirectories()) {
            Path file = directory.resolve(name);
            if (Files.isReadable(file)) return Files.readString(file);
        }
        throw new IOException("shader fixture '" + name + "' is not available: put the sources in " + LOCAL_DIR
                + " or point HLSL_TEST_SOURCE_DIR at a directory that holds them");
    }

    public static String requireDeferred() throws IOException {
        return require("Deferred.hlsl");
    }

    public static String requireLightingUtil() throws IOException {
        return require("LightingUtil.hlsl");
    }

    private static Path sourceDirectory() {
        String configured = System.getenv("HLSL_TEST_SOURCE_DIR");
        if (configured != null && !configured.isBlank()) {
            Path path = Path.of(configured);
            return Files.isDirectory(path) ? path : null;
        }
        return Files.isDirectory(LOCAL_DIR) ? LOCAL_DIR : null;
    }

    private static List<Path> localDirectories() {
        List<Path> directories = new ArrayList<>();
        Path configured = sourceDirectory();
        if (configured != null) directories.add(configured);
        if (Files.isDirectory(LOCAL_SAMPLE_DIR)) directories.add(LOCAL_SAMPLE_DIR);
        return directories;
    }

    private static String readFromClasspath(String path) {
        try (InputStream in = ShaderFixtures.class.getResourceAsStream(path)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}
