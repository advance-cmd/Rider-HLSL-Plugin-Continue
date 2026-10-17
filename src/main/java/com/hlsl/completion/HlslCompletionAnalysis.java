package com.hlsl.completion;

import com.hlsl.lexer.HlslLexer;
import com.hlsl.psi.HlslTokenTypes;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A deliberately small declaration reader for code insight. The highlighting lexer calls any word
 * followed by '(' a FUNCTION_CALL; that token is not evidence of a declaration or a visible symbol.
 * Unknown receiver types produce no members rather than falling back to unrelated global names.
 *
 * <p>Code completion and parameter info have the same need — what is declared in this file and in its
 * transitive include chain — so both read declarations through this class.</p>
 */
public final class HlslCompletionAnalysis {
    enum Context { CODE, SEMANTIC, MEMBER, ATTRIBUTE, SUPPRESSED }
    public enum Kind { TYPE, FUNCTION, VARIABLE, MACRO, FIELD }
    record Candidate(String name, String type, Kind kind) { }
    record Result(Context context, List<Candidate> candidates) { }

    /** One declared parameter, kept as written so the popup can highlight exactly it. */
    public record Parameter(String type, String name) {
        public String label() {
            return name.isEmpty() ? type : type + " " + name;
        }
    }

    /** A declared function. The parameter popup shows {@link #label()}. */
    public record Signature(String name, String returnType, List<Parameter> parameters) {
        public String label() {
            StringBuilder label = new StringBuilder(name).append('(');
            for (int i = 0; i < parameters.size(); i++) {
                if (i > 0) label.append(", ");
                label.append(parameters.get(i).label());
            }
            return label.append(')').toString();
        }

        /** Where one parameter sits inside {@link #label()}, empty when the index is out of range. */
        public TextRange parameterRange(int index) {
            int start = name.length() + 1;
            for (int i = 0; i < parameters.size(); i++) {
                String parameter = parameters.get(i).label();
                if (i == index) return new TextRange(start, start + parameter.length());
                start += parameter.length() + 2;
            }
            return new TextRange(name.length(), name.length());
        }
    }

    /** The call the caret sits inside, with every overload of the callee that is visible there. */
    public record CallInfo(String name, int parameterIndex, List<Signature> signatures) { }

    /**
     * A declaration with the comment written above it, which is what hovering the symbol shows. {@code text}
     * is the declaration as it appears in the source, so a signature is never reconstructed from tokens.
     */
    public record Declaration(String name, Kind kind, String comment, String text) { }

    /** Parentheses that never belong to a user function, so parameter info stays quiet there. */
    private static final Set<String> NOT_A_CALL = Set.of(
            "if", "for", "while", "switch", "sizeof", "defined", "return", "case");

    private static final Set<String> QUALIFIERS = Set.of(
            "const", "static", "extern", "uniform", "volatile", "inline", "shared", "groupshared",
            "in", "out", "inout", "row_major", "column_major", "nointerpolation", "linear", "centroid",
            "noperspective", "sample", "precise", "globallycoherent", "snorm", "unorm", "export");
    private static final Pattern DIRECTIVE = Pattern.compile("^#\\s*(define|undef)\\s+([A-Za-z_][A-Za-z0-9_]*)");

    private static final Set<String> SAMPLING_TEXTURES = Set.of(
            "Texture1D", "Texture1DArray", "Texture2D", "Texture2DArray", "Texture3D",
            "TextureCube", "TextureCubeArray");
    private static final Set<String> MULTISAMPLE_TEXTURES = Set.of("Texture2DMS", "Texture2DMSArray");
    private static final Set<String> RW_TEXTURES = Set.of(
            "RWTexture1D", "RWTexture1DArray", "RWTexture2D", "RWTexture2DArray", "RWTexture3D");
    private static final Set<String> STRUCTURED_RESOURCES = Set.of(
            "Buffer", "RWBuffer", "StructuredBuffer", "RWStructuredBuffer",
            "AppendStructuredBuffer", "ConsumeStructuredBuffer");
    private static final Set<String> RW_STRUCTURED_RESOURCES = Set.of(
            "RWBuffer", "RWStructuredBuffer", "AppendStructuredBuffer", "ConsumeStructuredBuffer");
    private static final Set<String> ADDRESS_BUFFERS = Set.of("ByteAddressBuffer", "RWByteAddressBuffer");
    private static final List<String> SAMPLING_METHODS = List.of(
            "Sample", "SampleBias", "SampleCmp", "SampleCmpLevelZero", "SampleGrad", "SampleLevel",
            "Gather", "GatherRed", "GatherGreen", "GatherBlue", "GatherAlpha",
            "GatherCmp", "GatherCmpRed", "GatherCmpGreen", "GatherCmpBlue", "GatherCmpAlpha",
            "CalculateLevelOfDetail", "CalculateLevelOfDetailUnclamped");
    private static final List<String> INTERLOCKED_METHODS = List.of(
            "InterlockedAdd", "InterlockedAnd", "InterlockedCompareExchange", "InterlockedCompareStore",
            "InterlockedExchange", "InterlockedMax", "InterlockedMin", "InterlockedOr", "InterlockedXor");
    private static final Pattern VECTOR = Pattern.compile("(bool|int|uint|half|float|double|min16float|min10float|min16int|min12int|min16uint)([1-4])");
    /** Scalar types, which carry the same one-component swizzles as a vector of width one. */
    private static final Set<String> SCALAR_TYPES =
            Set.of("bool", "int", "uint", "half", "float", "double", "min16float", "min10float", "min16int",
                    "min12int", "min16uint");

    private HlslCompletionAnalysis() { }

    static Set<String> collectExportedIdentifiers(CharSequence source) {
        Parser parser = new Parser(source, true);
        Set<String> result = new LinkedHashSet<>();
        for (Symbol symbol : parser.symbols) {
            if (symbol.exported && (symbol.candidate.kind != Kind.MACRO || symbol.end > source.length())) {
                result.add(symbol.candidate.name);
            }
        }
        return result;
    }

    static Result analyze(CharSequence source, int offset, List<CharSequence> includes) {
        if (isSuppressed(source, offset)) return new Result(Context.SUPPRESSED, List.of());
        Map<String, Candidate> visible = new LinkedHashMap<>();
        Map<String, Map<String, Candidate>> structures = new LinkedHashMap<>();
        for (CharSequence include : includes) {
            Parser imported = new Parser(include, true);
            structures.putAll(imported.structures);
            for (Symbol symbol : imported.symbols) {
                if (symbol.exported && (symbol.candidate.kind != Kind.MACRO || symbol.end > include.length())) {
                    visible.put(symbol.candidate.name, symbol.candidate);
                }
            }
        }
        Parser parser = new Parser(source, false, offset);
        structures.putAll(parser.structures);
        for (Symbol symbol : parser.symbols) {
            if (symbol.start <= offset && offset < symbol.end) visible.put(symbol.candidate.name, symbol.candidate);
        }
        int prefixStart = Math.min(offset, source.length());
        while (prefixStart > 0 && isIdentifierPart(source.charAt(prefixStart - 1))) prefixStart--;
        int previous = parser.before(prefixStart);
        if (parser.is(previous, "[") && parser.opensAttribute(previous)) {
            return new Result(Context.ATTRIBUTE, List.of());
        }
        if (parser.is(previous, ".")) {
            String type = parser.expressionType(previous - 1, visible, structures);
            return new Result(Context.MEMBER, new ArrayList<>(members(type, structures).values()));
        }
        if (parser.is(previous, ":")) {
            if (parser.isSemanticColon(previous)) {
                return new Result(Context.SEMANTIC, List.of());
            }
            if (parser.is(previous - 1, ":")) return new Result(Context.SUPPRESSED, List.of());
        }
        // Typing the name of a declaration is not a place to suggest unrelated global symbols.
        if (prefixStart < offset && parser.isDeclaratorPosition(prefixStart, visible, structures)) {
            return new Result(Context.SUPPRESSED, List.of());
        }
        return new Result(Context.CODE, new ArrayList<>(visible.values()));
    }

    static boolean isSuppressed(CharSequence source, int offset) {
        HlslLexer lexer = new HlslLexer();
        lexer.start(source);
        while (lexer.getTokenType() != null) {
            IElementType type = lexer.getTokenType();
            int start = lexer.getTokenStart();
            int end = lexer.getTokenEnd();
            if (start <= offset && offset <= end) {
                boolean inside = offset < end;
                if (type == HlslTokenTypes.LINE_COMMENT || type == HlslTokenTypes.PREPROCESSOR) return true;
                if (type == HlslTokenTypes.BLOCK_COMMENT && (inside || !endsWith(source, start, end, "*/"))) return true;
                if (type == HlslTokenTypes.STRING && (inside || end <= start + 1 || source.charAt(end - 1) != '"')) return true;
                if (type == HlslTokenTypes.NUMBER) return true;
            }
            if (start > offset) break;
            lexer.advance();
        }
        return false;
    }

    private static boolean endsWith(CharSequence source, int start, int end, String suffix) {
        return end - start >= suffix.length() && source.subSequence(end - suffix.length(), end).toString().equals(suffix);
    }

    /**
     * The call the caret sits inside, when the callee is a function declared in the file or in its include
     * chain. Control-flow parentheses and value constructors such as {@code float4(...)} are not calls, so
     * they produce nothing rather than a misleading popup.
     */
    public static CallInfo callAt(CharSequence source, int offset, List<CharSequence> includes) {
        if (isSuppressed(source, offset)) return null;
        int position = Math.min(offset, source.length());
        Parser parser = new Parser(source, false, position);
        int open = enclosingCallParen(parser, position);
        if (open <= 0 || parser.declarationParens.contains(open) || !parser.name(open - 1)) return null;
        // A matched pair that already ended before the caret means the caret is outside the call.
        Integer close = parser.pairs.get(open);
        if (close != null && parser.tokens.get(close).start < position) return null;
        String name = parser.tokens.get(open - 1).text;
        if (NOT_A_CALL.contains(name)) return null;
        Map<String, Set<Signature>> merged = new LinkedHashMap<>();
        for (CharSequence include : includes) {
            addSignatures(new Parser(include, true).functions, merged);
        }
        addSignatures(parser.functions, merged);
        Set<Signature> overloads = merged.get(name);
        if (overloads == null || overloads.isEmpty()) return null;
        return new CallInfo(name, argumentIndex(parser, open, position), List.copyOf(overloads));
    }

    /**
     * The declaration of {@code name} visible at {@code offset}: the file being edited wins over its
     * includes, exactly as in completion, and the first include wins over the later ones.
     */
    public static Declaration declarationOf(String name, CharSequence source, int offset,
                                            List<CharSequence> includes) {
        Declaration fromIncludes = null;
        for (CharSequence include : includes) {
            Declaration candidate = new Parser(include, true).declarations.get(name);
            if (candidate != null && fromIncludes == null) fromIncludes = candidate;
        }
        Declaration own = new Parser(source, false, Math.min(offset, source.length())).declarations.get(name);
        return own != null ? own : fromIncludes;
    }

    private static void addSignatures(Map<String, List<Signature>> from, Map<String, Set<Signature>> into) {
        for (Map.Entry<String, List<Signature>> entry : from.entrySet()) {
            into.computeIfAbsent(entry.getKey(), key -> new LinkedHashSet<>()).addAll(entry.getValue());
        }
    }

    /** The '(' opening the call the caret is inside, or -1 when the caret is not inside a call. */
    private static int enclosingCallParen(Parser parser, int offset) {
        int depth = 0;
        for (int i = parser.before(offset); i >= 0; i--) {
            String text = parser.tokens.get(i).text;
            if (text.equals(")")) {
                depth++;
            } else if (text.equals("(")) {
                if (depth == 0) return i;
                depth--;
            } else if (depth == 0 && (text.equals(";") || text.equals("{") || text.equals("}"))) {
                // A statement boundary means there is no enclosing call, only an earlier one.
                return -1;
            }
        }
        return -1;
    }

    /** How many top-level commas separate the caret from the opening parenthesis. */
    private static int argumentIndex(Parser parser, int open, int offset) {
        int index = 0;
        int nesting = 0;
        for (int i = open + 1; i < parser.tokens.size(); i++) {
            Token token = parser.tokens.get(i);
            if (token.start >= offset) break;
            String text = token.text;
            if (text.equals("(") || text.equals("[") || text.equals("{")) {
                nesting++;
            } else if (text.equals(")") || text.equals("]") || text.equals("}")) {
                nesting--;
            } else if (text.equals(",") && nesting == 0) {
                index++;
            }
        }
        return index;
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /** Extends a declaration start backwards over the comment block written directly above it. */
    private static int commentStart(CharSequence source, int start) {
        int lineStart = Math.min(start, source.length());
        while (lineStart > 0) {
            int previousLineEnd = lineStart - 1;
            while (previousLineEnd > 0 && (source.charAt(previousLineEnd) == '\n'
                    || source.charAt(previousLineEnd) == '\r')) {
                previousLineEnd--;
            }
            int previousLineStart = previousLineEnd;
            while (previousLineStart > 0 && source.charAt(previousLineStart - 1) != '\n') {
                previousLineStart--;
            }
            String line = source.subSequence(previousLineStart, previousLineEnd + 1).toString().strip();
            if (!isCommentLine(line)) break;
            lineStart = previousLineStart;
        }
        return lineStart;
    }

    private static boolean isCommentLine(String line) {
        return line.startsWith("//") || line.startsWith("/*") || line.startsWith("*") || line.endsWith("*/");
    }

    private static Map<String, Candidate> members(String type, Map<String, Map<String, Candidate>> structures) {
        if (type == null) return Map.of();
        // ConstantBuffer<T> exposes the fields of T, whereas every other resource type exposes intrinsics.
        Map<String, Candidate> fields = declaredMembers(type, structures);
        if (fields != null) return fields;
        String base = baseType(type);
        Map<String, Candidate> intrinsics = intrinsicMembers(base, elementTypeOf(type));
        if (!intrinsics.isEmpty()) return intrinsics;
        Matcher vector = VECTOR.matcher(base);
        if (vector.matches()) {
            return swizzles(vector.group(1), Integer.parseInt(vector.group(2)));
        }
        // A plain float or int has the same one-component swizzles as float1.
        if (SCALAR_TYPES.contains(base)) return swizzles(base, 1);
        return Map.of();
    }

    /** The swizzle members of a vector of this width, in both the xyzw and the rgba alphabet. */
    private static Map<String, Candidate> swizzles(String scalar, int width) {
        Map<String, Candidate> result = new LinkedHashMap<>();
        for (String alphabet : List.of("xyzw", "rgba")) {
            addSwizzles(result, alphabet.substring(0, width), "", scalar);
        }
        return result;
    }

    /** The fields a struct or constant buffer declares, or {@code null} when the type is not a structure. */
    private static Map<String, Candidate> declaredMembers(String type, Map<String, Map<String, Candidate>> structures) {
        if (type.endsWith("[]")) return null;
        Map<String, Candidate> fields = structures.get(type);
        String argument = typeArgument(type);
        if (fields == null && argument != null) fields = structures.get(argument);
        if (fields == null) fields = structures.get(baseType(type));
        return fields;
    }

    /** The element type of a resource: {@code Texture2D<float4>} has {@code float4}, a scalar has float4. */
    private static String elementTypeOf(String type) {
        String argument = typeArgument(type);
        return argument == null ? "float4" : argument;
    }

    /** {@code Texture2D<float>} becomes {@code Texture2D}; a plain name is returned unchanged. */
    private static String baseType(String type) {
        int generic = type.indexOf('<');
        return generic < 0 ? type : type.substring(0, generic);
    }

    private static String typeArgument(String type) {
        int generic = type.indexOf('<');
        return generic < 0 || !type.endsWith(">") ? null : type.substring(generic + 1, type.length() - 1);
    }

    /**
     * Intrinsic members of the built-in resource types. These are language features, not declarations,
     * so they are not discoverable in any source file and must be listed here.
     */
    private static Map<String, Candidate> intrinsicMembers(String base, String element) {
        Map<String, Candidate> result = new LinkedHashMap<>();
        if (SAMPLING_TEXTURES.contains(base)) {
            for (String method : SAMPLING_METHODS) {
                addMethod(result, method, method.startsWith("Gather") ? "float4" : element);
            }
            addMethod(result, "Load", element);
            addMethod(result, "GetDimensions", "void");
            result.put("mips", new Candidate("mips", "uint", Kind.FIELD));
        } else if (MULTISAMPLE_TEXTURES.contains(base)) {
            addMethod(result, "Sample", element);
            addMethod(result, "Load", element);
            addMethod(result, "GetDimensions", "void");
            addMethod(result, "GetSamplePosition", "float2");
        } else if (RW_TEXTURES.contains(base)) {
            addMethod(result, "Load", element);
            addMethod(result, "GetDimensions", "void");
            addMethod(result, "GetSamplePosition", "float2");
            for (String method : INTERLOCKED_METHODS) addMethod(result, method, element);
        } else if (STRUCTURED_RESOURCES.contains(base)) {
            addMethod(result, "Load", element);
            addMethod(result, "GetDimensions", "void");
            if (RW_STRUCTURED_RESOURCES.contains(base)) {
                for (String method : INTERLOCKED_METHODS) addMethod(result, method, element);
            }
        } else if (ADDRESS_BUFFERS.contains(base)) {
            addMethod(result, "Load", "uint");
            addMethod(result, "Load2", "uint2");
            addMethod(result, "Load3", "uint3");
            addMethod(result, "Load4", "uint4");
            addMethod(result, "GetDimensions", "void");
            if (base.startsWith("RW")) {
                addMethod(result, "Store", "void");
                addMethod(result, "Store2", "void");
                addMethod(result, "Store3", "void");
                addMethod(result, "Store4", "void");
                for (String method : INTERLOCKED_METHODS) addMethod(result, method, "uint");
            }
        }
        return result;
    }

    private static void addMethod(Map<String, Candidate> result, String name, String returns) {
        result.put(name, new Candidate(name, returns, Kind.FUNCTION));
    }

    private static void addSwizzles(Map<String, Candidate> result, String alphabet, String prefix, String scalar) {
        if (!prefix.isEmpty()) result.put(prefix, new Candidate(prefix, scalar + (prefix.length() == 1 ? "" : prefix.length()), Kind.FIELD));
        if (prefix.length() == 4) return;
        for (int i = 0; i < alphabet.length(); i++) addSwizzles(result, alphabet, prefix + alphabet.charAt(i), scalar);
    }

    private record Token(String text, IElementType type, int start, int end) { }
    private record Symbol(Candidate candidate, int start, int end, boolean exported) { }
    private enum Scope { GLOBAL, LOCAL, FIELDS }

    private static final class Parser {
        final CharSequence source;
        final boolean exportsOnly;
        final int completionOffset;
        final List<Token> tokens = new ArrayList<>();
        final List<Symbol> symbols = new ArrayList<>();
        final Map<String, Map<String, Candidate>> structures = new LinkedHashMap<>();
        final Map<String, List<Signature>> functions = new LinkedHashMap<>();
        /** Declarations together with the source text that documents them, for hover. */
        final Map<String, Declaration> declarations = new LinkedHashMap<>();
        /** Parentheses that belong to a declaration, where parameter info must stay quiet. */
        final Set<Integer> declarationParens = new HashSet<>();
        final Set<Integer> semanticColons = new HashSet<>();
        final Map<Integer, Integer> pairs = new HashMap<>();

        Parser(CharSequence source, boolean exportsOnly) {
            this(source, exportsOnly, source.length());
        }

        Parser(CharSequence source, boolean exportsOnly, int completionOffset) {
            this.source = source;
            this.exportsOnly = exportsOnly;
            this.completionOffset = completionOffset;
            lex();
            pairDelimiters();
            parseRange(0, tokens.size(), source.length() + 1, Scope.GLOBAL, null);
        }

        void lex() {
            HlslLexer lexer = new HlslLexer();
            lexer.start(source);
            Map<String, Integer> macros = new HashMap<>();
            while (lexer.getTokenType() != null) {
                ProgressManager.checkCanceled();
                IElementType type = lexer.getTokenType();
                int start = lexer.getTokenStart();
                int end = lexer.getTokenEnd();
                String text = source.subSequence(start, end).toString();
                if (type == HlslTokenTypes.PREPROCESSOR) {
                    Matcher matcher = DIRECTIVE.matcher(text);
                    if (isDirectiveStart(start) && matcher.find()) {
                        String name = matcher.group(2);
                        Integer old = macros.remove(name);
                        if (old != null) {
                            Symbol symbol = symbols.get(old);
                            symbols.set(old, new Symbol(symbol.candidate, symbol.start, start, true));
                        }
                        if (matcher.group(1).equals("define")) {
                            macros.put(name, symbols.size());
                            symbols.add(new Symbol(new Candidate(name, "macro", Kind.MACRO), end, source.length() + 1, true));
                            document(name, Kind.MACRO, start, end);
                        }
                    }
                } else if (type != TokenType.WHITE_SPACE && type != HlslTokenTypes.LINE_COMMENT && type != HlslTokenTypes.BLOCK_COMMENT) {
                    tokens.add(new Token(text, type, start, end));
                }
                lexer.advance();
            }
        }

        boolean isDirectiveStart(int offset) {
            for (int i = offset - 1; i >= 0 && source.charAt(i) != '\n' && source.charAt(i) != '\r'; i--) {
                if (!Character.isWhitespace(source.charAt(i))) return false;
            }
            return true;
        }

        void pairDelimiters() {
            Deque<Integer> stack = new ArrayDeque<>();
            for (int i = 0; i < tokens.size(); i++) {
                ProgressManager.checkCanceled();
                String text = tokens.get(i).text;
                if (text.equals("(") || text.equals("[") || text.equals("{")) stack.push(i);
                else if (text.equals(")") || text.equals("]") || text.equals("}")) {
                    // An unfinished expression inside a block must not hide that block's closing brace.
                    while (!stack.isEmpty() && !matches(tokens.get(stack.peek()).text, text)) stack.pop();
                    if (!stack.isEmpty()) {
                        int open = stack.pop();
                        pairs.put(open, i);
                        pairs.put(i, open);
                    }
                }
            }
        }

        boolean matches(String open, String close) {
            return open.equals("(") && close.equals(")") || open.equals("[") && close.equals("]") || open.equals("{") && close.equals("}");
        }

        void parseRange(int from, int to, int scopeEnd, Scope scope, Map<String, Candidate> fields) {
            for (int i = from; i < to;) {
                ProgressManager.checkCanceled();
                if (is(i, "namespace")) {
                    // Namespace qualification requires name resolution; do not leak its contents globally.
                    int body = find(i + 1, to, "{", ";");
                    i = is(body, "{") ? close(body, to) + 1 : Math.max(i + 1, body + 1);
                    continue;
                }
                if (is(i, "struct") || is(i, "class") || is(i, "cbuffer") || is(i, "tbuffer")) {
                    int next = parseStructure(i, to, scopeEnd, scope);
                    if (next > i) { i = next; continue; }
                }
                if (is(i, "for") && is(i + 1, "(")) {
                    int headerEnd = close(i + 1, to);
                    int body = headerEnd + 1;
                    int bodyEnd = is(body, "{") ? close(body, to) : statementEnd(body, to);
                    int loopEnd = bodyEnd < tokens.size() ? tokens.get(bodyEnd).end : scopeEnd;
                    parseDeclaration(i + 2, headerEnd, loopEnd, Scope.LOCAL, null);
                    if (is(body, "{")) parseRange(body + 1, bodyEnd, loopEnd, Scope.LOCAL, null);
                    else parseRange(body, bodyEnd, loopEnd, Scope.LOCAL, null);
                    i = Math.max(i + 1, bodyEnd + 1);
                    continue;
                }
                int next = parseDeclaration(i, to, scopeEnd, scope, fields);
                if (next > i) { i = next; continue; }
                if (is(i, "{")) {
                    int end = close(i, to);
                    parseRange(i + 1, end, end < tokens.size() ? tokens.get(end).end : scopeEnd, Scope.LOCAL, null);
                    i = end + 1;
                } else if (is(i, "(") || is(i, "[")) i = close(i, to) + 1;
                else i++;
            }
        }

        int parseStructure(int i, int to, int scopeEnd, Scope scope) {
            if (!name(i + 1)) return i;
            String kind = tokens.get(i).text;
            String name = tokens.get(i + 1).text;
            int body = find(i + 2, to, "{", ";");
            if (!is(body, "{")) return i;
            int end = close(body, to);
            if (kind.equals("cbuffer") || kind.equals("tbuffer")) {
                parseRange(body + 1, end, scopeEnd, scope, null);
            } else {
                add(i + 1, name, Kind.TYPE, scopeEnd, scope, null);
                document(i + 1, Kind.TYPE, tokens.get(i).start, tokens.get(body).start);
                Map<String, Candidate> fields = new LinkedHashMap<>();
                if (tokens.get(i + 1).end <= completionOffset && completionOffset < scopeEnd) structures.put(name, fields);
                parseRange(body + 1, end, scopeEnd, Scope.FIELDS, fields);
            }
            return end + 1;
        }

        int parseDeclaration(int from, int to, int scopeEnd, Scope scope, Map<String, Candidate> fields) {
            int i = from;
            boolean typedef = is(i, "typedef");
            if (typedef) i++;
            while (i < to && QUALIFIERS.contains(tokens.get(i).text)) i++;
            int typeStart = i;
            if (i >= to || (!name(i) && tokens.get(i).type != HlslTokenTypes.TYPE_KEYWORD)) return from;
            StringBuilder type = new StringBuilder(tokens.get(i++).text);
            if (is(i, "<")) {
                int depth = 0;
                do {
                    String token = tokens.get(i++).text;
                    type.append(token);
                    if (token.equals("<")) depth++;
                    else if (token.equals(">")) depth--;
                    else if (token.equals(">>")) depth -= 2;
                    if (token.equals(";") || token.equals("{")) return from;
                } while (i < to && depth > 0);
                if (depth != 0) return from;
            }
            if (i >= to || !name(i) || i == typeStart) return from;
            int declaredName = i++;
            if (is(i, "(")) {
                if (scope != Scope.GLOBAL || typedef) return from;
                int paramsEnd = close(i, to);
                if (paramsEnd >= to) return from;
                int after = paramsEnd + 1;
                if (is(after, ":")) {
                    semanticColons.add(tokens.get(after).start);
                    after = find(after + 1, to, "{", ";");
                }
                if (!is(after, "{") && !is(after, ";")) return from;
                add(declaredName, type.toString(), Kind.FUNCTION, scopeEnd, scope, null);
                declare(declaredName, type.toString(), i + 1, paramsEnd);
                declarationParens.add(i);
                // The attributes belong to the declaration: [numthreads(8, 8, 1)] stands between a compute
                // shader and the comment that documents it.
                document(declaredName, Kind.FUNCTION, attributesStart(from, typeStart), tokens.get(paramsEnd).end);
                if (!is(after, "{")) return after + 1;
                int bodyEnd = close(after, to);
                int functionEnd = bodyEnd < tokens.size() ? tokens.get(bodyEnd).end : scopeEnd;
                if (!exportsOnly) {
                    for (int p = i + 1; p < paramsEnd;) {
                        int next = parseDeclaration(p, paramsEnd, functionEnd, Scope.LOCAL, null);
                        p = next > p ? next : p + 1;
                    }
                    parseRange(after + 1, bodyEnd, functionEnd, Scope.LOCAL, null);
                }
                return bodyEnd + 1;
            }
            while (true) {
                String declaredType = type.toString();
                while (is(i, "[")) {
                    declaredType += "[]";
                    i = close(i, to) + 1;
                }
                if (i < to && !is(i, ";") && !is(i, ",") && !is(i, ":") && !is(i, "=") && !is(i, ")")) return from;
                add(declaredName, declaredType, typedef ? Kind.TYPE : Kind.VARIABLE, scopeEnd, scope, fields);
                int declarationEnd = find(i, to, ";", ",");
                document(declaredName, typedef ? Kind.TYPE : Kind.VARIABLE, tokens.get(typeStart).start,
                        declarationEnd < tokens.size() ? tokens.get(declarationEnd).start : tokens.get(i).end);
                if (is(i, ":")) semanticColons.add(tokens.get(i).start);
                while (i < to && !is(i, ";") && !is(i, ",")) {
                    if (is(i, "(") || is(i, "[") || is(i, "{")) i = close(i, to) + 1;
                    else i++;
                }
                if (!is(i, ",")) return Math.min(i + 1, to);
                if (!name(i + 1)) return i + 1;
                // A parameter comma introduces a new type, whereas a variable comma reuses its type.
                if (name(i + 2) || i + 1 < to && QUALIFIERS.contains(tokens.get(i + 1).text)
                        || i + 1 < to && tokens.get(i + 1).type == HlslTokenTypes.TYPE_KEYWORD) return i + 1;
                declaredName = i + 1;
                i += 2;
            }
        }

        void add(int token, String type, Kind kind, int scopeEnd, Scope scope, Map<String, Candidate> fields) {
            Token name = tokens.get(token);
            if (name.text.contains("IntellijIdeaRulezzz")) return;
            Candidate candidate = new Candidate(name.text, type, scope == Scope.FIELDS ? Kind.FIELD : kind);
            if (scope == Scope.FIELDS) {
                if (fields != null) fields.put(name.text, candidate);
            } else symbols.add(new Symbol(candidate, name.end, scopeEnd, scope == Scope.GLOBAL));
        }

        /** Records a declaration's own text plus the comment block directly above it. */
        void document(int nameToken, Kind kind, int startOffset, int endOffset) {
            document(tokens.get(nameToken).text, kind, startOffset, endOffset);
        }

        void document(String name, Kind kind, int startOffset, int endOffset) {
            if (declarations.containsKey(name)) return;
            int commentFrom = commentStart(source, startOffset);
            declarations.put(name, new Declaration(name, kind,
                    source.subSequence(commentFrom, startOffset).toString().strip(),
                    source.subSequence(startOffset, Math.min(endOffset, source.length())).toString().strip()));
        }

        /** Remembers a function's parameter list so parameter info can show it. */
        void declare(int nameToken, String returnType, int from, int to) {
            String name = tokens.get(nameToken).text;
            Signature signature = new Signature(name, returnType, parameters(from, to));
            List<Signature> overloads = functions.computeIfAbsent(name, key -> new ArrayList<>());
            if (!overloads.contains(signature)) overloads.add(signature);
        }

        /** Parses "type name" pairs; qualifiers, semantics and default values are skipped. */
        List<Parameter> parameters(int from, int to) {
            List<Parameter> parameters = new ArrayList<>();
            if (is(from, "void")) return parameters;
            int i = from;
            while (i < to) {
                while (i < to && QUALIFIERS.contains(tokens.get(i).text)) i++;
                if (i >= to) break;
                if (!name(i) && tokens.get(i).type != HlslTokenTypes.TYPE_KEYWORD) {
                    i++;
                    continue;
                }
                StringBuilder type = new StringBuilder(tokens.get(i++).text);
                if (is(i, "<")) {
                    int depth = 0;
                    while (i < to) {
                        String token = tokens.get(i++).text;
                        type.append(token);
                        if (token.equals("<")) {
                            depth++;
                        } else if (token.equals(">")) {
                            if (--depth == 0) break;
                        } else if (token.equals(">>")) {
                            break;
                        }
                    }
                }
                while (is(i, "[")) {
                    type.append("[]");
                    i = close(i, to) + 1;
                }
                String parameterName = "";
                if (i < to && name(i)) parameterName = tokens.get(i++).text;
                parameters.add(new Parameter(type.toString(), parameterName));
                // Skip the rest of this parameter: array sizes, semantics, default values.
                while (i < to && !is(i, ",")) {
                    if (is(i, "(") || is(i, "[") || is(i, "{")) i = close(i, to) + 1;
                    else if (is(i, ")")) break;
                    else i++;
                }
                if (is(i, ",")) i++;
            }
            return parameters;
        }

        int statementEnd(int from, int to) {            for (int i = from; i < to; i++) {
                if (is(i, ";")) return i;
                if (is(i, "(") || is(i, "[") || is(i, "{")) i = close(i, to);
            }
            return to;
        }

        int find(int from, int to, String first, String second) {
            for (int i = from; i < to; i++) {
                if (is(i, first) || is(i, second)) return i;
            }
            return to;
        }

        int close(int open, int fallback) { return pairs.getOrDefault(open, fallback); }
        boolean is(int i, String text) { return i >= 0 && i < tokens.size() && tokens.get(i).text.equals(text); }
        boolean name(int i) {
            if (i < 0 || i >= tokens.size()) return false;
            IElementType type = tokens.get(i).type;
            // A type keyword can name a call as well: `float3(...)`, `int2(...)` and `uint2(...)` are
            // constructors, and an expression such as gGBuffer1[uint2(pixel)] is built out of one.
            return type == HlslTokenTypes.IDENTIFIER || type == HlslTokenTypes.STRUCT_NAME
                    || type == HlslTokenTypes.FUNCTION_CALL || type == HlslTokenTypes.FIELD_ACCESS
                    || type == HlslTokenTypes.SEMANTIC || type == HlslTokenTypes.BUILTIN_FUNCTION
                    || type == HlslTokenTypes.INSTANCE_METHOD_CALL || type == HlslTokenTypes.TYPE_KEYWORD;
        }

        /**
         * True when the bracket at {@code open} starts an attribute such as {@code [numthreads(8, 8, 1)]} or
         * {@code [unroll]} rather than an index. An attribute stands where a declaration or a statement begins,
         * so what comes before it ends a statement, or is another attribute.
         */
        boolean opensAttribute(int open) {
            int before = open - 1;
            if (before < 0) return true;
            String text = tokens.get(before).text;
            if (text.equals(";") || text.equals("{") || text.equals("}")) return true;
            if (!text.equals("]")) return false;
            Integer previous = pairs.get(before);
            return previous != null && previous < before && is(previous, "[") && opensAttribute(previous);
        }

        /** The offset of the attributes written before the declaration at {@code from}, or of {@code typeStart}. */
        int attributesStart(int from, int typeStart) {
            int first = from;
            while (is(first - 1, "]")) {
                Integer open = pairs.get(first - 1);
                if (open == null || open >= first - 1 || !is(open, "[") || !opensAttribute(open)) break;
                first = open;
            }
            return tokens.get(first < from ? first : typeStart).start;
        }

        /**
         * True when the colon at {@code colon} introduces a semantic. The declaration parser marks the colons of
         * the declarations it recognised, but a function whose body is not written yet, as in
         * {@code void main(uint3 id : SV_|)}, is not one of them, so the colon is also read from what stands
         * before it: a name after its type ({@code uint3 id :}), or a parameter list after a return type and a
         * function name ({@code float4 main(...) :}). The operand of a ternary or a case label has no type
         * before it.
         */
        boolean isSemanticColon(int colon) {
            if (semanticColons.contains(tokens.get(colon).start)) return true;
            int before = colon - 1;
            // An array declarator: float2 uv[2] : TEXCOORD0.
            while (is(before, "]")) {
                Integer open = pairs.get(before);
                if (open == null || open >= before) return false;
                before = open - 1;
            }
            if (is(before, ")")) {
                Integer open = pairs.get(before);
                if (open == null || open >= before) return false;
                before = open - 1;
            }
            return name(before) && endsType(before - 1);
        }

        /** True when the token ends a type: a type keyword, a type name, or the > of a template argument list. */
        boolean endsType(int i) {
            if (i < 0 || i >= tokens.size()) return false;
            Token token = tokens.get(i);
            return token.type == HlslTokenTypes.TYPE_KEYWORD || token.type == HlslTokenTypes.IDENTIFIER
                    || token.type == HlslTokenTypes.STRUCT_NAME || token.text.equals(">");
        }

        int before(int offset) {
            int i = tokens.size() - 1;
            while (i >= 0 && tokens.get(i).end > offset) i--;
            return i;
        }

        /**
         * True when the caret sits where a newly declared name belongs, as in {@code int2 |pixel} or
         * {@code out MaterialConstants |mat}. The run of tokens before the caret must read as
         * "type, optionally one name" and nothing else, otherwise the position is an ordinary expression.
         */
        boolean isDeclaratorPosition(int prefixStart, Map<String, Candidate> visible,
                                     Map<String, Map<String, Candidate>> structures) {
            int i = before(prefixStart);
            if (i < 0) return false;
            boolean sawType = false;
            int names = 0;
            for (int guard = 0; i >= 0 && guard < 64; guard++) {
                Token token = tokens.get(i);
                if (token.text.equals(">")) {
                    int open = matchingAngle(i);
                    if (open < 0) return false;
                    i = open - 1;
                    continue;
                }
                if (QUALIFIERS.contains(token.text)) { i--; continue; }
                if (token.type == HlslTokenTypes.TYPE_KEYWORD) { sawType = true; i--; continue; }
                if (isKnownType(token.text, visible, structures)) { sawType = true; i--; continue; }
                if (name(i)) { names++; i--; continue; }
                break;
            }
            return sawType && names <= 2;
        }

        /** Finds the {@code <} opening the template argument list closed by {@code close}, or -1. */
        int matchingAngle(int close) {
            int depth = 0;
            for (int i = close; i >= 0; i--) {
                String text = tokens.get(i).text;
                if (text.equals(">>")) { depth += 2; continue; }
                if (text.equals(">")) { depth++; continue; }
                if (text.equals("<")) {
                    if (--depth == 0) return i;
                    continue;
                }
                if (depth == 1 && (text.equals(";") || text.equals("{") || text.equals("}")
                        || text.equals("=") || text.equals("(") || text.equals(")"))) return -1;
            }
            return -1;
        }

        boolean isKnownType(String text, Map<String, Candidate> visible,
                            Map<String, Map<String, Candidate>> structures) {
            if (structures.containsKey(text)) return true;
            Candidate symbol = visible.get(text);
            return symbol != null && symbol.kind == Kind.TYPE;
        }

        /**
         * The type a built-in name produces when it is called. {@code float3(...)} and {@code int2(...)} are
         * constructors that yield their own type, and a resource method such as {@code Load} or {@code Sample}
         * yields the element type of the resource it is called on, so {@code gGBuffer1.Load(...).} can offer
         * swizzles even though no declaration of {@code Load} exists anywhere.
         */
        String builtinResultType(String name, String receiverType,
                                 Map<String, Map<String, Candidate>> structures) {
            Matcher vector = VECTOR.matcher(name);
            if (vector.matches()) return name;
            // A resource method needs its receiver: without a known type there is nothing to ask, and
            // guessing float4 here would offer swizzles that the resource may not even have.
            if (receiverType == null) return null;
            Candidate method = members(receiverType, structures).get(name);
            return method == null || method.type == null || method.type.equals("void") ? null : method.type;
        }

        String expressionType(int end, Map<String, Candidate> visible, Map<String, Map<String, Candidate>> structures) {
            if (is(end, "]")) {
                Integer open = pairs.get(end);
                if (open == null) return null;
                String receiver = expressionType(open - 1, visible, structures);
                if (receiver == null) return null;
                if (receiver.endsWith("[]")) return receiver.substring(0, receiver.length() - 2);
                for (String buffer : List.of("StructuredBuffer<", "RWStructuredBuffer<", "Buffer<", "RWBuffer<")) {
                    if (receiver.startsWith(buffer) && receiver.endsWith(">")) return receiver.substring(buffer.length(), receiver.length() - 1);
                }
                // Indexing a resource such as Texture2D<float4> or RWTexture2D<float> yields its element type.
                String element = typeArgument(receiver);
                if (element != null) return element;
                Matcher vector = VECTOR.matcher(receiver);
                return vector.matches() ? vector.group(1) : null;
            }
            if (is(end, ")")) {
                // A call yields its callee's return type, which keeps `tex.Sample(s, uv).rgb` resolvable.
                Integer open = pairs.get(end);
                if (open == null || open == 0) return null;
                return callResultType(open, visible, structures);
            }
            if (!name(end)) return null;
            Candidate symbol;
            if (is(end - 1, ".")) {
                String receiver = expressionType(end - 2, visible, structures);
                symbol = members(receiver, structures).get(tokens.get(end).text);
            } else symbol = visible.get(tokens.get(end).text);
            return symbol == null || symbol.kind == Kind.FUNCTION || symbol.kind == Kind.TYPE ? null : symbol.type;
        }

        /**
         * The type the call with this opening parenthesis produces. The callee is either a method called on
         * the receiver in front of it, or a name of its own: a declared function, or a constructor and
         * resource method the language provides without a declaration.
         */
        String callResultType(int open, Map<String, Candidate> visible,
                             Map<String, Map<String, Candidate>> structures) {
            if (open == 0) return null;
            String calleeName = tokens.get(open - 1).text;
            if (is(open - 2, ".")) {
                String receiver = expressionType(open - 3, visible, structures);
                Candidate declared = members(receiver, structures).get(calleeName);
                if (declared != null) {
                    if (declared.kind == Kind.TYPE || declared.kind == Kind.VARIABLE) return null;
                    return useable(declared.type);
                }
                // A resource method such as Load or Sample is an intrinsic: its result type follows from the
                // type of the resource it is called on, not from any declaration.
                return builtinResultType(calleeName, receiver, structures);
            }
            if (name(open - 1)) {
                Candidate declared = visible.get(calleeName);
                if (declared != null) return declared.kind == Kind.FUNCTION ? useable(declared.type) : null;
            }
            // A constructor such as `float3(...)` names a type rather than a function.
            return builtinResultType(calleeName, null, structures);
        }

        /** The type a call produces, or {@code null} when it produces nothing a member can be read from. */
        String useable(String type) {
            return type == null || type.equals("void") ? null : type;
        }
    }
}
