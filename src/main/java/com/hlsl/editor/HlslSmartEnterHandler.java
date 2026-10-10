package com.hlsl.editor;

import com.hlsl.HlslLanguage;
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiFile;

import org.jetbrains.annotations.NotNull;

/**
 * Enter typed inside an empty block, with the caret between the braces:
 *
 * <pre>
 * float OcclusionFunction(float dist) {|}
 *
 * becomes
 *
 * float OcclusionFunction(float dist) {
 *     |
 * }
 * </pre>
 *
 * <p>Moving the two braces apart is the platform's job, so this handler only makes sure the line the caret
 * ends up on carries the block's indentation: the brace style is whatever the IDE is configured to use, and
 * the line indent provider answers for every other line.</p>
 */
public class HlslSmartEnterHandler implements EnterHandlerDelegate {

    @Override
    public Result preprocessEnter(@NotNull PsiFile file, @NotNull Editor editor,
                                  @NotNull Ref<Integer> caretOffsetRef, @NotNull Ref<Integer> caretAdvance,
                                  @NotNull DataContext dataContext, EditorActionHandler originalHandler) {
        return Result.Continue;
    }

    @Override
    public Result postProcessEnter(@NotNull PsiFile file, @NotNull Editor editor,
                                   @NotNull DataContext dataContext) {
        if (file.getLanguage() != HlslLanguage.INSTANCE) return Result.Continue;

        Document document = editor.getDocument();
        int caret = editor.getCaretModel().getOffset();
        if (!HlslIndent.isInsideEmptyBlock(document, caret)) return Result.Continue;

        // Only the whitespace the caret stands on is rewritten, and only when it is whitespace: a line that
        // already carries code is left alone.
        int lineStart = document.getLineStartOffset(document.getLineNumber(caret));
        if (!isBlank(document.getCharsSequence(), lineStart, caret)) return Result.Continue;

        String indent = HlslIndent.indentFor(editor, document.getLineNumber(caret));
        document.replaceString(lineStart, caret, indent);
        editor.getCaretModel().moveToOffset(lineStart + indent.length());
        return Result.Stop;
    }

    private static boolean isBlank(CharSequence text, int start, int end) {
        for (int i = start; i < end; i++) {
            if (!Character.isWhitespace(text.charAt(i))) return false;
        }
        return true;
    }
}
