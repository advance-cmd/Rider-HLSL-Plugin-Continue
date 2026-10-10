package com.hlsl.documentation;

import com.hlsl.validation.HlslDxcSettings;
import com.intellij.model.Pointer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Documents a system-value semantic or interpolation modifier: what the pipeline does with it, in the
 * language chosen on the settings page.
 */
final class HlslSemanticsTarget implements DocumentationTarget {

    private final PsiElement element;
    private final String name;

    HlslSemanticsTarget(PsiElement element, String name) {
        this.element = element;
        this.name = name;
    }

    /** The element is a semantics word only when the hints are on, which is what the provider already checked. */
    static @Nullable HlslSemanticsTarget of(@Nullable PsiElement element) {
        if (element == null || !HlslDxcSettings.getInstance().isSemanticsHintsEnabled()) return null;
        if (!HlslSemantics.isSemanticsWord(element)) return null;
        return new HlslSemanticsTarget(element, element.getText());
    }

    @Override
    public @NotNull Pointer<? extends DocumentationTarget> createPointer() {
        return Pointer.hardPointer(this);
    }

    @Override
    public @NotNull TargetPresentation computePresentation() {
        return TargetPresentation.builder(name).presentation();
    }

    /** What plain mouse hover shows: the hint is plain text, unlike the documentation popup. */
    @Override
    public @Nullable String computeDocumentationHint() {
        String description = HlslSemantics.description(name);
        return description == null ? null : name + " — " + description;
    }

    @Override
    public @Nullable DocumentationResult computeDocumentation() {
        String html = HlslSemantics.documentation(name);
        return html == null ? null : DocumentationResult.documentation(html);
    }

    @Override
    public @Nullable com.intellij.pom.Navigatable getNavigatable() {
        return element instanceof com.intellij.pom.Navigatable navigatable ? navigatable : null;
    }
}
