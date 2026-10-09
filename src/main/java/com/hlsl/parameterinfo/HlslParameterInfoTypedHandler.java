package com.hlsl.parameterinfo;

import com.hlsl.HlslLanguage;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.codeInsight.hint.ShowParameterInfoHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.application.ModalityState;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/** Opens the parameter popup as soon as an argument list is typed, not only on Ctrl+P. */
public final class HlslParameterInfoTypedHandler extends TypedHandlerDelegate {

    @Override
    public @NotNull Result charTyped(char c, @NotNull Project project, @NotNull Editor editor,
                                     @NotNull PsiFile file) {
        if ((c != '(' && c != ',') || file.getLanguage() != HlslLanguage.INSTANCE) return Result.CONTINUE;
        int offset = editor.getCaretModel().getOffset();
        // The document is still being updated while the char is handled, so show the hint afterwards.
        ApplicationManager.getApplication().invokeLater(
                () -> ShowParameterInfoHandler.invoke(project, editor, file, offset, null, false),
                ModalityState.current());
        return Result.CONTINUE;
    }
}
