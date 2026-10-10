package com.hlsl.editor;

import com.hlsl.HlslLanguage;
import com.intellij.codeInsight.editorActions.CopyPastePostProcessor;
import com.intellij.codeInsight.editorActions.TextBlockTransferableData;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.Transferable;
import java.util.Collections;
import java.util.List;

/**
 * Keeps the indentation of pasted HLSL code under control.
 *
 * <p>The platform indents every pasted line by the target line's own indentation, but it does not remove the
 * indentation the pasted block already carries. A block copied from a function body, four spaces in, and
 * pasted onto a four-space line inside another function body therefore lands eight spaces in, and a second
 * paste doubles it again. This processor puts the block at the target indentation instead, keeping the
 * relative indentation inside the block.</p>
 */
public class HlslPasteIndentProcessor extends CopyPastePostProcessor<TextBlockTransferableData> {

    @Override
    public @NotNull List<TextBlockTransferableData> collectTransferableData(@NotNull PsiFile file,
                                                                            @NotNull Editor editor,
                                                                            int @NotNull [] startOffsets,
                                                                            int @NotNull [] endOffsets) {
        return Collections.emptyList();
    }

    @Override
    public @Nullable List<TextBlockTransferableData> extractTransferableData(@NotNull Transferable content) {
        return Collections.emptyList();
    }

    @Override
    public void processTransferableData(@NotNull Project project, @NotNull Editor editor,
                                        @NotNull RangeMarker bounds, int caretOffset,
                                        @NotNull Ref<? super Boolean> indented,
                                        @NotNull List<? extends TextBlockTransferableData> values) {
        PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        if (file == null || file.getLanguage() != HlslLanguage.INSTANCE) return;

        Document document = editor.getDocument();
        int start = bounds.getStartOffset();
        int end = bounds.getEndOffset();
        if (start < 0 || end > document.getTextLength() || start >= end) return;

        int blockIndent = indentOfLinesAfterTheFirst(document, start, end);
        if (blockIndent < 0) return;
        int targetIndent = HlslIndent.indentFor(editor, document.getLineNumber(start)).length();
        if (blockIndent == targetIndent) return;

        reindent(document, start, end, blockIndent, targetIndent);
    }

    /**
     * The narrowest indentation among the lines the pasted text starts, ignoring the first one: that line
     * begins where the caret was, so whatever stands in front of it belongs to the target, not to the block.
     */
    private static int indentOfLinesAfterTheFirst(@NotNull Document document, int start, int end) {
        int firstLine = document.getLineNumber(start);
        int lastLine = document.getLineNumber(Math.max(start, end - 1));
        int minimum = -1;
        for (int line = firstLine + 1; line <= lastLine; line++) {
            int width = leadingWidth(document, line);
            if (width < 0) continue;
            if (minimum < 0 || width < minimum) minimum = width;
        }
        return minimum;
    }

    /**
     * Moves every line of the pasted region by the difference between its own indentation and the target
     * one, so the block lines up where it was pasted and keeps the shape it was copied with.
     */
    private static void reindent(@NotNull Document document, int start, int end, int from, int to) {
        int first = document.getLineNumber(start);
        int last = document.getLineNumber(Math.max(start, end - 1));
        for (int line = first; line <= last; line++) {
            int lineStart = document.getLineStartOffset(line);
            int lineEnd = document.getLineEndOffset(line);
            int width = leadingWhitespaceWidth(document, lineStart, lineEnd);
            // A line that holds nothing but whitespace is left as it is.
            if (width < 0 || lineStart + width >= lineEnd) continue;
            if (line == first) {
                // The first line carries no indentation of its own unless the paste added some.
                if (width == 0) continue;
                setLeadingWhitespace(document, lineStart, width, to);
            } else {
                setLeadingWhitespace(document, lineStart, width, Math.max(0, width - from) + to);
            }
        }
    }

    private static int leadingWidth(@NotNull Document document, int line) {
        int lineStart = document.getLineStartOffset(line);
        int lineEnd = document.getLineEndOffset(line);
        return leadingWhitespaceWidth(document, lineStart, lineEnd);
    }

    /** The width of the line's indentation, or {@code -1} when the line is empty. */
    private static int leadingWhitespaceWidth(@NotNull Document document, int lineStart, int lineEnd) {
        CharSequence text = document.getCharsSequence();
        int width = 0;
        while (lineStart + width < lineEnd
                && (text.charAt(lineStart + width) == ' ' || text.charAt(lineStart + width) == '\t')) {
            width++;
        }
        return lineStart + width >= lineEnd ? -1 : width;
    }

    /**
     * Replaces the line's indentation, reading the line first so the offsets stay valid while it is edited.
     */
    private static void setLeadingWhitespace(@NotNull Document document, int lineStart, int width, int wanted) {
        String content = document.getText(new com.intellij.openapi.util.TextRange(
                lineStart, document.getLineEndOffset(document.getLineNumber(lineStart))));
        String replacement = " ".repeat(wanted) + content.substring(width);
        document.replaceString(lineStart, lineStart + content.length(), replacement);
    }
}
