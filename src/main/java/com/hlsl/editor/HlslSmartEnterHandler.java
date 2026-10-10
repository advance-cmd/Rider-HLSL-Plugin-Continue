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

        // Only a line the caret stands on alone is rewritten: a line that already carries code, and a
        // closing brace, keep the indentation the platform gave them.
        Document document = editor.getDocument();
        int caret = editor.getCaretModel().getOffset();
        if (!isBlankBeforeCaret(document, caret)) return Result.Continue;
        if (HlslIndent.startsWithClosingBrace(document, document.getLineNumber(caret))) return Result.Continue;

        applyIndent(editor, document, caret);
        return Result.Stop;
    }

    /**
     * Leaves the caret line carrying exactly the indentation the plugin computed. The platform's own pass
     * has already run by now and does not know about an unbraced {@code if} or an {@code else} clause, so its
     * answer is replaced rather than trusted.
     */
    static void applyIndent(@NotNull Editor editor, @NotNull Document document, int caret) {
        int line = document.getLineNumber(caret);
        int lineStart = document.getLineStartOffset(line);
        String indent = HlslIndent.indentFor(editor, line);
        int end = caret;
        CharSequence text = document.getCharsSequence();
        while (end > lineStart && (text.charAt(end - 1) == ' ' || text.charAt(end - 1) == '\t')) end--;
        document.replaceString(end, caret, "");
        document.insertString(end, indent);
        editor.getCaretModel().moveToOffset(end + indent.length());
    }

    private static boolean isBlankBeforeCaret(@NotNull Document document, int caret) {
        return isBlank(document.getCharsSequence(),
                document.getLineStartOffset(document.getLineNumber(caret)), caret);
    }

    private static boolean isBlank(CharSequence text, int start, int end) {
        for (int i = start; i < end; i++) {
            if (!Character.isWhitespace(text.charAt(i))) return false;
        }
        return true;
    }
}
