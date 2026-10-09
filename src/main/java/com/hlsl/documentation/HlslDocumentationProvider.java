package com.hlsl.documentation;

import com.hlsl.HlslLanguage;
import com.hlsl.completion.HlslCompletionAnalysis;
import com.hlsl.completion.HlslIncludeIndex;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Documents the symbol under the mouse with its declaration and the comment written above it, read from the
 * file and its transitive include chain — so a call to a helper shows the helper's signature.
 */
public final class HlslDocumentationProvider implements DocumentationProvider {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /** The documentation popup (Ctrl+Q) and its tool window. */
    @Override
    public @Nullable String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
        return documentationFor(element);
    }

    /** Plain mouse hover goes through this one. */
    @Override
    public @Nullable String generateHoverDoc(PsiElement element, @Nullable PsiElement originalElement) {
        return documentationFor(element);
    }

    @Override
    public @Nullable String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
        return documentationFor(element);
    }

    private static @Nullable String documentationFor(@Nullable PsiElement element) {
        if (element == null || element.getLanguage() != HlslLanguage.INSTANCE) return null;
        String name = element.getText();
        if (name == null || !IDENTIFIER.matcher(name).matches()) return null;
        PsiFile file = element.getContainingFile();
        if (file == null) return null;
        Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        if (document == null) return null;
        HlslCompletionAnalysis.Declaration declaration = HlslCompletionAnalysis.declarationOf(name,
                document.getImmutableCharSequence(), element.getTextRange().getStartOffset(), includeSources(file));
        return declaration == null ? null : render(declaration);
    }

    private static List<CharSequence> includeSources(PsiFile file) {
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile == null) return List.of();
        HlslIncludeIndex index = file.getProject().getService(HlslIncludeIndex.class);
        return index == null ? List.of() : index.collectTransitiveSources(virtualFile);
    }

    /** The declaration in bold, followed by the comment above it. */
    private static String render(HlslCompletionAnalysis.Declaration declaration) {
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
