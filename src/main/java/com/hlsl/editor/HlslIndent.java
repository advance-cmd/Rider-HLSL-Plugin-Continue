package com.hlsl.editor;

import com.hlsl.lexer.HlslLexer;
import com.hlsl.psi.HlslTokenTypes;
import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.tree.IElementType;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Brace-aware indentation for HLSL.
 *
 * <p>The parser is a flat token stream on purpose, so indentation is derived from the text: the brace
 * nesting level of a line decides its indentation, and a line that starts with a closing brace is put back
 * at the column of the line that opened it. Lexing per line keeps braces inside comments and strings out of
 * the count.</p>
 */
public final class HlslIndent {

    private HlslIndent() {
    }

    /**
     * The indentation a line at {@code line} should have, as a string, without touching the document.
     */
    public static @NotNull String indentFor(@NotNull Editor editor, int line) {
        return computeIndentFor(editor, editor.getDocument(), line);
    }

    /** True when the line opens with a closing brace. */
    public static boolean startsWithClosingBrace(@NotNull Document document, int line) {
        return firstNonWhitespace(document, line) == '}';
    }

    private static @NotNull String computeIndentFor(@NotNull Editor editor, Document document, int line) {

        // A closing brace belongs to the construct it closes, so it is placed at the column of the line
        // that opened it instead of one level deeper.
        if (firstNonWhitespace(document, line) == '}') {
            int openerLine = openerLineOf(document, line);
            if (openerLine >= 0) return leadingWhitespace(document, openerLine);
        }

        // Everything above the line decides the level; the line itself may hold an opening brace that only
        // the next line is meant to be indented by.
        int depth = 0;
        for (int i = 0; i < line; i++) depth += depthOf(document, i);

        // A statement that continues the line above it sits one level deeper: the body of an unbraced if, or
        // the else of an if / else chain, which lines up with the if instead.
        String hanging = continuesPreviousLine(document, line)
                ? hangingIndent(editor, document, line, depth) : null;
        if (hanging != null) return hanging;
        return unit(editor).repeat(Math.max(0, depth));
    }

    /**
     * True when the caret sits inside an empty block, i.e. between the braces of {@code void f() {}}.
     * Parentheses and brackets are deliberately not included: Enter inside an argument list must not be
     * rewritten into a block. Both braces have to share the caret's line, otherwise the block is already
     * laid out and the code style has done its work.
     */
    public static boolean isInsideEmptyBlock(@NotNull Document document, int caret) {
        CharSequence text = document.getCharsSequence();
        int lineNumber = document.getLineNumber(caret);
        int lineStart = document.getLineStartOffset(lineNumber);
        int lineEnd = document.getLineEndOffset(lineNumber);
        int before = skipWhitespaceBackwards(text, Math.max(caret, lineStart));
        int after = skipWhitespaceForwards(text, Math.min(caret, lineEnd));
        return before >= lineStart && after < lineEnd
                && text.charAt(before) == '{' && text.charAt(after) == '}';
    }

    /** True when only whitespace precedes {@code offset} on its line. */
    public static boolean isFirstOnLine(@NotNull Document document, int offset) {
        return isBlank(document.getCharsSequence(),
                document.getLineStartOffset(document.getLineNumber(offset)), offset);
    }

    /** Keywords whose body is a single statement when no brace follows. */
    private static final Set<String> CONTROL_KEYWORDS = Set.of("if", "for", "while", "switch", "foreach");

    /**
     * True when the line is a statement that continues the line above it: either the body of a control
     * statement written without braces, or the {@code else} of an if / else chain.
     */
    private static boolean continuesPreviousLine(Document document, int line) {
        if (line == 0) return false;
        String previousLine = lineText(document, line - 1).trim();
        if (previousLine.endsWith("{")) return false;
        // The body of an unbraced control statement, and the statement after an else, hang below it.
        return opensWith(previousLine, "else") || endsWithControlHeader(previousLine);
    }

    /**
     * The indentation of a statement that is the body of an unbraced control statement above it, or
     * {@code null} when the line does not continue anything.
     */
    private static @Nullable String hangingIndent(Editor editor, Document document, int line, int depth) {
        String previousLine = lineText(document, line - 1).trim();
        if (!opensWith(previousLine, "else") && !endsWithControlHeader(previousLine)) return null;
        return unit(editor).repeat(Math.max(0, depth + 1));
    }

    /** True when the trimmed line opens with the given keyword, sparing {@code elsewhere} and the like. */
    private static boolean opensWith(@NotNull String trimmed, @NotNull String keyword) {
        if (!trimmed.startsWith(keyword)) return false;
        return trimmed.length() == keyword.length()
                || !Character.isLetterOrDigit(trimmed.charAt(keyword.length()));
    }

    /** True when the line, read as code, ends the header of a control statement, as {@code if (x)} does. */
    private static boolean endsWithControlHeader(String trimmed) {
        if (!trimmed.endsWith(")")) return false;
        return startsWithKeywordBeforeParen(trimmed);
    }

    /**
     * True when a control keyword opens the statement and the parenthesis that ends it belongs to that
     * keyword, which is what separates {@code if (dist > 0)} from a call such as {@code ApplyFog(a, b)}.
     */
    private static boolean startsWithKeywordBeforeParen(String trimmed) {
        String header = trimmed.contains("{") ? trimmed.substring(0, trimmed.indexOf('{')) : trimmed;
        int paren = header.indexOf('(');
        if (paren < 0) return false;
        String before = header.substring(0, paren).trim();
        if (before.isEmpty()) return false;
        return CONTROL_KEYWORDS.contains(before);
    }

    private static boolean startsWithWord(Document document, int line, @Nullable String word) {
        return word != null && word.equals(firstWord(document, line));
    }

    private static boolean startsWithKeyword(Document document, int line, String keyword) {
        return keyword.equals(firstWord(document, line));
    }

    private static @Nullable String firstWord(Document document, int line) {
        String text = lineText(document, line).trim();
        int end = 0;
        while (end < text.length() && (Character.isLetterOrDigit(text.charAt(end)) || text.charAt(end) == '_')) end++;
        return end == 0 ? null : text.substring(0, end);
    }

    private static @NotNull String lineText(Document document, int line) {
        return document.getText(new TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)));
    }

    private static boolean isBlank(CharSequence text, int start, int end) {
        for (int i = start; i < end; i++) {
            if (!Character.isWhitespace(text.charAt(i))) return false;
        }
        return true;
    }

    private static int skipWhitespaceBackwards(CharSequence text, int offset) {
        for (int i = Math.min(offset, text.length()) - 1; i >= 0; i--) {
            if (!Character.isWhitespace(text.charAt(i))) return i;
        }
        return -1;
    }

    private static int skipWhitespaceForwards(CharSequence text, int offset) {
        for (int i = Math.max(0, offset); i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) return i;
        }
        return text.length();
    }

    private static char firstNonWhitespace(Document document, int line) {
        int start = document.getLineStartOffset(line);
        int end = document.getLineEndOffset(line);
        CharSequence text = document.getCharsSequence();
        for (int i = start; i < end; i++) {
            if (!Character.isWhitespace(text.charAt(i))) return text.charAt(i);
        }
        return 0;
    }

    private static @NotNull String leadingWhitespace(Document document, int line) {
        int start = document.getLineStartOffset(line);
        int end = document.getLineEndOffset(line);
        CharSequence text = document.getCharsSequence();
        int i = start;
        while (i < end && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) i++;
        return text.subSequence(start, i).toString();
    }

    /**
     * The line that opened the brace on {@code closerLine}, or {@code -1} when the file is unbalanced.
     * Braces inside comments, strings and char literals do not count.
     */
    private static int openerLineOf(Document document, int closerLine) {
        int depth = 1;
        for (int line = closerLine; line >= 0; line--) {
            int openers = 0;
            int closers = 0;
            HlslLexer lexer = new HlslLexer();
            lexer.start(document.getImmutableCharSequence(),
                    document.getLineStartOffset(line), document.getLineEndOffset(line), 0);
            while (lexer.getTokenType() != null) {
                IElementType token = lexer.getTokenType();
                if (token == HlslTokenTypes.LBRACE) {
                    openers++;
                } else if (token == HlslTokenTypes.RBRACE) {
                    closers++;
                }
                lexer.advance();
            }
            if (line == closerLine) {
                // The brace being matched is the last closing brace on its own line.
                closers--;
            }
            for (int i = 0; i < closers && depth > 0; i++) depth++;
            for (int i = 0; i < openers; i++) {
                if (--depth == 0) return line;
            }
        }
        return -1;
    }

    /**
     * The brace balance of one line, ignoring braces that sit inside comments, strings and char literals.
     * A line of an unterminated block comment contributes nothing.
     */
    private static int depthOf(Document document, int line) {
        CharSequence source = document.getImmutableCharSequence();
        int start = document.getLineStartOffset(line);
        int end = document.getLineEndOffset(line);
        HlslLexer lexer = new HlslLexer();
        lexer.start(source, start, end, 0);
        int depth = 0;
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

    /** One indentation step, using the project's code style or a four-space default. */
    public static @NotNull String unit(@NotNull Editor editor) {
        CommonCodeStyleSettings.IndentOptions options =
                CodeStyle.getIndentOptions(editor.getProject(), editor.getDocument());
        if (options != null) {
            if (options.USE_TAB_CHARACTER) return "\t";
            if (options.INDENT_SIZE > 0) return " ".repeat(options.INDENT_SIZE);
        }
        return " ".repeat(4);
    }
}

