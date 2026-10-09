package com.hlsl.documentation;

import com.hlsl.HlslLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/** Attaches the hover tooltip to HLSL editors and detaches it again when they close. */
public final class HlslHoverTooltipRegistrar implements EditorFactoryListener {

    private static final HlslHoverTooltipListener LISTENER = new HlslHoverTooltipListener();

    @Override
    public void editorCreated(@NotNull EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        if (isHlsl(editor)) editor.addEditorMouseMotionListener(LISTENER);
    }

    @Override
    public void editorReleased(@NotNull EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        if (isHlsl(editor)) editor.removeEditorMouseMotionListener(LISTENER);
    }

    private static boolean isHlsl(Editor editor) {
        PsiFile file = editor.getProject() == null ? null
                : com.intellij.psi.PsiDocumentManager.getInstance(editor.getProject())
                        .getPsiFile(editor.getDocument());
        return file != null && file.getLanguage() == HlslLanguage.INSTANCE;
    }
}
