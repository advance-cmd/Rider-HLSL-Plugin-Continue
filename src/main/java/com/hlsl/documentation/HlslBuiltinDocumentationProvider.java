package com.hlsl.documentation;

import com.hlsl.HlslLanguage;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Documents an intrinsic function such as {@code reversebits} or a built-in type such as {@code RWTexture2D}
 * on hover. No source file declares them, so the provider that shows declarations has nothing for them.
 */
public final class HlslBuiltinDocumentationProvider implements DocumentationProvider {

    @Override
    public @Nullable String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
        return documentationFor(element, originalElement);
    }

    @Override
    public @Nullable String generateHoverDoc(PsiElement element, @Nullable PsiElement originalElement) {
        return documentationFor(element, originalElement);
    }

    @Override
    public @Nullable String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
        return documentationFor(element, originalElement);
    }

    /** Claims the hover for a built-in name and leaves everything else to the other providers. */
    @Override
    public @Nullable PsiElement getCustomDocumentationElement(@NotNull Editor editor, @NotNull PsiFile file,
                                                              @Nullable PsiElement contextElement,
                                                              int contextOffset) {
        if (file.getLanguage() != HlslLanguage.INSTANCE) return null;
        PsiElement candidate = contextElement != null ? contextElement : file.findElementAt(contextOffset);
        return HlslBuiltins.nameOf(candidate) != null ? candidate : null;
    }

    /** The element may be the declaration in the built-ins file and the original the name in the shader. */
    private static @Nullable String documentationFor(@Nullable PsiElement element, @Nullable PsiElement original) {
        String name = HlslBuiltins.nameOf(original);
        if (name == null) name = HlslBuiltins.nameOf(element);
        return name == null ? null : HlslBuiltins.documentation(name);
    }
}
