package com.hlsl.editor;

import com.hlsl.HlslLanguage;
import com.hlsl.lexer.HlslLexer;
import com.hlsl.psi.HlslTokenTypes;
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
import com.intellij.psi.tree.IElementType;

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
     *
     * <p>Its flavour has to be one of its own. With {@link DataFlavor#stringFlavor} the clipboard manager
     * takes the marker for the clipboard's text and casts it to {@code String}, which breaks copy and cut
     * outright. The flavour is built from the MIME type alone: the constructors that take a human presentable
     * name resolve the class through the context class loader, which does not know the plugin's classes and
     * fails the whole plugin with "can't find specified class".</p>
     */
    public static final class Marker implements TextBlockTransferableData, java.io.Serializable {
        private static final DataFlavor FLAVOR = createFlavor();

        /** The class is resolved by the plugin's own class loader, not by whoever happens to be copying. */
        private static DataFlavor createFlavor() {
            try {
                return new DataFlavor(
                        DataFlavor.javaJVMLocalObjectMimeType + ";class=" + Marker.class.getName(),
                        "HLSL paste indent marker",
                        Marker.class.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Could not build the marker flavour", e);
            }
        }

        @Override
        public @NotNull DataFlavor getFlavor() {
            return FLAVOR;
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
     * Re-indents what was pasted.
     *
     * <p>The platform has already inserted the text and indented part of it, bundling the block with the
     * indent of the line it landed on. Which pasted line belongs to which copied line cannot be told from the
     * line numbering, so the code of each line is matched against the copied text instead, and a line that is
     * only whitespace is dropped unless the copied text had one there too.</p>
     *
     * <p>Lines that carry braces are placed by the braces: a line that opens a block sits at the level of the
     * statement it belongs to, and a line that closes one sits under that statement. That is what the code
     * style of the file asks for, and it keeps a copied block from drifting inwards, which is what happens
     * when the block's own indentation is measured against the target's.</p>
     */
    private static void align(@NotNull Editor editor, @NotNull Document document, int start, int end,
                              @NotNull List<String> block) {
        // A block pasted behind existing code gets a line of its own, so it is not glued to that code. A single
        // line that starts at column zero stays where it was put: it continues the line it is pasted into,
        // which is how a trailing comment is written.
        if (!isFirstOnLine(document, start) && startsABlock(block)) {
            int line = document.getLineNumber(start);
            String indent = HlslIndent.indentFor(editor, line);
            document.insertString(start, "\n" + indent);
            start = start + 1 + indent.length();
        }

        // The pasted text is what the copied text was, so its length tells where the region ends. The platform
        // may have indented only part of it, and the report bounds end at the first line.
        int originalLength = block.stream().mapToInt(String::length).sum() + Math.max(0, block.size() - 1);
        end = Math.min(document.getTextLength(), Math.max(end, start + originalLength));

        int first = document.getLineNumber(start);
        int last = document.getLineNumber(Math.max(start, end - 1));
        String baseIndent = HlslIndent.indentFor(editor, first);
        String unit = HlslIndent.unit(editor);

        int copied = 0;
        // How many blocks the lines before this one have opened and not closed yet.
        int depth = 0;
        for (int line = first; line <= last; line++) {
            int lineStart = document.getLineStartOffset(line);
            int lineEnd = document.getLineEndOffset(line);
            String stripped = document.getText(new TextRange(lineStart, lineEnd)).stripLeading();
            if (stripped.isEmpty()) {
                // An empty line that the copied text does not have was added by the platform where it broke a
                // line; it is not part of what was pasted, so it goes away again.
                if (copied < block.size() && block.get(copied).isBlank()) {
                    copied++;
                    continue;
                }
                document.deleteString(lineStart, Math.min(document.getTextLength(), lineEnd + 1));
                last--;
                line--;
                continue;
            }
            if (copied >= block.size() || !stripped.startsWith(block.get(copied).stripLeading())) {
                LOG.info("HLSL paste: line " + line + " has no counterpart in the copied block, left alone");
                break;
            }
            // A line that closes a block sits one level above the lines around it.
            if (stripped.startsWith("}")) depth--;
            String replacement = baseIndent + unit.repeat(Math.max(0, depth)) + stripped;
            String current = document.getText(new TextRange(lineStart, lineEnd));
            if (!replacement.equals(current)) document.replaceString(lineStart, lineEnd, replacement);
            depth += netBraceDepth(stripped);
            copied++;
        }
    }

    /** How many blocks a line opens, one more for an opener than for a closer. */
    private static int netBraceDepth(@NotNull String line) {
        int depth = 0;
        HlslLexer lexer = new HlslLexer();
        lexer.start(line, 0, line.length(), 0);
        while (lexer.getTokenType() != null) {
            IElementType token = lexer.getTokenType();
            if (token == HlslTokenTypes.LBRACE) {
                depth++;
            } else if (token == HlslTokenTypes.RBRACE) {
                depth--;
            }
            lexer.advance();
        }
        return depth;
    }

    /**
     * True when the copied text is code that belongs on its own line: it carries its own indentation, it has
     * more than one line of content, or it is a comment on its own. Only a plain single-line fragment stays
     * where it was put, which is how text is appended to a line.
     */
    private static boolean startsABlock(@NotNull List<String> block) {
        if (block.isEmpty()) return false;
        String first = block.get(0).stripLeading();
        if (indentWidth(block.get(0)) > 0 || block.size() > 1) return true;
        return first.startsWith("//") || first.startsWith("/*");
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

