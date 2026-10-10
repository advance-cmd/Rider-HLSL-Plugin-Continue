package com.hlsl.documentation;

import com.hlsl.HlslLanguage;
import com.hlsl.validation.HlslDxcSettings;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Explains a system-value semantic or an interpolation modifier on hover, for example the {@code SV_Target}
 * of {@code float4 main() : SV_Target} or the {@code linear} of {@code float2 uv : linear}.
 *
 * <p>Registered before the provider that shows declarations, because the platform stops at the first
 * provider that claims the hovered element and a semantic must not be resolved as a declaration.</p>
 */
public final class HlslSemanticsDocumentationProvider implements DocumentationProvider {

    @Override
    public @Nullable String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
        return documentationFor(element);
    }

    @Override
    public @Nullable String generateHoverDoc(PsiElement element, @Nullable PsiElement originalElement) {
        return documentationFor(element);
    }

    @Override
    public @Nullable String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
        return documentationFor(element);
    }

    /** Claims the hover for a semantics word and leaves everything else to the other providers. */
    @Override
    public @Nullable PsiElement getCustomDocumentationElement(@NotNull Editor editor, @NotNull PsiFile file,
                                                              @Nullable PsiElement contextElement,
                                                              int contextOffset) {
        if (file.getLanguage() != HlslLanguage.INSTANCE) return null;
        if (!HlslDxcSettings.getInstance().isSemanticsHintsEnabled()) return null;
        PsiElement candidate = contextElement != null ? contextElement : file.findElementAt(contextOffset);
        return HlslSemantics.isSemanticsWord(candidate) ? candidate : null;
    }

    private static @Nullable String documentationFor(@Nullable PsiElement element) {
        return HlslSemantics.isSemanticsWord(element) ? HlslSemantics.documentation(element.getText()) : null;
    }
}
