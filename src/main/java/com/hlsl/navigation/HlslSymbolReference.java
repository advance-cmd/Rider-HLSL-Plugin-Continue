package com.hlsl.navigation;

import com.hlsl.completion.HlslIncludeIndex;
import com.hlsl.psi.HlslTokenTypes;
import com.hlsl.validation.HlslCompileRoot;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReferenceBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HlslSymbolReference extends PsiReferenceBase<PsiElement> {

    private static final Logger LOG = Logger.getInstance(HlslSymbolReference.class);

    private static final Pattern WORD = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private static final Pattern INCLUDE_PATTERN = Pattern.compile(
            "(?m)^\\s*#\\s*include\\s+[\"<]([^\">]+)[\">]"
    );

    private static final int MAX_INCLUDE_DEPTH = 16;

    public HlslSymbolReference(@NotNull PsiElement element) {
        super(element, new TextRange(0, element.getTextLength()), false);
    }

    @Override
    public @Nullable PsiElement resolve() {
        PsiElement element = getElement();
        String symbol = element.getText();
        if (symbol == null || symbol.isBlank()) return null;

        PsiFile file = element.getContainingFile();
        if (file == null) return null;

        String tokenName = element.getNode() != null ? element.getNode().getElementType().toString() : "";
        boolean preferFunction = "HlslTokenType.FUNCTION_CALL".equals(tokenName)
                || "HlslTokenType.INSTANCE_METHOD_CALL".equals(tokenName);
        int usageOffset = element.getTextRange().getStartOffset();

        LOG.debug("HLSL resolve: symbol=" + symbol + " token=" + tokenName
                + " file=" + (file.getVirtualFile() != null ? file.getVirtualFile().getPath() : "<in-memory>"));

        PsiElement found = findInFile(file, symbol, preferFunction, usageOffset);
        if (found != null) {
            LOG.debug("HLSL resolve: found in current file at offset " + found.getTextRange().getStartOffset());
            return found;
        }

        Set<String> visited = new HashSet<>();
        PsiElement viaInclude = findInIncludes(file, symbol, preferFunction, visited, 0, Integer.MAX_VALUE);
        LOG.debug("HLSL resolve: include search " + (viaInclude != null
                ? ("found in " + viaInclude.getContainingFile().getVirtualFile().getPath())
                : "returned null"));
        if (viaInclude != null) return viaInclude;

        PsiElement viaIncluder = findInIncluders(file, symbol, preferFunction, visited);
        LOG.debug("HLSL resolve: includer search " + (viaIncluder != null
                ? ("found in " + viaIncluder.getContainingFile().getVirtualFile().getPath())
                : "returned null"));
        return viaIncluder;
    }

    private static @Nullable PsiElement findInFile(PsiFile file,
                                                   String symbol,
                                                   boolean preferFunction,
                                                   int usageOffset) {
        String content = file.getText();
        if (content == null || content.isEmpty()) return null;

        int decl;
        if (preferFunction) {
            decl = findFunctionDeclarationOffset(content, symbol, usageOffset);
            if (decl < 0) decl = findVariableDeclarationOffset(content, symbol, usageOffset);
        } else {
            decl = findVariableDeclarationOffset(content, symbol, usageOffset);
            if (decl < 0) decl = findFunctionDeclarationOffset(content, symbol, usageOffset);
        }
        if (decl < 0) return null;
        return findSymbolElementAt(file, decl, symbol);
    }

    /**
     * A helper such as Shadow.hlsl uses what the shader including it declares before its {@code #include} of
     * it, so the shaders on the chain down to the file are searched as well, the nearest one first, each only
     * above that include: its own text, then what that text includes.
     */
    private static @Nullable PsiElement findInIncluders(PsiFile file,
                                                        String symbol,
                                                        boolean preferFunction,
                                                        Set<String> visited) {
        VirtualFile vf = file.getVirtualFile();
        if (vf == null) return null;
        HlslCompileRoot roots = file.getProject().getService(HlslCompileRoot.class);
        HlslCompileRoot.Chain chain = roots == null ? null : roots.findFor(vf, file.getText());
        if (chain == null) return null;
        List<VirtualFile> files = chain.files();
        for (int i = files.size() - 2; i >= 0; i--) {
            PsiFile includer = PsiManager.getInstance(file.getProject()).findFile(files.get(i));
            if (includer == null) continue;
            int includeOffset = HlslIncludeIndex.includeOffset(includer.getText(), files.get(i), files.get(i + 1));
            if (includeOffset < 0) continue;
            PsiElement found = findInFile(includer, symbol, preferFunction, includeOffset);
            if (found != null) return found;
            PsiElement deeper = findInIncludes(includer, symbol, preferFunction, visited, 0, includeOffset);
            if (deeper != null) return deeper;
        }
        return null;
    }

    /** Searches the files {@code file} includes before {@code limit}, and everything they include. */
    private static @Nullable PsiElement findInIncludes(PsiFile file,
                                                       String symbol,
                                                       boolean preferFunction,
                                                       Set<String> visited,
                                                       int depth,
                                                       int limit) {
        if (depth > MAX_INCLUDE_DEPTH) return null;
        VirtualFile vf = file.getVirtualFile();
        if (vf == null) return null;
        if (!visited.add(vf.getPath())) return null;
        VirtualFile baseDir = vf.getParent();
        if (baseDir == null) return null;

        String content = file.getText();
        if (content == null) return null;

        Matcher m = INCLUDE_PATTERN.matcher(content);
        while (m.find() && m.start(1) < limit) {
            String includePath = m.group(1);
            VirtualFile included = baseDir.findFileByRelativePath(includePath);
            if (included == null || !included.exists() || included.isDirectory()) {
                LOG.debug("HLSL include: cannot resolve '" + includePath + "' from " + baseDir.getPath());
                continue;
            }
            PsiFile includedPsi = PsiManager.getInstance(file.getProject()).findFile(included);
            if (includedPsi == null) {
                LOG.debug("HLSL include: PsiManager.findFile returned null for " + included.getPath());
                continue;
            }

            LOG.debug("HLSL include: searching " + included.getPath() + " for '" + symbol + "'");
            PsiElement found = findInFile(includedPsi, symbol, preferFunction, Integer.MAX_VALUE);
            if (found != null) return found;

            PsiElement deeper = findInIncludes(includedPsi, symbol, preferFunction, visited, depth + 1,
                    Integer.MAX_VALUE);
            if (deeper != null) return deeper;
        }
        return null;
    }

    private static int findFunctionDeclarationOffset(String content, String name, int usageOffset) {
        String escaped = Pattern.quote(name);

        Pattern pattern = Pattern.compile(
                "(?m)(?:^|[;{}])\\s*(?:(?:inline|static|precise|const|extern|groupshared|uniform|export)\\s+)*" +
                        "(?:[A-Za-z_][A-Za-z0-9_]*(?:\\s*<[^>]+>)?|[A-Za-z_][A-Za-z0-9_]*\\s*[0-9]*x?[0-9]*)\\s+" +
                        "(" + escaped + ")\\s*\\("
        );

        Matcher m = pattern.matcher(content);
        int best = -1;
        while (m.find()) {
            int nameOffset = m.start(1);
            if (nameOffset >= usageOffset) break;

            int openParen = content.indexOf('(', m.end(1) - 1);
            if (openParen < 0) continue;
            int closeParen = findMatchingParen(content, openParen);
            if (closeParen < 0) continue;

            int next = skipWhitespace(content, closeParen + 1);
            if (next >= content.length()) continue;

            char tail = content.charAt(next);
            if (tail == '{' || tail == ';' || tail == ':') {
                best = nameOffset;
            }
        }
        return best;
    }

    private static int findVariableDeclarationOffset(String content, String name, int usageOffset) {
        String escaped = Pattern.quote(name);

        Pattern localOrGlobal = Pattern.compile(
                "(?m)(?:^|[;{}(,])\\s*" +
                        "(?:(?:const|static|groupshared|uniform|extern|in|out|inout|row_major|column_major|volatile|precise)\\s+)*" +
                        "(?:[A-Za-z_][A-Za-z0-9_]*(?:\\s*<[^>]+>)?|[A-Za-z_][A-Za-z0-9_]*\\s*[0-9]*x?[0-9]*)\\s+" +
                        "(" + escaped + ")\\b"
        );

        Matcher m = localOrGlobal.matcher(content);
        int best = -1;
        while (m.find()) {
            int nameOffset = m.start(1);
            if (nameOffset >= usageOffset) break;

            if (isInsideCommentOrString(content, nameOffset)) continue;
            best = nameOffset;
        }

        return best;
    }

    private static @Nullable PsiElement findSymbolElementAt(PsiFile file, int offset, String symbol) {
        PsiElement direct = file.findElementAt(offset);
        if (direct != null && symbol.equals(direct.getText())) return direct;

        for (int i = Math.max(0, offset - 2); i <= offset + 2; i++) {
            PsiElement e = file.findElementAt(i);
            if (e != null && symbol.equals(e.getText())) {
                return e;
            }
        }
        return null;
    }

    private static int skipWhitespace(String text, int from) {
        int i = from;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    private static int findMatchingParen(String text, int openIndex) {
        int depth = 0;
        for (int i = openIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static boolean isInsideCommentOrString(String content, int offset) {
        boolean inString = false;
        boolean inBlock = false;
        boolean inLine = false;

        for (int i = 0; i < content.length() && i < offset; i++) {
            char c = content.charAt(i);
            char n = i + 1 < content.length() ? content.charAt(i + 1) : '\0';

            if (inLine) {
                if (c == '\n') inLine = false;
                continue;
            }
            if (inBlock) {
                if (c == '*' && n == '/') {
                    inBlock = false;
                    i++;
                }
                continue;
            }
            if (inString) {
                if (c == '\\' && i + 1 < content.length()) {
                    i++;
                    continue;
                }
                if (c == '"') inString = false;
                continue;
            }

            if (c == '/' && n == '/') {
                inLine = true;
                i++;
                continue;
            }
            if (c == '/' && n == '*') {
                inBlock = true;
                i++;
                continue;
            }
            if (c == '"') {
                inString = true;
            }
        }

        return inString || inBlock || inLine;
    }

    @Override
    public Object @NotNull [] getVariants() {
        return new Object[0];
    }
}

