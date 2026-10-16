package com.hlsl.documentation;

import com.hlsl.completion.HlslCompletionAnalysis;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Registers hover and quick documentation with the platform's current API. The legacy
 * {@link HlslDocumentationProvider} stays registered for older code paths, but hover in current IDEs is
 * resolved through documentation targets like this one.
 */
public final class HlslDocumentationTargetProvider implements PsiDocumentationTargetProvider {

    @Override
    public @Nullable DocumentationTarget documentationTarget(@NotNull PsiElement element,
                                                            @Nullable PsiElement originalElement) {
        PsiElement symbol = originalElement != null ? originalElement : element;
        // A semantic, such as the SV_Target of "float4 main() : SV_Target", is explained rather than resolved
        // as a declaration.
        DocumentationTarget semantics = HlslSemanticsTarget.of(symbol);
        if (semantics == null && symbol != element) semantics = HlslSemanticsTarget.of(element);
        if (semantics != null) return semantics;

        // An intrinsic function or a built-in type is declared by the compiler, not by any source file.
        DocumentationTarget builtin = HlslBuiltinTarget.of(symbol);
        if (builtin == null && symbol != element) builtin = HlslBuiltinTarget.of(element);
        if (builtin != null) return builtin;

        HlslCompletionAnalysis.Declaration declaration =
                HlslDocumentationLookup.resolve(symbol, "psiTargetProvider");
        if (declaration == null && symbol != element) {
            declaration = HlslDocumentationLookup.resolve(element, "psiTargetProvider(element)");
        }
        return declaration == null ? null : new HlslDocumentationTarget(symbol, declaration);
    }
}
