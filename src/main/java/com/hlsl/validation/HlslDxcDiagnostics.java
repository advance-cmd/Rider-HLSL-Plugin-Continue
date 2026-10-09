package com.hlsl.validation;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class HlslDxcDiagnostics {
    private static final Pattern DIAGNOSTIC = Pattern.compile(
            "^(?:(.*):(\\d+):(\\d+):\\s*)?(?:dxc(?:\\.exe)?:\\s*)?(fatal error|error|warning|note):\\s*(.+)$");
    private static final int MAX_MESSAGE_LENGTH = 500;

    record Diagnostic(int line, int column, String severity, String message) {}

    static List<Diagnostic> parse(String output, int exitCode, Path temporarySource,
                                  String originalFile, boolean explicitEntry, boolean includeOtherFiles) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        boolean recognizedDiagnostic = false;
        for (String rawLine : output.split("\\R")) {
            Matcher matcher = DIAGNOSTIC.matcher(rawLine.trim());
            if (!matcher.matches()) continue;
            recognizedDiagnostic = true;
            String source = matcher.group(1);
            String severity = matcher.group(4);
            String message = matcher.group(5);
            if ("<built-in>".equals(source)) continue;
            // A helper .hlsl may have no entry point. Do not resurrect this via raw-output fallback.
            if (!explicitEntry && message.contains("missing entry point definition")) continue;
            if ("fatal error".equals(severity)) severity = "error";
            int line = parsePosition(matcher.group(2));
            int column = parsePosition(matcher.group(3));
            // Clang explains an unviable overload in a following note ("candidate function not viable: no
            // known conversion from 'uint' to 'Light' for 1st argument"). That explanation belongs on the
            // error, and it has to survive even though the note itself points into an include file that is
            // not annotated on its own.
            if ("note".equals(severity) && !diagnostics.isEmpty()) {
                int last = diagnostics.size() - 1;
                Diagnostic previous = diagnostics.get(last);
                diagnostics.set(last, new Diagnostic(previous.line(), previous.column(), previous.severity(),
                        append(previous.message(), message)));
                continue;
            }
            if (source == null || !isCurrentSource(source, temporarySource, originalFile)) {
                if (source != null && looksLikeFilePath(source)) {
                    // An include file's coordinates never refer to the file being edited. Keeping them as
                    // file-level annotations piles unrelated compiler noise onto the top of the edited file,
                    // so they are dropped unless the user asked to validate include files.
                    if (!includeOtherFiles) continue;
                    message = source + ":" + line + ":" + column + ": " + message;
                } else if (!"error".equals(severity) && !"info".equals(severity)) {
                    // Positionless compiler chatter such as "unknown warning option" is not worth an annotation.
                    continue;
                }
                line = 0;
                column = 0;
            }
            diagnostics.add(new Diagnostic(line, column, severity, message));
        }
        if (exitCode != 0 && !recognizedDiagnostic) {
            String message = output.isBlank() ? "Compiler exited with code " + exitCode + " without diagnostics." : output.strip();
            if (message.length() > 2000) message = message.substring(0, 2000) + "…";
            diagnostics.add(new Diagnostic(0, 0, "info", "DXC could not validate this file: " + message));
        }
        return List.copyOf(diagnostics);
    }

    /** Distinguishes real files from pseudo sources such as {@code <command line>}. */
    private static boolean looksLikeFilePath(String source) {
        if (source.indexOf('/') >= 0 || source.indexOf('\\') >= 0) return true;
        return source.matches(".*\\.[A-Za-z0-9]{1,6}$");
    }

    /** Keeps an explanatory note readable in a tooltip instead of letting it grow without bound. */
    private static String append(String message, String note) {
        if (message.length() >= MAX_MESSAGE_LENGTH) return message;
        String combined = message + " — " + note;
        return combined.length() <= MAX_MESSAGE_LENGTH ? combined : combined.substring(0, MAX_MESSAGE_LENGTH) + "…";
    }

    /** Clang/DXC columns count UTF-8 bytes; IntelliJ documents use UTF-16 offsets. */
    static int offsetForByteColumn(CharSequence line, int column) {
        int targetByteOffset = Math.max(0, column - 1);
        int byteOffset = 0;
        int charOffset = 0;
        while (charOffset < line.length()) {
            int codePoint = Character.codePointAt(line, charOffset);
            int byteLength = codePoint <= 0x7f ? 1 : codePoint <= 0x7ff ? 2 : codePoint <= 0xffff ? 3 : 4;
            if (byteOffset + byteLength > targetByteOffset) break;
            byteOffset += byteLength;
            charOffset += Character.charCount(codePoint);
        }
        return charOffset;
    }

    private static int parsePosition(String text) {
        if (text == null) return 0;
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static boolean isCurrentSource(String source, Path temporarySource, String originalFile) {
        try {
            Path sourcePath = Path.of(source);
            Path originalPath = Path.of(originalFile).toAbsolutePath().normalize();
            if (!sourcePath.isAbsolute() && originalPath.getParent() != null) {
                sourcePath = originalPath.getParent().resolve(sourcePath);
            }
            String normalizedSource = normalize(sourcePath);
            return normalizedSource.equals(normalize(temporarySource))
                    || normalizedSource.equals(normalize(originalPath));
        } catch (InvalidPathException ignored) {
            return false;
        }
    }

    private static String normalize(Path path) {
        String normalized = path.toAbsolutePath().normalize().toString().replace('\\', '/');
        return File.separatorChar == '\\' ? normalized.toLowerCase(Locale.ROOT) : normalized;
    }
}
