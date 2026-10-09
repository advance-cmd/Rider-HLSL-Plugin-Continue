package com.hlsl.documentation;

import com.hlsl.HlslLanguage;
import com.hlsl.completion.HlslCompletionAnalysis;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import java.awt.event.MouseEvent;

/**
 * Shows a declaration on plain mouse hover.
 *
 * <p>The platform only asks a language plugin for hover documentation when its own hover machinery reaches
 * the file, which does not happen for shaders in every host IDE, so the tooltip is drawn here instead. It
 * stays quiet for a few seconds after the platform answered a documentation request itself, so the two
 * never show two popups at once.</p>
 */
final class HlslHoverTooltipListener implements EditorMouseMotionListener {

    private static final int HOVER_DELAY_MS = 600;
    private static final int HINT_TIMEOUT_MS = 6000;
    private static final int HINT_FLAGS = HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE
            | HintManager.HIDE_BY_SCROLLING | HintManager.HIDE_IF_OUT_OF_EDITOR | HintManager.HIDE_BY_CARET_MOVE;

    private final Alarm alarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

    @Override
    public void mouseMoved(@NotNull EditorMouseEvent event) {
        alarm.cancelAllRequests();
        if (event.isConsumed() || event.getArea() != EditorMouseEventArea.EDITING_AREA) return;
        int offset = event.getOffset();
        if (offset < 0) return;
        Editor editor = event.getEditor();
        MouseEvent mouse = event.getMouseEvent();
        // Wait for the mouse to rest, the way the platform's own hover does.
        alarm.addRequest(() -> showTooltip(editor, offset, mouse), HOVER_DELAY_MS);
    }

    private static void showTooltip(Editor editor, int offset, MouseEvent mouse) {
        if (editor.isDisposed() || HlslDocumentationLookup.answeredRecently()) return;
        Project project = editor.getProject();
        if (project == null) return;
        Document document = editor.getDocument();
        PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
        if (file == null || file.getLanguage() != HlslLanguage.INSTANCE) return;
        HlslCompletionAnalysis.Declaration declaration =
                HlslDocumentationLookup.declarationAt(file, document.getImmutableCharSequence(), offset);
        if (declaration == null) return;
        if (mouse.getComponent() == null || !mouse.getComponent().isShowing()) return;

        JLabel label = new JLabel(HlslDocumentationLookup.html(declaration));
        label.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        HintManager.getInstance().showHint(label, new RelativePoint(mouse), HINT_FLAGS, HINT_TIMEOUT_MS);
    }
}
