package com.hlsl.completion;

import com.hlsl.lexer.HlslLexer;
import com.hlsl.psi.HlslTokenTypes;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Caches source snapshots and declarations, and walks the transitive include graph. */
@Service(Service.Level.PROJECT)
public final class HlslIncludeIndex {

    private static final Pattern INCLUDE_PATTERN = Pattern.compile(
            "^#[ \\t]*include[ \\t]*(?:\"([^\"\\r\\n]+)\"|<([^>\\r\\n]+)>)");

    private static final int MAX_PARENT_WALK = 10;
    private static final int MAX_INCLUDE_DEPTH = 32;

    private final ConcurrentHashMap<VirtualFile, Entry> cache = new ConcurrentHashMap<>();

    public HlslIncludeIndex(@SuppressWarnings("unused") Project project) {
    }

    private static final class Entry {
        final long modificationStamp;
        final boolean fromDocument;
        final CharSequence source;
        final Set<String> identifiers;
        final List<String> includes;

        Entry(long stamp, boolean fromDocument, CharSequence source,
              Set<String> identifiers, List<String> includes) {
            this.modificationStamp = stamp;
            this.fromDocument = fromDocument;
            this.source = source;
            this.identifiers = identifiers;
            this.includes = includes;
        }
    }

    /** The current file's declarations are handled separately with its lexical scope. */
    public @NotNull Set<String> collectTransitiveIdentifiers(@Nullable VirtualFile file) {
        if (file == null) return Collections.emptySet();
        Set<String> result = new HashSet<>();
        forEachIncludedEntry(file, entry -> result.addAll(entry.identifiers));
        return result;
    }

    /** Supplies included type declarations for member completion, including unsaved edits. */
    public @NotNull List<CharSequence> collectTransitiveSources(@Nullable VirtualFile file) {
        if (file == null) return Collections.emptyList();
        List<CharSequence> result = new ArrayList<>();
        forEachIncludedEntry(file, entry -> result.add(entry.source));
        return result;
    }

    private void forEachIncludedEntry(VirtualFile file, Consumer<Entry> consumer) {
        Entry root = getOrParse(file);
        if (root == null) return;
        Set<VirtualFile> visited = new HashSet<>();
        visited.add(file);
        collectIncludes(file, root, visited, 0, consumer);
    }

    private void collectIncludes(VirtualFile file, Entry entry, Set<VirtualFile> visited,
                                 int depth, Consumer<Entry> consumer) {
        ProgressManager.checkCanceled();
        if (depth >= MAX_INCLUDE_DEPTH) return;
        for (String include : entry.includes) {
            ProgressManager.checkCanceled();
            VirtualFile resolved = resolve(file, include);
            if (resolved == null || !visited.add(resolved)) continue;
            Entry included = getOrParse(resolved);
            if (included == null) continue;
            consumer.accept(included);
            collectIncludes(resolved, included, visited, depth + 1, consumer);
        }
    }

    private @Nullable Entry getOrParse(VirtualFile file) {
        ProgressManager.checkCanceled();
        if (!file.isValid() || file.isDirectory()) {
            cache.remove(file);
            return null;
        }
        Document document = FileDocumentManager.getInstance().getCachedDocument(file);
        boolean fromDocument = document != null;
        // VFS and document stamps have different lifetimes and must not be interchangeable.
        long stamp = fromDocument ? document.getModificationStamp() : file.getModificationStamp();
        Entry cached = cache.get(file);
        if (cached != null && cached.fromDocument == fromDocument && cached.modificationStamp == stamp) {
            return cached;
        }

        CharSequence source;
        try {
            source = fromDocument ? document.getImmutableCharSequence() : VfsUtilCore.loadText(file);
        } catch (IOException e) {
            cache.remove(file);
            return null;
        }

        // Keep the stamp captured before the snapshot, never a later stamp for older contents.
        Entry parsed = new Entry(stamp, fromDocument, source,
                Set.copyOf(HlslCompletionAnalysis.collectExportedIdentifiers(source)),
                List.copyOf(parseIncludes(source)));
        cache.put(file, parsed);
        return parsed;
    }

    static @NotNull List<String> parseIncludes(CharSequence source) {
        List<String> includes = new ArrayList<>();
        HlslLexer lexer = new HlslLexer();
        lexer.start(source, 0, source.length(), 0);
        boolean directiveStart = true;
        while (lexer.getTokenType() != null) {
            ProgressManager.checkCanceled();
            IElementType type = lexer.getTokenType();
            if (type == HlslTokenTypes.PREPROCESSOR && directiveStart) {
                String directive = source.subSequence(lexer.getTokenStart(), lexer.getTokenEnd()).toString()
                        .replace("\\\r\n", "").replace("\\\n", "");
                // Match only the directive itself: a #include in a macro body is not an include.
                Matcher match = INCLUDE_PATTERN.matcher(directive);
                if (match.find()) {
                    includes.add(match.group(1) != null ? match.group(1) : match.group(2));
                }
            }
            if (type == TokenType.WHITE_SPACE || type == HlslTokenTypes.LINE_COMMENT
                    || type == HlslTokenTypes.BLOCK_COMMENT) {
                // A leading comment is preprocessing whitespace, too.
                if (containsLineBreak(source, lexer.getTokenStart(), lexer.getTokenEnd())) directiveStart = true;
            } else {
                directiveStart = false;
            }
            lexer.advance();
        }
        return includes;
    }

    private static boolean containsLineBreak(CharSequence source, int start, int end) {
        for (int i = start; i < end; i++) {
            char c = source.charAt(i);
            if (c == '\n' || c == '\r') return true;
        }
        return false;
    }

    private static @Nullable VirtualFile resolve(VirtualFile fromFile, String include) {
        String normalized = include.replace('\\', '/');
        VirtualFile dir = fromFile.getParent();
        if (dir == null) return null;

        VirtualFile found = dir.findFileByRelativePath(normalized);
        if (found != null && !found.isDirectory()) return found;

        VirtualFile cur = dir.getParent();
        int walked = 0;
        while (cur != null && walked < MAX_PARENT_WALK) {
            ProgressManager.checkCanceled();
            found = cur.findFileByRelativePath(normalized);
            if (found != null && !found.isDirectory()) return found;
            cur = cur.getParent();
            walked++;
        }
        return null;
    }
}
