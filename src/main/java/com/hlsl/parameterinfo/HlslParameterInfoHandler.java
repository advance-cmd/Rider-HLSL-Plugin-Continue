package com.hlsl.parameterinfo;

import com.hlsl.HlslLanguage;
import com.hlsl.completion.HlslCompletionAnalysis;
import com.hlsl.completion.HlslIncludeIndex;
import com.intellij.lang.parameterInfo.CreateParameterInfoContext;
import com.intellij.lang.parameterInfo.ParameterInfoContext;
import com.intellij.lang.parameterInfo.ParameterInfoHandler;
import com.intellij.lang.parameterInfo.ParameterInfoUIContext;
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Shows the parameter list of the function being called and highlights the argument under the caret.
 *
 * <p>The file is read as text through {@link HlslCompletionAnalysis}, which already locates calls and
 * declarations, so this handler needs no PSI structure of its own.</p>
 */
public final class HlslParameterInfoHandler
        implements ParameterInfoHandler<PsiFile, HlslCompletionAnalysis.Signature> {

    @Override
    public @Nullable PsiFile findElementForParameterInfo(@NotNull CreateParameterInfoContext context) {
        HlslCompletionAnalysis.CallInfo call = callAt(context);
        if (call == null) return null;
        context.setItemsToShow(call.signatures().toArray());
        return context.getFile();
    }

    @Override
    public void showParameterInfo(@NotNull PsiFile element, @NotNull CreateParameterInfoContext context) {
        context.showHint(element, context.getOffset(), this);
    }

    @Override
    public @Nullable PsiFile findElementForUpdatingParameterInfo(@NotNull UpdateParameterInfoContext context) {
        return callAt(context) == null ? null : context.getFile();
    }

    @Override
    public void updateParameterInfo(@NotNull PsiFile file, @NotNull UpdateParameterInfoContext context) {
        HlslCompletionAnalysis.CallInfo call = callAt(context);
        if (call == null) {
            context.removeHint();
            return;
        }
        context.setParameterOwner(file);
        context.setCurrentParameter(call.parameterIndex());
    }

    @Override
    public void updateUI(@NotNull HlslCompletionAnalysis.Signature signature,
                         @NotNull ParameterInfoUIContext context) {
        String label = signature.label();
        int index = context.getCurrentParameterIndex();
        boolean noArguments = index == 0 && signature.parameters().isEmpty();
        if (noArguments) {
            context.setUIComponentEnabled(true);
            context.setupUIComponentPresentation(label, 0, 0, false, false, true,
                    context.getDefaultParameterColor());
            return;
        }
        if (index < 0 || index >= signature.parameters().size()) {
            // More arguments than this overload accepts: the signature is shown as unusable.
            context.setUIComponentEnabled(false);
            context.setupUIComponentPresentation(label, 0, 0, false, false, true,
                    context.getDefaultParameterColor());
            return;
        }
        TextRange range = signature.parameterRange(index);
        context.setUIComponentEnabled(true);
        context.setupUIComponentPresentation(label, range.getStartOffset(), range.getEndOffset(), false, false,
                false, context.getDefaultParameterColor());
    }

    /** Several overloads can match one name, so the popup lets the user step through them. */
    @Override
    public boolean supportsOverloadSwitching() {
        return true;
    }

    @Override
    public boolean tracksParameterIndex() {
        return true;
    }

    private static HlslCompletionAnalysis.CallInfo callAt(ParameterInfoContext context) {
        PsiFile file = context.getFile();
        if (file.getLanguage() != HlslLanguage.INSTANCE) return null;
        Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        if (document == null) return null;
        return HlslCompletionAnalysis.callAt(document.getImmutableCharSequence(), context.getOffset(),
                includeSources(file));
    }

    private static List<CharSequence> includeSources(PsiFile file) {
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile == null) return List.of();
        HlslIncludeIndex index = file.getProject().getService(HlslIncludeIndex.class);
        // A callee is often declared by an included helper, exactly as for completion.
        return index == null ? List.of() : index.collectTransitiveSources(virtualFile);
    }
}
