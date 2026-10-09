package com.hlsl.templates;

import com.hlsl.HlslLanguage;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/** Scopes the HLSL live templates so their abbreviations only expand inside HLSL files. */
public final class HlslTemplateContextType extends TemplateContextType {

    public HlslTemplateContextType() {
        super("HLSL", "HLSL");
    }

    @Override
    public boolean isInContext(@NotNull PsiFile file, int offset) {
        return file.getLanguage() == HlslLanguage.INSTANCE;
    }
}
