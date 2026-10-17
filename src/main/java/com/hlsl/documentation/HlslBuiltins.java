package com.hlsl.documentation;

import com.hlsl.HlslFileType;
import com.hlsl.HlslLanguage;
import com.hlsl.completion.HlslCompletionAnalysis;
import com.hlsl.psi.HlslTokenTypes;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The intrinsic functions, built-in types and attributes of HLSL, such as {@code reversebits},
 * {@code RWTexture2D} or {@code [numthreads(8, 8, 1)]}.
 *
 * <p>The compiler provides these names, so no source file declares them, and hover and Go to Declaration had
 * nothing to show for them. Their declarations are kept in {@code builtins.hlsl}, and the plugin generates a
 * read-only file from it with the description of each declaration written above it as a comment. The
 * descriptions come from a resource bundle, so the file reads in the language chosen for the hints; one file
 * is generated per language and kept for the session.</p>
 */
public final class HlslBuiltins {

    public enum Kind { FUNCTION, TYPE, ATTRIBUTE }

    /** One built-in: its declaration as written in the file, and the offset of its name in that file. */
    public record Entry(String name, Kind kind, String declaration, int nameOffset) { }

    private static final String TEMPLATE = "/hlsl/builtins/builtins.hlsl";
    private static final String FILE_NAME = "HLSL built-ins.hlsl";
    private static final HlslHintBundle TEXT = new HlslHintBundle("hlsl.builtins.builtins");
    private static final Key<Boolean> BUILTINS_FILE = Key.create("hlsl.builtins.file");

    private static final Pattern TYPE_DECLARATION =
            Pattern.compile("(?m)^(?:class|struct)\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern FUNCTION_NAME = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");
    private static final Pattern ATTRIBUTE_DECLARATION = Pattern.compile("^\\[([A-Za-z_][A-Za-z0-9_]*)");

    /** A blank-line separated block of the template: one type, or every form of one function or attribute. */
    private record Group(String name, Kind kind, String declaration, int nameOffset) { }

    private record Stub(LightVirtualFile file, Map<String, Entry> entries) { }

    private static final List<Group> GROUPS = readTemplate();
    private static final Map<String, Stub> STUBS = new ConcurrentHashMap<>();

    private HlslBuiltins() {
    }

    /** The built-in declared under {@code name}, or {@code null} for a name the compiler does not provide. */
    public static @Nullable Entry entry(@NotNull String name) {
        return stub().entries().get(name);
    }

    /** Every name declared in the built-ins file. */
    public static @NotNull List<String> names() {
        List<String> names = new ArrayList<>(GROUPS.size());
        for (Group group : GROUPS) names.add(group.name());
        return names;
    }

    /** What a built-in does, in the configured language, without markup. */
    public static @Nullable String description(@NotNull String name) {
        return TEXT.text(name);
    }

    /**
     * The built-in name the element is, or {@code null}: only the tokens the lexer reads as an intrinsic
     * function or a built-in type count, and the name of an attribute only inside its brackets, so a field or
     * variable that happens to share a name does not.
     */
    public static @Nullable String nameOf(@Nullable PsiElement element) {
        if (element == null || element.getNode() == null) return null;
        PsiFile file = element.getContainingFile();
        if (file == null || file.getLanguage() != HlslLanguage.INSTANCE) return null;
        IElementType type = element.getNode().getElementType();
        String name = element.getText();
        Entry entry = entry(name);
        if (entry == null) return null;
        if (entry.kind() == Kind.ATTRIBUTE) {
            // Inside [numthreads(8, 8, 1)] the name is lexed as a call, inside [unroll] as a plain name.
            if (type != HlslTokenTypes.FUNCTION_CALL && type != HlslTokenTypes.IDENTIFIER) return null;
            boolean attribute = HlslCompletionAnalysis.isAttributeName(
                    file.getViewProvider().getContents(), element.getTextRange().getStartOffset());
            return attribute ? name : null;
        }
        return type == HlslTokenTypes.BUILTIN_FUNCTION || type == HlslTokenTypes.TYPE_KEYWORD ? name : null;
    }

    /** The name in the generated built-ins file, which is where Go to Declaration takes a built-in to. */
    public static @Nullable PsiElement declaration(@NotNull Project project, @NotNull String name) {
        Stub stub = stub();
        Entry entry = stub.entries().get(name);
        if (entry == null) return null;
        PsiFile file = PsiManager.getInstance(project).findFile(stub.file());
        if (file == null) return null;
        PsiElement element = file.findElementAt(entry.nameOffset());
        return element != null && name.equals(element.getText()) ? element : null;
    }

    /** The generated file for the configured language. */
    public static @NotNull VirtualFile file() {
        return stub().file();
    }

    /** True for the generated file, which is not a shader and must not be compiled. */
    public static boolean isBuiltinsFile(@Nullable VirtualFile file) {
        return file != null && Boolean.TRUE.equals(file.getUserData(BUILTINS_FILE));
    }

    /** The documentation popup: the declaration, what kind of built-in it is, and what it does. */
    public static @Nullable String documentation(@NotNull String name) {
        Entry entry = entry(name);
        if (entry == null) return null;
        StringBuilder html = new StringBuilder(DocumentationMarkup.DEFINITION_START)
                .append(StringUtil.escapeXmlEntities(entry.declaration()))
                .append(DocumentationMarkup.DEFINITION_END)
                .append(DocumentationMarkup.CONTENT_START)
                .append(DocumentationMarkup.GRAYED_START)
                .append(StringUtil.escapeXmlEntities(kindName(entry.kind())))
                .append(DocumentationMarkup.GRAYED_END);
        String description = description(name);
        if (description != null) html.append("<br>").append(StringUtil.escapeXmlEntities(description));
        return html.append(DocumentationMarkup.CONTENT_END).toString();
    }

    /** The hint shown while Ctrl is held over the name: the signature and the description, as plain text. */
    public static @Nullable String hint(@NotNull String name) {
        Entry entry = entry(name);
        if (entry == null) return null;
        String description = description(name);
        String summary = summary(entry);
        return description == null ? summary : summary + "\n" + description;
    }

    /** The first form of a function or an attribute, or the head of a type up to its name. */
    private static String summary(Entry entry) {
        String declaration = entry.declaration();
        if (entry.kind() != Kind.TYPE) {
            int lineEnd = declaration.indexOf('\n');
            String first = lineEnd < 0 ? declaration : declaration.substring(0, lineEnd);
            return StringUtil.trimEnd(first.strip(), ";");
        }
        Matcher type = TYPE_DECLARATION.matcher(declaration);
        String head = type.find() ? declaration.substring(0, type.end()) : entry.name();
        return head.replaceAll("\\s+", " ").strip();
    }

    private static String kindName(Kind kind) {
        String localized = TEXT.text("kind." + kind.name().toLowerCase(Locale.ROOT));
        return localized != null ? localized : kind.name().toLowerCase(Locale.ROOT);
    }

    private static Stub stub() {
        String language = HlslHintBundle.locale().getLanguage();
        return STUBS.computeIfAbsent(language.isEmpty() ? "en" : language, ignored -> generate());
    }

    /** Writes the declarations of the template with the description of each one above it. */
    private static Stub generate() {
        StringBuilder text = new StringBuilder();
        String header = TEXT.text("header");
        if (header != null) appendComment(text, header);
        Map<String, Entry> entries = new LinkedHashMap<>();
        for (Group group : GROUPS) {
            text.append('\n');
            String description = TEXT.text(group.name());
            if (description != null) appendComment(text, description);
            int start = text.length();
            text.append(group.declaration()).append('\n');
            entries.put(group.name(),
                    new Entry(group.name(), group.kind(), group.declaration(), start + group.nameOffset()));
        }
        LightVirtualFile file = new LightVirtualFile(FILE_NAME, HlslFileType.INSTANCE, text.toString());
        file.setWritable(false);
        file.putUserData(BUILTINS_FILE, Boolean.TRUE);
        return new Stub(file, Map.copyOf(entries));
    }

    private static void appendComment(StringBuilder text, String comment) {
        for (String line : comment.split("\n")) text.append("// ").append(line.strip()).append('\n');
    }

    private static List<Group> readTemplate() {
        String template;
        try (InputStream in = HlslBuiltins.class.getResourceAsStream(TEMPLATE)) {
            if (in == null) throw new IllegalStateException("The plugin is missing " + TEMPLATE);
            template = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<Group> groups = new ArrayList<>();
        for (String block : template.split("\n[ \t]*\n")) {
            String declaration = block.strip();
            if (declaration.isEmpty()) continue;
            Matcher attribute = ATTRIBUTE_DECLARATION.matcher(declaration);
            if (attribute.find()) {
                groups.add(new Group(attribute.group(1), Kind.ATTRIBUTE, declaration, attribute.start(1)));
                continue;
            }
            Matcher type = TYPE_DECLARATION.matcher(declaration);
            if (type.find()) {
                groups.add(new Group(type.group(1), Kind.TYPE, declaration, type.start(1)));
                continue;
            }
            Matcher function = FUNCTION_NAME.matcher(declaration);
            if (function.find()) {
                groups.add(new Group(function.group(1), Kind.FUNCTION, declaration, function.start(1)));
            }
        }
        return List.copyOf(groups);
    }
}
