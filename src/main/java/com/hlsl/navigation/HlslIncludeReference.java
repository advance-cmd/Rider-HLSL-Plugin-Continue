package com.hlsl.navigation;

import com.hlsl.completion.HlslIncludeIndex;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReferenceBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The path of an {@code #include}, so that Ctrl+Click on {@code "LightingUtil.hlsl"} opens that file. It is
 * resolved the way the include index resolves it, so navigation reaches the same file completion reads.
 *
 * <p>The range covers the path with its quotes or angle brackets, which is what Ctrl+hover underlines.</p>
 */
public final class HlslIncludeReference extends PsiReferenceBase<PsiElement> {

    private final String path;

    private HlslIncludeReference(@NotNull PsiElement directive, @NotNull TextRange range, @NotNull String path) {
        super(directive, range, true);
        this.path = path;
    }

    /** The reference of a preprocessor directive, or {@code null} when the directive is not an include. */
    public static @Nullable HlslIncludeReference of(@NotNull PsiElement directive) {
        String text = directive.getText();
        TextRange path = HlslIncludeIndex.includePathRange(text);
        if (path == null) return null;
        TextRange quoted = new TextRange(path.getStartOffset() - 1, Math.min(path.getEndOffset() + 1, text.length()));
        return new HlslIncludeReference(directive, quoted, path.substring(text));
    }

    @Override
    public @Nullable PsiElement resolve() {
        PsiFile file = getElement().getContainingFile();
        if (file == null) return null;
        VirtualFile from = file.getOriginalFile().getVirtualFile();
        if (from == null) return null;
        VirtualFile target = HlslIncludeIndex.resolve(from, path);
        return target == null ? null : PsiManager.getInstance(file.getProject()).findFile(target);
    }

    /**
     * The include is not a usage a refactoring may rewrite: the directive is a single preprocessor token, and
     * renaming or moving the file must not try to edit it.
     */
    @Override
    public boolean isReferenceTo(@NotNull PsiElement element) {
        return false;
    }

    @Override
    public Object @NotNull [] getVariants() {
        return EMPTY_ARRAY;
    }
}
