package com.hlsl.documentation;

import com.hlsl.completion.HlslCompletionAnalysis;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * Documents the symbol under the mouse with its declaration and the comment written above it, read from the
 * file and its transitive include chain — so a call to a helper shows the helper's signature.
 */
public final class HlslDocumentationProvider implements DocumentationProvider {

    /** The documentation popup (Ctrl+Q) and its tool window. */
    @Override
    public @Nullable String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
        return documentationFor(element, originalElement);
    }

    /** Mouse hover, on the code paths that still go through the legacy provider. */
    @Override
    public @Nullable String generateHoverDoc(PsiElement element, @Nullable PsiElement originalElement) {
        return documentationFor(element, originalElement);
    }

    @Override
    public @Nullable String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
        return documentationFor(element, originalElement);
    }

    /**
     * The platform hands over the element under the cursor, which is not necessarily the token itself, so
     * both candidates are tried and the identifier is read from the document.
     */
    private static @Nullable String documentationFor(@Nullable PsiElement element, @Nullable PsiElement original) {
        HlslCompletionAnalysis.Declaration declaration =
                HlslDocumentationLookup.resolve(original != null ? original : element);
        if (declaration == null && element != original) {
            declaration = HlslDocumentationLookup.resolve(element);
        }
        return declaration == null ? null : HlslDocumentationLookup.html(declaration);
    }
}
