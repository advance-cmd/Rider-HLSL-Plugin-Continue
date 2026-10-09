package com.hlsl.documentation;

import com.hlsl.completion.HlslCompletionAnalysis;
import com.intellij.model.Pointer;
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Documents one resolved declaration: the hover hint and the documentation popup text. */
final class HlslDocumentationTarget implements DocumentationTarget {

    private final PsiElement element;
    private final HlslCompletionAnalysis.Declaration declaration;

    HlslDocumentationTarget(PsiElement element, HlslCompletionAnalysis.Declaration declaration) {
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

    /** What plain mouse hover shows; the hint is plain text, unlike the documentation popup. */
    @Override
    public @Nullable String computeDocumentationHint() {
        HlslDocumentationLookup.log("target.computeDocumentationHint", declaration.name(), "returned");
        return HlslDocumentationLookup.plainText(declaration);
    }

    @Override
    public @Nullable DocumentationResult computeDocumentation() {
        HlslDocumentationLookup.log("target.computeDocumentation", declaration.name(), "returned");
        return DocumentationResult.documentation(HlslDocumentationLookup.html(declaration));
    }

    @Override
    public @Nullable com.intellij.pom.Navigatable getNavigatable() {
        return element instanceof com.intellij.pom.Navigatable navigatable ? navigatable : null;
    }
}
