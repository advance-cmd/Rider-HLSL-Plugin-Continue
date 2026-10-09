package com.hlsl.documentation;

import com.hlsl.HlslLanguage;
import com.hlsl.completion.HlslCompletionAnalysis;
import com.hlsl.completion.HlslIncludeIndex;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Pattern;

/** Shared resolution and rendering for both documentation entry points. */
final class HlslDocumentationLookup {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private HlslDocumentationLookup() { }

    /**
     * The declaration of the symbol the element points at, following the include chain.
     *
     * <p>The identifier is read from the document at the element's offset rather than from the element's own
     * text: the platform may hand over a composite element instead of the token, and its text is then not an
     * identifier even though the symbol under the cursor is one.</p>
     */
    static @Nullable HlslCompletionAnalysis.Declaration resolve(@Nullable PsiElement element) {
        if (element == null) return null;
        PsiFile file = element.getContainingFile();
        if (file == null || file.getLanguage() != HlslLanguage.INSTANCE) return null;
        Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        if (document == null) return null;
        int offset = element.getTextRange().getStartOffset();
        CharSequence text = document.getImmutableCharSequence();
        String name = nameAt(text, offset);
        if (name == null) return null;
        return HlslCompletionAnalysis.declarationOf(name, text, offset, includeSources(file));
    }

    /** The identifier at an offset, looking one character back for offsets that sit just after a word. */
    static @Nullable String nameAt(CharSequence text, int offset) {
        String word = wordAt(text, offset);
        return word != null ? word : wordAt(text, offset - 1);
    }

    private static @Nullable String wordAt(CharSequence text, int offset) {
        if (offset < 0 || offset >= text.length() || !isIdentifierPart(text.charAt(offset))) return null;
        int start = offset;
        while (start > 0 && isIdentifierPart(text.charAt(start - 1))) start--;
        int end = offset;
        while (end < text.length() && isIdentifierPart(text.charAt(end))) end++;
        String word = text.subSequence(start, end).toString();
        return IDENTIFIER.matcher(word).matches() ? word : null;
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /** A callee is often declared by an included helper, exactly as for completion and parameter info. */
    static List<CharSequence> includeSources(PsiFile file) {
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile == null) return List.of();
        HlslIncludeIndex index = file.getProject().getService(HlslIncludeIndex.class);
        return index == null ? List.of() : index.collectTransitiveSources(virtualFile);
    }

    /** The declaration in bold, followed by the comment written above it. */
    static String html(HlslCompletionAnalysis.Declaration declaration) {
        StringBuilder html = new StringBuilder("<html><b>")
                .append(StringUtil.escapeXmlEntities(declaration.text()))
                .append("</b>");
        if (!declaration.comment().isEmpty()) {
            html.append("<br>")
                    .append(StringUtil.escapeXmlEntities(declaration.comment()).replace("\n", "<br>"));
        }
        return html.append("</html>").toString();
    }
}
