package com.hlsl.parameterinfo;

import com.hlsl.HlslLanguage;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/** Opens the parameter popup as soon as an argument list is typed, not only on a shortcut. */
public final class HlslParameterInfoTypedHandler extends TypedHandlerDelegate {

    @Override
    public @NotNull Result charTyped(char c, @NotNull Project project, @NotNull Editor editor,
                                     @NotNull PsiFile file) {
        if ((c != '(' && c != ',') || file.getLanguage() != HlslLanguage.INSTANCE) return Result.CONTINUE;
        // The document is still being modified while the char is handled, so ask for the hint afterwards.
        ApplicationManager.getApplication().invokeLater(
                () -> HlslParameterInfoHandler.showAt(project, editor, file), ModalityState.current());
        return Result.CONTINUE;
    }
}
