package com.hlsl.editor;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.lineIndent.LineIndentProvider;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.hlsl.HlslLanguage;

/**
 * Makes Enter indent HLSL code: pressing Enter after a line that opens a block puts the caret one level
 * deeper, and a line that starts with a closing brace is placed at the column of the line that opened it.
 */
public class HlslLineIndentProvider implements LineIndentProvider {

    @Override
    public @Nullable String getLineIndent(@NotNull Project project, @NotNull Editor editor,
                                          @Nullable Language language, int offset) {
        return HlslIndent.indentFor(editor, editor.getDocument().getLineNumber(offset));
    }

    @Override
    public boolean isSuitableFor(@Nullable Language language) {
        return language == HlslLanguage.INSTANCE;
    }
}
