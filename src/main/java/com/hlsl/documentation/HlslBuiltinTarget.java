package com.hlsl.documentation;

import com.intellij.model.Pointer;
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Documents an intrinsic function or a built-in type: its declaration and what it does. */
final class HlslBuiltinTarget implements DocumentationTarget {

    private final PsiElement element;
    private final String name;

    private HlslBuiltinTarget(PsiElement element, String name) {
        this.element = element;
        this.name = name;
    }

    static @Nullable HlslBuiltinTarget of(@Nullable PsiElement element) {
        String name = HlslBuiltins.nameOf(element);
        return name == null ? null : new HlslBuiltinTarget(element, name);
    }

    @Override
    public @NotNull Pointer<? extends DocumentationTarget> createPointer() {
        return Pointer.hardPointer(this);
    }

    @Override
    public @NotNull TargetPresentation computePresentation() {
        return TargetPresentation.builder(name).presentation();
    }

    /** What Ctrl+hover shows; the hint is plain text, unlike the documentation popup. */
    @Override
    public @Nullable String computeDocumentationHint() {
        return HlslBuiltins.hint(name);
    }

    @Override
    public @Nullable DocumentationResult computeDocumentation() {
        String html = HlslBuiltins.documentation(name);
        return html == null ? null : DocumentationResult.documentation(html);
    }

    /** The declaration in the built-ins file, so the popup's jump to source lands where Ctrl+Click does. */
    @Override
    public @Nullable Navigatable getNavigatable() {
        PsiElement declaration = HlslBuiltins.declaration(element.getProject(), name);
        return declaration instanceof Navigatable navigatable ? navigatable : null;
    }
}
