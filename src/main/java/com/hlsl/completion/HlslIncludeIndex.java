package com.hlsl.completion;

import com.hlsl.lexer.HlslLexer;
import com.hlsl.psi.HlslTokenTypes;
import com.hlsl.validation.HlslCompileRoot;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
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

    private final Project project;
    private final ConcurrentHashMap<VirtualFile, Entry> cache = new ConcurrentHashMap<>();

    public HlslIncludeIndex(Project project) {
        this.project = project;
    }

    /** An {@code #include} as written: the path it names, and the offset its directive starts at. */
    private record Include(String path, int offset) { }

    private static final class Entry {
        final long modificationStamp;
        final boolean fromDocument;
        final CharSequence source;
        final Set<String> identifiers;
        final List<Include> includes;

        Entry(long stamp, boolean fromDocument, CharSequence source,
              Set<String> identifiers, List<Include> includes) {
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

    /**
     * Every source code insight in {@code file} reads besides the file itself. First what the file includes;
     * then, for a helper that other shaders include, what each shader on the chain down to it declares before
     * its {@code #include} of the next file, the nearest shader first: its text up to that directive, followed
     * by what that text includes. A Shadow.hlsl that LightingUtil.hlsl includes after declaring PassConstants
     * therefore knows PassConstants, but not what LightingUtil.hlsl declares after the include.
     *
     * <p>{@code text} is the file as it is edited: its {@code #pragma hlsl root} picks the chain, exactly as
     * for validation.</p>
     */
    public @NotNull List<CharSequence> collectVisibleSources(@Nullable VirtualFile file, @NotNull CharSequence text) {
        if (file == null) return Collections.emptyList();
        Entry own = getOrParse(file);
        if (own == null) return Collections.emptyList();
        List<CharSequence> result = new ArrayList<>();
        Consumer<Entry> add = entry -> result.add(entry.source);
        Set<VirtualFile> visited = new HashSet<>();
        visited.add(file);
        collectIncludes(file, own, visited, 0, Integer.MAX_VALUE, add);

        HlslCompileRoot roots = project.getService(HlslCompileRoot.class);
        HlslCompileRoot.Chain chain = roots == null ? null : roots.findFor(file, text);
        if (chain == null) return result;
        List<VirtualFile> files = chain.files();
        // A file of the chain contributes only its text above the include, never the whole of it.
        visited.addAll(files);
        for (int i = files.size() - 2; i >= 0; i--) {
            VirtualFile includer = files.get(i);
            Entry entry = getOrParse(includer);
            int offset = entry == null ? -1 : includeOffset(includer, entry.includes, files.get(i + 1));
            if (offset < 0) continue;
            result.add(entry.source.subSequence(0, offset));
            collectIncludes(includer, entry, visited, 0, offset, add);
        }
        return result;
    }

    /**
     * Where the text of {@code includer} includes {@code included}: the offset of the first {@code #include}
     * that resolves to it, or -1 when none does.
     */
    public static int includeOffset(@NotNull CharSequence source, @NotNull VirtualFile includer,
                                    @NotNull VirtualFile included) {
        return includeOffset(includer, parseDirectives(source), included);
    }

    private static int includeOffset(VirtualFile includer, List<Include> includes, VirtualFile included) {
        for (Include include : includes) {
            if (included.equals(resolve(includer, include.path()))) return include.offset();
        }
        return -1;
    }

    /** The files {@code file} includes itself, resolved the way completion resolves them. */
    public @NotNull List<VirtualFile> directIncludes(@NotNull VirtualFile file) {
        Entry entry = getOrParse(file);
        if (entry == null) return List.of();
        List<VirtualFile> result = new ArrayList<>();
        for (Include include : entry.includes) {
            VirtualFile resolved = resolve(file, include.path());
            if (resolved != null && !result.contains(resolved)) result.add(resolved);
        }
        return result;
    }

    private void forEachIncludedEntry(VirtualFile file, Consumer<Entry> consumer) {
        Entry root = getOrParse(file);
        if (root == null) return;
        Set<VirtualFile> visited = new HashSet<>();
        visited.add(file);
        collectIncludes(file, root, visited, 0, Integer.MAX_VALUE, consumer);
    }

    /** Follows the includes of {@code entry} whose directive starts before {@code limit}, and all of theirs. */
    private void collectIncludes(VirtualFile file, Entry entry, Set<VirtualFile> visited,
                                 int depth, int limit, Consumer<Entry> consumer) {
        ProgressManager.checkCanceled();
        if (depth >= MAX_INCLUDE_DEPTH) return;
        for (Include include : entry.includes) {
            ProgressManager.checkCanceled();
            if (include.offset() >= limit) break;
            VirtualFile resolved = resolve(file, include.path());
            if (resolved == null || !visited.add(resolved)) continue;
            Entry included = getOrParse(resolved);
            if (included == null) continue;
            consumer.accept(included);
            collectIncludes(resolved, included, visited, depth + 1, Integer.MAX_VALUE, consumer);
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
                List.copyOf(parseDirectives(source)));
        cache.put(file, parsed);
        return parsed;
    }

    static @NotNull List<String> parseIncludes(CharSequence source) {
        return parseDirectives(source).stream().map(Include::path).toList();
    }

    private static List<Include> parseDirectives(CharSequence source) {
        List<Include> includes = new ArrayList<>();
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
                    includes.add(new Include(match.group(1) != null ? match.group(1) : match.group(2),
                            lexer.getTokenStart()));
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

    /**
     * The range of the path inside an {@code #include} directive, without its quotes or angle brackets, or
     * {@code null} when the directive is not an include.
     */
    public static @Nullable TextRange includePathRange(CharSequence directive) {
        Matcher match = INCLUDE_PATTERN.matcher(directive);
        if (!match.find()) return null;
        int group = match.group(1) != null ? 1 : 2;
        return new TextRange(match.start(group), match.end(group));
    }

    private static boolean containsLineBreak(CharSequence source, int start, int end) {
        for (int i = start; i < end; i++) {
            char c = source.charAt(i);
            if (c == '\n' || c == '\r') return true;
        }
        return false;
    }

    /**
     * The file an include names: relative to the including file's directory first, then to each directory
     * above it, which is where shader trees usually keep their shared headers.
     */
    public static @Nullable VirtualFile resolve(VirtualFile fromFile, String include) {
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
