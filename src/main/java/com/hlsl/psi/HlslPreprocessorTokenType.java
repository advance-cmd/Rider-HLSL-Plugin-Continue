package com.hlsl.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.ILeafElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/** The token of a preprocessor directive, built as a leaf that can carry the reference of an include. */
public final class HlslPreprocessorTokenType extends HlslTokenType implements ILeafElementType {

    public HlslPreprocessorTokenType(@NonNls @NotNull String debugName) {
        super(debugName);
    }

    @Override
    public @NotNull ASTNode createLeafNode(@NotNull CharSequence leafText) {
        return new HlslPreprocessorElement(this, leafText);
    }
}
