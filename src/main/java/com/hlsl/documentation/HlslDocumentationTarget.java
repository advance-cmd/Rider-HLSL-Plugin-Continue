package com.hlsl.documentation;

import com.hlsl.completion.HlslCompletionAnalysis;
import com.intellij.model.Pointer;
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.presentation.TargetPresentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Documents one resolved declaration: the hover hint and the documentation popup text. */
final class HlslDocumentationTarget implements DocumentationTarget {

    private final com.intellij.psi.PsiElement element;
    private final HlslCompletionAnalysis.Declaration declaration;

    HlslDocumentationTarget(com.intellij.psi.PsiElement element, HlslCompletionAnalysis.Declaration declaration) {
        this.element = element;
        this.declaration = declaration;
    }

    @Override
    public @NotNull Pointer<? extends DocumentationTarget> createPointer() {
        return Pointer.hardPointer(this);
    }

    @Override
    public @NotNull TargetPresentation computePresentation() {
        return TargetPresentation.builder(declaration.text()).presentation();
    }

    /** What plain mouse hover shows. */
    @Override
    public @Nullable String computeDocumentationHint() {
        return HlslDocumentationLookup.html(declaration);
    }

    @Override
    public @Nullable DocumentationResult computeDocumentation() {
        return DocumentationResult.documentation(HlslDocumentationLookup.html(declaration));
    }

    @Override
    public @Nullable com.intellij.pom.Navigatable getNavigatable() {
        return element instanceof com.intellij.pom.Navigatable navigatable ? navigatable : null;
    }
}
