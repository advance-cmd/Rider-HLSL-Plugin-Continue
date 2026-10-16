package com.hlsl.psi;

import com.hlsl.navigation.HlslIncludeReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A preprocessor directive. The lexer keeps the whole line as one token, so the path of an {@code #include}
 * is a range inside it, and the reference over that range is what takes Ctrl+Click to the included file.
 */
public final class HlslPreprocessorElement extends LeafPsiElement {

    public HlslPreprocessorElement(@NotNull IElementType type, @NotNull CharSequence text) {
        super(type, text);
    }

    @Override
    public @Nullable PsiReference getReference() {
        return HlslIncludeReference.of(this);
    }

    @Override
    public PsiReference @NotNull [] getReferences() {
        PsiReference reference = getReference();
        return reference == null ? PsiReference.EMPTY_ARRAY : new PsiReference[]{reference};
    }
}
