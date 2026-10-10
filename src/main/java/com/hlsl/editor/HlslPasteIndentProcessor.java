package com.hlsl.editor;

import com.hlsl.HlslLanguage;
import com.intellij.codeInsight.editorActions.CopyPastePostProcessor;
import com.intellij.codeInsight.editorActions.TextBlockTransferableData;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.List;

/**
 * Puts pasted HLSL code at the indentation of the line it lands on.
 *
 * <p>The platform brackets a pasted block with the target line's indentation: it indents the block's first
 * line, leaves the rest of the block exactly as it was copied, and indents whatever follows the block. Code
 * copied out of a function body and pasted into another one therefore ends up with one line at the right
 * level and the others at the level they were copied at, and repeating the paste makes it worse every
 * time.</p>
 *
 * <p>The text that was copied is read back from the clipboard, so the block's own indentation is known, and
 * every line of the pasted region is then written at the target indentation plus the line's relative
 * indentation inside the block.</p>
 */
public class HlslPasteIndentProcessor extends CopyPastePostProcessor<HlslPasteIndentProcessor.Marker> {

    private static final Logger LOG = Logger.getInstance(HlslPasteIndentProcessor.class);

    /**
     * The processor needs no data of its own, but the platform skips every post processor whose extracted
     * data list is empty, so a marker is what gets it called at all.
     */
    public static final class Marker implements TextBlockTransferableData {
        @Override
        public @NotNull DataFlavor getFlavor() {
            return DataFlavor.stringFlavor;
        }
    }

    private static final Marker MARKER = new Marker();

    @Override
    public @NotNull List<Marker> collectTransferableData(@NotNull PsiFile file, @NotNull Editor editor,
                                                         int @NotNull [] startOffsets,
                                                         int @NotNull [] endOffsets) {
        return List.of(MARKER);
    }

    @Override
    public @Nullable List<Marker> extractTransferableData(@NotNull Transferable content) {
        return List.of(MARKER);
    }

    @Override
    public void processTransferableData(@NotNull Project project, @NotNull Editor editor,
                                        @NotNull RangeMarker bounds, int caretOffset,
                                        @NotNull Ref<? super Boolean> indented,
                                        @NotNull List<? extends Marker> values) {
        PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        if (file == null || file.getLanguage() != HlslLanguage.INSTANCE) return;

        List<String> block = copiedLines();
        if (block.isEmpty()) return;

        Document document = editor.getDocument();
        int start = bounds.getStartOffset();
        int end = bounds.getEndOffset();
        if (start < 0 || end > document.getTextLength() || start >= end) return;

        // The platform can call this outside a write action, so the edit is wrapped in one.
        ApplicationManager.getApplication().runWriteAction(() -> align(editor, document, start, end, block));
        indented.set(Boolean.TRUE);
    }

    /** The lines that were copied. Reading them back is what makes the block's own indentation known. */
    private static @NotNull List<String> copiedLines() {
        String text = null;
        Transferable contents = CopyPasteManager.getInstance().getContents();
        if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            try {
                text = (String) contents.getTransferData(DataFlavor.stringFlavor);
            } catch (Exception ignored) {
                text = null;
            }
        }
        if (text == null || text.isEmpty()) return List.of();
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\r\n|\r|\n", -1)) {
            if (!line.isBlank()) lines.add(line);
        }
        return lines;
    }

    /**
     * Writes every line of the pasted region at the target indentation plus its own indentation inside the
     * block. The lines are matched in order; a line whose code does not show up where it is expected is left
     * alone, so a paste that the platform rearranged is not damaged further.
     */
    private static void align(@NotNull Editor editor, @NotNull Document document, int start, int end,
                              @NotNull List<String> block) {
        // Pasting behind existing code: the block would share a line with it, so it gets a line of its own.
        if (!isFirstOnLine(document, start)) {
            int line = document.getLineNumber(start);
            String indent = HlslIndent.indentFor(editor, line);
            int lineStart = document.getLineStartOffset(line);
            document.insertString(start, "\n" + indent);
            start = start + 1 + indent.length();
            end = start + (end - start);
        }

        int first = document.getLineNumber(start);
        int last = document.getLineNumber(Math.max(start, end - 1));
        if (block.size() > last - first + 1) {
            LOG.info("HLSL paste: " + block.size() + " copied lines became " + (last - first + 1)
                    + " lines, leaving the indentation alone");
            return;
        }

        int targetIndent = HlslIndent.indentFor(editor, first).length();
        // The block's own base indentation is what the target indentation replaces; only the extra
        // indentation of a nested line inside the block is kept.
        int blockBaseIndent = baseIndentOf(block);
        for (int i = 0; i < block.size(); i++) {
            int line = first + i;
            int lineStart = document.getLineStartOffset(line);
            int lineEnd = document.getLineEndOffset(line);
            String current = document.getText(new TextRange(lineStart, lineEnd));
            String wanted = block.get(i);
            String wantedCode = wanted.stripLeading();
            String stripped = current.stripLeading();
            // The block's last line may share its line with code that followed the caret.
            if (!stripped.startsWith(wantedCode)) {
                LOG.info("HLSL paste: line " + line + " does not match the copied block, left alone");
                continue;
            }
            int relative = Math.max(0, indentWidth(wanted) - blockBaseIndent);
            String replacement = " ".repeat(targetIndent + relative) + stripped;
            if (!replacement.equals(current)) {
                document.replaceString(lineStart, lineEnd, replacement);
            }
        }
    }

    /** The narrowest indentation among the copied lines, which is where the block starts. */
    private static int baseIndentOf(@NotNull List<String> block) {
        int minimum = Integer.MAX_VALUE;
        for (String line : block) {
            if (line.isBlank()) continue;
            minimum = Math.min(minimum, indentWidth(line));
        }
        return minimum == Integer.MAX_VALUE ? 0 : minimum;
    }

    private static int indentWidth(@NotNull String line) {
        return line.length() - line.stripLeading().length();
    }

    /** True when only indentation stands in front of the offset. */
    private static boolean isFirstOnLine(@NotNull Document document, int offset) {
        int lineStart = document.getLineStartOffset(document.getLineNumber(offset));
        CharSequence text = document.getCharsSequence();
        for (int i = lineStart; i < offset; i++) {
            if (!Character.isWhitespace(text.charAt(i))) return false;
        }
        return true;
    }
}
