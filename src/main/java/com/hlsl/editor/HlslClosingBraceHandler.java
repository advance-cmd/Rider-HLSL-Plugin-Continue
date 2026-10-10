package com.hlsl.editor;

import com.hlsl.HlslLanguage;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;

import org.jetbrains.annotations.NotNull;

/**
 * A closing brace typed on its own line is put back at the column of the line that opened the block, so
 * finishing a body by hand gives the same result as pressing Enter inside an empty one.
 */
public class HlslClosingBraceHandler extends TypedHandlerDelegate {

    @Override
    public Result charTyped(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        if (c != '}' || file.getLanguage() != HlslLanguage.INSTANCE) return Result.CONTINUE;

        int caret = editor.getCaretModel().getOffset();
        Document document = editor.getDocument();
        int brace = caret - 1;
        if (brace < 0 || document.getCharsSequence().charAt(brace) != '}') return Result.CONTINUE;
        // Only a brace that starts its line is realigned: one written after code belongs where it is.
        if (!HlslIndent.isFirstOnLine(document, brace)) return Result.CONTINUE;

        String indent = HlslIndent.indentFor(editor, document.getLineNumber(brace));
        int lineStart = document.getLineStartOffset(document.getLineNumber(brace));
        String current = document.getText(new TextRange(lineStart, brace));
        if (current.equals(indent)) return Result.CONTINUE;

        document.replaceString(lineStart, brace, indent);
        editor.getCaretModel().moveToOffset(lineStart + indent.length() + 1);
        return Result.STOP;
    }
}
