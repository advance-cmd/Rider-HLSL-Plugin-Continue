package com.hlsl.documentation;

import com.hlsl.HlslLanguage;
import com.hlsl.completion.HlslCompletionAnalysis;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
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
     * The platform asks for this when it cannot identify the element itself, which happens for a language
     * without a rich PSI: the element is then chosen from the document at the hovered offset.
     */
    @Override
    public @Nullable PsiElement getCustomDocumentationElement(@NotNull Editor editor, @NotNull PsiFile file,
                                                              @Nullable PsiElement contextElement,
                                                              int contextOffset) {
        if (file.getLanguage() != HlslLanguage.INSTANCE) return null;
        PsiElement candidate = contextElement != null ? contextElement : file.findElementAt(contextOffset);
        return HlslDocumentationLookup.resolve(candidate) == null ? null : candidate;
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
